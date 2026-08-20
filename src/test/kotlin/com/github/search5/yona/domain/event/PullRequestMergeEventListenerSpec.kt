package com.github.search5.yona.domain.event

import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.issue.IssueService
import com.github.search5.yona.domain.notification.NotificationEvent
import com.github.search5.yona.domain.notification.NotificationEventRecorder
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.pullrequest.PullRequest
import com.github.search5.yona.domain.pullrequest.PullRequestCommitRepository
import com.github.search5.yona.domain.pullrequest.PullRequestEvent
import com.github.search5.yona.domain.pullrequest.PullRequestEventRepository
import com.github.search5.yona.domain.pullrequest.PullRequestMergeResult
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.domain.pullrequest.PullRequestService
import com.github.search5.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional

class PullRequestMergeEventListenerSpec : DescribeSpec({
    val pullRequestRepository = mockk<PullRequestRepository>()
    val pullRequestCommitRepository = mockk<PullRequestCommitRepository>()
    val issueRepository = mockk<IssueRepository>()
    val issueService = mockk<IssueService>()
    val pullRequestService = mockk<PullRequestService>()
    val notificationEventRecorder = mockk<NotificationEventRecorder>()
    val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    val pullRequestEventRepository = mockk<PullRequestEventRepository>(relaxed = true)

    val listener = PullRequestMergeEventListener(
        pullRequestRepository, pullRequestCommitRepository, issueRepository, issueService,
        pullRequestService, notificationEventRecorder, eventPublisher, pullRequestEventRepository
    )

    val project = Project(id = 1L, name = "yona-project", owner = "gildong")
    val contributor = User(id = 5L, loginId = "contributor", name = "기여자")
    val sender = User(id = 9L, loginId = "pusher", name = "푸셔")

    fun pr(id: Long, conflict: Boolean?) = PullRequest(
        id = id, title = "관련 PR", body = "...", toProject = project, fromProject = project,
        toBranch = "master", fromBranch = "feature", contributor = contributor,
        state = State.OPEN, number = id, isConflict = conflict, isMerging = false
    )

    beforeTest {
        io.mockk.clearMocks(
            pullRequestRepository, pullRequestCommitRepository, issueRepository, issueService,
            pullRequestService, notificationEventRecorder, eventPublisher, pullRequestEventRepository, answers = false
        )
        every { notificationEventRecorder.record(any()) } answers { firstArg() }
    }

    describe("PullRequestMergeEventListener.handlePullRequestMergeEvent") {
        it("PR을 병합하면 PullRequestEvent 타임라인 항목이 생성되어야 한다") {
            val mergedPr = pr(200L, conflict = false)
            every { pullRequestRepository.findById(200L) } returns Optional.of(mergedPr)
            every { pullRequestRepository.save(any()) } answers { firstArg() }
            every { pullRequestCommitRepository.findByPullRequest(mergedPr) } returns emptyList()

            val captured = slot<PullRequestEvent>()
            every { pullRequestEventRepository.save(capture(captured)) } answers { firstArg() }

            listener.handlePullRequestMergeEvent(PullRequestMergeEvent(pullRequestId = 200L, sender = sender, isNewPullRequest = false))

            mergedPr.state shouldBe State.MERGED
            captured.captured.eventType.name shouldBe "PULL_REQUEST_STATE_CHANGED"
            captured.captured.newValue shouldBe "MERGED"
            captured.captured.senderLoginId shouldBe "pusher"
        }
    }

    describe("PullRequestMergeEventListener.handleRelatedPullRequestMergeEvent") {
        it("재병합 검사 후 isMerging을 다시 false로 되돌려야 한다") {
            val relatedPr = pr(100L, conflict = false)
            every { pullRequestRepository.findRelatedPullRequests(project, "feature") } returns listOf(relatedPr)
            every { pullRequestRepository.save(any()) } answers { firstArg() }
            every { pullRequestService.processMergeCheck(100L, sender, false) } returns PullRequestMergeResult(pullRequest = relatedPr)
            every { pullRequestRepository.findById(100L) } returns Optional.of(relatedPr)

            listener.handleRelatedPullRequestMergeEvent(RelatedPullRequestMergeEvent(project, "feature", sender))

            relatedPr.isMerging shouldBe false
            verify(exactly = 1) { pullRequestService.processMergeCheck(100L, sender, false) }
        }

        it("충돌이 새로 발생하면(false to true) 알림 이벤트를 발행해야 한다") {
            val relatedPr = pr(101L, conflict = false)
            every { pullRequestRepository.findRelatedPullRequests(project, "feature") } returns listOf(relatedPr)
            every { pullRequestRepository.save(any()) } answers { firstArg() }
            every { pullRequestService.processMergeCheck(101L, sender, false) } answers {
                relatedPr.isConflict = true
                PullRequestMergeResult(pullRequest = relatedPr)
            }
            every { pullRequestRepository.findById(101L) } returns Optional.of(relatedPr)

            val captured = slot<NotificationEvent>()
            every { eventPublisher.publishEvent(capture(captured)) } returns Unit

            listener.handleRelatedPullRequestMergeEvent(RelatedPullRequestMergeEvent(project, "feature", sender))

            verify(exactly = 1) { eventPublisher.publishEvent(any<NotificationEvent>()) }
            captured.captured.eventType.name shouldBe "PULL_REQUEST_MERGED"
        }

        it("충돌이 해소되면(true to false) 알림 이벤트를 발행해야 한다") {
            val relatedPr = pr(102L, conflict = true)
            every { pullRequestRepository.findRelatedPullRequests(project, "feature") } returns listOf(relatedPr)
            every { pullRequestRepository.save(any()) } answers { firstArg() }
            every { pullRequestService.processMergeCheck(102L, sender, false) } answers {
                relatedPr.isConflict = false
                PullRequestMergeResult(pullRequest = relatedPr)
            }
            every { pullRequestRepository.findById(102L) } returns Optional.of(relatedPr)

            listener.handleRelatedPullRequestMergeEvent(RelatedPullRequestMergeEvent(project, "feature", sender))

            verify(exactly = 1) { eventPublisher.publishEvent(any<NotificationEvent>()) }
        }

        it("충돌 상태에 변화가 없으면 알림 이벤트를 발행하지 않아야 한다") {
            val relatedPr = pr(103L, conflict = false)
            every { pullRequestRepository.findRelatedPullRequests(project, "feature") } returns listOf(relatedPr)
            every { pullRequestRepository.save(any()) } answers { firstArg() }
            every { pullRequestService.processMergeCheck(103L, sender, false) } returns PullRequestMergeResult(pullRequest = relatedPr)
            every { pullRequestRepository.findById(103L) } returns Optional.of(relatedPr)

            listener.handleRelatedPullRequestMergeEvent(RelatedPullRequestMergeEvent(project, "feature", sender))

            verify(exactly = 0) { eventPublisher.publishEvent(any<NotificationEvent>()) }
        }

        it("processMergeCheck가 예외를 던져도 isMerging은 false로 복구되어야 한다") {
            val relatedPr = pr(104L, conflict = false)
            every { pullRequestRepository.findRelatedPullRequests(project, "feature") } returns listOf(relatedPr)
            every { pullRequestRepository.save(any()) } answers { firstArg() }
            every { pullRequestService.processMergeCheck(104L, sender, false) } throws RuntimeException("git error")
            every { pullRequestRepository.findById(104L) } returns Optional.of(relatedPr)

            listener.handleRelatedPullRequestMergeEvent(RelatedPullRequestMergeEvent(project, "feature", sender))

            relatedPr.isMerging shouldBe false
        }
    }
})
