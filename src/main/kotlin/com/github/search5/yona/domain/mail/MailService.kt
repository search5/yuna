package com.github.search5.yona.domain.mail

interface MailService {
    fun sendHtmlMail(toEmail: String, toName: String, subject: String, htmlContent: String)
    fun sendHtmlMail(fromEmail: String, toEmail: String, toName: String, subject: String, htmlContent: String)
}
