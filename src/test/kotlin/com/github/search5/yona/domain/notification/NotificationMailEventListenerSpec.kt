package com.github.search5.yona.domain.notification

import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueCommentRepository
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.board.PostingCommentRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.mail.MailService
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.Runs
import io.mockk.just
import java.time.Instant
import java.util.Optional

class NotificationMailEventListenerSpec : DescribeSpec({
    val mailService = mockk<MailService>(relaxed = true)
    val notificationMailRepository = mockk<NotificationMailRepository>()
    val issueRepository = mockk<IssueRepository>(relaxed = true)
    val postingRepository = mockk<PostingRepository>(relaxed = true)
    val issueCommentRepository = mockk<IssueCommentRepository>(relaxed = true)
    val postingCommentRepository = mockk<PostingCommentRepository>(relaxed = true)

    val listener = NotificationMailEventListener(
        mailService, notificationMailRepository,
        issueRepository, postingRepository, issueCommentRepository, postingCommentRepository,
        "yona@example.com"
    )

    beforeTest {
        io.mockk.clearMocks(
            mailService, notificationMailRepository,
            issueRepository, postingRepository, issueCommentRepository, postingCommentRepository,
            answers = false
        )
        every { notificationMailRepository.save(any()) } returnsArgument 0
        // 기본값: resourceId로 프로젝트를 되짚어 찾지 못하면 Reply-To 없이(=replyTo null) 발송된다.
        every { issueRepository.findById(any()) } returns Optional.empty()
        every { postingRepository.findById(any()) } returns Optional.empty()
        every { issueCommentRepository.findById(any()) } returns Optional.empty()
        every { postingCommentRepository.findById(any()) } returns Optional.empty()
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
                mailService.sendHtmlMailWithReplyTo(toEmail = "u1@example.com", toName = "사용자1", subject = event.title, htmlContent = any(), replyTo = any())
            }
            verify(exactly = 1) {
                mailService.sendHtmlMailWithReplyTo(toEmail = "u2@example.com", toName = "사용자2", subject = event.title, htmlContent = any(), replyTo = any())
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
                mailService.sendHtmlMailWithReplyTo(any(), any(), any(), capture(captured), any())
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

            verify(exactly = 0) { mailService.sendHtmlMailWithReplyTo(any(), any(), any(), any(), any()) }
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

            verify(exactly = 0) { mailService.sendHtmlMailWithReplyTo(any(), any(), any(), any(), any()) }
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
                mailService.sendHtmlMailWithReplyTo(toEmail = "u1@example.com", toName = any(), subject = any(), htmlContent = any(), replyTo = any())
            } throws RuntimeException("SMTP down")

            listener.handleNotificationEvent(event)

            verify(exactly = 1) {
                mailService.sendHtmlMailWithReplyTo(toEmail = "u2@example.com", toName = "사용자2", subject = any(), htmlContent = any(), replyTo = any())
            }
        }

        describe("본문 변경 diff 하이라이트 (P2-02, yona NotificationEvent.getMessage()의 DiffUtil.getDiffText 대응)") {
            it("ISSUE_BODY_CHANGED면 DiffUtil로 렌더링한 삭제/삽입 하이라이트가 메일 본문에 포함돼야 한다") {
                val receiver = User(id = 1L, loginId = "u1", name = "사용자1", email = "u1@example.com")
                val event = NotificationEvent(
                    id = 200L,
                    title = "이슈 본문 변경",
                    created = Instant.now(),
                    resourceType = ResourceType.ISSUE_POST,
                    resourceId = "1",
                    eventType = EventType.ISSUE_BODY_CHANGED,
                    oldValue = "old text",
                    newValue = "new text",
                    receivers = mutableSetOf(receiver)
                )
                val captured = slot<String>()
                every {
                    mailService.sendHtmlMailWithReplyTo(any(), any(), any(), capture(captured), any())
                } just Runs

                listener.handleNotificationEvent(event)

                captured.captured shouldBe "<div>${com.github.search5.yona.domain.support.DiffUtil.getDiffText("old text", "new text")}</div>"
            }

            it("POSTING_BODY_CHANGED도 동일하게 DiffUtil 렌더링을 사용해야 한다") {
                val receiver = User(id = 1L, loginId = "u1", name = "사용자1", email = "u1@example.com")
                val event = NotificationEvent(
                    id = 201L,
                    title = "게시글 본문 변경",
                    created = Instant.now(),
                    resourceType = ResourceType.BOARD_POST,
                    resourceId = "1",
                    eventType = EventType.POSTING_BODY_CHANGED,
                    oldValue = "old body",
                    newValue = "new body",
                    receivers = mutableSetOf(receiver)
                )
                val captured = slot<String>()
                every {
                    mailService.sendHtmlMailWithReplyTo(any(), any(), any(), capture(captured), any())
                } just Runs

                listener.handleNotificationEvent(event)

                captured.captured shouldBe "<div>${com.github.search5.yona.domain.support.DiffUtil.getDiffText("old body", "new body")}</div>"
            }
        }

        describe("Reply-To 헤더 (P1-28, yona NotificationMail.getReplyTo() 대응)") {
            it("ISSUE_POST 리소스면 owner/project detail이 담긴 plus-address를 Reply-To로 설정해야 한다") {
                val project = Project(owner = "gildong", name = "yona-project")
                val issue = Issue(id = 1L, title = "제목", body = "본문", project = project, number = 1L)
                every { issueRepository.findById(1L) } returns Optional.of(issue)

                val receiver = User(id = 1L, loginId = "u1", name = "사용자1", email = "u1@example.com")
                val event = NotificationEvent(
                    id = 200L, title = "새 이슈", created = Instant.now(),
                    resourceType = ResourceType.ISSUE_POST, resourceId = "1",
                    eventType = EventType.NEW_ISSUE, receivers = mutableSetOf(receiver)
                )

                listener.handleNotificationEvent(event)

                verify(exactly = 1) {
                    mailService.sendHtmlMailWithReplyTo(
                        toEmail = "u1@example.com", toName = "사용자1", subject = any(), htmlContent = any(),
                        replyTo = "yona+gildong/yona-project@example.com"
                    )
                }
            }

            it("IMAP 주소가 설정돼 있지 않으면 Reply-To 없이 발송해야 한다") {
                val listenerWithoutImap = NotificationMailEventListener(
                    mailService, notificationMailRepository,
                    issueRepository, postingRepository, issueCommentRepository, postingCommentRepository,
                    ""
                )
                val project = Project(owner = "gildong", name = "yona-project")
                val issue = Issue(id = 1L, title = "제목", body = "본문", project = project, number = 1L)
                every { issueRepository.findById(1L) } returns Optional.of(issue)

                val receiver = User(id = 1L, loginId = "u1", name = "사용자1", email = "u1@example.com")
                val event = NotificationEvent(
                    id = 201L, title = "새 이슈", created = Instant.now(),
                    resourceType = ResourceType.ISSUE_POST, resourceId = "1",
                    eventType = EventType.NEW_ISSUE, receivers = mutableSetOf(receiver)
                )

                listenerWithoutImap.handleNotificationEvent(event)

                verify(exactly = 1) {
                    mailService.sendHtmlMailWithReplyTo(toEmail = "u1@example.com", toName = "사용자1", subject = any(), htmlContent = any(), replyTo = null)
                }
            }

            it("리소스를 찾을 수 없으면 Reply-To 없이 발송해야 한다") {
                every { issueRepository.findById(999L) } returns Optional.empty()

                val receiver = User(id = 1L, loginId = "u1", name = "사용자1", email = "u1@example.com")
                val event = NotificationEvent(
                    id = 202L, title = "새 이슈", created = Instant.now(),
                    resourceType = ResourceType.ISSUE_POST, resourceId = "999",
                    eventType = EventType.NEW_ISSUE, receivers = mutableSetOf(receiver)
                )

                listener.handleNotificationEvent(event)

                verify(exactly = 1) {
                    mailService.sendHtmlMailWithReplyTo(toEmail = "u1@example.com", toName = "사용자1", subject = any(), htmlContent = any(), replyTo = null)
                }
            }
        }
    }
})
