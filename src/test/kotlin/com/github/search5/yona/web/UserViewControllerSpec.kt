package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.mention.MentionService
import com.github.search5.yona.domain.attachment.AttachmentRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.user.UserService
import com.github.search5.yona.domain.watch.WatchRepository
import com.github.search5.yona.domain.notification.UserProjectNotificationRepository
import com.github.search5.yona.domain.user.FavoriteProjectRepository
import com.github.search5.yona.domain.user.FavoriteOrganizationRepository
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.organization.OrganizationRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import java.util.Optional

class UserViewControllerSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val issueRepository = mockk<IssueRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val watchRepository = mockk<WatchRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val userProjectNotificationRepository = mockk<UserProjectNotificationRepository>()
    val attachmentRepository = mockk<AttachmentRepository>()
    val postingRepository = mockk<PostingRepository>()
    val favoriteProjectRepository = mockk<FavoriteProjectRepository>()
    val favoriteOrganizationRepository = mockk<FavoriteOrganizationRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val organizationRepository = mockk<OrganizationRepository>()
    val userService = mockk<UserService>()
    val accessControl = mockk<AccessControl>()
    val mentionService = mockk<MentionService>(relaxed = true)

    val userViewController = UserViewController(
        userRepository,
        projectUserRepository,
        issueRepository,
        pullRequestRepository,
        watchRepository,
        projectRepository,
        userProjectNotificationRepository,
        attachmentRepository,
        postingRepository,
        favoriteProjectRepository,
        favoriteOrganizationRepository,
        organizationUserRepository,
        organizationRepository,
        userService,
        accessControl,
        mentionService
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(userViewController)
        .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
        .build()

    beforeTest {
        io.mockk.clearMocks(
            userRepository,
            projectUserRepository,
            issueRepository,
            userService,
            pullRequestRepository,
            watchRepository,
            projectRepository,
            userProjectNotificationRepository,
            attachmentRepository,
            postingRepository,
            favoriteProjectRepository,
            favoriteOrganizationRepository,
            organizationUserRepository,
            organizationRepository,
            accessControl,
            mentionService
        )
        every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
        every { accessControl.isAllowedToReadProject(any(), any()) } returns true
    }

    describe("UserViewController 템플릿 연동 테스트") {
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        describe("GET /user/{loginId}") {
            it("200 OK와 user/view 뷰를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByUserId(10L) } returns emptyList()
                every { issueRepository.findByAuthorId(10L) } returns emptyList()
                every { pullRequestRepository.findByContributor(user) } returns emptyList()

                mockMvc.perform(get("/user/testuser").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("user/view"))
                    .andExpect(model().attributeExists("user", "projects", "issues", "pullRequests"))
            }
        }

        // yona Mention.getMentioningIssueIds() 대응 (P2-41) — LIKE 텍스트 검색 대신 멘션 인덱스
        // 테이블 조회 결과(이슈 id 목록)를 그대로 이슈 조회에 사용해야 한다.
        describe("GET /user/issues?mentionId=... (P2-41)") {
            it("멘션 인덱스 조회 결과 이슈 id 목록을 findMentionedByState에 그대로 전달해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { mentionService.getMentioningIssueIds(10L) } returns listOf(100L, 200L)
                every {
                    issueRepository.findMentionedByState(listOf(100L, 200L), State.OPEN, null, any())
                } returns org.springframework.data.domain.PageImpl(emptyList())
                every { issueRepository.countMentionedByState(listOf(100L, 200L), State.OPEN) } returns 2L
                every { issueRepository.countMentionedByState(listOf(100L, 200L), State.CLOSED) } returns 0L
                every { issueRepository.countSharedByState(10L, State.OPEN) } returns 0L
                every { issueRepository.countFavoriteByState(10L, State.OPEN) } returns 0L

                mockMvc.perform(get("/user/issues").param("mentionId", "1").principal(userAuth))
                    .andExpect(status().isOk)

                verify(exactly = 1) { issueRepository.findMentionedByState(listOf(100L, 200L), State.OPEN, null, any()) }
            }

            it("멘션된 이슈가 하나도 없으면 repository를 호출하지 않고 빈 결과를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { mentionService.getMentioningIssueIds(10L) } returns emptyList()
                every { issueRepository.countSharedByState(10L, State.OPEN) } returns 0L
                every { issueRepository.countFavoriteByState(10L, State.OPEN) } returns 0L

                mockMvc.perform(get("/user/issues").param("mentionId", "1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("mentionCount", 0L))

                verify(exactly = 0) { issueRepository.findMentionedByState(any(), any(), any(), any()) }
                verify(exactly = 0) { issueRepository.countMentionedByState(any(), any()) }
            }
        }

        describe("GET /user/editform") {
            it("로그인된 사용자라면 200 OK와 user/edit 뷰를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)

                mockMvc.perform(get("/user/editform").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("user/edit"))
                    .andExpect(model().attributeExists("user"))
            }
        }

        describe("GET /user/editform/emails") {
            it("로그인된 사용자라면 200 OK와 user/edit_emails 뷰를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)

                mockMvc.perform(get("/user/editform/emails").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("user/edit_emails"))
                    .andExpect(model().attributeExists("user", "emails"))
            }
        }

        describe("GET /user/editform/notifications") {
            it("로그인된 사용자라면 200 OK와 user/edit_notifications 뷰를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { watchRepository.findByUserAndResourceType(user, any()) } returns emptyList()

                mockMvc.perform(get("/user/editform/notifications").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("user/edit_notifications"))
                    .andExpect(model().attributeExists("user", "projects", "notiTypes", "notiMap", "notiTypeDescriptions"))
            }
        }
    }

    // yona UserApp.java:752 "!HIDE_PROJECT_LISTING || !currentUser().isAnonymous()" 대응 (P0-23).
    describe("HIDE_PROJECT_LISTING=true일 때 GET /user/{loginId}") {
        val hiddenController = UserViewController(
            userRepository, projectUserRepository, issueRepository, pullRequestRepository, watchRepository,
            projectRepository, userProjectNotificationRepository, attachmentRepository, postingRepository,
            favoriteProjectRepository, favoriteOrganizationRepository, organizationUserRepository,
            organizationRepository, userService, accessControl, mentionService, hideProjectListing = true
        )
        val model = org.springframework.ui.ExtendedModelMap()

        it("비로그인 방문자에게는 프로젝트/이슈/PR 목록이 비어 있어야 한다") {
            val viewedUser = User(id = 20L, loginId = "viewed", name = "대상유저")
            every { userRepository.findByLoginId("viewed") } returns Optional.of(viewedUser)

            hiddenController.userProfile(loginId = "viewed", daysAgo = 14, selected = "issues", authentication = null, model = model)

            model.getAttribute("projects") shouldBe emptyList<Any>()
            model.getAttribute("issues") shouldBe emptyList<Any>()
            model.getAttribute("pullRequests") shouldBe emptyList<Any>()
        }

        it("로그인한 방문자에게는 영향이 없어야 한다") {
            val viewedUser = User(id = 20L, loginId = "viewed", name = "대상유저")
            val viewer = User(id = 30L, loginId = "viewer", name = "방문자")
            val viewerAuth = UsernamePasswordAuthenticationToken("viewer", "password")
            every { userRepository.findByLoginId("viewed") } returns Optional.of(viewedUser)
            every { userRepository.findByLoginId("viewer") } returns Optional.of(viewer)
            every { projectUserRepository.findByUserId(20L) } returns emptyList()
            every { issueRepository.findByAuthorId(20L) } returns emptyList()
            every { pullRequestRepository.findByContributor(viewedUser) } returns emptyList()

            val viewerModel = org.springframework.ui.ExtendedModelMap()
            hiddenController.userProfile(loginId = "viewed", daysAgo = 14, selected = "issues", authentication = viewerAuth, model = viewerModel)

            viewerModel.getAttribute("currentUser") shouldBe viewer
        }
    }

    // yona UserApp.java:811-846 getAclValidatedIssues()/getAclValidatedPullRequests()/
    // collectProjects() 대응 (P0-25). 대상 사용자가 작성한 이슈/PR/소속 프로젝트 중 방문자가
    // READ 권한이 없는 것은 프로필에서 감춰져야 한다.
    describe("GET /user/{loginId} - 방문자의 프로젝트 READ 권한에 따른 필터링") {
        it("방문자가 READ 권한이 없는 프로젝트의 이슈/PR/소속 프로젝트는 감춰져야 한다") {
            val viewedUser = User(id = 20L, loginId = "viewed", name = "대상유저")
            val readableProject = com.github.search5.yona.domain.project.Project(id = 1L, name = "readable", owner = "viewed")
            val hiddenProject = com.github.search5.yona.domain.project.Project(id = 2L, name = "hidden", owner = "viewed")
            val readableIssue = com.github.search5.yona.domain.issue.Issue(id = 100L, title = "보이는 이슈", project = readableProject)
            val hiddenIssue = com.github.search5.yona.domain.issue.Issue(id = 101L, title = "숨겨진 이슈", project = hiddenProject)
            val readablePr = com.github.search5.yona.domain.pullrequest.PullRequest(
                id = 200L, number = 1L, toProject = readableProject, fromProject = readableProject, contributor = viewedUser
            )
            val hiddenPr = com.github.search5.yona.domain.pullrequest.PullRequest(
                id = 201L, number = 2L, toProject = hiddenProject, fromProject = hiddenProject, contributor = viewedUser
            )
            val memberRole = com.github.search5.yona.domain.role.Role(id = com.github.search5.yona.domain.role.RoleType.MEMBER.roleType)
            val readableProjectUser = com.github.search5.yona.domain.project.ProjectUser(id = 900L, user = viewedUser, project = readableProject, role = memberRole)
            val hiddenProjectUser = com.github.search5.yona.domain.project.ProjectUser(id = 901L, user = viewedUser, project = hiddenProject, role = memberRole)

            every { userRepository.findByLoginId("viewed") } returns Optional.of(viewedUser)
            every { projectUserRepository.findByUserId(20L) } returns listOf(readableProjectUser, hiddenProjectUser)
            every { issueRepository.findByAuthorId(20L) } returns listOf(readableIssue, hiddenIssue)
            every { pullRequestRepository.findByContributor(viewedUser) } returns listOf(readablePr, hiddenPr)
            every { accessControl.isAllowedToReadProject(null, readableProject) } returns true
            every { accessControl.isAllowedToReadProject(null, hiddenProject) } returns false

            mockMvc.perform(get("/user/viewed"))
                .andExpect(status().isOk)
                .andExpect(model().attribute("projects", listOf(readableProject)))
                .andExpect(model().attribute("issues", listOf(readableIssue)))
                .andExpect(model().attribute("pullRequests", listOf(readablePr)))
        }
    }
})
