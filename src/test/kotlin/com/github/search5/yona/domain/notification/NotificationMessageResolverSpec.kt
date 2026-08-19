package com.github.search5.yona.domain.notification

import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.pullrequest.ReviewCommentRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.vcs.RepositoryService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.context.MessageSource
import java.time.Instant
import java.util.Locale
import java.util.Optional

// yona models/NotificationEvent.java의 getMessage(Lang)/getPlainMessage(Lang) 대응 (P1-27).
class NotificationMessageResolverSpec : DescribeSpec({
    val messageSource = mockk<MessageSource>()
    val userRepository = mockk<UserRepository>()
    val reviewCommentRepository = mockk<ReviewCommentRepository>()
    val repositoryService = mockk<RepositoryService>()
    val resolver = NotificationMessageResolver(messageSource, userRepository, reviewCommentRepository, repositoryService)
    val locale = Locale.KOREAN

    beforeTest {
        // key와 args를 그대로 이어붙여 반환 — 어떤 메시지 키/인자로 해석했는지만 검증한다.
        every { messageSource.getMessage(any<String>(), any(), any<String>(), any()) } answers {
            val key = firstArg<String>()
            val args = secondArg<Array<Any>>()
            "$key(${args.joinToString(",")})"
        }
    }

    fun event(
        eventType: EventType,
        oldValue: String? = null,
        newValue: String? = null,
        resourceType: ResourceType = ResourceType.ISSUE_POST,
        resourceId: String = "1",
        senderId: Long? = null
    ) = NotificationEvent(
        title = "제목", senderId = senderId, created = Instant.now(),
        resourceType = resourceType, resourceId = resourceId,
        eventType = eventType, oldValue = oldValue, newValue = newValue
    )

    describe("getMessage") {
        it("ISSUE_STATE_CHANGED: newValue가 CLOSED면 closed 메시지를 반환한다") {
            resolver.getMessage(event(EventType.ISSUE_STATE_CHANGED, newValue = "CLOSED"), locale) shouldBe "notification.issue.closed()"
        }

        it("ISSUE_STATE_CHANGED: 그 외에는 reopened 메시지를 반환한다") {
            resolver.getMessage(event(EventType.ISSUE_STATE_CHANGED, newValue = "OPEN"), locale) shouldBe "notification.issue.reopened()"
        }

        it("ISSUE_ASSIGNEE_CHANGED: newValue가 없으면 unassigned를 반환한다") {
            resolver.getMessage(event(EventType.ISSUE_ASSIGNEE_CHANGED, newValue = null), locale) shouldBe "notification.issue.unassigned()"
        }

        it("ISSUE_ASSIGNEE_CHANGED: newValue가 있으면 assigned에 담당자 이름을 담아 반환한다") {
            resolver.getMessage(event(EventType.ISSUE_ASSIGNEE_CHANGED, newValue = "홍길동"), locale) shouldBe "notification.issue.assigned(홍길동)"
        }

        it("ISSUE_MILESTONE_CHANGED: newValue가 비어있으면 noMilestone을 인자로 담는다") {
            every { messageSource.getMessage("issue.noMilestone", any(), any<String>(), Locale.getDefault()) } returns "마일스톤 없음"
            resolver.getMessage(event(EventType.ISSUE_MILESTONE_CHANGED, newValue = ""), locale) shouldBe "notification.milestone.changed(마일스톤 없음)"
        }

        it("ISSUE_MILESTONE_CHANGED: yuna는 마일스톤 ID가 아니라 제목 문자열을 그대로 저장하므로 조회 없이 그대로 쓴다") {
            resolver.getMessage(event(EventType.ISSUE_MILESTONE_CHANGED, newValue = "1.0 릴리즈"), locale) shouldBe "notification.milestone.changed(1.0 릴리즈)"
        }

        it("NEW_ISSUE/NEW_POSTING/NEW_PULL_REQUEST/NEW_COMMIT/COMMENT_UPDATED는 newValue를 그대로 반환한다") {
            resolver.getMessage(event(EventType.NEW_ISSUE, newValue = "이슈 본문"), locale) shouldBe "이슈 본문"
            resolver.getMessage(event(EventType.COMMENT_UPDATED, newValue = "수정된 댓글"), locale) shouldBe "수정된 댓글"
        }

        it("NEW_COMMENT: oldValue가 null이어도 'null' 문자열을 붙이지 않는다") {
            resolver.getMessage(event(EventType.NEW_COMMENT, newValue = "댓글 내용", oldValue = null), locale) shouldBe "댓글 내용"
        }

        it("ISSUE_BODY_CHANGED/POSTING_BODY_CHANGED는 DiffUtil로 렌더링한다") {
            val message = resolver.getMessage(event(EventType.ISSUE_BODY_CHANGED, oldValue = "old", newValue = "new"), locale)
            message shouldBe com.github.search5.yona.domain.support.DiffUtil.getDiffText("old", "new")
        }

        it("PULL_REQUEST_STATE_CHANGED: newValue가 OPEN이면 reopened를 반환한다") {
            resolver.getMessage(event(EventType.PULL_REQUEST_STATE_CHANGED, newValue = "OPEN"), locale) shouldBe "notification.pullrequest.reopened()"
        }

        it("PULL_REQUEST_STATE_CHANGED: 그 외에는 소문자로 변환한 상태를 key suffix로 쓴다") {
            resolver.getMessage(event(EventType.PULL_REQUEST_STATE_CHANGED, newValue = "CLOSED"), locale) shouldBe "notification.pullrequest.closed(CLOSED)"
        }

        it("PULL_REQUEST_MERGED: 코드워드 키가 없으면 원본 값을 default로 보여준다") {
            resolver.getMessage(event(EventType.PULL_REQUEST_MERGED, oldValue = "부가 설명", newValue = "임의 제목"), locale) shouldBe
                "notification.type.pullrequest.merged.임의 제목(임의 제목)\n부가 설명"
        }

        it("MEMBER_ENROLL_REQUEST: REQUEST/CANCEL 값에 따라 분기한다") {
            resolver.getMessage(event(EventType.MEMBER_ENROLL_REQUEST, newValue = "REQUEST"), locale) shouldBe "notification.member.enroll.request()"
            resolver.getMessage(event(EventType.MEMBER_ENROLL_REQUEST, newValue = "CANCEL"), locale) shouldBe "notification.member.enroll.cancel()"
        }

        it("MEMBER_ENROLL_ACCEPT: legacy의 default 분기(요청 locale과 무관하게 항상 기본 로케일)를 재현한다") {
            every { messageSource.getMessage("notification.member.enroll.accept", any(), any<String>(), Locale.getDefault()) } returns "수락됨"
            resolver.getMessage(event(EventType.MEMBER_ENROLL_ACCEPT), locale) shouldBe "수락됨"
        }

        it("PULL_REQUEST_REVIEW_STATE_CHANGED: 발신자 loginId를 인자로 담는다") {
            every { userRepository.findById(5L) } returns Optional.of(User(id = 5L, loginId = "reviewer1", name = "리뷰어"))
            resolver.getMessage(event(EventType.PULL_REQUEST_REVIEW_STATE_CHANGED, newValue = "DONE", senderId = 5L), locale) shouldBe
                "notification.pullrequest.reviewed(reviewer1)"
        }

        it("REVIEW_THREAD_STATE_CHANGED: CLOSED 여부로 분기한다") {
            resolver.getMessage(event(EventType.REVIEW_THREAD_STATE_CHANGED, newValue = "CLOSED"), locale) shouldBe "notification.reviewthread.closed()"
            resolver.getMessage(event(EventType.REVIEW_THREAD_STATE_CHANGED, newValue = "OPEN"), locale) shouldBe "notification.reviewthread.reopened()"
        }

        it("ISSUE_SHARER_CHANGED: newValue(loginId)가 있으면 해당 사용자 이름으로 added 메시지를 만든다") {
            every { userRepository.findByLoginId("sharer1") } returns Optional.of(User(loginId = "sharer1", name = "공유대상"))
            resolver.getMessage(event(EventType.ISSUE_SHARER_CHANGED, newValue = "sharer1"), locale) shouldBe "notification.issue.sharer.added(공유대상)"
        }

        it("ISSUE_SHARER_CHANGED: newValue가 비어있고 oldValue만 있으면 deleted 메시지를 반환한다") {
            resolver.getMessage(event(EventType.ISSUE_SHARER_CHANGED, oldValue = "sharer1", newValue = ""), locale) shouldBe "notification.issue.sharer.deleted()"
        }

        it("ISSUE_LABEL_CHANGED: yuna는 loginId가 아니라 라벨 이름 목록을 저장하므로 그대로 인자로 쓴다") {
            resolver.getMessage(event(EventType.ISSUE_LABEL_CHANGED, newValue = "bug, urgent"), locale) shouldBe "notification.issue.label.added(bug, urgent)"
        }

        it("RESOURCE_DELETED: yuna는 삭제된 리소스의 표시 제목을 저장하므로 loginId로 못 찾으면 원본 값을 그대로 보여준다") {
            every { userRepository.findByLoginId("삭제된 글 제목") } returns Optional.empty()
            resolver.getMessage(event(EventType.RESOURCE_DELETED, newValue = "삭제된 글 제목"), locale) shouldBe "notification.resource.deleted(삭제된 글 제목)"
        }
    }

    describe("getPlainMessage") {
        it("ISSUE_BODY_CHANGED는 DiffUtil의 plain text 버전을 사용한다") {
            val plain = resolver.getPlainMessage(event(EventType.ISSUE_BODY_CHANGED, oldValue = "old", newValue = "new"), locale)
            plain shouldBe com.github.search5.yona.domain.support.DiffUtil.getDiffPlainText("old", "new")
        }

        it("그 외에는 getMessage 결과에서 '\\n\\n<br />\\n' 패턴만 제거한다") {
            resolver.getPlainMessage(event(EventType.NEW_ISSUE, newValue = "본문\n\n<br />\n이어짐"), locale) shouldBe "본문\n\n이어짐"
        }
    }

    describe("MergedNotificationEvent 대응") {
        it("여러 messageSources를 '\\n\\n---\\n\\n'로 join한다") {
            val e1 = event(EventType.NEW_ISSUE, newValue = "첫 번째")
            val e2 = event(EventType.NEW_ISSUE, newValue = "두 번째")
            val merged = MergedNotificationEvent(e1, listOf(e1, e2))

            resolver.getMessage(merged, locale) shouldBe "첫 번째\n\n---\n\n두 번째"
        }
    }
})
