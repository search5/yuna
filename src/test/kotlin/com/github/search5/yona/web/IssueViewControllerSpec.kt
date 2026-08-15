package com.github.search5.yona.web

import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.issue.IssueCommentRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import org.springframework.data.web.PageableHandlerMethodArgumentResolver

import com.github.search5.yona.domain.watch.WatchService
import com.github.search5.yona.domain.milestone.MilestoneService
import com.github.search5.yona.domain.issue.IssueLabelRepository
import com.github.search5.yona.domain.user.FavoriteIssueRepository
import com.github.search5.yona.domain.attachment.AttachmentRepository

import com.github.search5.yona.domain.project.RecentProjectRepository
import com.github.search5.yona.domain.project.ProjectService
import com.github.search5.yona.domain.vcs.RepositoryService

class IssueViewControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val projectService = mockk<ProjectService>()
    val issueRepository = mockk<IssueRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val issueCommentRepository = mockk<IssueCommentRepository>()
    val watchService = mockk<WatchService>()
    val milestoneService = mockk<MilestoneService>()
    val issueLabelRepository = mockk<IssueLabelRepository>()
    val favoriteIssueRepository = mockk<FavoriteIssueRepository>()
    val attachmentRepository = mockk<AttachmentRepository>()
    val messageSource = mockk<org.springframework.context.MessageSource>()
    val recentProjectRepository = mockk<RecentProjectRepository>()
    val issueService = mockk<com.github.search5.yona.domain.issue.IssueService>()
    val templateHelper = mockk<com.github.search5.yona.config.TemplateHelper>()
    val issueExcelService = mockk<com.github.search5.yona.domain.issue.IssueExcelService>()
    val repositoryService = mockk<RepositoryService>()

    val issueViewController = IssueViewController(
        projectRepository,
        projectService,
        issueRepository,
        projectUserRepository,
        userRepository,
        issueCommentRepository,
        watchService,
        milestoneService,
        issueLabelRepository,
        favoriteIssueRepository,
        attachmentRepository,
        messageSource,
        recentProjectRepository,
        issueService,
        templateHelper,
        issueExcelService,
        repositoryService
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(issueViewController)
        .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
        .build()

    beforeTest {
        io.mockk.clearMocks(
            projectRepository, projectService, issueRepository, projectUserRepository, userRepository, issueCommentRepository,
            watchService, milestoneService, issueLabelRepository, favoriteIssueRepository, attachmentRepository,
            messageSource, recentProjectRepository, issueService, templateHelper, issueExcelService, repositoryService
        )
    }

    describe("IssueViewController 템플릿 연동 테스트") {
        val memberRole = com.github.search5.yona.domain.role.Role(id = 2L, name = "MEMBER")
        val project = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PRIVATE)
        
        val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val projectUser = com.github.search5.yona.domain.project.ProjectUser(id = 1L, user = memberUser, project = project, role = memberRole)
        memberUser.projectUsers.add(projectUser)

        val nonMemberUser = User(id = 11L, loginId = "testuser", name = "테스트유저") // projectUsers가 비어있는 비멤버 유저

        val issue = Issue(id = 5L, title = "이슈 제목", project = project)

        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
        val pageRequest = PageRequest.of(0, 20)

        describe("GET /{owner}/{projectName}/issues") {
            it("비공개 프로젝트일 때 멤버라면 200 OK와 issue/list 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findByProjectAndState(project, State.OPEN, any<Pageable>()) } returns PageImpl(listOf(issue), pageRequest, 1)
                every { issueRepository.findAll(any<org.springframework.data.jpa.domain.Specification<Issue>>(), any<Pageable>()) } returns PageImpl(listOf(issue), pageRequest, 1)
                every { issueRepository.count(any<org.springframework.data.jpa.domain.Specification<Issue>>()) } returns 1L
                every { issueRepository.countByProjectAndState(project, State.OPEN) } returns 1L
                every { issueRepository.countByProjectAndState(project, State.CLOSED) } returns 0L
                every { milestoneService.getMilestones(any<Long>(), any<State>()) } returns emptyList()
                every { projectUserRepository.findByProjectId(any<Long>()) } returns emptyList()
                every { issueLabelRepository.findByProject(any<Project>()) } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/issues").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("issue/list"))
                    .andExpect(model().attributeExists("project", "issuePage", "state"))
            }

            it("프로젝트 멤버가 아닐 경우 403 Forbidden 뷰 혹은 status를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(nonMemberUser)

                mockMvc.perform(get("/owner/TestProj/issues").principal(userAuth))
                    .andExpect(view().name("error/403"))
            }
        }

        describe("GET /{owner}/{projectName}/issue/{number}") {
            it("프로젝트 멤버가 이슈 조회를 요청하면 200 OK와 issue/view 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue
                every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(5L) } returns emptyList()
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { favoriteIssueRepository.findByUserIdAndIssueId(10L, 5L) } returns java.util.Optional.empty()
                every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/issue/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("issue/view"))
                    .andExpect(model().attributeExists("project", "issue", "comments", "currentUser", "isWatching", "isWatchingProject"))
            }
        }
        describe("GET /user/issues/new") {
            it("commentId가 주어지면 해당 댓글을 조회하고 레퍼런스 본문 및 ISSUE_TEMPLATE을 포함하여 200 OK를 반환해야 한다") {
                val recentProject = com.github.search5.yona.domain.project.RecentProject(id = 1L, userId = 10L, projectId = 1L)
                every { recentProjectRepository.findByUserIdOrderByVisitedDateDesc(10L) } returns listOf(recentProject)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true

                val mockComment = mockk<com.github.search5.yona.domain.issue.IssueComment>()
                every { mockComment.id } returns 200L
                every { mockComment.contents } returns "댓글 원본 내용"
                every { mockComment.authorLoginId } returns "commenter"
                every { mockComment.issue } returns issue
                every { issueCommentRepository.findById(200L) } returns Optional.of(mockComment)

                val mockPlayRepo = mockk<com.github.search5.yona.domain.vcs.PlayRepository>()
                every { repositoryService.getRepository(project) } returns mockPlayRepo
                every { mockPlayRepo.getRawFile("HEAD", "ISSUE_TEMPLATE.md") } returns "템플릿 내용".toByteArray()

                every { milestoneService.getMilestones(1L, State.OPEN) } returns emptyList()
                every { projectUserRepository.findByProjectId(1L) } returns emptyList()
                every { projectUserRepository.findByUserId(10L) } returns emptyList()
                every { issueLabelRepository.findByProject(project) } returns emptyList()
                every { issueRepository.findByProjectAndState(project, State.OPEN) } returns emptyList()

                mockMvc.perform(get("/user/issues/new").param("commentId", "200").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("issue/create"))
                    .andExpect(model().attributeExists("project", "issueTemplate"))
            }
        }
    }
})
