package com.github.search5.yona.domain.notification

import com.github.search5.yona.domain.board.PostingCommentRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.issue.IssueCommentRepository
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.mail.MailRecipient
import com.github.search5.yona.domain.mail.MailService
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.pullrequest.CommentThreadRepository
import com.github.search5.yona.domain.pullrequest.CommitCommentRepository
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.domain.pullrequest.ReviewCommentRepository
import com.github.search5.yona.domain.support.MarkdownService
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.user.UserState
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.util.Optional

// yona models/NotificationMail.startSchedule()/sendMail()/sendNotification() 대응 (P1-27).
class NotificationMailDigestSchedulerSpec : DescribeSpec({
    val notificationMailRepository = mockk<NotificationMailRepository>()
    val notificationEventMerger = mockk<NotificationEventMerger>()
    val messageResolver = mockk<NotificationMessageResolver>()
    val urlResolver = mockk<NotificationUrlResolver>()
    val mailRenderer = mockk<NotificationMailRenderer>()
    val markdownService = mockk<MarkdownService>()
    val mailService = mockk<MailService>(relaxed = true)
    val userRepository = mockk<UserRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val issueCommentRepository = mockk<IssueCommentRepository>()
    val postingCommentRepository = mockk<PostingCommentRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val commitCommentRepository = mockk<CommitCommentRepository>()
    val reviewCommentRepository = mockk<ReviewCommentRepository>()
    val commentThreadRepository = mockk<CommentThreadRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val organizationRepository = mockk<OrganizationRepository>()

    fun scheduler(
        enabled: Boolean = true,
        hideAddress: Boolean = true,
        recipientLimit: Int = 0,
        allowedDomains: String = ""
    ) = NotificationMailDigestScheduler(
        notificationMailRepository, notificationEventMerger, messageResolver, urlResolver, mailRenderer,
        markdownService, mailService, userRepository, issueRepository, postingRepository,
        issueCommentRepository, postingCommentRepository, pullRequestRepository, commitCommentRepository,
        reviewCommentRepository, commentThreadRepository, projectRepository, organizationRepository,
        enabled, hideAddress, recipientLimit, 180000L, allowedDomains, "", "yona.example.com", "Yona"
    )

    beforeTest {
        io.mockk.clearMocks(
            notificationMailRepository, notificationEventMerger, messageResolver, urlResolver, mailRenderer,
            markdownService, mailService, userRepository, issueRepository, postingRepository,
            issueCommentRepository, postingCommentRepository, pullRequestRepository, commitCommentRepository,
            reviewCommentRepository, commentThreadRepository, projectRepository, organizationRepository,
            answers = false
        )
        every { markdownService.render(any(), any(), any(), any()) } answers { firstArg() }
        every { mailRenderer.render(any(), any(), any(), any(), any(), any()) } answers { firstArg() }
        every { mailRenderer.renderPlain(any()) } answers { firstArg() }
        every { urlResolver.getUrlToView(any()) } returns null
        every { userRepository.findById(any()) } returns Optional.empty()
    }

    fun receiver(id: Long, email: String = "u$id@yona.io", lang: String? = "ko", state: UserState = UserState.ACTIVE) =
        User(id = id, loginId = "user$id", name = "사용자$id", email = email, state = state, lang = lang)

    fun issueEvent(receivers: Set<User>, id: Long = 1L) = NotificationEvent(
        id = id, title = "제목", created = Instant.now(),
        resourceType = ResourceType.ISSUE_POST, resourceId = "1",
        eventType = EventType.NEW_ISSUE, newValue = "본문",
        receivers = receivers.toMutableSet()
    )

    describe("sendDueNotificationMails") {
        it("발송 비활성화 설정이면 아무 것도 조회하지 않는다") {
            scheduler(enabled = false).sendDueNotificationMails()

            verify(exactly = 0) { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) }
        }
    }

    describe("sendMail") {
        it("지연 시간이 지난 NotificationMail을 큐에서 꺼내 삭제하고, 이벤트를 병합해서 처리한다") {
            val receiverUser = receiver(2L)
            val event = issueEvent(setOf(receiverUser))
            val mail = NotificationMail(id = 100L, notificationEvent = event)
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(mail)
            every { notificationMailRepository.delete(mail) } returns Unit
            every { notificationEventMerger.mergeEvents(listOf(event)) } returns listOf(MergedNotificationEvent(event))
            every { issueRepository.existsById(1L) } returns true
            every { messageResolver.getMessage(any<MergedNotificationEvent>(), any()) } returns "메시지"
            every { messageResolver.getPlainMessage(any<MergedNotificationEvent>(), any()) } returns "평문 메시지"
            every { issueRepository.findById(1L) } returns Optional.empty()

            scheduler().sendMail()

            verify(exactly = 1) { notificationMailRepository.delete(mail) }
            verify(exactly = 1) { mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        it("리소스가 이미 삭제됐으면(resourceExists=false) 메일을 보내지 않는다") {
            val receiverUser = receiver(2L)
            val event = issueEvent(setOf(receiverUser))
            val mail = NotificationMail(id = 100L, notificationEvent = event)
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(mail)
            every { notificationMailRepository.delete(mail) } returns Unit
            every { notificationEventMerger.mergeEvents(listOf(event)) } returns listOf(MergedNotificationEvent(event))
            every { issueRepository.existsById(1L) } returns false

            scheduler().sendMail()

            verify(exactly = 0) { mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        it("비활성(UserState.LOCKED 등) 사용자는 수신자에서 제외한다") {
            val activeUser = receiver(2L, state = UserState.ACTIVE)
            val lockedUser = receiver(3L, state = UserState.LOCKED)
            val event = issueEvent(setOf(activeUser, lockedUser))
            val mail = NotificationMail(id = 100L, notificationEvent = event)
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(mail)
            every { notificationMailRepository.delete(mail) } returns Unit
            every { notificationEventMerger.mergeEvents(listOf(event)) } returns listOf(MergedNotificationEvent(event))
            every { issueRepository.existsById(1L) } returns true
            every { messageResolver.getMessage(any<MergedNotificationEvent>(), any()) } returns "메시지"
            every { messageResolver.getPlainMessage(any<MergedNotificationEvent>(), any()) } returns "평문 메시지"
            every { issueRepository.findById(1L) } returns Optional.empty()

            val bccSlot = slot<List<MailRecipient>>()
            every {
                mailService.sendNotificationMail(any(), capture(bccSlot), any(), any(), any(), any(), any(), any(), any(), any())
            } returns Unit

            scheduler(hideAddress = true).sendMail()

            bccSlot.captured.map { it.email } shouldBe listOf("u2@yona.io")
        }

        it("허용 도메인 설정이 있으면 다른 도메인 수신자는 제외한다") {
            val allowed = receiver(2L, email = "allowed@ok.com")
            val disallowed = receiver(3L, email = "blocked@bad.com")
            val event = issueEvent(setOf(allowed, disallowed))
            val mail = NotificationMail(id = 100L, notificationEvent = event)
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(mail)
            every { notificationMailRepository.delete(mail) } returns Unit
            every { notificationEventMerger.mergeEvents(listOf(event)) } returns listOf(MergedNotificationEvent(event))
            every { issueRepository.existsById(1L) } returns true
            every { messageResolver.getMessage(any<MergedNotificationEvent>(), any()) } returns "메시지"
            every { messageResolver.getPlainMessage(any<MergedNotificationEvent>(), any()) } returns "평문 메시지"
            every { issueRepository.findById(1L) } returns Optional.empty()

            val bccSlot = slot<List<MailRecipient>>()
            every {
                mailService.sendNotificationMail(any(), capture(bccSlot), any(), any(), any(), any(), any(), any(), any(), any())
            } returns Unit

            scheduler(allowedDomains = "ok.com").sendMail()

            bccSlot.captured.map { it.email } shouldBe listOf("allowed@ok.com")
        }

        it("언어가 다른 수신자는 각자 다른 로케일로 별도 발송된다") {
            val korean = receiver(2L, lang = "ko")
            val english = receiver(3L, lang = "en")
            val event = issueEvent(setOf(korean, english))
            val mail = NotificationMail(id = 100L, notificationEvent = event)
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(mail)
            every { notificationMailRepository.delete(mail) } returns Unit
            every { notificationEventMerger.mergeEvents(listOf(event)) } returns listOf(MergedNotificationEvent(event))
            every { issueRepository.existsById(1L) } returns true
            every { messageResolver.getMessage(any<MergedNotificationEvent>(), any()) } returns "메시지"
            every { messageResolver.getPlainMessage(any<MergedNotificationEvent>(), any()) } returns "평문 메시지"
            every { issueRepository.findById(1L) } returns Optional.empty()

            scheduler().sendMail()

            verify(exactly = 2) { mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            // yona Markdown.render(source, project, lang) 대응 (P1-140) — 이 스케줄러는 요청 스레드가
            // 아니라 LocaleContextHolder로 언어를 알 수 없으므로, 배치별로 계산해둔 수신자 언어를
            // markdownService.render()에 명시적으로 넘겨야 한다(각 배치가 자기 로케일을 그대로 받는지 검증).
            verify(exactly = 1) { markdownService.render("메시지", true, any(), "ko") }
            verify(exactly = 1) { markdownService.render("메시지", true, any(), "en") }
        }

        it("recipientLimit이 설정되면 hideAddress일 때 (limit-1)명 단위로 쪼개 발송한다") {
            val users = (1..5L).map { receiver(it + 1) }.toSet()
            val event = issueEvent(users)
            val mail = NotificationMail(id = 100L, notificationEvent = event)
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(mail)
            every { notificationMailRepository.delete(mail) } returns Unit
            every { notificationEventMerger.mergeEvents(listOf(event)) } returns listOf(MergedNotificationEvent(event))
            every { issueRepository.existsById(1L) } returns true
            every { messageResolver.getMessage(any<MergedNotificationEvent>(), any()) } returns "메시지"
            every { messageResolver.getPlainMessage(any<MergedNotificationEvent>(), any()) } returns "평문 메시지"
            every { issueRepository.findById(1L) } returns Optional.empty()

            // recipientLimit=3, hideAddress=true -> partialRecipientSize = 3-1 = 2 -> 5명을 3그룹(2,2,1)으로 분할
            scheduler(recipientLimit = 3, hideAddress = true).sendMail()

            verify(exactly = 3) { mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        it("hideAddress=false면 To에 실제 수신자를 담고 Bcc는 비운다") {
            val user = receiver(2L)
            val event = issueEvent(setOf(user))
            val mail = NotificationMail(id = 100L, notificationEvent = event)
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(mail)
            every { notificationMailRepository.delete(mail) } returns Unit
            every { notificationEventMerger.mergeEvents(listOf(event)) } returns listOf(MergedNotificationEvent(event))
            every { issueRepository.existsById(1L) } returns true
            every { messageResolver.getMessage(any<MergedNotificationEvent>(), any()) } returns "메시지"
            every { messageResolver.getPlainMessage(any<MergedNotificationEvent>(), any()) } returns "평문 메시지"
            every { issueRepository.findById(1L) } returns Optional.empty()

            val toSlot = slot<List<MailRecipient>>()
            val bccSlot = slot<List<MailRecipient>>()
            every {
                mailService.sendNotificationMail(capture(toSlot), capture(bccSlot), any(), any(), any(), any(), any(), any(), any(), any())
            } returns Unit

            scheduler(hideAddress = false).sendMail()

            toSlot.captured.map { it.email } shouldBe listOf("u2@yona.io")
            bccSlot.captured shouldBe emptyList()
        }

        it("isCreating() 이벤트 타입이면 결정론적 Message-ID를 지정한다") {
            val user = receiver(2L)
            val event = issueEvent(setOf(user))
            val mail = NotificationMail(id = 100L, notificationEvent = event)
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(mail)
            every { notificationMailRepository.delete(mail) } returns Unit
            every { notificationEventMerger.mergeEvents(listOf(event)) } returns listOf(MergedNotificationEvent(event))
            every { issueRepository.existsById(1L) } returns true
            every { messageResolver.getMessage(any<MergedNotificationEvent>(), any()) } returns "메시지"
            every { messageResolver.getPlainMessage(any<MergedNotificationEvent>(), any()) } returns "평문 메시지"
            every { issueRepository.findById(1L) } returns Optional.empty()

            val messageIdSlot = slot<String>()
            every {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), capture(messageIdSlot), any(), any())
            } returns Unit

            scheduler().sendMail()

            messageIdSlot.captured shouldBe "<issue_post/1@yona.example.com>"
        }

        it("REVIEW_COMMENT 이벤트는 References로 스레드의 첫 리뷰 댓글 Message-ID를 채운다") {
            val project = Project(id = 3L, name = "proj", owner = "owner")
            val thread = com.github.search5.yona.domain.pullrequest.CodeCommentThread(id = 10L, project = project)
            val firstComment = com.github.search5.yona.domain.pullrequest.ReviewComment(id = 50L, thread = thread)
            val newComment = com.github.search5.yona.domain.pullrequest.ReviewComment(id = 55L, thread = thread)

            val user = receiver(2L)
            val event = NotificationEvent(
                id = 1L, title = "제목", created = Instant.now(),
                resourceType = ResourceType.REVIEW_COMMENT, resourceId = "55",
                eventType = EventType.NEW_REVIEW_COMMENT, newValue = "댓글 내용",
                receivers = mutableSetOf(user)
            )
            val mail = NotificationMail(id = 101L, notificationEvent = event)
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(mail)
            every { notificationMailRepository.delete(mail) } returns Unit
            every { notificationEventMerger.mergeEvents(listOf(event)) } returns listOf(MergedNotificationEvent(event))
            every { reviewCommentRepository.existsById(55L) } returns true
            every { reviewCommentRepository.findById(55L) } returns Optional.of(newComment)
            every { reviewCommentRepository.findByThreadIdOrderByCreatedDateAsc(10L) } returns listOf(firstComment, newComment)
            every { messageResolver.getMessage(any<MergedNotificationEvent>(), any()) } returns "메시지"
            every { messageResolver.getPlainMessage(any<MergedNotificationEvent>(), any()) } returns "평문 메시지"

            val referencesSlot = slot<String>()
            every {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), capture(referencesSlot), any())
            } returns Unit

            scheduler().sendMail()

            referencesSlot.captured shouldBe "<review_comment/50@yona.example.com>"
        }

        it("COMMIT_COMMENT 이벤트는 References로 커밋 리소스의 Message-ID를 채운다") {
            val project = Project(id = 3L, name = "proj", owner = "owner")
            val commitComment = com.github.search5.yona.domain.pullrequest.CommitComment(id = 77L, project = project, commitId = "abc123")

            val user = receiver(2L)
            val event = NotificationEvent(
                id = 1L, title = "제목", created = Instant.now(),
                resourceType = ResourceType.COMMIT_COMMENT, resourceId = "77",
                eventType = EventType.NEW_COMMENT, newValue = "댓글 내용",
                receivers = mutableSetOf(user)
            )
            val mail = NotificationMail(id = 102L, notificationEvent = event)
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(mail)
            every { notificationMailRepository.delete(mail) } returns Unit
            every { notificationEventMerger.mergeEvents(listOf(event)) } returns listOf(MergedNotificationEvent(event))
            every { commitCommentRepository.existsById(77L) } returns true
            every { commitCommentRepository.findById(77L) } returns Optional.of(commitComment)
            every { messageResolver.getMessage(any<MergedNotificationEvent>(), any()) } returns "메시지"
            every { messageResolver.getPlainMessage(any<MergedNotificationEvent>(), any()) } returns "평문 메시지"

            val referencesSlot = slot<String>()
            every {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), capture(referencesSlot), any())
            } returns Unit

            scheduler().sendMail()

            referencesSlot.captured shouldBe "<commit/3:abc123@yona.example.com>"
        }

        it("REVIEW_THREAD_STATE_CHANGED 이벤트(컨테이너 없음)는 References를 채우지 않는다") {
            val user = receiver(2L)
            val event = NotificationEvent(
                id = 1L, title = "제목", created = Instant.now(),
                resourceType = ResourceType.COMMENT_THREAD, resourceId = "10",
                eventType = EventType.REVIEW_THREAD_STATE_CHANGED, oldValue = "OPEN", newValue = "CLOSED",
                receivers = mutableSetOf(user)
            )
            val mail = NotificationMail(id = 103L, notificationEvent = event)
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(mail)
            every { notificationMailRepository.delete(mail) } returns Unit
            every { notificationEventMerger.mergeEvents(listOf(event)) } returns listOf(MergedNotificationEvent(event))
            every { commentThreadRepository.existsById(10L) } returns true
            every { messageResolver.getMessage(any<MergedNotificationEvent>(), any()) } returns "메시지"
            every { messageResolver.getPlainMessage(any<MergedNotificationEvent>(), any()) } returns "평문 메시지"

            scheduler().sendMail()

            verify(exactly = 1) {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), isNull(), any())
            }
        }
    }
})
