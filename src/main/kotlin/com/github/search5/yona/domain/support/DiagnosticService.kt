package com.github.search5.yona.domain.support

import org.springframework.stereotype.Service
import java.io.File
import javax.sql.DataSource
import org.springframework.mail.javamail.JavaMailSender
import com.github.search5.yona.domain.mail.ImapMailboxPoller

@Service
class DiagnosticService(
    private val dataSource: DataSource,
    // yona MailboxService.java:176-188 Diagnostic.register(SimpleDiagnostic { checkOne() }) 대응
    // (P1-137). ImapMailboxPoller는 yuna.mailbox.imap.enabled=true일 때만 빈으로 등록되므로 null 허용.
    private val imapMailboxPoller: ImapMailboxPoller? = null,
    private val mailSender: JavaMailSender? = null
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

        return errors
    }
}
