package com.github.search5.yona.web

import com.github.search5.yona.domain.notification.NotificationEvent
import com.github.search5.yona.domain.notification.NotificationEventRepository
import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.user.UserSetting
import com.github.search5.yona.domain.user.UserSettingRepository
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.milestone.MilestoneRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.Optional
import io.mockk.clearMocks

class IndexControllerSpec : DescribeSpec({
    val notificationEventRepository = mockk<NotificationEventRepository>()
    val userRepository = mockk<UserRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val organizationRepository = mockk<OrganizationRepository>()
    val milestoneRepository = mockk<MilestoneRepository>()
    val userSettingRepository = mockk<UserSettingRepository>()

    val indexController = IndexController(
        notificationEventRepository,
        userRepository,
        projectRepository,
        issueRepository,
        postingRepository,
        pullRequestRepository,
        organizationRepository,
        milestoneRepository,
        userSettingRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(indexController).build()

    beforeTest {
        clearMocks(
            notificationEventRepository,
            userRepository,
            projectRepository,
            issueRepository,
            postingRepository,
            pullRequestRepository,
            organizationRepository,
            userSettingRepository,
            milestoneRepository
        )
    }

    describe("IndexController 웹 테스트") {
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        val event = NotificationEvent(
            id = 1L,
            title = "새로운 이슈 등록",
            senderId = 20L,
            receivers = mutableSetOf(user),
            created = Instant.now(),
            resourceType = ResourceType.ISSUE_POST,
            resourceId = "100",
            eventType = EventType.NEW_ISSUE
        )

        describe("GET /") {
            it("로그인된 유저가 접속 시 최근 20개의 알림 목록을 모델에 담고 index 뷰를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { userSettingRepository.findByUserId(10L) } returns Optional.empty()
                every { notificationEventRepository.findByReceiver(user, PageRequest.of(0, 20)) } returns PageImpl(listOf(event))
                every { issueRepository.findAllById(any()) } returns emptyList()
                every { userRepository.findAllById(any()) } returns emptyList()

                mockMvc.perform(
                    get("/")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("index"))
                    .andExpect(model().attributeExists("notifications"))
            }

            it("비로그인 유저가 접속 시 데이터 바인딩 없이 index 뷰를 반환해야 한다") {
                mockMvc.perform(
                    get("/")
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("index"))
                    .andExpect(model().attributeDoesNotExist("notifications"))
            }

            // yona Application.java:45-52 index()의 loginDefaultPage 리다이렉트 대응 (P2-11)
            it("기본 페이지가 설정된 로그인 유저가 접속하면 해당 경로로 리다이렉트해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { userSettingRepository.findByUserId(10L) } returns Optional.of(
                    UserSetting(id = 1L, user = user, loginDefaultPage = "notifications")
                )

                mockMvc.perform(
                    get("/")
                        .principal(userAuth)
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("notifications"))
            }
        }

        describe("GET /_notifications") {
            it("비동기 알림 목록 HTML 스니펫을 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { notificationEventRepository.findByReceiver(user, PageRequest.of(1, 20)) } returns PageImpl(listOf(event))
                every { issueRepository.findAllById(any()) } returns emptyList()
                every { userRepository.findAllById(any()) } returns emptyList()

                mockMvc.perform(
                    get("/_notifications")
                        .param("from", "20")
                        .param("size", "20")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("index/partial_notifications"))
                    .andExpect(model().attributeExists("notifications"))
            }
        }
    }
})
