package com.github.search5.yona.domain.notification

import com.github.search5.yona.domain.board.PostingCommentRepository
import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueComment
import com.github.search5.yona.domain.issue.IssueCommentRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.pullrequest.CodeCommentThread
import com.github.search5.yona.domain.pullrequest.ReviewComment
import com.github.search5.yona.domain.pullrequest.ReviewCommentRepository
import com.github.search5.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.Optional

// yona models/NotificationMail.java의 mergeEvents() 대응 (P1-27).
class NotificationEventMergerSpec : DescribeSpec({
    val issueCommentRepository = mockk<IssueCommentRepository>()
    val postingCommentRepository = mockk<PostingCommentRepository>()
    val reviewCommentRepository = mockk<ReviewCommentRepository>()
    val merger = NotificationEventMerger(issueCommentRepository, postingCommentRepository, reviewCommentRepository)

    val project = Project(id = 1L, name = "proj", owner = "owner")
    val issue = Issue(id = 100L, title = "이슈", project = project)
    val thread = CodeCommentThread(id = 200L, project = project)
    val alice = User(id = 1L, loginId = "alice", name = "앨리스")
    val bob = User(id = 2L, loginId = "bob", name = "밥")
    val carol = User(id = 3L, loginId = "carol", name = "캐롤")

    fun stateChanged(sender: Long?, receivers: Set<User>, at: Instant) = NotificationEvent(
        title = "상태 변경", senderId = sender, created = at,
        resourceType = ResourceType.ISSUE_STATE, resourceId = "100",
        eventType = EventType.ISSUE_STATE_CHANGED, oldValue = "OPEN", newValue = "CLOSED",
        receivers = receivers.toMutableSet()
    )

    fun newComment(commentId: String, sender: Long?, receivers: Set<User>, at: Instant) = NotificationEvent(
        title = "새 댓글", senderId = sender, created = at,
        resourceType = ResourceType.ISSUE_COMMENT, resourceId = commentId,
        eventType = EventType.NEW_COMMENT, newValue = "댓글 내용",
        receivers = receivers.toMutableSet()
    )

    fun threadStateChanged(sender: Long?, receivers: Set<User>, at: Instant) = NotificationEvent(
        title = "스레드 상태 변경", senderId = sender, created = at,
        resourceType = ResourceType.COMMENT_THREAD, resourceId = "200",
        eventType = EventType.REVIEW_THREAD_STATE_CHANGED, oldValue = "OPEN", newValue = "CLOSED",
        receivers = receivers.toMutableSet()
    )

    fun newReviewComment(commentId: String, sender: Long?, receivers: Set<User>, at: Instant) = NotificationEvent(
        title = "새 리뷰 댓글", senderId = sender, created = at,
        resourceType = ResourceType.REVIEW_COMMENT, resourceId = commentId,
        eventType = EventType.NEW_REVIEW_COMMENT, newValue = "댓글 내용",
        receivers = receivers.toMutableSet()
    )

    beforeTest {
        val comment = IssueComment(id = 500L, contents = "댓글", issue = issue)
        every { issueCommentRepository.findById(500L) } returns Optional.of(comment)
        val reviewComment = ReviewComment(id = 700L, contents = "리뷰 댓글", thread = thread)
        every { reviewCommentRepository.findById(700L) } returns Optional.of(reviewComment)
    }

    describe("NotificationEventMerger.mergeEvents") {
        it("서로 무관한 이벤트는 각각 단독으로 남아야 한다") {
            val e1 = stateChanged(1L, setOf(alice), Instant.parse("2026-01-01T00:00:00Z"))
            val e2 = newComment("999", 2L, setOf(bob), Instant.parse("2026-01-01T00:00:05Z"))
            every { issueCommentRepository.findById(999L) } returns Optional.empty()

            val merged = merger.mergeEvents(listOf(e1, e2))

            merged.size shouldBe 2
        }

        // legacy 클래스 주석대로 "이슈/리뷰 스레드를 열거나 닫는 알림 + 같은 사람이 남긴 '이전' 댓글 알림"의
        // 병합이므로, 댓글이 먼저(더 오래됨) 발생하고 상태변경이 나중(더 최근)에 발생해야 병합된다.
        it("댓글을 남긴 뒤 같은 사용자가 같은 수신자에게 상태변경을 하면 하나로 합쳐져야 한다") {
            val e1 = newComment("500", sender = 1L, receivers = setOf(alice, bob), at = Instant.parse("2026-01-01T00:00:00Z"))
            val e2 = stateChanged(sender = 1L, receivers = setOf(alice, bob), at = Instant.parse("2026-01-01T00:00:05Z"))

            val merged = merger.mergeEvents(listOf(e1, e2))

            merged.size shouldBe 1
            merged.first().messageSources shouldBe listOf(e1, e2)
            merged.first().receivers shouldBe setOf(alice, bob)
        }

        it("수신자 집합이 다르면 교집합/댓글전용/상태변경전용 세 갈래로 쪼개져야 한다") {
            val e1 = newComment("500", sender = 1L, receivers = setOf(bob, carol), at = Instant.parse("2026-01-01T00:00:00Z"))
            val e2 = stateChanged(sender = 1L, receivers = setOf(alice, bob), at = Instant.parse("2026-01-01T00:00:05Z"))

            val merged = merger.mergeEvents(listOf(e1, e2))

            merged.size shouldBe 3
            val byReceivers = merged.map { it.receivers }.toSet()
            byReceivers shouldBe setOf(setOf(bob), setOf(alice), setOf(carol))
        }

        it("발신자가 다르면 합쳐지지 않아야 한다") {
            val e1 = newComment("500", sender = 2L, receivers = setOf(alice), at = Instant.parse("2026-01-01T00:00:00Z"))
            val e2 = stateChanged(sender = 1L, receivers = setOf(alice), at = Instant.parse("2026-01-01T00:00:05Z"))

            val merged = merger.mergeEvents(listOf(e1, e2))

            merged.size shouldBe 2
        }

        // P1-50이 NEW_REVIEW_COMMENT/REVIEW_THREAD_STATE_CHANGED 프로듀서를 추가하면서 이 병합 로직이
        // 함께 갱신되지 않아 리뷰 댓글은 항상 단독 이벤트로 남던 문제(P1-51에서 발견) 대응.
        it("리뷰 댓글을 남긴 뒤 같은 사용자가 같은 수신자에게 스레드 상태를 변경하면 하나로 합쳐져야 한다") {
            val e1 = newReviewComment("700", sender = 1L, receivers = setOf(alice, bob), at = Instant.parse("2026-01-01T00:00:00Z"))
            val e2 = threadStateChanged(sender = 1L, receivers = setOf(alice, bob), at = Instant.parse("2026-01-01T00:00:05Z"))

            val merged = merger.mergeEvents(listOf(e1, e2))

            merged.size shouldBe 1
            merged.first().messageSources shouldBe listOf(e1, e2)
            merged.first().receivers shouldBe setOf(alice, bob)
        }

        it("리뷰 댓글과 스레드 상태변경의 수신자 집합이 다르면 세 갈래로 쪼개져야 한다") {
            val e1 = newReviewComment("700", sender = 1L, receivers = setOf(bob, carol), at = Instant.parse("2026-01-01T00:00:00Z"))
            val e2 = threadStateChanged(sender = 1L, receivers = setOf(alice, bob), at = Instant.parse("2026-01-01T00:00:05Z"))

            val merged = merger.mergeEvents(listOf(e1, e2))

            merged.size shouldBe 3
            val byReceivers = merged.map { it.receivers }.toSet()
            byReceivers shouldBe setOf(setOf(bob), setOf(alice), setOf(carol))
        }
    }
})
