package com.github.search5.yona.domain.notification

import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.mail.MailService
import com.github.search5.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.Runs
import io.mockk.just
import java.time.Instant

class NotificationMailEventListenerSpec : DescribeSpec({
    val mailService = mockk<MailService>(relaxed = true)
    val notificationMailRepository = mockk<NotificationMailRepository>()

    val listener = NotificationMailEventListener(mailService, notificationMailRepository)

    beforeTest {
        io.mockk.clearMocks(mailService, notificationMailRepository, answers = false)
        every { notificationMailRepository.save(any()) } returnsArgument 0
    }

    describe("NotificationMailEventListener") {
        it("수신자 각각에게 알림 메일을 발송해야 한다") {
            val receiver1 = User(id = 1L, loginId = "u1", name = "사용자1", email = "u1@example.com")
            val receiver2 = User(id = 2L, loginId = "u2", name = "사용자2", email = "u2@example.com")
            val event = NotificationEvent(
                id = 100L,
                title = "[project] 새 이슈: #1 제목",
                created = Instant.now(),
                resourceType = ResourceType.ISSUE_POST,
                resourceId = "1",
                eventType = EventType.NEW_ISSUE,
                newValue = "이슈 본문 내용",
                receivers = mutableSetOf(receiver1, receiver2)
            )

            listener.handleNotificationEvent(event)

            verify(exactly = 1) {
                mailService.sendHtmlMail(toEmail = "u1@example.com", toName = "사용자1", subject = event.title, htmlContent = any())
            }
            verify(exactly = 1) {
                mailService.sendHtmlMail(toEmail = "u2@example.com", toName = "사용자2", subject = event.title, htmlContent = any())
            }
        }

        it("메일 본문에는 escape된 알림 내용이 포함되어야 한다 (XSS 방지)") {
            val receiver = User(id = 1L, loginId = "u1", name = "사용자1", email = "u1@example.com")
            val event = NotificationEvent(
                id = 100L,
                title = "새 댓글",
                created = Instant.now(),
                resourceType = ResourceType.ISSUE_COMMENT,
                resourceId = "1",
                eventType = EventType.NEW_COMMENT,
                newValue = "<script>alert(1)</script>",
                receivers = mutableSetOf(receiver)
            )
            val captured = slot<String>()
            every {
                mailService.sendHtmlMail(any(), any(), any(), capture(captured))
            } just Runs

            listener.handleNotificationEvent(event)

            captured.captured.shouldNotContain("<script")
        }

        it("이메일이 비어있는 수신자는 건너뛰어야 한다") {
            val noEmailUser = User(id = 3L, loginId = "u3", name = "이메일없음", email = "")
            val event = NotificationEvent(
                id = 101L,
                title = "제목",
                created = Instant.now(),
                resourceType = ResourceType.ISSUE_POST,
                resourceId = "1",
                eventType = EventType.NEW_ISSUE,
                receivers = mutableSetOf(noEmailUser)
            )

            listener.handleNotificationEvent(event)

            verify(exactly = 0) { mailService.sendHtmlMail(any(), any(), any(), any()) }
        }

        it("수신자가 없으면 아무 메일도 발송하지 않고 NotificationMail도 저장하지 않아야 한다") {
            val event = NotificationEvent(
                id = 102L,
                title = "제목",
                created = Instant.now(),
                resourceType = ResourceType.ISSUE_POST,
                resourceId = "1",
                eventType = EventType.NEW_ISSUE,
                receivers = mutableSetOf()
            )

            listener.handleNotificationEvent(event)

            verify(exactly = 0) { mailService.sendHtmlMail(any(), any(), any(), any()) }
            verify(exactly = 0) { notificationMailRepository.save(any()) }
        }

        it("처리 완료 후 NotificationMail 마커를 저장해야 한다") {
            val receiver = User(id = 1L, loginId = "u1", name = "사용자1", email = "u1@example.com")
            val event = NotificationEvent(
                id = 103L,
                title = "제목",
                created = Instant.now(),
                resourceType = ResourceType.ISSUE_POST,
                resourceId = "1",
                eventType = EventType.NEW_ISSUE,
                receivers = mutableSetOf(receiver)
            )

            listener.handleNotificationEvent(event)

            verify(exactly = 1) { notificationMailRepository.save(match { it.notificationEvent == event }) }
        }

        it("한 수신자에게 발송 실패해도 나머지 수신자에게는 계속 발송을 시도해야 한다") {
            val receiver1 = User(id = 1L, loginId = "u1", name = "사용자1", email = "u1@example.com")
            val receiver2 = User(id = 2L, loginId = "u2", name = "사용자2", email = "u2@example.com")
            val event = NotificationEvent(
                id = 104L,
                title = "제목",
                created = Instant.now(),
                resourceType = ResourceType.ISSUE_POST,
                resourceId = "1",
                eventType = EventType.NEW_ISSUE,
                receivers = mutableSetOf(receiver1, receiver2)
            )
            every {
                mailService.sendHtmlMail(toEmail = "u1@example.com", toName = any(), subject = any(), htmlContent = any())
            } throws RuntimeException("SMTP down")

            listener.handleNotificationEvent(event)

            verify(exactly = 1) {
                mailService.sendHtmlMail(toEmail = "u2@example.com", toName = "사용자2", subject = any(), htmlContent = any())
            }
        }
    }
})
