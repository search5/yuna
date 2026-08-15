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
        sendHtmlMail("no-reply@yona.io", toEmail, toName, subject, htmlContent)
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
}
