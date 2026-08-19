package com.github.search5.yona.domain.notification

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

// yona models/NotificationMail.java의 handleLinks()/handleImages()/removeHeadAnchor() 대응 (P1-27).
class NotificationMailBodyProcessorSpec : DescribeSpec({
    describe("process") {
        it("상대경로 링크를 baseUrl 기준 절대경로로 바꾼다") {
            val processor = NotificationMailBodyProcessor("yona.example.com", "https://yona.example.com", false)

            val result = processor.process("""<a href="/issue/1">link</a>""")

            result shouldContain """href="https://yona.example.com/issue/1""""
        }

        it("noreferrer 설정이 켜져 있고 외부 호스트로 가는 링크면 rel=noreferrer를 붙인다") {
            val processor = NotificationMailBodyProcessor("yona.example.com", "https://yona.example.com", true)

            val result = processor.process("""<a href="https://external.example.com/page">link</a>""")

            result shouldContain "noreferrer"
        }

        it("noreferrer 설정이 켜져 있어도 같은 호스트로 가는 링크에는 붙이지 않는다") {
            val processor = NotificationMailBodyProcessor("yona.example.com", "https://yona.example.com", true)

            val result = processor.process("""<a href="https://yona.example.com/issue/1">link</a>""")

            result shouldNotContain "noreferrer"
        }

        it("이미지에 max-width 스타일을 추가하고 새 탭으로 여는 링크로 감싼다") {
            val processor = NotificationMailBodyProcessor("yona.example.com", "https://yona.example.com", false)

            val result = processor.process("""<img src="https://yona.example.com/img.png">""")

            result shouldContain "max-width:1024px"
            result shouldContain """<a href="https://yona.example.com/img.png" target="_blank""""
        }

        it("head-anchor 앵커 텍스트를 제거한다") {
            val processor = NotificationMailBodyProcessor("yona.example.com", "https://yona.example.com", false)

            val result = processor.process("""<a class="head-anchor">#</a>""")

            result shouldNotContain "head-anchor\">#</a>"
        }
    }
})
