package com.github.search5.yona.web

import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.notification.NotificationEvent
import com.github.search5.yona.domain.notification.NotificationEventRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.Optional
import io.mockk.clearMocks

class NotificationControllerSpec : DescribeSpec({
    val notificationEventRepository = mockk<NotificationEventRepository>()
    val userRepository = mockk<UserRepository>()
    val notificationController = NotificationController(notificationEventRepository, userRepository)
    val mockMvc = MockMvcBuilders.standaloneSetup(notificationController).build()

    beforeTest {
        clearMocks(notificationEventRepository, userRepository)
    }

    describe("NotificationController 웹 API 테스트") {
        val testUser = User(id = 1L, loginId = "gildong", name = "홍길동", email = "gildong@example.com")
        val auth = UsernamePasswordAuthenticationToken("gildong", "password")

        describe("GET /api/notifications") {
            it("로그인한 수신 유저에 속한 알림 이벤트 리스트를 DTO 형태로 반환해야 한다") {
                // Given
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                
                val event = NotificationEvent(
                    id = 100L,
                    title = "새 이슈 등록알림",
                    senderId = 2L,
                    created = Instant.now(),
                    resourceType = ResourceType.ISSUE_POST,
                    resourceId = "1",
                    eventType = EventType.NEW_ISSUE
                )
                val pageable = PageRequest.of(0, 10)
                every { notificationEventRepository.findByReceiver(testUser, pageable) } returns PageImpl(listOf(event))

                // When & Then
                mockMvc.perform(
                    get("/api/notifications")
                        .param("from", "0")
                        .param("size", "10")
                        .principal(auth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].id").value(100))
                    .andExpect(jsonPath("$[0].title").value("새 이슈 등록알림"))
                    .andExpect(jsonPath("$[0].resourceType").value("ISSUE_POST"))
                    .andExpect(jsonPath("$[0].eventType").value("NEW_ISSUE"))
            }

            it("인증 정보가 없을 경우 401 권한 없음 에러를 리턴해야 한다") {
                // When & Then
                mockMvc.perform(
                    get("/api/notifications")
                        .param("from", "0")
                        .param("size", "10")
                )
                    .andExpect(status().isUnauthorized)
            }
        }
    }
})
