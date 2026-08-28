package com.github.search5.yona.config

import com.github.search5.yona.domain.apitoken.ApiTokenAuthorizer
import com.github.search5.yona.domain.apitoken.ApiTokenPermission
import com.github.search5.yona.domain.apitoken.ApiTokenRepository
import com.github.search5.yona.domain.apitoken.hashApiToken
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.project.ProjectRepository
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
import java.time.Instant
import java.util.regex.Pattern

/**
 * yona의 models/User.java extractUserTokenFromRequestHeader/findByUserToken +
 * controllers/api/UserApi.java isAuthored 대응.
 * "Authorization: token <값>" 또는 "Yona-Token: <값>" 헤더로 API 토큰 인증을 지원한다.
 *
 * yona-wiki P3-02 Step3 — `/api/v1/projects/{owner}/{project}/{resource}` 네임스페이스(Step4~6에서
 * 실제 컨트롤러가 채워질 신규 REST API 전용, 이번 라운드는 필터의 인가 로직만 선행 구현)로 들어오는
 * 요청은 ApiTokenRepository의 스코프 토큰으로만 인증하고, 스코프 밖이면 403으로 거부한다. 그 외
 * 기존 URL(레거시 `/api/projects/{id}/...` 등)은 기존 UserRepository.findByToken 기반 "매칭되면
 * 전권 부여" 동작을 그대로 유지한다 — 기존 전권 토큰 사용자 마이그레이션 전략이 아직 미정이라
 * (docs/yona-wiki/plans/p3-02-cli-and-rest-api.md "리스크/미결정 사항" 참고) 레거시 경로를 이번
 * 라운드에서 강제로 끊지 않는다.
 */
@Component
class ApiTokenAuthenticationFilter(
    private val userRepository: UserRepository,
    private val userDetailsService: UserDetailsService,
    private val apiTokenRepository: ApiTokenRepository,
    private val projectRepository: ProjectRepository
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
                val target = parseScopedApiTarget(request.requestURI)
                if (target != null) {
                    if (!authenticateScoped(token, target, request, response)) {
                        return
                    }
                } else {
                    authenticateLegacy(token)
                }
            }
        }

        filterChain.doFilter(request, response)
    }

    // 스코프 토큰 인증. 요청을 계속 진행해도 되면 true, 이미 response에 오류를 써서 체인을 끊었으면
    // false를 반환한다(GitAuthorizationFilter의 sendError + return 패턴과 동일).
    private fun authenticateScoped(
        token: String,
        target: ScopedApiTarget,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Boolean {
        val apiToken = apiTokenRepository.findByTokenHash(hashApiToken(token)).orElse(null)
            ?: return true // 스코프 토큰이 아니면(모르는 토큰) 인증 없이 통과 — 컨트롤러/후속 인가에서 401 처리

        val project = projectRepository.findByOwnerAndName(target.owner, target.projectName).orElse(null)
        val requiredPermission = requiredPermissionFor(request.method)

        val allowed = ApiTokenAuthorizer.isAuthorized(
            token = apiToken,
            resourceType = target.representativeResourceType,
            project = project,
            requiredPermission = requiredPermission
        )
        if (!allowed) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden")
            return false
        }

        val owner = apiToken.owner
        if (owner != null && owner.state != UserState.LOCKED && owner.state != UserState.DELETED) {
            val userDetails = userDetailsService.loadUserByUsername(owner.loginId) as YonaUserDetails
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
        }

        apiToken.lastUsedAt = Instant.now()
        apiTokenRepository.save(apiToken)
        return true
    }

    private fun authenticateLegacy(token: String) {
        val user = userRepository.findByToken(token).orElse(null)
        if (user != null && user.state != UserState.LOCKED && user.state != UserState.DELETED) {
            val userDetails = userDetailsService.loadUserByUsername(user.loginId) as YonaUserDetails
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
        }
    }

    companion object {
        fun extractToken(request: HttpServletRequest): String? {
            val authHeader = request.getHeader("Authorization")
            if (authHeader != null && authHeader.contains("token ")) {
                return authHeader.substringAfter("token ").trim().takeIf { it.isNotBlank() }
            }
            return request.getHeader("Yona-Token")
        }

        // 신규 범용 REST API 네임스페이스(Step4~6에서 컨트롤러가 채워질 예정) 전용 패턴.
        // GitAuthorizationFilter.gitUriPattern과 동일한 접근(Pattern 상수 + group 추출)이다.
        private val scopedApiPattern = Pattern.compile("^/api/v1/projects/([^/]+)/([^/]+)/([^/]+)(?:/.*)?$")

        // URL의 리소스 세그먼트(예: "issues")를 스코프 판정용 대표 ResourceType 하나로 매핑한다.
        // ApiTokenAuthorizer는 ResourceType을 ApiTokenScopeGroup으로 다시 뭉뚱그리므로, 그룹 안에서
        // 어떤 대표값을 고르는지는 판정 결과에 영향을 주지 않는다(같은 그룹이면 결과 동일).
        private val resourceSegmentToResourceType = mapOf(
            "issues" to ResourceType.ISSUE_POST,
            "pull-requests" to ResourceType.PULL_REQUEST,
            "code" to ResourceType.CODE,
            "board" to ResourceType.BOARD_POST,
            "wiki" to ResourceType.WIKI_PAGE,
            "webhooks" to ResourceType.WEBHOOK,
            "settings" to ResourceType.PROJECT_SETTING
        )

        private fun parseScopedApiTarget(requestUri: String?): ScopedApiTarget? {
            if (requestUri == null) return null
            val matcher = scopedApiPattern.matcher(requestUri)
            if (!matcher.matches()) return null

            val owner = matcher.group(1)
            val projectName = matcher.group(2)
            val resourceSegment = matcher.group(3)
            val resourceType = resourceSegmentToResourceType[resourceSegment] ?: return null

            return ScopedApiTarget(owner, projectName, resourceType)
        }

        private fun requiredPermissionFor(method: String): ApiTokenPermission {
            return when (method.uppercase()) {
                "GET", "HEAD", "OPTIONS" -> ApiTokenPermission.READ
                else -> ApiTokenPermission.WRITE
            }
        }
    }

    private data class ScopedApiTarget(
        val owner: String,
        val projectName: String,
        val representativeResourceType: ResourceType
    )
}
