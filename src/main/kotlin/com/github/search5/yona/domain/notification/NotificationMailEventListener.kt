package com.github.search5.yona.domain.notification

import com.github.search5.yona.domain.mail.MailService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * yona의 NotificationMail.startSchedule()(Akka 스케줄러 기반 일괄 발송)에 대응.
 * yuna는 여러 NotificationEvent를 하나의 다이제스트 메일로 병합하는 배치 스케줄링 대신,
 * 이벤트 발생 시 즉시 개별 발송한다 — 핵심 요구사항인 "알림 메일이 실제로 발송되는지"를
 * 우선 충족하기 위한 의도적 범위 축소다. 언어별 그룹핑, 이벤트 병합, 다이제스트 주기는
 * docs/PARITY_BACKLOG.md의 별도 후속 항목으로 분리한다.
 */
@Component
class NotificationMailEventListener(
    private val mailService: MailService,
    private val notificationMailRepository: NotificationMailRepository
) {
    private val logger = LoggerFactory.getLogger(NotificationMailEventListener::class.java)

    @Async("taskExecutor")
    @EventListener
    @Transactional
    fun handleNotificationEvent(event: NotificationEvent) {
        if (event.receivers.isEmpty()) {
            return
        }

        val htmlContent = renderHtml(event.newValue?.takeIf { it.isNotBlank() } ?: event.title)

        for (receiver in event.receivers) {
            if (receiver.email.isBlank()) {
                continue
            }
            try {
                mailService.sendHtmlMail(
                    toEmail = receiver.email,
                    toName = receiver.name,
                    subject = event.title,
                    htmlContent = htmlContent
                )
            } catch (e: Exception) {
                logger.error("알림 메일 발송 실패: to=${receiver.email}, eventId=${event.id}", e)
            }
        }

        notificationMailRepository.save(NotificationMail(notificationEvent = event))
    }

    private fun renderHtml(content: String): String {
        val escaped = content
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br/>")
        return "<div>$escaped</div>"
    }
}
