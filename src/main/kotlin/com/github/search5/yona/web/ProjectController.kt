package com.github.search5.yona.web

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectService
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
class ProjectController(
    private val projectService: ProjectService,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository
) {

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    private fun isProjectManager(projectId: Long, userId: Long): Boolean {
        return projectUserRepository.findByProjectIdAndUserId(projectId, userId)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)
    }

    private fun isProjectMember(projectId: Long, userId: Long): Boolean {
        return projectUserRepository.existsByProjectIdAndUserId(projectId, userId)
    }

    private fun checkReadPermission(project: Project, user: User?): Boolean {
        if (project.projectScope == ProjectScope.PUBLIC) return true
        if (user == null) return false
        return isProjectMember(project.id!!, user.id!!)
    }

    @GetMapping("/api/projects/search")
    fun searchProjects(
        @RequestParam(value = "query", defaultValue = "") query: String,
        authentication: Authentication?
    ): ResponseEntity<List<String>> {
        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val pageable = org.springframework.data.domain.PageRequest.of(0, 100)
        val projectNames = if (user.isSiteManager) {
            projectRepository.findProjectsForAdmin(query, pageable).content.map { "${it.owner}/${it.name}" }
        } else {
            val allowedIds = projectRepository.findAllowedProjectIdsForUser(user.id!!)
            if (allowedIds.isEmpty()) {
                val publicIds = projectRepository.findPublicProjectIds()
                if (publicIds.isEmpty()) {
                    emptyList()
                } else {
                    projectRepository.searchProjects(publicIds, query, pageable).content.map { "${it.owner}/${it.name}" }
                }
            } else {
                projectRepository.searchProjects(allowedIds, query, pageable).content.map { "${it.owner}/${it.name}" }
            }
        }
        return ResponseEntity.ok(projectNames)
    }

    @PutMapping("/api/projects/{projectId}")
    fun updateProject(
        @PathVariable projectId: Long,
        @RequestBody request: UpdateProjectRequest,
        authentication: Authentication?
    ): ResponseEntity<Project> {
        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!isProjectManager(projectId, user.id!!)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val updated = projectService.updateProject(
            projectId = projectId,
            param = com.github.search5.yona.domain.project.UpdateProjectParam(
                overview = request.overview,
                projectScope = request.projectScope,
                isCodeAccessibleMemberOnly = request.isCodeAccessibleMemberOnly,
                isUsingReviewerCount = request.isUsingReviewerCount,
                defaultReviewerCount = request.defaultReviewerCount,
                defaultBranch = request.defaultBranch,
                isCodeEnabled = request.isCodeEnabled,
                isIssueEnabled = request.isIssueEnabled,
                isPullRequestEnabled = request.isPullRequestEnabled,
                isReviewEnabled = request.isReviewEnabled,
                isMilestoneEnabled = request.isMilestoneEnabled,
                isBoardEnabled = request.isBoardEnabled
            )
        )
        return ResponseEntity.ok(updated)
    }

    @DeleteMapping("/api/projects/{projectId}")
    fun deleteProject(
        @PathVariable projectId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        
        // 소유자(owner) 본인이거나 MANAGER여야 삭제 가능
        val isOwner = project.owner == user.loginId
        if (!isOwner && !isProjectManager(projectId, user.id!!)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        projectService.deleteProject(projectId)
        return ResponseEntity.ok(mapOf("status" to "success"))
    }

    @PostMapping("/api/{owner}/{projectName}/transfer")
    fun requestTransfer(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam destination: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        if (!isProjectManager(project.id!!, user.id!!)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return try {
            val transfer = projectService.requestNewTransfer(project.id!!, user.id!!, destination)
            ResponseEntity.ok(transfer)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @PostMapping("/api/projects/transfer/{transferId}/accept")
    fun acceptTransfer(
        @PathVariable transferId: Long,
        @RequestParam confirmKey: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        return try {
            projectService.acceptTransfer(transferId, confirmKey, user.id!!)
            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @PostMapping("/api/{owner}/{projectName}/fork")
    fun forkProject(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        return try {
            val forkedProject = projectService.forkProject(project.id!!, user.id!!)
            ResponseEntity.ok(forkedProject)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    // yona ProjectApp.labels() 대응 (P1-13)
    @GetMapping("/api/{owner}/{projectName}/labels")
    fun getProjectLabels(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return ResponseEntity.ok(projectService.getProjectLabels(project.id!!))
    }

    // yona ProjectApp.attachLabel() 대응 (P1-13). yona AccessControl은 PROJECT_LABELS를
    // 별도 케이스로 다루지 않아 일반 프로젝트 리소스 UPDATE 규칙(user.isMemberOf(project))을 그대로
    // 따른다 - MANAGER가 아니어도 프로젝트 멤버라면 라벨을 붙이고 뗄 수 있다.
    @PostMapping("/api/{owner}/{projectName}/labels")
    fun attachLabel(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false) category: String?,
        @RequestParam name: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!isProjectMember(project.id!!, user.id!!)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val result = projectService.attachLabel(project.id!!, category, name)
        if (!result.isAttached) {
            // 이미 붙어있던 라벨: yona는 204 No Content를 반환한다.
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build()
        }

        return if (result.isCreated) {
            ResponseEntity.status(HttpStatus.CREATED).body(result.label)
        } else {
            ResponseEntity.ok(result.label)
        }
    }

    // yona ProjectApp.detachLabel() 대응 (P1-13)
    @DeleteMapping("/api/{owner}/{projectName}/labels/{labelId}")
    fun detachLabel(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable labelId: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!isProjectMember(project.id!!, user.id!!)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val detached = projectService.detachLabel(project.id!!, labelId)
        if (!detached) {
            return ResponseEntity.notFound().build()
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build()
    }

    data class UpdateProjectRequest(
        val overview: String,
        val projectScope: ProjectScope,
        val isCodeAccessibleMemberOnly: Boolean = false,
        val isUsingReviewerCount: Boolean = false,
        val defaultReviewerCount: Int = 1,
        val defaultBranch: String? = null,
        val isCodeEnabled: Boolean = true,
        val isIssueEnabled: Boolean = true,
        val isPullRequestEnabled: Boolean = true,
        val isReviewEnabled: Boolean = true,
        val isMilestoneEnabled: Boolean = true,
        val isBoardEnabled: Boolean = true
    )
}
