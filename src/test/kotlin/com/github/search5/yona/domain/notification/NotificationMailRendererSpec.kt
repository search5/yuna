package com.github.search5.yona.domain.notification

import com.github.search5.yona.domain.enumeration.ResourceType
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import org.springframework.context.MessageSource
import java.util.Locale

// yona views/common/notificationMail.scala.html 대응 (P1-27).
class NotificationMailRendererSpec : DescribeSpec({
    val messageSource = mockk<MessageSource>()
    val bodyProcessor = NotificationMailBodyProcessor("yona.example.com", "https://yona.example.com", false)
    val renderer = NotificationMailRenderer(messageSource, bodyProcessor, "https://yona.example.com", "Yona")
    val locale = Locale.KOREAN

    beforeTest {
        every { messageSource.getMessage(any<String>(), any(), any<Locale>()) } answers {
            val args = secondArg<Array<Any>?>().orEmpty()
            firstArg<String>() + args.joinToString("") { it.toString() }
        }
        every { messageSource.getMessage(any<String>(), any(), any<String>(), any()) } answers { firstArg() }
    }

    describe("render") {
        it("본문 메시지를 그대로 포함해야 한다") {
            val html = renderer.render("본문 내용", null, ResourceType.ISSUE_POST, "1", false, locale)

            html shouldContain "본문 내용"
        }

        it("urlToView가 있으면 회신 가능 여부에 따라 다른 안내 메시지 키를 쓴다") {
            every { messageSource.getMessage("notification.linkToViewHtml", any(), locale) } returns "일반 링크"
            every { messageSource.getMessage("notification.replyOrLinkToViewHtml", any(), locale) } returns "회신 가능 링크"

            val htmlNoReply = renderer.render("내용", "https://yona.example.com/issue/1", ResourceType.ISSUE_POST, "1", false, locale)
            val htmlWithReply = renderer.render("내용", "https://yona.example.com/issue/1", ResourceType.ISSUE_POST, "1", true, locale)

            htmlNoReply shouldContain "일반 링크"
            htmlWithReply shouldContain "회신 가능 링크"
        }

        it("urlToView가 없으면 링크 안내를 넣지 않는다") {
            val html = renderer.render("내용", null, ResourceType.ISSUE_POST, "1", false, locale)

            html shouldNotContain "notification.linkToViewHtml"
        }

        it("리소스 unwatch 링크에 resourceType/resourceId를 담아야 한다") {
            val html = renderer.render("내용", null, ResourceType.BOARD_POST, "42", false, locale)

            html shouldContain "resource.type=BOARD_POST"
            html shouldContain "resource.id=42"
        }
    }

    describe("renderPlain") {
        it("고정 안내 문구를 덧붙인다(legacy와 동일하게 urlToView/lang과 무관)") {
            val plain = renderer.renderPlain("본문")

            plain shouldContain "본문"
            plain shouldContain "Yona"
        }
    }
})
