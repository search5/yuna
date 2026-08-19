package com.github.search5.yona.domain.mail

/**
 * IMAP으로 수신한 이메일을 순수 데이터로 옮겨온 것.
 * jakarta.mail.Message에 직접 의존하지 않게 분리해, 실제 IMAP 연결 없이도
 * 라우팅/생성 로직(IncomingMailService)을 단위테스트할 수 있게 한다.
 */
data class InboundEmailMessage(
    val messageId: String,
    val subject: String,
    val fromAddress: String,
    val fromName: String,
    val recipientAddresses: List<String>,
    val inReplyTo: String?,
    val references: String?,
    val textBody: String,
    val attachments: List<InboundAttachment> = emptyList()
)

// yona CreationViaEmail.saveAttachment()/saveAttachments() 대응 (P1-29 — 첨부파일 부분만 범위에 포함).
data class InboundAttachment(
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray
)
