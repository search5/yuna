package com.github.search5.yona.web

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUser
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.vcs.RepositoryService
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import java.util.Optional

import com.github.search5.yona.domain.project.ProjectService
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.attachment.AttachmentRepository
import com.github.search5.yona.domain.attachment.AttachmentService
import com.github.search5.yona.domain.organization.OrganizationRepository
import org.springframework.context.MessageSource
import com.github.search5.yona.domain.mail.MailService
import com.github.search5.yona.domain.support.MarkdownService
import com.github.search5.yona.domain.role.RoleRepository
import com.github.search5.yona.domain.project.ProjectTransferRepository
import com.github.search5.yona.domain.issue.IssueLabelService
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.domain.milestone.MilestoneRepository
import com.github.search5.yona.domain.project.RecentProjectRepository
import com.github.search5.yona.domain.watch.WatchService

class ProjectViewControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val repositoryService = mockk<RepositoryService>()
    val projectService = mockk<ProjectService>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val attachmentRepository = mockk<AttachmentRepository>()
    val attachmentService = mockk<AttachmentService>()
    val organizationRepository = mockk<OrganizationRepository>()
    val messageSource = mockk<MessageSource>()
    val mailService = mockk<MailService>()
    val markdownService = mockk<MarkdownService>()
    val roleRepository = mockk<RoleRepository>()
    val projectTransferRepository = mockk<ProjectTransferRepository>()
    val issueLabelService = mockk<IssueLabelService>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val milestoneRepository = mockk<MilestoneRepository>()
    val watchService = mockk<WatchService>()
    val recentProjectRepository = mockk<RecentProjectRepository>()

    val projectViewController = ProjectViewController(
        projectRepository,
        projectUserRepository,
        userRepository,
        repositoryService,
        projectService,
        organizationUserRepository,
        attachmentRepository,
        attachmentService,
        organizationRepository,
        messageSource,
        mailService,
        markdownService,
        roleRepository,
        projectTransferRepository,
        issueLabelService,
        issueRepository,
        postingRepository,
        pullRequestRepository,
        milestoneRepository,
        watchService,
        recentProjectRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(projectViewController)
        .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
        .build()

    beforeTest {
        io.mockk.clearMocks(
            projectRepository, projectUserRepository, userRepository, repositoryService, projectService,
            organizationUserRepository, attachmentRepository, attachmentService, organizationRepository,
            messageSource, mailService, markdownService, roleRepository, projectTransferRepository,
            issueLabelService, issueRepository, postingRepository, pullRequestRepository, milestoneRepository,
            watchService, recentProjectRepository
        )
    }

    describe("ProjectViewController 템플릿 연동 테스트") {
        val project = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PRIVATE)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val managerRole = Role(id = RoleType.MANAGER.roleType)
        val projectUser = ProjectUser(id = 100L, user = user, project = project, role = managerRole)

        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        describe("GET /{owner}/{projectName}") {
            it("비공개 프로젝트일 때 멤버라면 200 OK와 project/home 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectId(1L) } returns listOf(projectUser)
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { watchService.findWatchers(any(), any()) } returns emptySet()

                mockMvc.perform(get("/owner/TestProj").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/home"))
                    .andExpect(model().attributeExists("project", "projectUsers"))
            }

            it("프로젝트 멤버가 아닐 경우 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

                mockMvc.perform(get("/owner/TestProj").principal(userAuth))
                    .andExpect(view().name("error/403"))
            }
        }

        describe("GET /{owner}/{projectName}/members") {
            it("멤버라면 200 OK와 project/members 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectId(1L) } returns listOf(projectUser)

                mockMvc.perform(get("/owner/TestProj/members").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/members"))
                    .andExpect(model().attributeExists("project", "projectUsers"))
            }
        }

        describe("GET /{owner}/{projectName}/setting") {
            it("MANAGER 권한을 지닌 멤버라면 200 OK와 project/setting 뷰를 반환해야 한다") {
                val playRepository = mockk<com.github.search5.yona.domain.vcs.PlayRepository>()
                every { repositoryService.getRepository(project) } returns playRepository
                every { playRepository.getRefNames() } returns listOf("refs/heads/master")
                every { playRepository.getDefaultBranch() } returns "refs/heads/master"

                every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)

                mockMvc.perform(get("/owner/TestProj/setting").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/setting"))
                    .andExpect(model().attributeExists("project", "branches", "defaultBranch"))
            }

            it("MANAGER 권한이 없는 일반 멤버라면 403 Forbidden 뷰를 반환해야 한다") {
                val memberRole = Role(id = RoleType.MEMBER.roleType)
                val memberProjectUser = ProjectUser(id = 100L, user = user, project = project, role = memberRole)

                every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(memberProjectUser)

                mockMvc.perform(get("/owner/TestProj/setting").principal(userAuth))
                    .andExpect(view().name("error/403"))
            }
        }

        describe("GET /{owner}/{projectName}/changeVCS") {
            it("MANAGER 권한이 있는 멤버는 change_vcs 뷰를 반환받아야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)

                mockMvc.perform(get("/owner/TestProj/changeVCS").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/change_vcs"))
            }
        }

        describe("POST /{owner}/{projectName}/changeVCS") {
            it("MANAGER 권한을 지닌 멤버는 VCS 변경을 성공적으로 요청할 수 있어야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every { projectService.changeVCS(1L) } returns project

                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/owner/TestProj/changeVCS").principal(userAuth))
                    .andExpect(status().isNoContent)
            }
        }
    }
})
