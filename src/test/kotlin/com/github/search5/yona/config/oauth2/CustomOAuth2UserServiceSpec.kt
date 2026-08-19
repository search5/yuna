package com.github.search5.yona.config.oauth2

import com.github.search5.yona.domain.user.LinkedAccount
import com.github.search5.yona.domain.user.LinkedAccountRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.user.UserState
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import java.util.Optional

class CustomOAuth2UserServiceSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val linkedAccountRepository = mockk<LinkedAccountRepository>()
    val delegate = mockk<DefaultOAuth2UserService>()
    val customOAuth2UserService = CustomOAuth2UserService(userRepository, linkedAccountRepository, delegate)

    beforeTest {
        clearMocks(userRepository, linkedAccountRepository, delegate)
    }

    describe("CustomOAuth2UserService") {
        it("소셜 로그인 성공 시 신규 사용자라면 자동 회원가입이 수행되어야 한다") {
            // Given
            val clientRegistration = ClientRegistration.withRegistrationId("google")
                .clientId("client-id")
                .tokenUri("https://token.uri")
                .authorizationUri("https://auth.uri")
                .userInfoUri("https://user.info.uri")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://redirect.uri")
                .build()

            val userRequest = mockk<OAuth2UserRequest>()
            every { userRequest.clientRegistration } returns clientRegistration

            val attributes = mapOf(
                "sub" to "google-sub-id",
                "name" to "홍길동",
                "email" to "gildong@example.com"
            )
            val defaultOAuth2User = DefaultOAuth2User(
                listOf(SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                "sub"
            )

            every { delegate.loadUser(userRequest) } returns defaultOAuth2User

            every { linkedAccountRepository.findByProviderKeyAndProviderUserId("google", "google-sub-id") } returns Optional.empty()
            every { userRepository.findByEmail("gildong@example.com") } returns Optional.empty()
            every { userRepository.findByLoginId("gildong") } returns Optional.empty()

            val savedUserSlot = slot<User>()
            every { userRepository.save(capture(savedUserSlot)) } answers {
                val user = firstArg<User>()
                user.id = 100L
                user
            }
            every { linkedAccountRepository.save(any()) } answers { firstArg() }

            // When
            val oauth2User = customOAuth2UserService.loadUser(userRequest) as YonaOAuth2User

            // Then
            oauth2User shouldNotBe null
            oauth2User.user.id shouldBe 100L
            oauth2User.user.name shouldBe "홍길동"
            oauth2User.user.email shouldBe "gildong@example.com"
            oauth2User.user.loginId shouldBe "gildong"
            oauth2User.authorities.map { it.authority } shouldBe listOf("ROLE_ACTIVE")

            verify(exactly = 1) { userRepository.save(any()) }
            verify(exactly = 1) {
                linkedAccountRepository.save(match { it.providerKey == "google" && it.providerUserId == "google-sub-id" })
            }
        }

        it("이미 연결된 provider 계정으로 재로그인하면 신규 가입 없이 연결된 사용자로 로그인해야 한다") {
            val clientRegistration = ClientRegistration.withRegistrationId("google")
                .clientId("client-id").tokenUri("https://token.uri").authorizationUri("https://auth.uri")
                .userInfoUri("https://user.info.uri").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://redirect.uri").build()
            val userRequest = mockk<OAuth2UserRequest>()
            every { userRequest.clientRegistration } returns clientRegistration

            val attributes = mapOf("sub" to "google-sub-id", "name" to "홍길동", "email" to "gildong@example.com")
            val defaultOAuth2User = DefaultOAuth2User(listOf(SimpleGrantedAuthority("ROLE_USER")), attributes, "sub")
            every { delegate.loadUser(userRequest) } returns defaultOAuth2User

            val existingUser = User(id = 42L, loginId = "gildong", name = "홍길동", email = "gildong@example.com", state = UserState.ACTIVE)
            val existingLink = LinkedAccount(id = 1L, user = existingUser, providerKey = "google", providerUserId = "google-sub-id")
            every { linkedAccountRepository.findByProviderKeyAndProviderUserId("google", "google-sub-id") } returns Optional.of(existingLink)

            val oauth2User = customOAuth2UserService.loadUser(userRequest) as YonaOAuth2User

            oauth2User.user.id shouldBe 42L
            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 0) { linkedAccountRepository.save(any()) }
        }

        it("다른 provider로 이미 가입된(이메일 일치) 사용자가 새 provider로 로그인하면 기존 계정에 연결(link)만 하고 새 계정을 만들지 않아야 한다") {
            val clientRegistration = ClientRegistration.withRegistrationId("github")
                .clientId("client-id").tokenUri("https://token.uri").authorizationUri("https://auth.uri")
                .userInfoUri("https://user.info.uri").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://redirect.uri").build()
            val userRequest = mockk<OAuth2UserRequest>()
            every { userRequest.clientRegistration } returns clientRegistration

            val attributes = mapOf("id" to 555, "login" to "gildong-gh", "name" to "홍길동", "email" to "gildong@example.com")
            val defaultOAuth2User = DefaultOAuth2User(listOf(SimpleGrantedAuthority("ROLE_USER")), attributes, "id")
            every { delegate.loadUser(userRequest) } returns defaultOAuth2User

            val existingUser = User(id = 42L, loginId = "gildong", name = "홍길동", email = "gildong@example.com", state = UserState.ACTIVE)
            every { linkedAccountRepository.findByProviderKeyAndProviderUserId("github", "555") } returns Optional.empty()
            every { userRepository.findByEmail("gildong@example.com") } returns Optional.of(existingUser)
            every { linkedAccountRepository.save(any()) } answers { firstArg() }

            val oauth2User = customOAuth2UserService.loadUser(userRequest) as YonaOAuth2User

            oauth2User.user.id shouldBe 42L
            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 1) {
                linkedAccountRepository.save(match { it.providerKey == "github" && it.providerUserId == "555" && it.user.id == 42L })
            }
        }
    }
})
