package com.github.search5.yona.domain.user

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class UserVerificationSpec : DescribeSpec({
    describe("UserVerification.isValidDate") {
        it("생성된 지 24시간 이내면 true를 반환해야 한다") {
            val user = User(id = 1L, loginId = "test", name = "Test", email = "test@example.com")
            val verification = UserVerification(
                user = user,
                loginId = "test",
                verificationCode = "code",
                timestamp = System.currentTimeMillis() - 1000 // 1초 전
            )
            verification.isValidDate() shouldBe true
        }

        it("생성된 지 24시간이 넘었으면 false를 반환해야 한다") {
            val user = User(id = 1L, loginId = "test", name = "Test", email = "test@example.com")
            val verification = UserVerification(
                user = user,
                loginId = "test",
                verificationCode = "code",
                timestamp = System.currentTimeMillis() - (24 * 60 * 60 * 1000) - 1000 // 24시간 1초 전
            )
            verification.isValidDate() shouldBe false
        }
    }
})
