package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.enumeration.Operation
import com.github.search5.yona.domain.issue.DuplicateLabelCategoryNameException
import com.github.search5.yona.domain.issue.IssueLabel
import com.github.search5.yona.domain.issue.IssueLabelCategory
import com.github.search5.yona.domain.issue.IssueLabelService
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/projects/{projectId}/labels")
class IssueLabelController(
    private val issueLabelService: IssueLabelService,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val accessControl: AccessControl
) {

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    private fun checkReadPermission(project: Project, user: User?): Boolean {
        return accessControl.isAllowed(user, project, Operation.READ)
    }

    private fun isProjectManager(projectId: Long, userId: Long): Boolean {
        return projectUserRepository.findByProjectIdAndUserId(projectId, userId)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)
    }

    @GetMapping
    fun getLabels(
        @PathVariable projectId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, Any>> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val labels = issueLabelService.getLabels(projectId)
        val categories = issueLabelService.getCategories(projectId)

        return ResponseEntity.ok(mapOf(
            "labels" to labels,
            "categories" to categories
        ))
    }

    @PostMapping
    fun createLabel(
        @PathVariable projectId: Long,
        @RequestBody request: CreateLabelRequest,
        authentication: Authentication?
    ): ResponseEntity<IssueLabel> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!isProjectManager(projectId, user.id!!)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val saved = issueLabelService.createLabel(
            projectId = projectId,
            categoryId = request.categoryId,
            name = request.name,
            color = request.color
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(saved)
    }

    @PostMapping("/categories")
    fun createCategory(
        @PathVariable projectId: Long,
        @RequestBody request: CreateCategoryRequest,
        authentication: Authentication?
    ): ResponseEntity<IssueLabelCategory> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!isProjectManager(projectId, user.id!!)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val saved = issueLabelService.createCategory(
            projectId = projectId,
            name = request.name,
            isExclusive = request.isExclusive ?: false
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(saved)
    }

    // yona IssueLabelApp.update() 대응 (P1-10)
    @PutMapping("/{labelId}")
    fun updateLabel(
        @PathVariable projectId: Long,
        @PathVariable labelId: Long,
        @RequestBody request: UpdateLabelRequest,
        authentication: Authentication?
    ): ResponseEntity<IssueLabel> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!isProjectManager(projectId, user.id!!)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val updated = issueLabelService.updateLabel(
            labelId = labelId,
            name = request.name,
            color = request.color,
            categoryId = request.categoryId
        )
        return ResponseEntity.ok(updated)
    }

    // yona IssueLabelApp.updateCategory() 대응 (P1-11)
    @PutMapping("/categories/{categoryId}")
    fun updateCategory(
        @PathVariable projectId: Long,
        @PathVariable categoryId: Long,
        @RequestBody request: UpdateCategoryRequest,
        authentication: Authentication?
    ): ResponseEntity<IssueLabelCategory> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!isProjectManager(projectId, user.id!!)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return try {
            val updated = issueLabelService.updateCategory(
                categoryId = categoryId,
                name = request.name,
                isExclusive = request.isExclusive ?: false
            )
            ResponseEntity.ok(updated)
        } catch (e: DuplicateLabelCategoryNameException) {
            ResponseEntity.badRequest().build()
        }
    }

    // yona IssueLabelApp.copyLabels() 대응 (P1-12). 대상(projectId) 프로젝트에는 생성 권한(관리자),
    // 원본(fromProjectId) 프로젝트에는 읽기 권한이 있어야 한다(yona AccessControl.isAllowed(..., READ)).
    @PostMapping("/copy")
    fun copyLabels(
        @PathVariable projectId: Long,
        @RequestBody request: CopyLabelsRequest,
        authentication: Authentication?
    ): ResponseEntity<List<IssueLabel>> {
        val toProject = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!isProjectManager(projectId, user.id!!)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val fromProject = projectRepository.findById(request.fromProjectId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        if (!checkReadPermission(fromProject, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val copied = issueLabelService.copyLabels(fromProjectId = request.fromProjectId, toProjectId = projectId)
        return ResponseEntity.ok(copied)
    }

    @DeleteMapping("/{labelId}")
    fun deleteLabel(
        @PathVariable projectId: Long,
        @PathVariable labelId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!isProjectManager(projectId, user.id!!)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        issueLabelService.deleteLabel(labelId)
        return ResponseEntity.ok(mapOf("status" to "success"))
    }

    @DeleteMapping("/categories/{categoryId}")
    fun deleteCategory(
        @PathVariable projectId: Long,
        @PathVariable categoryId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!isProjectManager(projectId, user.id!!)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        issueLabelService.deleteCategory(categoryId)
        return ResponseEntity.ok(mapOf("status" to "success"))
    }

    data class CreateLabelRequest(
        val categoryId: Long,
        val name: String,
        val color: String
    )

    data class CreateCategoryRequest(
        val name: String,
        val isExclusive: Boolean?
    )

    data class UpdateLabelRequest(
        val name: String,
        val color: String,
        val categoryId: Long
    )

    data class UpdateCategoryRequest(
        val name: String,
        val isExclusive: Boolean?
    )

    data class CopyLabelsRequest(
        val fromProjectId: Long
    )
}
