package com.github.search5.yona.config.oauth2

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class OAuth2UserInfoFactorySpec : DescribeSpec({
    describe("OAuth2UserInfoFactory") {
        it("google registrationId를 전달하면 GoogleOAuth2UserInfo를 반환해야 한다") {
            val attributes = mapOf<String, Any>("sub" to "google-sub")
            val userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo("google", attributes)
            userInfo.id shouldBe "google-sub"
        }

        it("github registrationId를 전달하면 GithubOAuth2UserInfo를 반환해야 한다") {
            val attributes = mapOf<String, Any>("id" to 12345)
            val userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo("github", attributes)
            userInfo.id shouldBe "12345"
        }

        it("지원하지 않는 registrationId를 전달하면 IllegalArgumentException이 발생해야 한다") {
            val attributes = mapOf<String, Any>()
            val exception = shouldThrow<IllegalArgumentException> {
                OAuth2UserInfoFactory.getOAuth2UserInfo("kakao", attributes)
            }
            exception.message shouldBe "지원하지 않는 소셜 로그인 제공자입니다: kakao"
        }
    }
})
