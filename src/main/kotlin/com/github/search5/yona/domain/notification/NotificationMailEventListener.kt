package com.github.search5.yona.domain.notification

import com.github.search5.yona.domain.board.PostingCommentRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.issue.IssueCommentRepository
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.mail.EmailAddressDetail
import com.github.search5.yona.domain.mail.MailService
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.support.DiffUtil
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * yona의 NotificationMail.startSchedule()(Akka 스케줄러 기반 일괄 발송)에 대응.
 * yuna는 여러 NotificationEvent를 하나의 다이제스트 메일로 병합하는 배치 스케줄링 대신,
 * 이벤트 발생 시 즉시 개별 발송한다 — 핵심 요구사항인 "알림 메일이 실제로 발송되는지"를
 * 우선 충족하기 위한 의도적 범위 축소다. 이벤트 병합/다이제스트 주기는 P1-27로 별도 추적한다.
 * Reply-To 헤더(IMAP 답장 스레딩)는 P1-28에서 구현됨.
 */
@Component
class NotificationMailEventListener(
    private val mailService: MailService,
    private val notificationMailRepository: NotificationMailRepository,
    private val issueRepository: IssueRepository,
    private val postingRepository: PostingRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val postingCommentRepository: PostingCommentRepository,
    @Value("\${yuna.mailbox.imap.address:}") private val imapAddress: String
) {
    private val logger = LoggerFactory.getLogger(NotificationMailEventListener::class.java)

    @Async("taskExecutor")
    @EventListener
    @Transactional
    fun handleNotificationEvent(event: NotificationEvent) {
        if (event.receivers.isEmpty()) {
            return
        }

        val htmlContent = buildHtmlContent(event)
        val replyTo = buildReplyTo(event.resourceType, event.resourceId)

        for (receiver in event.receivers) {
            if (receiver.email.isBlank()) {
                continue
            }
            try {
                mailService.sendHtmlMailWithReplyTo(
                    toEmail = receiver.email,
                    toName = receiver.name,
                    subject = event.title,
                    htmlContent = htmlContent,
                    replyTo = replyTo
                )
            } catch (e: Exception) {
                logger.error("알림 메일 발송 실패: to=${receiver.email}, eventId=${event.id}", e)
            }
        }

        notificationMailRepository.save(NotificationMail(notificationEvent = event))
    }

    // yona NotificationEvent.getMessage()의 ISSUE_BODY_CHANGED/POSTING_BODY_CHANGED 분기(DiffUtil.getDiffText)
    // 대응 (P2-02). 이 두 이벤트 타입은 DiffUtil이 이미 HTML 이스케이프+하이라이트 span을 직접 생성하므로
    // renderHtml()로 다시 이스케이프하면 안 된다(span 태그 자체가 깨짐).
    private fun buildHtmlContent(event: NotificationEvent): String {
        return when (event.eventType) {
            EventType.ISSUE_BODY_CHANGED, EventType.POSTING_BODY_CHANGED ->
                "<div>${DiffUtil.getDiffText(event.oldValue, event.newValue)}</div>"
            else ->
                renderHtml(event.newValue?.takeIf { it.isNotBlank() } ?: event.title)
        }
    }

    private fun renderHtml(content: String): String {
        val escaped = content
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br/>")
        return "<div>$escaped</div>"
    }

    // yona NotificationMail.getReplyTo() 대응 (P1-28). 커밋(P1-25와 동일한 이유로 재조회 불가)을
    // 제외한 나머지 리소스 타입은 project owner/name을 detail로 담은 plus-address를 반환한다.
    // yona처럼 상세 경로(issue/5 등)까지는 담지 않는다 — IMAP 수신 처리(P0-02)가 현재
    // owner/project까지만 detail을 해석하기 때문(P1-32 참고). project 단위 착지만으로도
    // 완전히 유실되던 답장 스레딩보다는 나은 상태다.
    private fun buildReplyTo(resourceType: ResourceType, resourceId: String): String? {
        if (imapAddress.isBlank()) return null
        val project = resolveProject(resourceType, resourceId) ?: return null
        val owner = project.owner ?: return null

        val base = runCatching { EmailAddressDetail.of(imapAddress) }.getOrNull() ?: return null
        return EmailAddressDetail(base.user, "$owner/${project.name}", base.domain).toString()
    }

    private fun resolveProject(resourceType: ResourceType, resourceId: String): Project? {
        val id = resourceId.toLongOrNull() ?: return null
        return when (resourceType) {
            ResourceType.ISSUE_POST -> issueRepository.findById(id).orElse(null)?.project
            ResourceType.BOARD_POST -> postingRepository.findById(id).orElse(null)?.project
            ResourceType.ISSUE_COMMENT -> issueCommentRepository.findById(id).orElse(null)?.issue?.project
            ResourceType.NONISSUE_COMMENT -> postingCommentRepository.findById(id).orElse(null)?.posting?.project
            else -> null
        }
    }
}
