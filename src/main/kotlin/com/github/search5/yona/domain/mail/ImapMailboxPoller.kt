package com.github.search5.yona.domain.mail

import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import jakarta.mail.search.FlagTerm
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.Properties

/**
 * yona의 mailbox/MailboxService.java + IMAPMessageUtil.java 대응.
 * yona는 IDLE 명령 우선 + 폴링 폴백, 커스텀 lastSeenUID 추적 방식이지만,
 * yuna는 IMAP `\Seen` 플래그를 북마크로 삼는 폴링 전용 방식으로 단순화했다
 * (별도 상태 테이블 없이 멱등적으로 동작, 단 IDLE 대비 최대 polling-interval만큼 지연 발생).
 *
 * 실제 IMAP 서버 연결이 필요한 순수 글루 코드라 단위테스트 대상에서 제외했다
 * (이 저장소의 다른 *Config류 인프라 배선과 동일한 관례) — 라우팅/생성 비즈니스 로직은
 * IncomingMailProcessingService에 전부 위임되어 있으며 그쪽은 전체 커버됨.
 */
@Component
@ConditionalOnProperty(prefix = "yuna.mailbox.imap", name = ["enabled"], havingValue = "true")
class ImapMailboxPoller(
    private val incomingMailProcessingService: IncomingMailProcessingService,
    @Value("\${yuna.mailbox.imap.host}") private val host: String,
    @Value("\${yuna.mailbox.imap.user}") private val user: String,
    @Value("\${yuna.mailbox.imap.password}") private val password: String,
    @Value("\${yuna.mailbox.imap.ssl:true}") private val useSsl: Boolean,
    @Value("\${yuna.mailbox.imap.folder:inbox}") private val folderName: String
) {
    private val logger = LoggerFactory.getLogger(ImapMailboxPoller::class.java)

    @Scheduled(fixedDelayString = "\${yuna.mailbox.imap.polling-interval-ms:300000}")
    fun poll() {
        try {
            connectAndProcess()
        } catch (e: Exception) {
            logger.error("IMAP 수신함 폴링 실패", e)
        }
    }

    private fun connectAndProcess() {
        val protocol = if (useSsl) "imaps" else "imap"
        val props = Properties()
        props["mail.store.protocol"] = protocol

        val session = Session.getDefaultInstance(props)
        val store = session.getStore(protocol)
        try {
            store.connect(host, user, password)
            val folder = store.getFolder(folderName)
            folder.open(Folder.READ_WRITE)
            try {
                val unseen = folder.search(FlagTerm(Flags(Flags.Flag.SEEN), false))
                logger.info("IMAP 수신함에서 처리할 새 메일 ${unseen.size}건 발견")
                for (message in unseen) {
                    processMessage(message)
                }
            } finally {
                folder.close(true)
            }
        } finally {
            store.close()
        }
    }

    private fun processMessage(message: Message) {
        try {
            val inbound = toInboundEmailMessage(message)
            val outcomes = incomingMailProcessingService.process(inbound)
            logger.info("메일 처리 결과: messageId=${inbound.messageId}, outcomes=$outcomes")
        } catch (e: Exception) {
            logger.error("메일 처리 실패: ${runCatching { message.subject }.getOrNull()}", e)
        } finally {
            try {
                message.setFlag(Flags.Flag.SEEN, true)
            } catch (e: Exception) {
                logger.warn("SEEN 플래그 설정 실패", e)
            }
        }
    }

    private fun toInboundEmailMessage(message: Message): InboundEmailMessage {
        val from = message.from?.filterIsInstance<InternetAddress>()?.firstOrNull()
        val recipients = message.allRecipients
            ?.filterIsInstance<InternetAddress>()
            ?.map { it.address }
            ?: emptyList()
        val mime = message as? MimeMessage

        return InboundEmailMessage(
            messageId = mime?.messageID ?: "",
            subject = message.subject ?: "",
            fromAddress = from?.address ?: "",
            fromName = from?.personal ?: (from?.address ?: ""),
            recipientAddresses = recipients,
            inReplyTo = mime?.getHeader("In-Reply-To")?.firstOrNull(),
            references = mime?.getHeader("References")?.firstOrNull(),
            textBody = extractTextBody(message)
        )
    }

    private fun extractTextBody(part: Part): String {
        return try {
            when {
                part.isMimeType("text/plain") -> part.content as? String ?: ""
                part.isMimeType("multipart/*") -> {
                    val multipart = part.content as Multipart
                    (0 until multipart.count)
                        .map { extractTextBody(multipart.getBodyPart(it)) }
                        .firstOrNull { it.isNotBlank() } ?: ""
                }
                part.isMimeType("text/html") -> Jsoup.parse(part.content as? String ?: "").text()
                else -> ""
            }
        } catch (e: Exception) {
            logger.warn("메일 본문 추출 실패", e)
            ""
        }
    }
}
