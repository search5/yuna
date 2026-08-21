package com.github.search5.yona.domain.support

import com.github.search5.yona.domain.mail.ImapMailboxPoller
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.sql.Connection
import javax.sql.DataSource

// yona utils/Config.java:26-39 "application.port가 설정됐는데 application.hostname이 없으면
// application.port가 무시될 수 있다" 경고 대응 (P1-137 2번 항목). yuna는 hostname/port 조합 대신
// yuna.base-url 단일 설정으로 웹훅/알림메일의 모든 절대경로 URL을 만들므로, 그 값이 비어있는지로
// 이식했다(개념적으로 같은 "URL이 깨지는 설정 실수"를 검출).
class DiagnosticServiceSpec : DescribeSpec({
    fun validDataSource(): DataSource {
        val dataSource = mockk<DataSource>()
        val connection = mockk<Connection>(relaxed = true)
        every { dataSource.connection } returns connection
        every { connection.isValid(5) } returns true
        return dataSource
    }

    describe("checkAll - yuna.base-url 설정 점검") {
        it("yuna.base-url이 비어있으면 경고 메시지를 포함해야 한다") {
            val service = DiagnosticService(dataSource = validDataSource(), baseUrl = "")

            val errors = service.checkAll()

            errors shouldContain
                "yuna.base-url is not configured. Links in webhook payloads and notification emails " +
                "may be relative or broken."
        }

        it("yuna.base-url이 설정돼 있으면 해당 경고 메시지를 포함하지 않아야 한다") {
            val service = DiagnosticService(dataSource = validDataSource(), baseUrl = "https://yona.example.com")

            val errors = service.checkAll()

            errors.filter { it.contains("yuna.base-url") } shouldBe emptyList()
        }
    }

    describe("checkAll - IMAP 메일 수신기 헬스체크 (P1-137)") {
        it("ImapMailboxPoller가 비정상이면 해당 메시지를 그대로 포함해야 한다") {
            val poller = mockk<ImapMailboxPoller>()
            every { poller.healthCheckMessage() } returns "The Email Receiver is not running"

            val service = DiagnosticService(
                dataSource = validDataSource(), imapMailboxPoller = poller, baseUrl = "https://yona.example.com"
            )

            service.checkAll() shouldContain "The Email Receiver is not running"
        }

        it("ImapMailboxPoller 빈 자체가 없으면(비활성화) 관련 에러를 추가하지 않아야 한다") {
            val service = DiagnosticService(
                dataSource = validDataSource(), imapMailboxPoller = null, baseUrl = "https://yona.example.com"
            )

            service.checkAll() shouldNotContain "The Email Receiver is not initialized"
        }
    }
})
