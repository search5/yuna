package com.github.search5.yona.domain.mail

import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.slf4j.LoggerFactory

@Service
class MailServiceImpl(
    private val mailSender: JavaMailSender
) : MailService {

    private val logger = LoggerFactory.getLogger(MailServiceImpl::class.java)

    override fun sendHtmlMail(toEmail: String, toName: String, subject: String, htmlContent: String) {
        sendHtmlMailWithReplyTo(toEmail, toName, subject, htmlContent, null)
    }

    override fun sendHtmlMailWithReplyTo(toEmail: String, toName: String, subject: String, htmlContent: String, replyTo: String?) {
        try {
            val message = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(message, true, "UTF-8")

            helper.setTo(toEmail)
            helper.setSubject(subject)
            helper.setText(htmlContent, true)
            helper.setFrom("no-reply@yona.io")
            if (!replyTo.isNullOrBlank()) {
                helper.setReplyTo(replyTo)
            }

            mailSender.send(message)
            logger.info("Email sent to $toEmail ($toName) with subject '$subject'" + (replyTo?.let { ", replyTo=$it" } ?: ""))
        } catch (e: Exception) {
            logger.error("Failed to send email to $toEmail", e)
            throw e
        }
    }

    override fun sendHtmlMail(fromEmail: String, toEmail: String, toName: String, subject: String, htmlContent: String) {
        try {
            val message = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(message, true, "UTF-8")

            helper.setTo(toEmail)
            helper.setSubject(subject)
            helper.setText(htmlContent, true)
            helper.setFrom(fromEmail)

            mailSender.send(message)
            logger.info("Email sent from $fromEmail to $toEmail ($toName) with subject '$subject'")
        } catch (e: Exception) {
            logger.error("Failed to send email to $toEmail", e)
            throw e
        }
    }

    override fun sendReply(toEmail: String, toName: String, subject: String, textContent: String, inReplyToMessageId: String) {
        try {
            val message = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(message, false, "UTF-8")

            helper.setTo(toEmail)
            helper.setSubject(if (subject.lowercase().startsWith("re:")) subject else "Re: $subject")
            helper.setText(textContent, false)
            helper.setFrom("no-reply@yona.io")
            if (inReplyToMessageId.isNotBlank()) {
                message.setHeader("In-Reply-To", inReplyToMessageId)
                message.setHeader("References", inReplyToMessageId)
            }

            mailSender.send(message)
            logger.info("Reply email sent to $toEmail ($toName), inReplyTo=$inReplyToMessageId")
        } catch (e: Exception) {
            logger.error("Failed to send reply email to $toEmail", e)
        }
    }
}
