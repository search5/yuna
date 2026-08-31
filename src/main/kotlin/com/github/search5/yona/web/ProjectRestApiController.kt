package com.github.search5.yona.web

import com.github.search5.yona.config.ApiTokenAuthenticationFilter
import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.apitoken.ApiToken
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectService
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.watch.WatchService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
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
    private val accessControl: AccessControl,
    private val projectService: ProjectService,
    private val organizationRepository: OrganizationRepository,
    private val watchService: WatchService,
    private val projectController: ProjectController
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

    // yona-wiki P3-02 4라운드(Step8.5 서버 보강) — `yona project create`. ProjectViewController.
    // newProject()(세션/폼 기반)와 동일한 권한 로직(owner가 기존 조직명이면 그 조직 admin만 생성
    // 가능)을 그대로 재사용하되, JSON 요청/응답으로 노출한다. 의도적으로 `/api/v1/projects`
    // 세그먼트 없는 bare POST로 두었다 - ApiTokenAuthenticationFilter의 어떤 스코프 패턴과도
    // 매칭되지 않아(3세그먼트/2세그먼트/owner전용 1세그먼트 전부 요구 형태가 다름) 세션 로그인이나
    // 레거시 전권 토큰으로만 호출 가능하다. GitHub Fine-grained PAT도 새 저장소 "생성" 자체는
    // 지원하지 않는 것(기존 저장소에만 스코프될 수 있음)과 동일한 제약이라 의도적 설계로 문서화한다
    // (계획 문서 "리스크/미결정 사항" 참고).
    @PostMapping
    fun create(
        @RequestBody request: CreateProjectRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val trimmedOwner = request.owner.trim()
        val organization = organizationRepository.findByName(trimmedOwner).orElse(null)
        if (organization != null && !accessControl.isOrganizationAdmin(organization, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return try {
            val project = Project().apply {
                this.owner = trimmedOwner
                this.name = request.name.trim()
                this.overview = request.overview?.trim() ?: ""
                this.projectScope = request.projectScope
                this.vcs = request.vcs.uppercase()
                this.isCodeEnabled = request.isCodeEnabled
                this.isIssueEnabled = request.isIssueEnabled
                this.isPullRequestEnabled = request.isPullRequestEnabled
                this.isReviewEnabled = request.isReviewEnabled
                this.isMilestoneEnabled = request.isMilestoneEnabled
                this.isBoardEnabled = request.isBoardEnabled
                if (organization != null) {
                    this.organization = organization
                }
            }

            val saved = projectService.createProject(project, user)
            watchService.watch(user, ResourceType.PROJECT, saved.id.toString())
            ResponseEntity.status(HttpStatus.CREATED).body(toProjectNode(saved))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf("error" to e.message))
        }
    }

    // yona-wiki P3-02 4라운드(Step8.5 서버 보강) — `yona project fork`. ProjectController.forkProject()
    // (`/api/{owner}/{projectName}/fork`)가 이미 owner/projectName 이름 기반으로 동작해(숫자 ID 변환이
    // 필요 없음) 그대로 위임한다. ApiTokenAuthenticationFilter의 resourceSegmentToResourceType에
    // "fork" -> ResourceType.FORK(CODE 그룹)가 매핑돼 있어 Fine-grained 스코프 토큰(CODE:write)으로도
    // 호출 가능하다.
    @PostMapping("/{owner}/{project}/fork")
    fun fork(
        @PathVariable owner: String,
        @PathVariable project: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        return projectController.forkProject(owner, project, authentication)
    }

    // yona-wiki P3-02 4라운드(Step8.5 서버 보강) — `yona project edit`/`yona project delete`.
    // ProjectController.updateProject()/deleteProject()는 숫자 projectId 기반이라 owner/project
    // 이름을 먼저 id로 바꿔 위임한다(Issue/PR REST API와 동일한 어댑터 패턴). URL에 "settings"
    // 세그먼트를 붙인 이유: 세그먼트 없는 `/api/v1/projects/{owner}/{project}`는 이미 "metadata"
    // 스코프(그룹/권한 매트릭스 없이 repo scope만 확인)로 매핑돼 있어, 그 경로를 그대로 PATCH/DELETE에
    // 재사용하면 ADMINISTRATION 쓰기 권한이 전혀 없는 스코프 토큰도 프로젝트를 수정/삭제할 수 있게
    // 되는 구멍이 생긴다. "settings"는 이미 ResourceType.PROJECT_SETTING(ADMINISTRATION 그룹)으로
    // 매핑돼 있어 이 세그먼트를 재사용하면 별도 필터 변경 없이 올바른 권한 등급을 강제할 수 있다.
    @PatchMapping("/{owner}/{project}/settings")
    fun updateSettings(
        @PathVariable owner: String,
        @PathVariable project: String,
        @RequestBody request: ProjectController.UpdateProjectRequest,
        authentication: Authentication?
    ): ResponseEntity<*> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build<Any>()
        return projectController.updateProject(found.id!!, request, authentication)
    }

    @DeleteMapping("/{owner}/{project}/settings")
    fun deleteProject(
        @PathVariable owner: String,
        @PathVariable project: String,
        authentication: Authentication?
    ): ResponseEntity<*> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build<Any>()
        return projectController.deleteProject(found.id!!, authentication)
    }

    data class CreateProjectRequest(
        val owner: String,
        val name: String,
        val overview: String? = null,
        val projectScope: ProjectScope = ProjectScope.PUBLIC,
        val vcs: String = "GIT",
        val isCodeEnabled: Boolean = true,
        val isIssueEnabled: Boolean = true,
        val isPullRequestEnabled: Boolean = true,
        val isReviewEnabled: Boolean = true,
        val isMilestoneEnabled: Boolean = true,
        val isBoardEnabled: Boolean = true
    )

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
