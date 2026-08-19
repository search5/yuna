package com.github.search5.yona.domain.webhook

import com.github.search5.yona.domain.board.Posting
import com.github.search5.yona.domain.board.PostingComment
import com.github.search5.yona.domain.board.PostingCommentRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueComment
import com.github.search5.yona.domain.issue.IssueCommentRepository
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.notification.NotificationEvent
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.Optional

class WebhookNotificationEventListenerSpec : DescribeSpec({
    val webhookService = mockk<WebhookService>(relaxed = true)
    val userRepository = mockk<UserRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val issueCommentRepository = mockk<IssueCommentRepository>()
    val postingCommentRepository = mockk<PostingCommentRepository>()

    val listener = WebhookNotificationEventListener(
        webhookService, userRepository, issueRepository, postingRepository,
        issueCommentRepository, postingCommentRepository
    )

    val project = Project(id = 1L, name = "yona-project", owner = "gildong")
    val sender = User(id = 9L, loginId = "gildong", name = "길동")

    beforeTest {
        io.mockk.clearMocks(webhookService, userRepository, issueRepository, postingRepository, issueCommentRepository, postingCommentRepository, answers = false)
        every { userRepository.findById(9L) } returns Optional.of(sender)
    }

    describe("WebhookNotificationEventListener") {
        it("ISSUE_POST 타입 NotificationEvent를 받으면 해당 이슈로 웹훅을 발송해야 한다") {
            val issue = Issue(id = 100L, title = "제목", body = "본문", project = project, number = 1L)
            every { issueRepository.findById(100L) } returns Optional.of(issue)

            val event = NotificationEvent(
                title = "새 이슈", senderId = 9L, created = Instant.now(),
                resourceType = ResourceType.ISSUE_POST, resourceId = "100", eventType = EventType.NEW_ISSUE
            )

            listener.handleNotificationEvent(event)

            verify(exactly = 1) { webhookService.sendWebhook(project, EventType.NEW_ISSUE, sender, issue) }
        }

        it("ISSUE_COMMENT 타입은 댓글이 속한 이슈의 project로 웹훅을 발송해야 한다") {
            val issue = Issue(id = 100L, title = "제목", body = "본문", project = project, number = 1L)
            val comment = IssueComment(id = 200L, contents = "댓글", issue = issue)
            every { issueCommentRepository.findById(200L) } returns Optional.of(comment)

            val event = NotificationEvent(
                title = "새 댓글", senderId = 9L, created = Instant.now(),
                resourceType = ResourceType.ISSUE_COMMENT, resourceId = "200", eventType = EventType.NEW_COMMENT
            )

            listener.handleNotificationEvent(event)

            verify(exactly = 1) { webhookService.sendWebhook(project, EventType.NEW_COMMENT, sender, comment) }
        }

        it("BOARD_POST 타입 NotificationEvent를 받으면 해당 게시글로 웹훅을 발송해야 한다") {
            val posting = Posting(id = 300L, title = "게시글", body = "내용", project = project, number = 1L)
            every { postingRepository.findById(300L) } returns Optional.of(posting)

            val event = NotificationEvent(
                title = "새 게시글", senderId = 9L, created = Instant.now(),
                resourceType = ResourceType.BOARD_POST, resourceId = "300", eventType = EventType.NEW_POSTING
            )

            listener.handleNotificationEvent(event)

            verify(exactly = 1) { webhookService.sendWebhook(project, EventType.NEW_POSTING, sender, posting) }
        }

        it("NONISSUE_COMMENT 타입은 댓글이 속한 게시글의 project로 웹훅을 발송해야 한다") {
            val posting = Posting(id = 300L, title = "게시글", body = "내용", project = project, number = 1L)
            val comment = PostingComment(id = 400L, contents = "댓글", posting = posting)
            every { postingCommentRepository.findById(400L) } returns Optional.of(comment)

            val event = NotificationEvent(
                title = "새 댓글", senderId = 9L, created = Instant.now(),
                resourceType = ResourceType.NONISSUE_COMMENT, resourceId = "400", eventType = EventType.NEW_COMMENT
            )

            listener.handleNotificationEvent(event)

            verify(exactly = 1) { webhookService.sendWebhook(project, EventType.NEW_COMMENT, sender, comment) }
        }

        it("리소스를 찾을 수 없으면 웹훅을 발송하지 않아야 한다") {
            every { issueRepository.findById(999L) } returns Optional.empty()

            val event = NotificationEvent(
                title = "새 이슈", senderId = 9L, created = Instant.now(),
                resourceType = ResourceType.ISSUE_POST, resourceId = "999", eventType = EventType.NEW_ISSUE
            )

            listener.handleNotificationEvent(event)

            verify(exactly = 0) { webhookService.sendWebhook(any(), any(), any(), any()) }
        }

        it("senderId가 없으면 웹훅을 발송하지 않아야 한다") {
            val event = NotificationEvent(
                title = "새 이슈", senderId = null, created = Instant.now(),
                resourceType = ResourceType.ISSUE_POST, resourceId = "100", eventType = EventType.NEW_ISSUE
            )

            listener.handleNotificationEvent(event)

            verify(exactly = 0) { webhookService.sendWebhook(any(), any(), any(), any()) }
        }

        it("아직 지원하지 않는 리소스 타입(PULL_REQUEST)은 조용히 스킵해야 한다") {
            val event = NotificationEvent(
                title = "PR 리뷰", senderId = 9L, created = Instant.now(),
                resourceType = ResourceType.PULL_REQUEST, resourceId = "500",
                eventType = EventType.PULL_REQUEST_REVIEW_STATE_CHANGED
            )

            listener.handleNotificationEvent(event)

            verify(exactly = 0) { webhookService.sendWebhook(any(), any(), any(), any()) }
        }
    }
})
