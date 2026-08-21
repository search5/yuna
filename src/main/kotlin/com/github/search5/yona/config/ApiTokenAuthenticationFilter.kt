package com.github.search5.yona.config

import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.user.UserState
import com.github.search5.yona.domain.user.YonaUserDetails
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * yona의 models/User.java extractUserTokenFromRequestHeader/findByUserToken +
 * controllers/api/UserApi.java isAuthored 대응.
 * "Authorization: token <값>" 또는 "Yona-Token: <값>" 헤더로 API 토큰 인증을 지원한다.
 * yona는 UserApp.USER_TOKEN_HEADER("Yona-Token")로 토큰을 발급/재발급하는 API는
 * yuna에도 이미 있었지만(/api/users/token/reset), 그 토큰을 실제로 검증해
 * 요청을 인증하는 경로가 없어 사실상 write-only였던 문제를 해결한다.
 */
@Component
class ApiTokenAuthenticationFilter(
    private val userRepository: UserRepository,
    private val userDetailsService: UserDetailsService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val current = SecurityContextHolder.getContext().authentication
        val alreadyAuthenticated = current != null && current.isAuthenticated && current !is AnonymousAuthenticationToken

        if (!alreadyAuthenticated) {
            val token = extractToken(request)
            if (token != null) {
                val user = userRepository.findByToken(token).orElse(null)
                if (user != null && user.state != UserState.LOCKED &&
                    user.state != UserState.DELETED
                ) {
                    val userDetails = userDetailsService.loadUserByUsername(user.loginId) as YonaUserDetails
                    SecurityContextHolder.getContext().authentication =
                        UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
                }
            }
        }

        filterChain.doFilter(request, response)
    }

    companion object {
        fun extractToken(request: HttpServletRequest): String? {
            val authHeader = request.getHeader("Authorization")
            if (authHeader != null && authHeader.contains("token ")) {
                return authHeader.substringAfter("token ").trim().takeIf { it.isNotBlank() }
            }
            return request.getHeader("Yona-Token")
        }
    }
}
