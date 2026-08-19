package com.github.search5.yona.config

import com.github.search5.yona.domain.user.UserState
import com.github.search5.yona.domain.user.YonaUserDetails
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.LockedException
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

        it("계정 상태가 LOCKED인 사용자는 비밀번호가 맞아도 LockedException이 발생해야 한다") {
            // Given
            val salt = "test-salt"
            val rawPassword = "myPassword123!"
            val expectedHashed = getLegacyHashedPassword(rawPassword, salt)

            val userDetails = YonaUserDetails(
                id = 1L,
                loginId = "lockedUser",
                passwordVal = expectedHashed,
                passwordSalt = salt,
                authoritiesVal = listOf(SimpleGrantedAuthority("ROLE_LOCKED")),
                state = UserState.LOCKED
            )

            every { userDetailsService.loadUserByUsername("lockedUser") } returns userDetails

            val authRequest = UsernamePasswordAuthenticationToken("lockedUser", rawPassword)

            // When & Then
            shouldThrow<LockedException> {
                authenticationProvider.authenticate(authRequest)
            }
        }

        it("계정 상태가 DELETED인 사용자는 비밀번호가 맞아도 DisabledException이 발생해야 한다") {
            // Given
            val salt = "test-salt"
            val rawPassword = "myPassword123!"
            val expectedHashed = getLegacyHashedPassword(rawPassword, salt)

            val userDetails = YonaUserDetails(
                id = 1L,
                loginId = "deletedUser",
                passwordVal = expectedHashed,
                passwordSalt = salt,
                authoritiesVal = listOf(SimpleGrantedAuthority("ROLE_DELETED")),
                state = UserState.DELETED
            )

            every { userDetailsService.loadUserByUsername("deletedUser") } returns userDetails

            val authRequest = UsernamePasswordAuthenticationToken("deletedUser", rawPassword)

            // When & Then
            shouldThrow<DisabledException> {
                authenticationProvider.authenticate(authRequest)
            }
        }

        it("계정 상태가 ACTIVE가 아니어도 LOCKED/DELETED가 아니면(SITE_ADMIN 등) 정상 인증되어야 한다") {
            // Given
            val salt = "test-salt"
            val rawPassword = "myPassword123!"
            val expectedHashed = getLegacyHashedPassword(rawPassword, salt)

            val userDetails = YonaUserDetails(
                id = 1L,
                loginId = "adminUser",
                passwordVal = expectedHashed,
                passwordSalt = salt,
                authoritiesVal = listOf(SimpleGrantedAuthority("ROLE_SITE_ADMIN")),
                state = UserState.SITE_ADMIN
            )

            every { userDetailsService.loadUserByUsername("adminUser") } returns userDetails

            val authRequest = UsernamePasswordAuthenticationToken("adminUser", rawPassword)

            // When
            val authResult = authenticationProvider.authenticate(authRequest)

            // Then
            authResult.isAuthenticated shouldBe true
        }
    }
})
