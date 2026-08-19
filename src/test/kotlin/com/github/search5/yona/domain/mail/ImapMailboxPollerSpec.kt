package com.github.search5.yona.domain.mail

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import jakarta.mail.Session
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import java.util.Properties

// yona mailbox/CreationViaEmail.java의 MIME 파트 트리 순회(processPart 등) 대응 (P1-29).
// 실제 jakarta.mail 객체를 구성해 IMAP 서버 연결 없이도 파싱 로직을 검증한다.
class ImapMailboxPollerSpec : DescribeSpec({
    val poller = ImapMailboxPoller(
        incomingMailProcessingService = mockk(relaxed = true),
        host = "localhost",
        user = "yona",
        password = "secret",
        useSsl = true,
        folderName = "inbox"
    )
    val session = Session.getDefaultInstance(Properties())

    describe("ImapMailboxPoller.toInboundEmailMessage") {
        it("text/plain 단일 파트 메일은 본문을 그대로 추출하고 첨부파일은 없어야 한다") {
            val message = MimeMessage(session)
            message.setFrom("gildong@example.com")
            message.setRecipients(jakarta.mail.Message.RecipientType.TO, "yona+dlab/hive@example.com")
            message.subject = "제목"
            message.setText("안녕하세요, 본문입니다.", "UTF-8")
            message.saveChanges()

            val result = poller.toInboundEmailMessage(message)

            result.textBody shouldBe "안녕하세요, 본문입니다."
            result.attachments.size shouldBe 0
        }

        it("multipart/mixed 메일에서 첨부파일을 추출해야 한다") {
            val message = MimeMessage(session)
            message.setFrom("gildong@example.com")
            message.setRecipients(jakarta.mail.Message.RecipientType.TO, "yona+dlab/hive@example.com")
            message.subject = "첨부파일 테스트"

            val textPart = MimeBodyPart()
            textPart.setText("본문입니다.", "UTF-8")

            val attachmentPart = MimeBodyPart()
            attachmentPart.setFileName("hello.txt")
            attachmentPart.setContent("attachment content", "text/plain")
            attachmentPart.setDisposition(jakarta.mail.Part.ATTACHMENT)

            val multipart = MimeMultipart("mixed")
            multipart.addBodyPart(textPart)
            multipart.addBodyPart(attachmentPart)
            message.setContent(multipart)
            message.saveChanges()

            val result = poller.toInboundEmailMessage(message)

            result.textBody shouldBe "본문입니다."
            result.attachments.size shouldBe 1
            result.attachments[0].fileName shouldBe "hello.txt"
            String(result.attachments[0].bytes, Charsets.UTF_8) shouldBe "attachment content"
        }

        it("첨부파일이 여러 개면 전부 추출해야 한다") {
            val message = MimeMessage(session)
            message.setFrom("gildong@example.com")
            message.setRecipients(jakarta.mail.Message.RecipientType.TO, "yona+dlab/hive@example.com")
            message.subject = "여러 첨부파일"

            val textPart = MimeBodyPart()
            textPart.setText("본문", "UTF-8")

            val attachment1 = MimeBodyPart()
            attachment1.setFileName("a.txt")
            attachment1.setContent("A", "text/plain")
            attachment1.setDisposition(jakarta.mail.Part.ATTACHMENT)

            val attachment2 = MimeBodyPart()
            attachment2.setFileName("b.txt")
            attachment2.setContent("B", "text/plain")
            attachment2.setDisposition(jakarta.mail.Part.ATTACHMENT)

            val multipart = MimeMultipart("mixed")
            multipart.addBodyPart(textPart)
            multipart.addBodyPart(attachment1)
            multipart.addBodyPart(attachment2)
            message.setContent(multipart)
            message.saveChanges()

            val result = poller.toInboundEmailMessage(message)

            result.attachments.map { it.fileName }.toSet() shouldBe setOf("a.txt", "b.txt")
        }
    }
})
