package com.github.search5.yona.web

import com.github.search5.yona.config.ApiTokenAuthenticationFilter
import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.apitoken.ApiToken
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// yona-wiki P3-02 Step6 — Go CLI 등 외부 클라이언트를 위한 신규 범용 REST API
// (`/api/v1/projects/{owner}` 목록, `/api/v1/projects/{owner}/{project}` 조회).
//
// 이 컨트롤러는 AccessControl.isAllowedToReadProject()로 웹 UI와 동일한 가시성 규칙을 그대로
// 적용한다(공개 프로젝트는 비로그인 사용자에게도 노출, 비공개는 멤버/조직관리자/사이트 매니저만).
// 응답 필드는 ProjectApiController.createdProjectNode()와 동일한 컨벤션을 따른다.
//
// yona-wiki P3-02 Step6.5 — 개별 조회(`/api/v1/projects/{owner}/{project}`)는
// ApiTokenAuthenticationFilter가 "metadata" 스코프(그룹/권한 매트릭스 없이 repo scope만 확인)로
// 인가하므로 Fine-grained 스코프 토큰으로도 호출 가능해졌다(스코프 밖이면 필터가 이미 403으로
// 막으므로 이 컨트롤러는 별도 처리가 필요 없다). 목록(`/api/v1/projects/{owner}`)은 "인증됨/아님"
// 만으로는 부족해(어떤 프로젝트를 보여줄지는 컨트롤러가 결정해야 함) 필터가 403을 내지 않고
// SecurityContext 신원 설정 + request attribute(ApiTokenAuthenticationFilter.SCOPED_API_TOKEN_ATTRIBUTE)
// 로 인증에 쓰인 ApiToken을 넘긴다 — 이 컨트롤러가 그 값을 읽어 전체/선택 저장소 스코프에 따라
// AccessControl 통과 목록을 다시 한번 좁힌다. request attribute가 없으면(세션 로그인/레거시 전권
// 토큰/비로그인) 기존 AccessControl 기반 목록을 그대로 반환해 그 경로들의 동작은 완전히 보존한다.
@RestController
@RequestMapping("/api/v1/projects")
class ProjectRestApiController(
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val accessControl: AccessControl
) {

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    @GetMapping("/{owner}")
    fun list(
        @PathVariable owner: String,
        authentication: Authentication?,
        request: HttpServletRequest
    ): ResponseEntity<List<Map<String, Any?>>> {
        val user = getLoginUser(authentication)
        val allOwnerProjects = projectRepository.findByOwner(owner)
            .filter { accessControl.isAllowedToReadProject(user, it) }

        val scopedToken = request.getAttribute(ApiTokenAuthenticationFilter.SCOPED_API_TOKEN_ATTRIBUTE) as? ApiToken
        val visible = when {
            scopedToken == null -> allOwnerProjects // 세션/레거시 토큰/비로그인 — 기존 동작 100% 유지
            scopedToken.allRepositories -> allOwnerProjects
            else -> {
                val scopedProjectIds = scopedToken.scopedProjects.mapNotNull { it.id }.toSet()
                allOwnerProjects.filter { it.id in scopedProjectIds }
            }
        }
        return ResponseEntity.ok(visible.map { toProjectNode(it) })
    }

    @GetMapping("/{owner}/{project}")
    fun get(
        @PathVariable owner: String,
        @PathVariable project: String,
        authentication: Authentication?
    ): ResponseEntity<Map<String, Any?>> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!accessControl.isAllowedToReadProject(user, found)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return ResponseEntity.ok(toProjectNode(found))
    }

    // yona ProjectApi.java:220-228 createdProjectNode() 대응(ProjectApiController.kt와 동일 필드
    // 컨벤션) — id/scope만 이 신규 API 전용으로 추가한다.
    private fun toProjectNode(project: Project): Map<String, Any?> {
        return mapOf(
            "id" to project.id,
            "owner" to project.owner,
            "name" to project.name,
            "overview" to project.overview,
            "vcs" to project.vcs,
            "scope" to project.projectScope.name
        )
    }
}
