package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.milestone.Milestone
import com.github.search5.yona.domain.milestone.MilestoneService
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
import java.time.Instant

@RestController
@RequestMapping("/api/projects/{projectId}/milestones")
class MilestoneController(
    private val milestoneService: MilestoneService,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository
) {

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    private fun checkReadPermission(project: Project, user: User?): Boolean {
        if (project.projectScope == ProjectScope.PUBLIC) return true
        if (user == null) return false
        return projectUserRepository.existsByProjectIdAndUserId(project.id!!, user.id!!) ||
            AccessControl.isAllowedIfGroupMember(project, user)
    }

    private fun isProjectManager(projectId: Long, userId: Long): Boolean {
        return projectUserRepository.findByProjectIdAndUserId(projectId, userId)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)
    }

    @GetMapping
    fun getMilestones(
        @PathVariable projectId: Long,
        @RequestParam(required = false, defaultValue = "OPEN") state: State,
        authentication: Authentication?
    ): ResponseEntity<List<Milestone>> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val milestones = milestoneService.getMilestones(projectId, state)
        return ResponseEntity.ok(milestones)
    }

    @GetMapping("/{milestoneId}")
    fun getMilestone(
        @PathVariable projectId: Long,
        @PathVariable milestoneId: Long,
        authentication: Authentication?
    ): ResponseEntity<Milestone> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val milestone = milestoneService.getMilestone(milestoneId)
            ?: return ResponseEntity.notFound().build()

        if (milestone.project.id != project.id) {
            return ResponseEntity.badRequest().build()
        }

        val user = getLoginUser(authentication)
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return ResponseEntity.ok(milestone)
    }

    @PostMapping
    fun createMilestone(
        @PathVariable projectId: Long,
        @RequestBody request: CreateMilestoneRequest,
        authentication: Authentication?
    ): ResponseEntity<Milestone> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!isProjectManager(projectId, user.id!!)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val milestone = Milestone(
            title = request.title,
            contents = request.contents,
            dueDate = request.dueDate,
            state = request.state ?: State.OPEN,
            project = project
        )

        val saved = milestoneService.createMilestone(projectId, milestone)
        return ResponseEntity.status(HttpStatus.CREATED).body(saved)
    }

    @PutMapping("/{milestoneId}")
    fun updateMilestone(
        @PathVariable projectId: Long,
        @PathVariable milestoneId: Long,
        @RequestBody request: UpdateMilestoneRequest,
        authentication: Authentication?
    ): ResponseEntity<Milestone> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val milestone = milestoneService.getMilestone(milestoneId)
            ?: return ResponseEntity.notFound().build()

        if (milestone.project.id != project.id) {
            return ResponseEntity.badRequest().build()
        }

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!isProjectManager(projectId, user.id!!)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val updated = milestoneService.updateMilestone(
            milestoneId = milestoneId,
            title = request.title,
            contents = request.contents,
            dueDate = request.dueDate,
            state = request.state ?: State.OPEN
        )

        return ResponseEntity.ok(updated)
    }

    @DeleteMapping("/{milestoneId}")
    fun deleteMilestone(
        @PathVariable projectId: Long,
        @PathVariable milestoneId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val milestone = milestoneService.getMilestone(milestoneId)
            ?: return ResponseEntity.notFound().build()

        if (milestone.project.id != project.id) {
            return ResponseEntity.badRequest().build()
        }

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!isProjectManager(projectId, user.id!!)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        milestoneService.deleteMilestone(milestoneId)
        return ResponseEntity.ok(mapOf("status" to "success"))
    }

    data class CreateMilestoneRequest(
        val title: String,
        val contents: String?,
        val dueDate: Instant?,
        val state: State?
    )

    data class UpdateMilestoneRequest(
        val title: String,
        val contents: String?,
        val dueDate: Instant?,
        val state: State?
    )
}
