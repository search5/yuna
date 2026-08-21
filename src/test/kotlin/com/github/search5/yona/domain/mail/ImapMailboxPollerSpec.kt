package com.github.search5.yona.domain.mail

import com.github.search5.yona.domain.support.PropertyService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import jakarta.activation.DataHandler
import jakarta.mail.Message
import jakarta.mail.Part
import jakarta.mail.Session
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.util.ByteArrayDataSource
import org.springframework.scheduling.TaskScheduler
import java.util.Properties

// yona mailbox/CreationViaEmail.java의 MIME 파트 트리 순회(processPart 등) 대응 (P1-29).
// 실제 jakarta.mail 객체를 구성해 IMAP 서버 연결 없이도 파싱 로직을 검증한다.
class ImapMailboxPollerSpec : DescribeSpec({
    val propertyService = mockk<PropertyService>(relaxed = true)
    val taskScheduler = mockk<TaskScheduler>(relaxed = true)
    val poller = ImapMailboxPoller(
        incomingMailProcessingService = mockk(relaxed = true),
        propertyService = propertyService,
        taskScheduler = taskScheduler,
        host = "localhost",
        user = "yona",
        password = "secret",
        useSsl = true,
        folderName = "inbox",
        pollingIntervalMs = 300000L
    )
    val session = Session.getDefaultInstance(Properties())

    // yona MailboxService.java:176-188 Diagnostic checkOne() 대응 (P1-137).
    describe("ImapMailboxPoller.healthCheckMessage") {
        it("start()가 호출되지 않아 idleThread가 초기화되지 않았으면 미초기화 메시지를 반환해야 한다") {
            poller.healthCheckMessage() shouldBe "The Email Receiver is not initialized"
        }
    }

    describe("ImapMailboxPoller.toInboundEmailMessage") {
        it("text/plain 단일 파트 메일은 본문을 그대로 추출하고 첨부파일은 없어야 한다") {
            val message = MimeMessage(session)
            message.setFrom("gildong@example.com")
            message.setRecipients(Message.RecipientType.TO, "yona+dlab/hive@example.com")
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
            message.setRecipients(Message.RecipientType.TO, "yona+dlab/hive@example.com")
            message.subject = "첨부파일 테스트"

            val textPart = MimeBodyPart()
            textPart.setText("본문입니다.", "UTF-8")

            val attachmentPart = MimeBodyPart()
            attachmentPart.setFileName("hello.txt")
            attachmentPart.setContent("attachment content", "text/plain")
            attachmentPart.setDisposition(Part.ATTACHMENT)

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
            message.setRecipients(Message.RecipientType.TO, "yona+dlab/hive@example.com")
            message.subject = "여러 첨부파일"

            val textPart = MimeBodyPart()
            textPart.setText("본문", "UTF-8")

            val attachment1 = MimeBodyPart()
            attachment1.setFileName("a.txt")
            attachment1.setContent("A", "text/plain")
            attachment1.setDisposition(Part.ATTACHMENT)

            val attachment2 = MimeBodyPart()
            attachment2.setFileName("b.txt")
            attachment2.setContent("B", "text/plain")
            attachment2.setDisposition(Part.ATTACHMENT)

            val multipart = MimeMultipart("mixed")
            multipart.addBodyPart(textPart)
            multipart.addBodyPart(attachment1)
            multipart.addBodyPart(attachment2)
            message.setContent(multipart)
            message.saveChanges()

            val result = poller.toInboundEmailMessage(message)

            result.attachments.map { it.fileName }.toSet() shouldBe setOf("a.txt", "b.txt")
        }

        it("text/html뿐인 메일은 태그를 벗기지 않고 원본 HTML을 그대로 보존하고 isHtml=true여야 한다(P1-47)") {
            val message = MimeMessage(session)
            message.setFrom("gildong@example.com")
            message.setRecipients(Message.RecipientType.TO, "yona+dlab/hive@example.com")
            message.subject = "HTML 메일"
            message.setContent("<p>안녕하세요 <b>굵게</b></p>", "text/html; charset=UTF-8")
            message.saveChanges()

            val result = poller.toInboundEmailMessage(message)

            result.isHtml shouldBe true
            result.textBody shouldBe "<p>안녕하세요 <b>굵게</b></p>"
        }

        it("인라인 이미지 첨부의 Content-ID를 추출해야 한다(P1-47)") {
            val message = MimeMessage(session)
            message.setFrom("gildong@example.com")
            message.setRecipients(Message.RecipientType.TO, "yona+dlab/hive@example.com")
            message.subject = "인라인 이미지"

            val htmlPart = MimeBodyPart()
            htmlPart.setContent("<p>사진: <img src=\"cid:image1\"></p>", "text/html; charset=UTF-8")

            val imagePart = MimeBodyPart()
            imagePart.fileName = "photo.png"
            imagePart.dataHandler = DataHandler(
                ByteArrayDataSource("fake-image-bytes".toByteArray(), "image/png")
            )
            imagePart.setContentID("<image1>")
            imagePart.setDisposition(Part.INLINE)

            val multipart = MimeMultipart("related")
            multipart.addBodyPart(htmlPart)
            multipart.addBodyPart(imagePart)
            message.setContent(multipart)
            message.saveChanges()

            val result = poller.toInboundEmailMessage(message)

            result.isHtml shouldBe true
            result.textBody shouldBe "<p>사진: <img src=\"cid:image1\"></p>"
            result.attachments.size shouldBe 1
            result.attachments[0].contentId shouldBe "image1"
        }
    }

    // yona EmailHandler.handleNewMessages()의 "lastUIDValidity == uidValidity && lastSeenUID != null"
    // 조건 대응 (P1-55). 이 조건이 성립할 때만 UID 구간 조회로 새 메일을 찾는다.
    describe("ImapMailboxPoller.shouldFetchByUidRange") {
        it("이전 기록이 전혀 없으면(최초 실행) false여야 한다") {
            poller.shouldFetchByUidRange(lastUidValidity = null, lastSeenUid = null, currentUidValidity = 100L) shouldBe false
        }

        it("uidValidity가 이전과 다르면(메일함이 재생성됨) false여야 한다") {
            poller.shouldFetchByUidRange(lastUidValidity = 99L, lastSeenUid = 5L, currentUidValidity = 100L) shouldBe false
        }

        it("lastSeenUid가 없으면 uidValidity가 같아도 false여야 한다") {
            poller.shouldFetchByUidRange(lastUidValidity = 100L, lastSeenUid = null, currentUidValidity = 100L) shouldBe false
        }

        it("uidValidity가 같고 lastSeenUid가 있으면 true여야 한다") {
            poller.shouldFetchByUidRange(lastUidValidity = 100L, lastSeenUid = 5L, currentUidValidity = 100L) shouldBe true
        }
    }

    // yona MailboxService.updateLastSeenUID()의 "uid <= lastSeenUID면 갱신하지 않는다" 대응 (P1-55).
    describe("ImapMailboxPoller.advancedSeenUid") {
        it("기존 워터마크가 없으면 새 uid를 그대로 반환해야 한다") {
            poller.advancedSeenUid(currentSeenUid = null, candidateUid = 7L) shouldBe 7L
        }

        it("새 uid가 기존보다 크면 새 uid를 반환해야 한다") {
            poller.advancedSeenUid(currentSeenUid = 5L, candidateUid = 7L) shouldBe 7L
        }

        it("새 uid가 기존보다 작거나 같으면 null(갱신 불필요)을 반환해야 한다") {
            poller.advancedSeenUid(currentSeenUid = 7L, candidateUid = 7L) shouldBe null
            poller.advancedSeenUid(currentSeenUid = 7L, candidateUid = 3L) shouldBe null
        }
    }
})
