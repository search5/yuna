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
import com.github.search5.yona.domain.pullrequest.CommitComment
import com.github.search5.yona.domain.pullrequest.CommitCommentRepository
import com.github.search5.yona.domain.pullrequest.PullRequest
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.domain.pullrequest.ReviewComment
import com.github.search5.yona.domain.pullrequest.ReviewCommentRepository
import com.github.search5.yona.domain.pullrequest.SimpleCommentThread
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearMocks
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
    val pullRequestRepository = mockk<PullRequestRepository>()
    val reviewCommentRepository = mockk<ReviewCommentRepository>()
    val commitCommentRepository = mockk<CommitCommentRepository>()

    val listener = WebhookNotificationEventListener(
        webhookService, userRepository, issueRepository, postingRepository,
        issueCommentRepository, postingCommentRepository, pullRequestRepository,
        reviewCommentRepository, commitCommentRepository
    )

    val project = Project(id = 1L, name = "yona-project", owner = "gildong")
    val sender = User(id = 9L, loginId = "gildong", name = "길동")

    beforeTest {
        clearMocks(webhookService, userRepository, issueRepository, postingRepository, issueCommentRepository, postingCommentRepository, pullRequestRepository, reviewCommentRepository, commitCommentRepository, answers = false)
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

        it("PULL_REQUEST 타입 NotificationEvent를 받으면 해당 PR의 toProject로 웹훅을 발송해야 한다 (P1-26)") {
            val contributor = User(id = 20L, loginId = "contributor", name = "기여자")
            val pullRequest = PullRequest(
                id = 500L, title = "PR 제목", toProject = project, fromProject = project,
                toBranch = "master", fromBranch = "feature", contributor = contributor
            )
            every { pullRequestRepository.findById(500L) } returns Optional.of(pullRequest)

            val event = NotificationEvent(
                title = "PR 리뷰", senderId = 9L, created = Instant.now(),
                resourceType = ResourceType.PULL_REQUEST, resourceId = "500",
                eventType = EventType.PULL_REQUEST_REVIEW_STATE_CHANGED
            )

            listener.handleNotificationEvent(event)

            verify(exactly = 1) { webhookService.sendWebhook(project, EventType.PULL_REQUEST_REVIEW_STATE_CHANGED, sender, pullRequest) }
        }

        it("PULL_REQUEST 타입인데 대상 PR을 찾을 수 없으면 웹훅을 발송하지 않아야 한다") {
            every { pullRequestRepository.findById(999L) } returns Optional.empty()

            val event = NotificationEvent(
                title = "PR 리뷰", senderId = 9L, created = Instant.now(),
                resourceType = ResourceType.PULL_REQUEST, resourceId = "999",
                eventType = EventType.PULL_REQUEST_REVIEW_STATE_CHANGED
            )

            listener.handleNotificationEvent(event)

            verify(exactly = 0) { webhookService.sendWebhook(any(), any(), any(), any()) }
        }

        // yona NotificationEvent.java:756 webhookRequest(NEW_REVIEW_COMMENT, pullRequest, newComment) 대응 (P1-69)
        it("REVIEW_COMMENT 타입은 댓글이 속한 스레드의 project로 웹훅을 발송해야 한다") {
            val thread = SimpleCommentThread(id = 700L, project = project)
            val comment = ReviewComment(id = 600L, contents = "리뷰 의견", thread = thread)
            every { reviewCommentRepository.findById(600L) } returns Optional.of(comment)

            val event = NotificationEvent(
                title = "새 리뷰 댓글", senderId = 9L, created = Instant.now(),
                resourceType = ResourceType.REVIEW_COMMENT, resourceId = "600", eventType = EventType.NEW_REVIEW_COMMENT
            )

            listener.handleNotificationEvent(event)

            verify(exactly = 1) { webhookService.sendWebhook(project, EventType.NEW_REVIEW_COMMENT, sender, comment) }
        }

        it("REVIEW_COMMENT 타입인데 대상 댓글을 찾을 수 없으면 웹훅을 발송하지 않아야 한다") {
            every { reviewCommentRepository.findById(999L) } returns Optional.empty()

            val event = NotificationEvent(
                title = "새 리뷰 댓글", senderId = 9L, created = Instant.now(),
                resourceType = ResourceType.REVIEW_COMMENT, resourceId = "999", eventType = EventType.NEW_REVIEW_COMMENT
            )

            listener.handleNotificationEvent(event)

            verify(exactly = 0) { webhookService.sendWebhook(any(), any(), any(), any()) }
        }

        // yona NotificationEvent.java:780 webhookRequest(NEW_COMMENT, comment) 대응 (P1-69)
        it("COMMIT_COMMENT 타입은 댓글의 project로 웹훅을 발송해야 한다") {
            val comment = CommitComment(id = 800L, contents = "커밋 의견", project = project, commitId = "abc123")
            every { commitCommentRepository.findById(800L) } returns Optional.of(comment)

            val event = NotificationEvent(
                title = "새 커밋 댓글", senderId = 9L, created = Instant.now(),
                resourceType = ResourceType.COMMIT_COMMENT, resourceId = "800", eventType = EventType.NEW_COMMENT
            )

            listener.handleNotificationEvent(event)

            verify(exactly = 1) { webhookService.sendWebhook(project, EventType.NEW_COMMENT, sender, comment) }
        }

        it("COMMIT_COMMENT 타입인데 대상 댓글을 찾을 수 없으면 웹훅을 발송하지 않아야 한다") {
            every { commitCommentRepository.findById(999L) } returns Optional.empty()

            val event = NotificationEvent(
                title = "새 커밋 댓글", senderId = 9L, created = Instant.now(),
                resourceType = ResourceType.COMMIT_COMMENT, resourceId = "999", eventType = EventType.NEW_COMMENT
            )

            listener.handleNotificationEvent(event)

            verify(exactly = 0) { webhookService.sendWebhook(any(), any(), any(), any()) }
        }
    }
})
