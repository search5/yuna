package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
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
// [설계상 알려진 한계 — 계획 문서 "리스크/미결정 사항"에 이월] ApiTokenAuthenticationFilter의
// scopedApiPattern은 `/api/v1/projects/{owner}/{project}/{resource}` 형태(owner/project 뒤에
// 리소스 세그먼트가 반드시 와야 함)만 스코프 토큰으로 인가한다. 이 컨트롤러의 두 엔드포인트는
// 구조상 리소스 세그먼트가 없어(목록은 owner 하나, 조회는 owner+project 둘뿐) 정규식이 매칭되지
// 않고 필터의 "레거시 경로"로 빠진다 — 즉 Fine-grained 스코프 토큰으로는 아직 호출할 수 없고,
// 세션 로그인 또는 기존 전권 토큰으로만 호출 가능하다(이슈/PR 엔드포인트는 "issues"/
// "pull-requests" 세그먼트가 있어 이 문제가 없다). 대안(예: "settings" 세그먼트 재사용, 신규
// "info" 세그먼트 추가, 또는 필터 정규식을 3번째 세그먼트 선택적으로 완화)은 Step1~3에서 이미
// 완성된 필터/스코프 판정 로직을 변경해야 해 이번 라운드에서는 임의로 결정하지 않고 그대로
// 문서화만 한다.
//
// 그 대신 이 컨트롤러는 AccessControl.isAllowedToReadProject()로 웹 UI와 동일한 가시성 규칙을
// 그대로 적용한다(공개 프로젝트는 비로그인 사용자에게도 노출, 비공개는 멤버/조직관리자/사이트
// 매니저만). 응답 필드는 ProjectApiController.createdProjectNode()와 동일한 컨벤션을 따른다.
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
        authentication: Authentication?
    ): ResponseEntity<List<Map<String, Any?>>> {
        val user = getLoginUser(authentication)
        val visible = projectRepository.findByOwner(owner)
            .filter { accessControl.isAllowedToReadProject(user, it) }
            .map { toProjectNode(it) }
        return ResponseEntity.ok(visible)
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
