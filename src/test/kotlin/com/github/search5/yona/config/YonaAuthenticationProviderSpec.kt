package com.github.search5.yona.config

import com.github.search5.yona.domain.user.YonaUserDetails
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetailsService
import java.security.MessageDigest
import java.util.Base64

class YonaAuthenticationProviderSpec : DescribeSpec({
    val userDetailsService = mockk<UserDetailsService>()
    val authenticationProvider = YonaAuthenticationProvider(userDetailsService)

    fun getLegacyHashedPassword(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.reset()
        digest.update(salt.toByteArray(Charsets.UTF_8))
        var hashed = digest.digest(password.toByteArray(Charsets.UTF_8))
        for (i in 1 until 1024) {
            digest.reset()
            hashed = digest.digest(hashed)
        }
        return Base64.getEncoder().encodeToString(hashed)
    }

    describe("YonaAuthenticationProvider") {
        it("올바른 비밀번호를 입력하면 인증이 정상적으로 완료되어야 한다") {
            // Given
            val salt = "test-salt"
            val rawPassword = "myPassword123!"
            val expectedHashed = getLegacyHashedPassword(rawPassword, salt)
            
            val userDetails = YonaUserDetails(
                id = 1L,
                loginId = "gildong",
                passwordVal = expectedHashed,
                passwordSalt = salt,
                authoritiesVal = listOf(SimpleGrantedAuthority("ROLE_ACTIVE"))
            )
            
            every { userDetailsService.loadUserByUsername("gildong") } returns userDetails
            
            val authRequest = UsernamePasswordAuthenticationToken("gildong", rawPassword)

            // When
            val authResult = authenticationProvider.authenticate(authRequest)

            // Then
            authResult shouldNotBe null
            authResult.isAuthenticated shouldBe true
            authResult.name shouldBe "gildong"
            authResult.principal shouldBe userDetails
        }

        it("잘못된 비밀번호를 입력하면 BadCredentialsException 예외가 발생해야 한다") {
            // Given
            val salt = "test-salt"
            val rawPassword = "myPassword123!"
            val expectedHashed = getLegacyHashedPassword(rawPassword, salt)
            
            val userDetails = YonaUserDetails(
                id = 1L,
                loginId = "gildong",
                passwordVal = expectedHashed,
                passwordSalt = salt,
                authoritiesVal = listOf(SimpleGrantedAuthority("ROLE_ACTIVE"))
            )
            
            every { userDetailsService.loadUserByUsername("gildong") } returns userDetails
            
            val authRequest = UsernamePasswordAuthenticationToken("gildong", "wrongPassword")

            // When & Then
            shouldThrow<BadCredentialsException> {
                authenticationProvider.authenticate(authRequest)
            }
        }
    }
})
