package com.github.search5.yona.domain.support

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import java.time.Instant

// yona AbstractPostingApp.addToHistory()/getHistoryMadeBy() 대응 (P2-02).
class HistoryUtilSpec : DescribeSpec({
    describe("HistoryUtil.appendHistory") {
        it("작성자 이름/아이디와 삽입·삭제 건수를 담은 헤더를 새 이력 앞에 붙여야 한다") {
            val result = HistoryUtil.appendHistory(
                originalBody = "Hi, there?",
                newBody = "Hello, mijeong?",
                updaterName = "홍길동",
                updaterLoginId = "gildong",
                updatedDate = Instant.parse("2026-01-01T00:00:00Z"),
                existingHistory = null
            )

            result shouldStartWith "<div class='history-made-by'>홍길동(gildong) "
            result shouldContain "<span class='added'>"
            result shouldContain "<span class='deleted'>"
            result shouldContain "diff-deleted"
            result shouldContain "diff-added"
        }

        it("기존 history가 있으면 새 이력 뒤에 그대로 이어붙여야 한다") {
            val result = HistoryUtil.appendHistory(
                originalBody = "old",
                newBody = "new",
                updaterName = "홍길동",
                updaterLoginId = "gildong",
                updatedDate = Instant.now(),
                existingHistory = "PREVIOUS_HISTORY_MARKER"
            )

            result shouldEndWith "PREVIOUS_HISTORY_MARKER"
        }

        it("삽입만 있고 삭제가 없으면 삭제 배지는 포함하지 않아야 한다") {
            val result = HistoryUtil.appendHistory(
                originalBody = "",
                newBody = "brand new content",
                updaterName = "홍길동",
                updaterLoginId = "gildong",
                updatedDate = Instant.now(),
                existingHistory = null
            )

            result shouldContain "<span class='added'>"
            (result.contains("<span class='deleted'>")) shouldBe false
        }

        it("originalBody가 null이면 diff 본문 없이 헤더만 기록해야 한다(yona 원본 동작)") {
            val result = HistoryUtil.appendHistory(
                originalBody = null,
                newBody = "new content",
                updaterName = "홍길동",
                updaterLoginId = "gildong",
                updatedDate = Instant.now(),
                existingHistory = null
            )

            (result.contains("diff-added")) shouldBe false
            (result.contains("diff-deleted")) shouldBe false
        }
    }
})
