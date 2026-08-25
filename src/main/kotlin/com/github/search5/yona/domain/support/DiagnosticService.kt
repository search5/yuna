package com.github.search5.yona.domain.support

import org.springframework.stereotype.Service
import java.io.File
import javax.sql.DataSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import com.github.search5.yona.domain.mail.ImapMailboxPoller

@Service
class DiagnosticService(
    private val dataSource: DataSource,
    // yona MailboxService.java:176-188 Diagnostic.register(SimpleDiagnostic { checkOne() }) 대응
    // (P1-137). ImapMailboxPoller는 yuna.mailbox.imap.enabled=true일 때만 빈으로 등록되므로 null 허용.
    private val imapMailboxPoller: ImapMailboxPoller? = null,
    private val mailSender: JavaMailSender? = null,
    // yona Config.java:26-39 "application.port가 설정됐는데 application.hostname이 없으면 port가
    // 무시된다" 경고 대응 (P1-137 2번 항목). yona는 hostname/port 두 설정을 조합해 URL을 만들지만, [GL-utils_Config-004;GL-utils_Config-005]
    // yuna는 그 대신 yuna.base-url 단일 설정 하나로 웹훅/알림메일의 모든 절대경로 URL을 만든다
    // (WebhookServiceImpl/NotificationUrlResolver/NotificationMailRenderer/NotificationMailBodyProcessor
    // 4곳에서 재사용) — yona가 막으려던 "설정이 반쯤만 채워져 URL이 깨지는" 문제는 yuna에서는 이 값이
    // 비어있을 때 똑같이 발생하므로, 두 필드 조합이 아니라 이 단일 값의 공백 여부로 대응 이식한다.
    @Value("\${yuna.base-url:}")
    private val baseUrl: String = ""
) {

    fun checkAll(): List<String> {
        val errors = mutableListOf<String>()

        // 1. 데이터베이스 커넥션 점검
        try {
            dataSource.connection.use { conn ->
                if (!conn.isValid(5)) {
                    errors.add("Database Connection is invalid (isValid returned false)")
                }
            }
        } catch (e: Exception) {
            errors.add("Database Connection Check Failed: ${e.message}")
        }

        // 2. 물리 저장소 디렉터리 쓰기 권한 점검
        try {
            val repoPath = System.getProperty("yona.data") ?: "./yona-data"
            val gitDir = File(repoPath, "repo/git")
            if (!gitDir.exists()) {
                gitDir.mkdirs()
            }
            if (!gitDir.canWrite()) {
                errors.add("Git Repository Storage Directory is not writable: ${gitDir.absolutePath}")
            }
        } catch (e: Exception) {
            errors.add("Git Storage Check Failed: ${e.message}")
        }

        try {
            val repoPath = System.getProperty("yona.data") ?: "./yona-data"
            val svnDir = File(repoPath, "repo/svn")
            if (!svnDir.exists()) {
                svnDir.mkdirs()
            }
            if (!svnDir.canWrite()) {
                errors.add("SVN Repository Storage Directory is not writable: ${svnDir.absolutePath}")
            }
        } catch (e: Exception) {
            errors.add("SVN Storage Check Failed: ${e.message}")
        }

        // 3. 메일 연동 빈 설정 점검
        if (mailSender == null) {
            errors.add("JavaMailSender is not configured. Email notifications may not work.")
        }

        // 4. IMAP 메일 수신기 상태 점검 (yona MailboxService.java:176-188 대응, P1-137)
        imapMailboxPoller?.healthCheckMessage()?.let { errors.add(it) }

        // 5. 절대경로 URL 기준값 설정 점검 (yona Config.java:26-39 대응, P1-137) [GL-utils_Config-004;GL-utils_Config-005]
        if (baseUrl.isBlank()) {
            errors.add(
                "yuna.base-url is not configured. Links in webhook payloads and notification emails " +
                    "may be relative or broken."
            )
        }

        return errors
    }
}
