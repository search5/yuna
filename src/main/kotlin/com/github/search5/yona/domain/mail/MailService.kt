package com.github.search5.yona.domain.mail

interface MailService {
    fun sendHtmlMail(toEmail: String, toName: String, subject: String, htmlContent: String)
    fun sendHtmlMail(fromEmail: String, toEmail: String, toName: String, subject: String, htmlContent: String)

    // yona NotificationMail.getReplyTo() 대응 (P1-28). IMAP 답장 스레딩용 plus-address.
    // 기존 두 오버로드와 이름이 같으면 JVM 시그니처가 충돌해(둘 다 String 5개) 별도 메서드명을 쓴다.
    fun sendHtmlMailWithReplyTo(toEmail: String, toName: String, subject: String, htmlContent: String, replyTo: String?)

    // yona EmailHandler.reply() 대응 (P1-31). In-Reply-To/References를 원본 메일의 Message-ID로
    // 설정해 메일 클라이언트가 원본 스레드의 답장으로 인식하게 한다.
    fun sendReply(toEmail: String, toName: String, subject: String, textContent: String, inReplyToMessageId: String)
}
