package com.github.search5.yona.config

import com.github.search5.yona.domain.apitoken.ApiToken
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
 *
 * yona-wiki P3-02 Step6.5 — 리소스 세그먼트가 없는 두 경로(개별 프로젝트 조회 `/api/v1/projects/
 * {owner}/{project}`, 목록 `/api/v1/projects/{owner}`)도 스코프 토큰으로 인증되도록 갱신했다:
 * - 개별 조회는 "metadata" 스코프(그룹/권한 매트릭스 없이 repo scope만 확인, GitHub의 "Metadata:
 *   Read-only" 자동 부여와 동일한 개념)로 취급한다. URL 자체는 바꾸지 않고(3세그먼트 요구를
 *   완화하는 대신), 리소스 세그먼트가 없는 2세그먼트 요청을 별도 패턴으로 인식해 대표
 *   ResourceType으로 `resourceSegmentToResourceType["metadata"]`(= null)를 대입한다.
 * - 목록은 "인증됨/아님"만으로 부족해(어떤 프로젝트가 보이는지는 컨트롤러가 결정해야 함) 403을
 *   내지 않고 SecurityContext에 신원만 세팅한 뒤, 인증에 쓰인 ApiToken 객체를 request attribute
 *   (SCOPED_API_TOKEN_ATTRIBUTE)로 다운스트림 컨트롤러에 넘긴다(Spring Security가 CSRF 토큰 등을
 *   넘기는 방식과 동일).
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
                when {
                    target != null -> {
                        if (!authenticateScoped(token, target, request, response)) {
                            return
                        }
                    }
                    isOwnerOnlyListRequest(request.requestURI) -> authenticateScopedList(token, request)
                    else -> authenticateLegacy(token)
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

        setAuthenticatedIdentity(apiToken)
        request.setAttribute(SCOPED_API_TOKEN_ATTRIBUTE, apiToken)

        apiToken.lastUsedAt = Instant.now()
        apiTokenRepository.save(apiToken)
        return true
    }

    // yona-wiki P3-02 Step6.5 — owner 전용 목록 경로(`/api/v1/projects/{owner}`)는 특정 프로젝트
    // 하나가 아니라 "owner 밑 전체"에 대한 요청이라 여기서 403을 내지 않는다. 어떤 프로젝트가
    // 보이는지는 컨트롤러가 request attribute로 넘겨받은 ApiToken을 보고 직접 필터링한다.
    private fun authenticateScopedList(token: String, request: HttpServletRequest) {
        val apiToken = apiTokenRepository.findByTokenHash(hashApiToken(token)).orElse(null) ?: return
        val expiresAt = apiToken.expiresAt ?: return
        if (!expiresAt.isAfter(Instant.now())) return

        setAuthenticatedIdentity(apiToken)
        request.setAttribute(SCOPED_API_TOKEN_ATTRIBUTE, apiToken)
    }

    private fun setAuthenticatedIdentity(apiToken: ApiToken) {
        val owner = apiToken.owner
        if (owner != null && owner.state != UserState.LOCKED && owner.state != UserState.DELETED) {
            val userDetails = userDetailsService.loadUserByUsername(owner.loginId) as YonaUserDetails
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
        }
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

        // yona-wiki P3-02 Step6.5 — 리소스 세그먼트가 없는 개별 프로젝트 조회
        // (`/api/v1/projects/{owner}/{project}`, 정확히 2세그먼트). scopedApiPattern은 3세그먼트를
        // 요구하므로 이 URL과 겹치지 않는다. 이 URL은 URL을 바꾸지 않고도 "metadata" 스코프로
        // 취급한다(resourceSegmentToResourceType["metadata"] 참고).
        private val individualProjectPattern = Pattern.compile("^/api/v1/projects/([^/]+)/([^/]+)/?$")

        // yona-wiki P3-02 Step6.5 — owner만 있는 목록 경로(`/api/v1/projects/{owner}`, 정확히
        // 1세그먼트). 위 두 패턴과 세그먼트 수가 달라 겹치지 않는다.
        private val ownerOnlyPattern = Pattern.compile("^/api/v1/projects/([^/]+)/?$")

        // URL의 리소스 세그먼트(예: "issues")를 스코프 판정용 대표 ResourceType 하나로 매핑한다.
        // ApiTokenAuthorizer는 ResourceType을 ApiTokenScopeGroup으로 다시 뭉뚱그리므로, 그룹 안에서
        // 어떤 대표값을 고르는지는 판정 결과에 영향을 주지 않는다(같은 그룹이면 결과 동일).
        // "metadata" -> null은 리소스 그룹과 무관하게 repo scope만 확인하는 GitHub "Metadata:
        // Read-only" 자동 부여 대응이다 — 값 자체가 null이므로 조회는 반드시 containsKey로 해야
        // 한다(map[key] ?: return null은 "키 없음"과 "값이 null"을 구분하지 못한다).
        // yona-wiki P3-02 4라운드(Step8.5 서버 보강) — "labels"/"fork" 세그먼트 추가.
        // labels는 실제 위임 대상(ProjectViewController.newLabel/updateLabelForm/deleteLabelForm)이
        // 전부 ResourceType.ISSUE_LABEL 기준 AccessControl 체크를 쓰고 있어(ISSUES 그룹) 그대로
        // 맞춘다 - 필터가 부여하는 스코프와 컨트롤러가 실제로 요구하는 AccessControl 권한이 같은
        // 그룹이어야 "스코프는 통과했는데 컨트롤러가 거부"/그 반대의 불일치가 없다. fork는
        // ResourceType.FORK(CODE 그룹) - 저장소 코드를 복제하는 행위라 CODE 스코프가 자연스럽다.
        private val resourceSegmentToResourceType: Map<String, ResourceType?> = mapOf(
            "issues" to ResourceType.ISSUE_POST,
            "pull-requests" to ResourceType.PULL_REQUEST,
            "code" to ResourceType.CODE,
            "board" to ResourceType.BOARD_POST,
            "wiki" to ResourceType.WIKI_PAGE,
            "webhooks" to ResourceType.WEBHOOK,
            "settings" to ResourceType.PROJECT_SETTING,
            "metadata" to null,
            "labels" to ResourceType.ISSUE_LABEL,
            "fork" to ResourceType.FORK
        )

        private fun parseScopedApiTarget(requestUri: String?): ScopedApiTarget? {
            if (requestUri == null) return null

            val scopedMatcher = scopedApiPattern.matcher(requestUri)
            if (scopedMatcher.matches()) {
                val owner = scopedMatcher.group(1)
                val projectName = scopedMatcher.group(2)
                val resourceSegment = scopedMatcher.group(3)
                if (!resourceSegmentToResourceType.containsKey(resourceSegment)) return null
                return ScopedApiTarget(owner, projectName, resourceSegmentToResourceType[resourceSegment])
            }

            val individualMatcher = individualProjectPattern.matcher(requestUri)
            if (individualMatcher.matches()) {
                val owner = individualMatcher.group(1)
                val projectName = individualMatcher.group(2)
                return ScopedApiTarget(owner, projectName, resourceSegmentToResourceType.getValue("metadata"))
            }

            return null
        }

        private fun isOwnerOnlyListRequest(requestUri: String?): Boolean {
            if (requestUri == null) return false
            return ownerOnlyPattern.matcher(requestUri).matches()
        }

        private fun requiredPermissionFor(method: String): ApiTokenPermission {
            return when (method.uppercase()) {
                "GET", "HEAD", "OPTIONS" -> ApiTokenPermission.READ
                else -> ApiTokenPermission.WRITE
            }
        }

        // Spring Security가 CSRF 토큰 등을 필터→다운스트림으로 넘길 때 쓰는 것과 동일한 request
        // attribute 패턴 — 인증에 사용된 ApiToken을 컨트롤러(예: ProjectRestApiController 목록
        // API)가 꺼내 "어떤 프로젝트가 보이는지" 직접 필터링할 수 있게 한다.
        const val SCOPED_API_TOKEN_ATTRIBUTE = "SCOPED_API_TOKEN"
    }

    private data class ScopedApiTarget(
        val owner: String,
        val projectName: String,
        val representativeResourceType: ResourceType?
    )
}
