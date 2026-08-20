package com.github.search5.yona.web

import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.milestone.Milestone
import com.github.search5.yona.domain.milestone.MilestoneRepository
import com.github.search5.yona.domain.milestone.MilestoneService
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.attachment.AttachmentRepository
import com.github.search5.yona.domain.support.MarkdownService
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import java.util.Optional

class MilestoneViewControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val milestoneService = mockk<MilestoneService>()
    val milestoneRepository = mockk<MilestoneRepository>()
    val issueRepository = mockk<IssueRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val attachmentRepository = mockk<AttachmentRepository>()
    val markdownService = mockk<MarkdownService>()

    val milestoneViewController = MilestoneViewController(
        projectRepository,
        milestoneService,
        milestoneRepository,
        issueRepository,
        projectUserRepository,
        userRepository,
        attachmentRepository,
        markdownService
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(milestoneViewController)
        .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
        .build()

    beforeTest {
        io.mockk.clearMocks(
            projectRepository,
            milestoneService,
            milestoneRepository,
            issueRepository,
            projectUserRepository,
            userRepository,
            attachmentRepository,
            markdownService
        )
    }

    describe("MilestoneViewController 템플릿 연동 테스트") {
        val project = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PRIVATE)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val milestone = Milestone(id = 2L, title = "마일스톤 테스트", project = project)

        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        describe("GET /{owner}/{projectName}/milestones") {
            it("비공개 프로젝트일 때 멤버라면 200 OK와 milestone/list 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestones(1L, State.OPEN) } returns listOf(milestone)
                every { issueRepository.findByMilestone(milestone) } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/milestones").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("milestone/list"))
                    .andExpect(model().attributeExists("project", "milestones", "state"))
            }

            // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57)
            it("직접 멤버가 아니어도 프로젝트가 속한 조직의 멤버라면 200 OK를 반환해야 한다") {
                val groupOrg = com.github.search5.yona.domain.organization.Organization(id = 1L, name = "org")
                groupOrg.organizationUsers.add(
                    com.github.search5.yona.domain.organization.OrganizationUser(
                        id = 1L, user = user, organization = groupOrg,
                        role = com.github.search5.yona.domain.role.Role(id = com.github.search5.yona.domain.role.RoleType.ORG_MEMBER.roleType)
                    )
                )
                val groupProject = Project(id = 8L, name = "group-project", owner = "owner", projectScope = ProjectScope.PROTECTED, organization = groupOrg)

                every { projectRepository.findByOwnerAndName("owner", "group-project") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(8L, 10L) } returns false
                every { milestoneService.getMilestones(8L, State.OPEN) } returns emptyList()

                mockMvc.perform(get("/owner/group-project/milestones").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("milestone/list"))
            }

            it("프로젝트 멤버가 아닐 경우 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

                mockMvc.perform(get("/owner/TestProj/milestones").principal(userAuth))
                    .andExpect(view().name("error/403"))
            }
        }

        describe("GET /{owner}/{projectName}/milestone/{id}") {
            it("멤버라면 200 OK와 milestone/view 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestone(2L) } returns milestone
                every { issueRepository.findByMilestone(milestone) } returns emptyList()
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.MILESTONE, "2") } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/milestone/2").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("milestone/view"))
                    .andExpect(model().attributeExists("project", "milestoneDto"))
            }
        }

        describe("GET /{owner}/{projectName}/milestone/new") {
            it("멤버라면 200 OK와 milestone/create 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true

                mockMvc.perform(get("/owner/TestProj/milestone/new").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("milestone/create"))
                    .andExpect(model().attributeExists("project"))
            }
        }
    }
})
