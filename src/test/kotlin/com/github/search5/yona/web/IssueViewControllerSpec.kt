package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.issue.IssueCommentRepository
import com.github.search5.yona.domain.issue.IssueEventRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import com.github.search5.yona.domain.attachment.Attachment
import com.github.search5.yona.domain.attachment.AttachmentRepository
import com.github.search5.yona.domain.attachment.AttachmentService

import com.github.search5.yona.domain.project.RecentProjectRepository
import com.github.search5.yona.domain.project.ProjectService
import com.github.search5.yona.domain.vcs.RepositoryService
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.pullrequest.ReviewCommentRepository
import com.github.search5.yona.domain.pullrequest.CommitCommentRepository
import com.github.search5.yona.domain.milestone.MilestoneRepository
import org.springframework.context.MessageSource
import com.github.search5.yona.domain.issue.IssueService
import com.github.search5.yona.config.TemplateHelper
import com.github.search5.yona.domain.issue.IssueExcelService
import com.github.search5.yona.domain.issue.RecentIssueService
import com.github.search5.yona.domain.project.TitleHeadService
import io.mockk.clearMocks
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.project.ProjectUser
import org.springframework.data.jpa.domain.Specification
import io.mockk.slot
import com.github.search5.yona.domain.issue.IssueComment
import java.time.Instant
import com.github.search5.yona.domain.issue.IssueEvent
import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.issue.IssueTimelineItem
import com.github.search5.yona.domain.project.RecentProject
import com.github.search5.yona.domain.vcs.PlayRepository
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.organization.Organization
import com.github.search5.yona.domain.organization.OrganizationUser
import com.github.search5.yona.domain.role.RoleType
import org.springframework.ui.ExtendedModelMap

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
    val messageSource = mockk<MessageSource>()
    val recentProjectRepository = mockk<RecentProjectRepository>()
    val issueService = mockk<IssueService>()
    val templateHelper = mockk<TemplateHelper>()
    val issueExcelService = mockk<IssueExcelService>()
    val repositoryService = mockk<RepositoryService>()
    val recentIssueService = mockk<RecentIssueService>(relaxed = true)
    val titleHeadService = mockk<TitleHeadService>()
    val issueEventRepository = mockk<IssueEventRepository>()
    val attachmentService = mockk<AttachmentService>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    every { organizationUserRepository.findByOrganizationIdAndUserId(any(), any()) } returns Optional.empty()
    val userRepositoryForAccessControl = mockk<UserRepository>()
    val organizationRepositoryForAccessControl = mockk<OrganizationRepository>()
    val issueRepositoryForAccessControl = mockk<IssueRepository>()
    val postingRepositoryForAccessControl = mockk<PostingRepository>()
    val reviewCommentRepositoryForAccessControl = mockk<ReviewCommentRepository>()
    val commitCommentRepositoryForAccessControl = mockk<CommitCommentRepository>()
    val milestoneRepositoryForAccessControl = mockk<MilestoneRepository>()
    val accessControl = AccessControl(
        projectUserRepository, organizationUserRepository,
        userRepositoryForAccessControl, organizationRepositoryForAccessControl,
        issueRepositoryForAccessControl, postingRepositoryForAccessControl,
        reviewCommentRepositoryForAccessControl, commitCommentRepositoryForAccessControl,
        milestoneRepositoryForAccessControl
    )

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
        repositoryService,
        recentIssueService,
        accessControl,
        titleHeadService,
        issueEventRepository,
        attachmentService
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(issueViewController)
        .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
        .build()

    beforeTest {
        clearMocks(
            projectRepository, projectService, issueRepository, projectUserRepository, userRepository, issueCommentRepository,
            watchService, milestoneService, issueLabelRepository, favoriteIssueRepository, attachmentRepository,
            messageSource, recentProjectRepository, issueService, templateHelper, issueExcelService, repositoryService,
            recentIssueService, titleHeadService, issueEventRepository, attachmentService
        )
        every { titleHeadService.deleteTitleHeadKeyword(any(), any()) } returns Unit
        every { issueEventRepository.findByIssueOrderByCreatedAsc(any()) } returns emptyList()
        every { projectUserRepository.findByProjectIdAndUserId(any(), any()) } returns Optional.empty()
    }

    describe("IssueViewController 템플릿 연동 테스트") {
        val memberRole = Role(id = 2L, name = "MEMBER")
        val project = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PRIVATE)
        
        val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val projectUser = ProjectUser(id = 1L, user = memberUser, project = project, role = memberRole)
        memberUser.projectUsers.add(projectUser)

        val nonMemberUser = User(id = 11L, loginId = "testuser", name = "테스트유저") // projectUsers가 비어있는 비멤버 유저

        val issue = Issue(id = 5L, title = "이슈 제목", project = project)

        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
        val pageRequest = PageRequest.of(0, 20)

        describe("GET /{owner}/{projectName}/issues") {
            it("비공개 프로젝트일 때 멤버라면 200 OK와 issue/list 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findByProjectAndState(project, State.OPEN, any<Pageable>()) } returns PageImpl(listOf(issue), pageRequest, 1)
                every { issueRepository.findAll(any<Specification<Issue>>(), any<Pageable>()) } returns PageImpl(listOf(issue), pageRequest, 1)
                every { issueRepository.count(any<Specification<Issue>>()) } returns 1L
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

            it("프로젝트 멤버가 아닐 경우 컨텍스트 인지형 403(error/forbidden) 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(nonMemberUser)

                // yona error/forbidden.scala.html 대응 (P-템플릿 #47) — 프로젝트는 이미 찾았으므로
                // 프로젝트 헤더/메뉴가 붙는 컨텍스트 인지형 403(제네릭 error/403이 아니다).
                mockMvc.perform(get("/owner/TestProj/issues").principal(userAuth))
                    .andExpect(view().name("error/forbidden"))
                    .andExpect(model().attributeExists("project"))
            }

            // yona IssueApp.java:46,166-177 getItemsPerPage() 대응 (P1-105).
            it("itemsPerPage를 지정하지 않으면 기본 페이지 크기는 15여야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.countByProjectAndState(project, State.OPEN) } returns 1L
                every { issueRepository.countByProjectAndState(project, State.CLOSED) } returns 0L
                every { milestoneService.getMilestones(any<Long>(), any<State>()) } returns emptyList()
                every { projectUserRepository.findByProjectId(any<Long>()) } returns emptyList()
                every { issueLabelRepository.findByProject(any<Project>()) } returns emptyList()
                val pageableSlot = slot<Pageable>()
                every { issueRepository.findAll(any<Specification<Issue>>(), capture(pageableSlot)) } returns PageImpl(listOf(issue), pageRequest, 1)
                every { issueRepository.count(any<Specification<Issue>>()) } returns 1L

                mockMvc.perform(get("/owner/TestProj/issues").principal(userAuth))
                    .andExpect(status().isOk)

                pageableSlot.captured.pageSize shouldBe 15
            }

            it("itemsPerPage가 45를 넘게 요청해도 45로 clamp되어야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.countByProjectAndState(project, State.OPEN) } returns 1L
                every { issueRepository.countByProjectAndState(project, State.CLOSED) } returns 0L
                every { milestoneService.getMilestones(any<Long>(), any<State>()) } returns emptyList()
                every { projectUserRepository.findByProjectId(any<Long>()) } returns emptyList()
                every { issueLabelRepository.findByProject(any<Project>()) } returns emptyList()
                val pageableSlot = slot<Pageable>()
                every { issueRepository.findAll(any<Specification<Issue>>(), capture(pageableSlot)) } returns PageImpl(listOf(issue), pageRequest, 1)
                every { issueRepository.count(any<Specification<Issue>>()) } returns 1L

                mockMvc.perform(get("/owner/TestProj/issues").param("itemsPerPage", "999").principal(userAuth))
                    .andExpect(status().isOk)

                pageableSlot.captured.pageSize shouldBe 45
            }
        }

        describe("GET /{owner}/{projectName}/issue/{number}") {
            it("프로젝트 멤버가 이슈 조회를 요청하면 200 OK와 issue/view 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue
                every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(5L) } returns emptyList()
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { favoriteIssueRepository.findByUserIdAndIssueId(10L, 5L) } returns Optional.empty()
                every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/issue/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("issue/view"))
                    .andExpect(model().attributeExists("project", "issue", "comments", "currentUser", "isWatching", "isWatchingProject"))
            }

            // yona Issue.getTimeline() 대응 (P1-106) — 댓글+IssueEvent를 시간순으로 병합하고
            // ISSUE_BODY_CHANGED는 화면에서 제외한다.
            it("timeline 모델 속성에 댓글과 이벤트가 시간순으로 병합되고 ISSUE_BODY_CHANGED는 제외되어야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue
                val comment = IssueComment(
                    id = 300L, contents = "댓글", issue = issue,
                    createdDate = Instant.parse("2026-01-01T00:00:00Z")
                )
                every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(5L) } returns listOf(comment)
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { favoriteIssueRepository.findByUserIdAndIssueId(10L, 5L) } returns Optional.empty()
                every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()

                val stateEvent = IssueEvent(
                    id = 1L, issue = issue, senderLoginId = "testuser",
                    eventType = EventType.ISSUE_STATE_CHANGED,
                    oldValue = "OPEN", newValue = "CLOSED",
                    created = Instant.parse("2026-01-02T00:00:00Z")
                )
                val bodyChangedEvent = IssueEvent(
                    id = 2L, issue = issue, senderLoginId = "testuser",
                    eventType = EventType.ISSUE_BODY_CHANGED,
                    oldValue = "이전 본문", newValue = "새 본문",
                    created = Instant.parse("2026-01-03T00:00:00Z")
                )
                every { issueEventRepository.findByIssueOrderByCreatedAsc(issue) } returns listOf(stateEvent, bodyChangedEvent)

                val result = mockMvc.perform(get("/owner/TestProj/issue/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                val timeline = result.modelAndView!!.model["timeline"] as List<*>
                timeline.size shouldBe 2
                val kinds = timeline.map { (it as IssueTimelineItem).kind }
                kinds shouldBe listOf("COMMENT", "EVENT")
            }
        }
        describe("GET /user/issues/new") {
            it("commentId가 주어지면 해당 댓글을 조회하고 레퍼런스 본문 및 ISSUE_TEMPLATE을 포함하여 200 OK를 반환해야 한다") {
                val recentProject = RecentProject(id = 1L, userId = 10L, projectId = 1L)
                every { recentProjectRepository.findByUserIdOrderByVisitedDateDesc(10L) } returns listOf(recentProject)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true

                val mockComment = mockk<IssueComment>()
                every { mockComment.id } returns 200L
                every { mockComment.contents } returns "댓글 원본 내용"
                every { mockComment.authorLoginId } returns "commenter"
                every { mockComment.issue } returns issue
                every { issueCommentRepository.findById(200L) } returns Optional.of(mockComment)

                val mockPlayRepo = mockk<PlayRepository>()
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

        // yona Attachment.moveOnlySelected() 대응 (P0-22) — 요청받은 첨부파일 ID를 검증 없이 그대로
        // 재배선하지 않고, 실제로 이 로그인 사용자가 업로드한 임시 첨부만 옮기는지 검증한다.
        describe("POST /{owner}/{projectName}/issues - 임시 업로드 첨부파일 연결") {
            it("temporaryUploadFiles로 넘어온 첨부파일 ID들이 moveOnlySelected를 통해 생성된 이슈로 옮겨져야 한다") {
                val user = User(id = 10L, loginId = "testuser", name = "테스터")
                val project = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PUBLIC)
                val savedIssue = Issue(id = 100L, number = 5L, title = "제목", body = "본문", project = project)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every {
                    issueService.createIssue(any(), any(), any(), any(), any())
                } returns savedIssue
                every {
                    attachmentService.moveOnlySelected(
                        ResourceType.NOT_A_RESOURCE, "",
                        ResourceType.ISSUE_POST, "100",
                        listOf(900L, 901L), "testuser"
                    )
                } returns 2

                val auth = UsernamePasswordAuthenticationToken("testuser", "pass")

                val result = issueViewController.createIssue(
                    owner = "owner",
                    projectName = "TestProj",
                    title = "제목",
                    body = "본문",
                    parentIssueId = null,
                    targetProjectId = null,
                    assigneeLoginId = null,
                    milestoneId = null,
                    dueDate = null,
                    labelIds = null,
                    isDraft = false,
                    temporaryUploadFiles = "900,901",
                    authentication = auth,
                    model = ExtendedModelMap()
                )

                result shouldBe "redirect:/owner/TestProj/issue/5"
                verify(exactly = 1) {
                    attachmentService.moveOnlySelected(
                        ResourceType.NOT_A_RESOURCE, "",
                        ResourceType.ISSUE_POST, "100",
                        listOf(900L, 901L), "testuser"
                    )
                }
            }

            it("temporaryUploadFiles가 없으면 첨부파일 연결 로직 없이도 정상 생성되어야 한다") {
                val user = User(id = 10L, loginId = "testuser", name = "테스터")
                val project = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PUBLIC)
                val savedIssue = Issue(id = 100L, number = 5L, title = "제목", body = "본문", project = project)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every {
                    issueService.createIssue(any(), any(), any(), any(), any())
                } returns savedIssue

                val auth = UsernamePasswordAuthenticationToken("testuser", "pass")

                val result = issueViewController.createIssue(
                    owner = "owner",
                    projectName = "TestProj",
                    title = "제목",
                    body = "본문",
                    parentIssueId = null,
                    targetProjectId = null,
                    assigneeLoginId = null,
                    milestoneId = null,
                    dueDate = null,
                    labelIds = null,
                    isDraft = false,
                    temporaryUploadFiles = null,
                    authentication = auth,
                    model = ExtendedModelMap()
                )

                result shouldBe "redirect:/owner/TestProj/issue/5"
            }
        }

        describe("POST /{owner}/{projectName}/issue/{number}/edit") {
            // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57) — 작성자 본인도 아니고
            // 직접 프로젝트 멤버도 아니지만, 프로젝트가 속한 조직의 멤버라면 이슈를 수정할 수 있어야 한다.
            it("직접 멤버가 아니어도 프로젝트가 속한 조직의 멤버라면 수정에 성공해야 한다") {
                val org = Organization(id = 1L, name = "org")
                val groupUser = User(id = 20L, loginId = "groupuser", name = "그룹멤버")
                org.organizationUsers.add(
                    OrganizationUser(
                        id = 1L, user = groupUser, organization = org,
                        role = Role(id = RoleType.ORG_MEMBER.roleType)
                    )
                )
                val groupProject = Project(id = 7L, name = "GroupProj", owner = "owner", projectScope = ProjectScope.PROTECTED, organization = org)
                val groupIssue = Issue(id = 50L, number = 3L, title = "원제목", authorLoginId = "otherauthor", project = groupProject)
                val auth = UsernamePasswordAuthenticationToken("groupuser", "pass")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "GroupProj") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("groupuser") } returns Optional.of(groupUser)
                every { issueRepository.findByProjectAndNumber(groupProject, 3L) } returns groupIssue
                every { projectUserRepository.existsByProjectIdAndUserId(7L, 20L) } returns false
                every {
                    issueService.updateIssue(any(), any(), any(), any(), any(), any(), any())
                } returns groupIssue

                val result = issueViewController.editIssue(
                    owner = "owner",
                    projectName = "GroupProj",
                    number = 3L,
                    request = IssueForm(title = "새 제목", body = "새 본문"),
                    authentication = auth,
                    model = ExtendedModelMap()
                )

                result shouldBe "redirect:/owner/GroupProj/issue/3"
            }
        }

        // yona IssueApp.massUpdate()의 delete 분기(AbstractPosting.delete()) 대응 (P1-103).
        // 실제 연관 데이터(댓글/이벤트/즐겨찾기/첨부파일/TitleHead) 정리는 P0-19로 IssueServiceImpl
        // .deleteIssueCascade()에 위임되며(검증은 IssueServiceSpec 참고), 여기서는 위임 호출만 검증한다.
        describe("POST /{owner}/{projectName}/issues/massupdate (delete=true)") {
            it("일괄삭제 대상 이슈마다 issueService.deleteIssueCascade가 호출되어야 한다") {
                val toDelete = Issue(id = 7L, number = 7L, title = "[Bug] 지울 이슈", body = "본문", project = project, authorId = memberUser.id, state = State.OPEN)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findAllById(listOf(7L)) } returns listOf(toDelete)
                every { issueService.deleteIssueCascade(toDelete) } returns Unit

                val form = IssueMassUpdateForm()
                form.issues = listOf(IssueIdForm().apply { id = 7L })

                issueViewController.massUpdate(
                    owner = "owner",
                    projectName = "TestProj",
                    form = form,
                    authentication = userAuth,
                    delete = true,
                    isDueDateChanged = false,
                    dueDate = null,
                    model = ExtendedModelMap()
                )

                verify(exactly = 1) { issueService.deleteIssueCascade(toDelete) }
            }
        }
    }
})
