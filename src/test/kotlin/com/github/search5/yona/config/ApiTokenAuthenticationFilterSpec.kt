package com.github.search5.yona.config

import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.user.UserState
import com.github.search5.yona.domain.user.YonaUserDetails
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.FilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetailsService
import java.util.Optional

class ApiTokenAuthenticationFilterSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val userDetailsService = mockk<UserDetailsService>()
    val filter = ApiTokenAuthenticationFilter(userRepository, userDetailsService)
    val filterChain = mockk<FilterChain>(relaxed = true)

    beforeTest {
        clearMocks(userRepository, userDetailsService, filterChain)
        SecurityContextHolder.clearContext()
    }

    describe("ApiTokenAuthenticationFilter.extractToken") {
        it("Authorization: token <값> 헤더에서 토큰을 추출해야 한다") {
            val request = MockHttpServletRequest()
            request.addHeader("Authorization", "token abc123")

            ApiTokenAuthenticationFilter.extractToken(request) shouldBe "abc123"
        }

        it("Authorization 헤더가 없으면 Yona-Token 헤더를 사용해야 한다") {
            val request = MockHttpServletRequest()
            request.addHeader("Yona-Token", "xyz789")

            ApiTokenAuthenticationFilter.extractToken(request) shouldBe "xyz789"
        }

        it("둘 다 없으면 null이어야 한다") {
            val request = MockHttpServletRequest()

            ApiTokenAuthenticationFilter.extractToken(request) shouldBe null
        }
    }

    describe("ApiTokenAuthenticationFilter.doFilter") {
        it("유효한 토큰이면 SecurityContext에 인증 정보를 설정해야 한다") {
            val user = User(id = 1L, loginId = "gildong", name = "길동", token = "valid-token")
            val userDetails = YonaUserDetails(
                id = 1L, loginId = "gildong", passwordVal = "x", passwordSalt = "y",
                authoritiesVal = listOf(SimpleGrantedAuthority("ROLE_ACTIVE"))
            )
            every { userRepository.findByToken("valid-token") } returns Optional.of(user)
            every { userDetailsService.loadUserByUsername("gildong") } returns userDetails

            val request = MockHttpServletRequest()
            request.addHeader("Yona-Token", "valid-token")
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, filterChain)

            SecurityContextHolder.getContext().authentication?.principal shouldBe userDetails
        }

        it("토큰이 없으면 SecurityContext를 건드리지 않아야 한다") {
            val request = MockHttpServletRequest()
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, filterChain)

            SecurityContextHolder.getContext().authentication shouldBe null
        }

        it("존재하지 않는 토큰이면 SecurityContext를 건드리지 않아야 한다") {
            every { userRepository.findByToken("invalid-token") } returns Optional.empty()

            val request = MockHttpServletRequest()
            request.addHeader("Yona-Token", "invalid-token")
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, filterChain)

            SecurityContextHolder.getContext().authentication shouldBe null
        }

        it("탈퇴(DELETED)한 사용자의 토큰이면 인증하지 않아야 한다") {
            val deletedUser = User(id = 2L, loginId = "gone", name = "탈퇴", token = "deleted-token", state = UserState.DELETED)
            every { userRepository.findByToken("deleted-token") } returns Optional.of(deletedUser)

            val request = MockHttpServletRequest()
            request.addHeader("Yona-Token", "deleted-token")
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, filterChain)

            SecurityContextHolder.getContext().authentication shouldBe null
        }

        it("잠금(LOCKED)된 사용자의 토큰이면 인증하지 않아야 한다") {
            val lockedUser = User(id = 3L, loginId = "locked", name = "잠금", token = "locked-token", state = UserState.LOCKED)
            every { userRepository.findByToken("locked-token") } returns Optional.of(lockedUser)

            val request = MockHttpServletRequest()
            request.addHeader("Yona-Token", "locked-token")
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, filterChain)

            SecurityContextHolder.getContext().authentication shouldBe null
        }

        it("이미 인증된 상태(Anonymous가 아님)라면 필터가 다시 인증하지 않아야 한다") {
            val userDetails = YonaUserDetails(
                id = 1L, loginId = "gildong", passwordVal = "x", passwordSalt = "y",
                authoritiesVal = listOf(SimpleGrantedAuthority("ROLE_ACTIVE"))
            )
            val auth = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
            SecurityContextHolder.getContext().authentication = auth

            val request = MockHttpServletRequest()
            request.addHeader("Yona-Token", "some-token")
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, filterChain)

            // userRepository 호출이 없어야 함
            io.mockk.verify(exactly = 0) { userRepository.findByToken(any()) }
            SecurityContextHolder.getContext().authentication shouldBe auth
        }

        it("현재 인증이 AnonymousAuthenticationToken이면 재인증을 시도해야 한다") {
            val anonymousAuth = AnonymousAuthenticationToken(
                "key", "anonymousUser", listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS"))
            )
            SecurityContextHolder.getContext().authentication = anonymousAuth

            val user = User(id = 4L, loginId = "anon-user", name = "테스트", token = "valid-token")
            val userDetails = YonaUserDetails(
                id = 4L, loginId = "anon-user", passwordVal = "x", passwordSalt = "y",
                authoritiesVal = listOf(SimpleGrantedAuthority("ROLE_ACTIVE"))
            )
            every { userRepository.findByToken("valid-token") } returns Optional.of(user)
            every { userDetailsService.loadUserByUsername("anon-user") } returns userDetails

            val request = MockHttpServletRequest()
            request.addHeader("Yona-Token", "valid-token")
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, filterChain)

            SecurityContextHolder.getContext().authentication?.principal shouldBe userDetails
        }
    }
})
