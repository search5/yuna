package com.github.search5.yona.domain.mail

interface MailService {
    fun sendHtmlMail(toEmail: String, toName: String, subject: String, htmlContent: String)
    fun sendHtmlMail(fromEmail: String, toEmail: String, toName: String, subject: String, htmlContent: String)

    // yona NotificationMail.getReplyTo() 대응 (P1-28). IMAP 답장 스레딩용 plus-address.
    // 기존 두 오버로드와 이름이 같으면 JVM 시그니처가 충돌해(둘 다 String 5개) 별도 메서드명을 쓴다.
    fun sendHtmlMailWithReplyTo(toEmail: String, toName: String, subject: String, htmlContent: String, replyTo: String?)
}
