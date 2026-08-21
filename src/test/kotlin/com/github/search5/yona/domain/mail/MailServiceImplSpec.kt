package com.github.search5.yona.domain.mail

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.springframework.mail.javamail.JavaMailSender
import java.util.Date
import java.util.Properties

// yona models/NotificationMail.java의 EventEmail/sendMail() 대응 (P1-27).
class MailServiceImplSpec : DescribeSpec({
    val mailSender = mockk<JavaMailSender>()
    val service = MailServiceImpl(mailSender)
    val session = Session.getInstance(Properties())

    beforeTest {
        clearMocks(mailSender, answers = false)
        every { mailSender.createMimeMessage() } answers { MimeMessage(session) }
        every { mailSender.send(any<MimeMessage>()) } returns Unit
    }

    describe("sendNotificationMail") {
        it("toList가 비어있으면 아무 것도 보내지 않는다") {
            service.sendNotificationMail(
                toList = emptyList(), bccList = emptyList(), fromName = "발신자",
                subject = "제목", htmlBody = "<p>내용</p>", plainBody = "내용",
                replyTo = null, messageId = null, references = null, sentDate = Date()
            )

            verify(exactly = 0) { mailSender.send(any<MimeMessage>()) }
        }

        it("To/Bcc 수신자를 모두 설정해야 한다") {
            val captured = slot<MimeMessage>()
            every { mailSender.send(capture(captured)) } answers { captured.captured.saveChanges() }

            service.sendNotificationMail(
                toList = listOf(MailRecipient("to@yona.io", "받는사람")),
                bccList = listOf(MailRecipient("bcc@yona.io", "숨은참조")),
                fromName = "발신자", subject = "제목", htmlBody = "<p>내용</p>", plainBody = "내용",
                replyTo = null, messageId = null, references = null, sentDate = Date()
            )

            captured.captured.allRecipients.map { it.toString() }.let { recipients ->
                recipients.any { it.contains("to@yona.io") } shouldBe true
                recipients.any { it.contains("bcc@yona.io") } shouldBe true
            }
        }

        it("Reply-To를 설정해야 한다") {
            val captured = slot<MimeMessage>()
            every { mailSender.send(capture(captured)) } answers { captured.captured.saveChanges() }

            service.sendNotificationMail(
                toList = listOf(MailRecipient("to@yona.io", "받는사람")), bccList = emptyList(),
                fromName = "발신자", subject = "제목", htmlBody = "<p>내용</p>", plainBody = "내용",
                replyTo = "yona+owner/project@example.com", messageId = null, references = null, sentDate = Date()
            )

            captured.captured.replyTo.first().toString() shouldContain "yona+owner/project@example.com"
        }

        it("messageId를 지정하면 Message-ID 헤더가 그 값 그대로 유지돼야 한다(saveChanges가 덮어쓰지 않음)") {
            val captured = slot<MimeMessage>()
            every { mailSender.send(capture(captured)) } answers { captured.captured.saveChanges() }

            service.sendNotificationMail(
                toList = listOf(MailRecipient("to@yona.io", "받는사람")), bccList = emptyList(),
                fromName = "발신자", subject = "제목", htmlBody = "<p>내용</p>", plainBody = "내용",
                replyTo = null, messageId = "<issue_post/5@yona.io>", references = null, sentDate = Date()
            )

            captured.captured.messageID shouldBe "<issue_post/5@yona.io>"
        }

        it("messageId를 지정하지 않으면 JavaMail이 자동 생성한 Message-ID를 그대로 둔다") {
            val captured = slot<MimeMessage>()
            every { mailSender.send(capture(captured)) } answers { captured.captured.saveChanges() }

            service.sendNotificationMail(
                toList = listOf(MailRecipient("to@yona.io", "받는사람")), bccList = emptyList(),
                fromName = "발신자", subject = "제목", htmlBody = "<p>내용</p>", plainBody = "내용",
                replyTo = null, messageId = null, references = null, sentDate = Date()
            )

            captured.captured.messageID shouldNotBe null
        }

        it("references를 지정하면 References 헤더가 붙어야 한다") {
            val captured = slot<MimeMessage>()
            every { mailSender.send(capture(captured)) } answers { captured.captured.saveChanges() }

            service.sendNotificationMail(
                toList = listOf(MailRecipient("to@yona.io", "받는사람")), bccList = emptyList(),
                fromName = "발신자", subject = "제목", htmlBody = "<p>내용</p>", plainBody = "내용",
                replyTo = null, messageId = null, references = "<issue_post/5@yona.io>", sentDate = Date()
            )

            captured.captured.getHeader("References").first() shouldBe "<issue_post/5@yona.io>"
        }

        it("From 표시 이름에 이벤트 발신자 이름을 담아야 한다") {
            val captured = slot<MimeMessage>()
            every { mailSender.send(capture(captured)) } answers { captured.captured.saveChanges() }

            service.sendNotificationMail(
                toList = listOf(MailRecipient("to@yona.io", "받는사람")), bccList = emptyList(),
                fromName = "홍길동", subject = "제목", htmlBody = "<p>내용</p>", plainBody = "내용",
                replyTo = null, messageId = null, references = null, sentDate = Date()
            )

            captured.captured.from.first().toString() shouldContain "=?UTF-8?"
        }

        it("HTML/텍스트 dual body를 담아야 한다(multipart/alternative)") {
            val captured = slot<MimeMessage>()
            every { mailSender.send(capture(captured)) } answers { captured.captured.saveChanges() }

            service.sendNotificationMail(
                toList = listOf(MailRecipient("to@yona.io", "받는사람")), bccList = emptyList(),
                fromName = "발신자", subject = "제목", htmlBody = "<p>html내용</p>", plainBody = "plain내용",
                replyTo = null, messageId = null, references = null, sentDate = Date()
            )

            captured.captured.contentType shouldContain "multipart"
        }

        it("이벤트 발생 시각을 sentDate로 설정해야 한다") {
            val captured = slot<MimeMessage>()
            every { mailSender.send(capture(captured)) } answers { captured.captured.saveChanges() }
            val date = Date(1_700_000_000_000L)

            service.sendNotificationMail(
                toList = listOf(MailRecipient("to@yona.io", "받는사람")), bccList = emptyList(),
                fromName = "발신자", subject = "제목", htmlBody = "<p>내용</p>", plainBody = "내용",
                replyTo = null, messageId = null, references = null, sentDate = date
            )

            captured.captured.sentDate.time shouldBe date.time
        }
    }
})
