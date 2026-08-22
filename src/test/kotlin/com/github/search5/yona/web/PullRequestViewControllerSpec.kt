package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.pullrequest.CodeCommentThread
import com.github.search5.yona.domain.pullrequest.CodeReviewService
import com.github.search5.yona.domain.pullrequest.CommentThreadRepository
import com.github.search5.yona.domain.pullrequest.NonRangedCodeCommentThread
import com.github.search5.yona.domain.pullrequest.PullRequest
import com.github.search5.yona.domain.pullrequest.PullRequestEventRepository
import com.github.search5.yona.domain.pullrequest.PullRequestMergeResult
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.domain.pullrequest.PullRequestService
import com.github.search5.yona.domain.vcs.PlayRepository
import com.github.search5.yona.domain.vcs.RepositoryService
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.pullrequest.PullRequestCommitRepository
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.project.ProjectUser
import com.github.search5.yona.domain.vcs.PushedBranchRepository
import com.github.search5.yona.domain.watch.WatchService
import com.github.search5.yona.domain.attachment.AttachmentRepository
import org.springframework.context.MessageSource
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import java.util.Optional
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.pullrequest.ReviewCommentRepository
import com.github.search5.yona.domain.pullrequest.CommitCommentRepository
import com.github.search5.yona.domain.milestone.MilestoneRepository
import io.mockk.clearMocks
import io.mockk.slot
import com.github.search5.yona.domain.organization.Organization
import com.github.search5.yona.domain.organization.OrganizationUser
import com.github.search5.yona.domain.pullrequest.PullRequestEvent
import com.github.search5.yona.domain.enumeration.EventType
import java.time.Instant
import com.github.search5.yona.domain.pullrequest.PullRequestTimelineItem
import com.github.search5.yona.domain.pullrequest.CommentThread

class PullRequestViewControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val pullRequestService = mockk<PullRequestService>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val repositoryService = mockk<RepositoryService>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val commentThreadRepository = mockk<CommentThreadRepository>()
    val pullRequestEventRepository = mockk<PullRequestEventRepository>()
    val pullRequestCommitRepository = mockk<PullRequestCommitRepository>()
    val issueRepository = mockk<IssueRepository>()
    val codeReviewService = mockk<CodeReviewService>()
    every { codeReviewService.isThreadOutdated(any()) } returns false
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

    val pushedBranchRepository = mockk<PushedBranchRepository>()
    val watchService = mockk<WatchService>()
    val messageSource = mockk<MessageSource>()
    val attachmentRepository = mockk<AttachmentRepository>()
    every { pushedBranchRepository.findByProjectAndPushedDateAfter(any(), any()) } returns emptyList()
    every { pushedBranchRepository.findByOriginalProjectAndOwnerAndPushedDateAfter(any(), any(), any()) } returns emptyList()
    every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
    every { watchService.isWatching(any(), any(), any()) } returns false
    every { messageSource.getMessage(any(), any(), any()) } returns ""

    val pullRequestViewController = PullRequestViewController(
        projectRepository,
        pullRequestService,
        pullRequestRepository,
        repositoryService,
        projectUserRepository,
        userRepository,
        commentThreadRepository,
        pullRequestEventRepository,
        pullRequestCommitRepository,
        issueRepository,
        accessControl,
        codeReviewService,
        pushedBranchRepository,
        watchService,
        messageSource,
        attachmentRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(pullRequestViewController)
        .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
        .build()

    beforeTest {
        clearMocks(
            projectRepository,
            pullRequestService,
            pullRequestRepository,
            repositoryService,
            projectUserRepository,
            userRepository,
            commentThreadRepository,
            pullRequestEventRepository,
            pullRequestCommitRepository,
            issueRepository
        )
        every { pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(any()) } returns emptyList()
        every {
            pullRequestRepository.findAll(any<org.springframework.data.jpa.domain.Specification<PullRequest>>(), any<Pageable>())
        } returns PageImpl(emptyList())
        every {
            pullRequestRepository.count(any<org.springframework.data.jpa.domain.Specification<PullRequest>>())
        } returns 0L
        every { pullRequestRepository.findDistinctContributorsByToProject(any()) } returns emptyList()
    }

    describe("PullRequestViewController 템플릿 연동 테스트") {
        val project = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PRIVATE)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val pullRequest = PullRequest(
            id = 50L,
            title = "PR 테스트 제목",
            body = "PR 본문",
            toProject = project,
            fromProject = project,
            toBranch = "master",
            fromBranch = "feature",
            contributor = user,
            state = State.OPEN,
            number = 1L
        )

        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
        val pageRequest = PageRequest.of(0, 20)

        describe("GET /{owner}/{projectName}/pulls") {
            it("비공개 프로젝트일 때 멤버라면 200 OK와 pullrequest/list 뷰를 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 900L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every {
                    pullRequestRepository.findAll(any<org.springframework.data.jpa.domain.Specification<PullRequest>>(), any<Pageable>())
                } returns PageImpl(listOf(pullRequest), pageRequest, 1)

                mockMvc.perform(get("/owner/TestProj/pulls").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("pullrequest/list"))
                    .andExpect(model().attributeExists("project", "prPage", "state"))
            }

            it("프로젝트 멤버가 아닐 경우 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

                mockMvc.perform(get("/owner/TestProj/pulls").principal(userAuth))
                    .andExpect(view().name("error/forbidden"))
            }

            // yona models/PullRequest.java:66 ITEMS_PER_PAGE 대응 (P1-105) — PR 목록은 항상 고정 15.
            it("페이지 크기는 항상 15로 고정되어야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 900L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                val pageableSlot = slot<Pageable>()
                every {
                    pullRequestRepository.findAll(any<org.springframework.data.jpa.domain.Specification<PullRequest>>(), capture(pageableSlot))
                } returns PageImpl(listOf(pullRequest), pageRequest, 1)

                mockMvc.perform(get("/owner/TestProj/pulls").principal(userAuth))
                    .andExpect(status().isOk)

                pageableSlot.captured.pageSize shouldBe 15
            }

            // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57) — checkMemberAccess() 공용 헬퍼
            it("직접 멤버가 아니어도 프로젝트가 속한 조직의 멤버라면 200 OK를 반환해야 한다") {
                val groupOrg = Organization(id = 1L, name = "org")
                groupOrg.organizationUsers.add(
                    OrganizationUser(
                        id = 1L, user = user, organization = groupOrg,
                        role = Role(id = RoleType.ORG_MEMBER.roleType)
                    )
                )
                val groupProject = Project(id = 12L, name = "group-project", owner = "owner", projectScope = ProjectScope.PROTECTED, organization = groupOrg)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "group-project") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(12L, 10L) } returns false
                every {
                    pullRequestRepository.findAll(any<org.springframework.data.jpa.domain.Specification<PullRequest>>(), any<Pageable>())
                } returns PageImpl(emptyList(), pageRequest, 0)

                mockMvc.perform(get("/owner/group-project/pulls").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("pullrequest/list"))
            }
        }

        describe("GET /{owner}/{projectName}/closedPullRequests") {
            it("멤버라면 CLOSED와 MERGED 상태를 모두 포함한 목록을 pullrequest/list 뷰로 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 901L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every {
                    pullRequestRepository.findAll(any<org.springframework.data.jpa.domain.Specification<PullRequest>>(), any<Pageable>())
                } returns PageImpl(listOf(pullRequest), pageRequest, 1)

                mockMvc.perform(get("/owner/TestProj/closedPullRequests").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("pullrequest/list"))
                    .andExpect(model().attributeExists("project", "prPage"))
                    .andExpect(model().attribute("state", "closed"))
            }

            it("프로젝트 멤버가 아닐 경우 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

                mockMvc.perform(get("/owner/TestProj/closedPullRequests").principal(userAuth))
                    .andExpect(view().name("error/forbidden"))
            }
        }

        describe("GET /{owner}/{projectName}/sentPullRequests") {
            it("멤버라면 이 프로젝트가 출발지(fromProject)인 PR 목록을 pullrequest/list 뷰로 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 902L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every {
                    pullRequestRepository.findAll(any<org.springframework.data.jpa.domain.Specification<PullRequest>>(), any<Pageable>())
                } returns PageImpl(listOf(pullRequest), pageRequest, 1)

                mockMvc.perform(get("/owner/TestProj/sentPullRequests").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("pullrequest/list"))
                    .andExpect(model().attributeExists("project", "prPage"))
                    .andExpect(model().attribute("state", "sent"))
            }
        }

        describe("GET /{owner}/{projectName}/pull/{number}") {
            it("멤버라면 200 OK와 pullrequest/view 뷰를 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 903L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest

                val mergeResult = PullRequestMergeResult(pullRequest = pullRequest)
                every { pullRequestService.attemptMerge(50L) } returns mergeResult
                every { commentThreadRepository.findByPullRequest(pullRequest) } returns emptyList()
                every { pullRequestCommitRepository.findByPullRequest(pullRequest) } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/pull/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("pullrequest/view"))
                    .andExpect(model().attributeExists("project", "pr", "mergeResult"))
            }

            // yona git/view.scala.html conversation 탭 + partial_pull_request_event.scala.html 대응
            // (P2-39, P1-106 범위 정정) — 대화 탭은 PullRequestEvent만 시간순으로 보여주고 댓글
            // 스레드는 렌더링하지 않는다(legacy 원본 범위, 사용자 확인 완료로 되돌림). 렌더링 대상이
            // 아닌 이벤트 타입(NEW_PULL_REQUEST)은 제외된다.
            it("timeline 모델 속성에 이벤트만 시간순으로 담기고 댓글스레드/NEW_PULL_REQUEST는 제외되어야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 905L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.attemptMerge(50L) } returns PullRequestMergeResult(pullRequest = pullRequest)
                every { pullRequestCommitRepository.findByPullRequest(pullRequest) } returns emptyList()

                val stateEvent = PullRequestEvent(
                    id = 1L, pullRequest = pullRequest, senderLoginId = "testuser",
                    eventType = EventType.PULL_REQUEST_STATE_CHANGED,
                    oldValue = "OPEN", newValue = "CLOSED",
                    created = Instant.parse("2026-01-02T00:00:00Z")
                )
                val newPrEvent = PullRequestEvent(
                    id = 2L, pullRequest = pullRequest, senderLoginId = "testuser",
                    eventType = EventType.NEW_PULL_REQUEST,
                    oldValue = null, newValue = "PR 본문",
                    created = Instant.parse("2026-01-03T00:00:00Z")
                )
                every { pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pullRequest) } returns listOf(stateEvent, newPrEvent)
                every { commentThreadRepository.findByPullRequest(pullRequest) } returns emptyList()

                val result = mockMvc.perform(get("/owner/TestProj/pull/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                val timeline = result.modelAndView!!.model["timeline"] as List<*>
                timeline.size shouldBe 1
                val eventIds = timeline.map { (it as PullRequestTimelineItem).event.id }
                eventIds shouldBe listOf(1L)
                // openThreadCount(addCommonPrAttributes)를 위해 commentThreadRepository는 호출되지만,
                // 댓글스레드 자체가 timeline 모델 속성에 별도 항목으로 섞여 들어가지는 않아야 한다.
                result.modelAndView!!.model.containsKey("commentThreads") shouldBe false
            }
        }

        describe("GET /{owner}/{projectName}/pull/{number}/changes") {
            it("멤버라면 200 OK와 pullrequest/view 뷰를 반환하고 diffs 모델을 주입해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 904L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest

                val mergeResult = PullRequestMergeResult(pullRequest = pullRequest)
                every { pullRequestService.attemptMerge(50L) } returns mergeResult
                every { pullRequestService.getDiff(pullRequest) } returns emptyList()
                every { commentThreadRepository.findByPullRequest(pullRequest) } returns emptyList()
                every { pullRequestCommitRepository.findByPullRequest(pullRequest) } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/pull/1/changes").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("pullrequest/view"))
                    .andExpect(model().attributeExists("project", "pr", "diffs", "mergeResult"))
            }

            // yona PullRequest.java:1063-1103 getCodeCommentThreadsForChanges() 대응 (P1-114).
            // commitId 없이 조회하는 "전체 변경사항"에서는 커밋 단위 스레드(isCommitComment)와
            // outdated 스레드를 제외하고, NonRangedCodeCommentThread는 필터링 없이 그대로 포함해야 한다.
            it("commentThreads는 커밋단위/outdated 스레드를 제외하고 non-ranged 스레드는 그대로 포함해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 906L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.attemptMerge(50L) } returns PullRequestMergeResult(pullRequest = pullRequest)
                every { pullRequestService.getDiff(pullRequest) } returns emptyList()

                val outdatedThread = CodeCommentThread(
                    id = 801L, pullRequest = pullRequest, prevCommitId = "prev1", commitId = "commit1"
                )
                val validThread = CodeCommentThread(
                    id = 802L, pullRequest = pullRequest, prevCommitId = "prev2", commitId = "commit2"
                )
                val commitCommentThread = CodeCommentThread(
                    id = 803L, pullRequest = pullRequest, prevCommitId = "", commitId = "commit3"
                )
                val nonRangedThread = NonRangedCodeCommentThread(
                    id = 804L, pullRequest = pullRequest, prevCommitId = "prev4", commitId = "commit4"
                )
                every { commentThreadRepository.findByPullRequest(pullRequest) } returns
                    listOf(outdatedThread, validThread, commitCommentThread, nonRangedThread)
                every { pullRequestCommitRepository.findByPullRequest(pullRequest) } returns emptyList()
                every { codeReviewService.isThreadOutdated(801L) } returns true
                every { codeReviewService.isThreadOutdated(802L) } returns false

                val result = mockMvc.perform(get("/owner/TestProj/pull/1/changes").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                val commentThreads = result.modelAndView!!.model["commentThreads"] as List<*>
                val ids = commentThreads.map { (it as CommentThread).id }
                ids shouldBe listOf(802L, 804L)
            }
        }

        describe("GET /{owner}/{projectName}/pullRequest/{number}/changes/{commitId}") {
            it("멤버라면 200 OK와 특정 커밋 변경 사항을 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 905L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest

                val mergeResult = PullRequestMergeResult(pullRequest = pullRequest)
                every { pullRequestService.attemptMerge(50L) } returns mergeResult
                every { pullRequestService.getDiff(pullRequest, "abcdefg") } returns emptyList()
                every { commentThreadRepository.findByPullRequest(pullRequest) } returns emptyList()
                every { pullRequestCommitRepository.findByPullRequest(pullRequest) } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/pullRequest/1/changes/abcdefg").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("pullrequest/view"))
                    .andExpect(model().attributeExists("project", "pr", "diffs", "commitId"))
            }

            // yona PullRequest.java:1074-1077 대응 (P1-114). commitId를 지정해 조회할 때는 outdated
            // 여부와 무관하게 그 커밋에 달린 스레드만 노출해야 한다(다른 커밋 스레드는 제외).
            it("commitId 지정 조회 시 해당 커밋의 스레드만 commentThreads에 포함해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 907L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.attemptMerge(50L) } returns PullRequestMergeResult(pullRequest = pullRequest)
                every { pullRequestService.getDiff(pullRequest, "abcdefg") } returns emptyList()

                val matchingThread = CodeCommentThread(
                    id = 811L, pullRequest = pullRequest, prevCommitId = "", commitId = "abcdefg"
                )
                val otherThread = CodeCommentThread(
                    id = 812L, pullRequest = pullRequest, prevCommitId = "", commitId = "zzzzzzz"
                )
                val matchingNonRanged = NonRangedCodeCommentThread(
                    id = 813L, pullRequest = pullRequest, prevCommitId = "", commitId = "abcdefg"
                )
                every { commentThreadRepository.findByPullRequest(pullRequest) } returns
                    listOf(matchingThread, otherThread, matchingNonRanged)
                every { pullRequestCommitRepository.findByPullRequest(pullRequest) } returns emptyList()

                val result = mockMvc.perform(get("/owner/TestProj/pullRequest/1/changes/abcdefg").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                val commentThreads = result.modelAndView!!.model["commentThreads"] as List<*>
                val ids = commentThreads.map { (it as CommentThread).id }
                ids shouldBe listOf(811L, 813L)
            }
        }

        describe("GET /{owner}/{projectName}/pull/new") {
            it("멤버라면 200 OK와 pullrequest/create 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                
                val playRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns listOf("refs/heads/master", "refs/heads/feature")
                every { playRepo.getDefaultBranch() } returns "refs/heads/master"

                mockMvc.perform(get("/owner/TestProj/pull/new").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("pullrequest/create"))
                    .andExpect(model().attributeExists("project", "branches", "defaultBranch"))
            }
        }

        describe("GET /{owner}/{projectName}/pull/{number}/edit") {
            it("PR 작성자(contributor)라면 200 OK와 pullrequest/edit 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.empty()

                mockMvc.perform(get("/owner/TestProj/pull/1/edit").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("pullrequest/edit"))
                    .andExpect(model().attributeExists("project", "pr", "currentUser"))
            }

            it("프로젝트 매니저라면 작성자가 아니어도 200 OK와 pullrequest/edit 뷰를 반환해야 한다") {
                val managerUser = User(id = 20L, loginId = "manager", name = "매니저")
                val managerAuth = UsernamePasswordAuthenticationToken("manager", "password")
                val managerRole = Role(id = RoleType.MANAGER.roleType)
                val projectManagerUser = ProjectUser(id = 101L, user = managerUser, project = project, role = managerRole)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("manager") } returns Optional.of(managerUser)
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { projectUserRepository.findByProjectIdAndUserId(1L, 20L) } returns Optional.of(projectManagerUser)

                mockMvc.perform(get("/owner/TestProj/pull/1/edit").principal(managerAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("pullrequest/edit"))
            }

            it("작성자도 매니저도 아니라면 403 Forbidden 뷰를 반환해야 한다") {
                val otherUser = User(id = 30L, loginId = "other", name = "다른유저")
                val otherAuth = UsernamePasswordAuthenticationToken("other", "password")
                val memberRole = Role(id = RoleType.MEMBER.roleType)
                val projectMemberUser = ProjectUser(id = 102L, user = otherUser, project = project, role = memberRole)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("other") } returns Optional.of(otherUser)
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { projectUserRepository.findByProjectIdAndUserId(1L, 30L) } returns Optional.of(projectMemberUser)

                mockMvc.perform(get("/owner/TestProj/pull/1/edit").principal(otherAuth))
                    .andExpect(view().name("error/forbidden"))
            }

            it("로그인하지 않았다면 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest

                mockMvc.perform(get("/owner/TestProj/pull/1/edit"))
                    .andExpect(view().name("error/forbidden"))
            }
        }
    }
})
