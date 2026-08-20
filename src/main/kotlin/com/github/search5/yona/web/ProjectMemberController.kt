package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectUserService
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder

@RestController
@RequestMapping("/api/projects/{projectId}")
class ProjectMemberController(
    private val projectUserService: ProjectUserService,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val messageSource: MessageSource
) {

    private fun isProjectManager(projectId: Long, userId: Long): Boolean {
        return projectUserRepository.findByProjectIdAndUserId(projectId, userId)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)
    }

    private fun getLoginUserId(authentication: Authentication?): Long {
        if (authentication == null) throw IllegalArgumentException("Unauthorized")
        val user = userRepository.findByLoginId(authentication.name)
            .orElseThrow { IllegalArgumentException("User not found") }
        return user.id!!
    }

    @PostMapping("/members")
    fun addMember(
        @PathVariable projectId: Long,
        @RequestParam loginId: String,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val currentUserId = getLoginUserId(authentication)
        if (!isProjectManager(projectId, currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return try {
            projectUserService.addMember(projectId, loginId, currentUserId)
            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to add member")))
        }
    }

    @PutMapping("/members/{userId}/role")
    fun updateMemberRole(
        @PathVariable projectId: Long,
        @PathVariable userId: Long,
        @RequestParam roleId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val currentUserId = getLoginUserId(authentication)
        if (!isProjectManager(projectId, currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return try {
            projectUserService.updateMemberRole(projectId, userId, roleId, currentUserId)
            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to update role")))
        }
    }

    @DeleteMapping("/members/{userId}")
    fun removeMember(
        @PathVariable projectId: Long,
        @PathVariable userId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val currentUserId = getLoginUserId(authentication)
        // 본인 탈퇴이거나 관리자인 경우만 허용
        if (currentUserId != userId && !isProjectManager(projectId, currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return try {
            projectUserService.removeMember(projectId, userId, currentUserId)
            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to remove member")))
        }
    }

    @PostMapping("/enroll")
    fun enroll(
        @PathVariable projectId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val currentUserId = getLoginUserId(authentication)
        return try {
            projectUserService.enroll(projectId, currentUserId)
            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to enroll")))
        }
    }

    @PostMapping("/enroll/cancel")
    fun cancelEnroll(
        @PathVariable projectId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val currentUserId = getLoginUserId(authentication)
        return try {
            projectUserService.cancelEnroll(projectId, currentUserId)
            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to cancel enroll")))
        }
    }

    @PostMapping("/members/{userId}/accept")
    fun acceptMemberRequest(
        @PathVariable projectId: Long,
        @PathVariable userId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val currentUserId = getLoginUserId(authentication)
        if (!isProjectManager(projectId, currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return try {
            projectUserService.acceptMemberRequest(projectId, userId, currentUserId)
            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to accept member request")))
        }
    }

    @PostMapping("/members/{userId}/reject")
    fun rejectMemberRequest(
        @PathVariable projectId: Long,
        @PathVariable userId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val currentUserId = getLoginUserId(authentication)
        if (!isProjectManager(projectId, currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return try {
            projectUserService.rejectMemberRequest(projectId, userId, currentUserId)
            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to reject member request")))
        }
    }

    @GetMapping("/assignableUsers")
    fun assignableUsers(
        @PathVariable projectId: Long,
        @RequestParam(required = false, defaultValue = "") query: String,
        authentication: Authentication?
    ): ResponseEntity<List<Map<String, Any>>> {
        val currentUserId = getLoginUserId(authentication)
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val currentUser = userRepository.findById(currentUserId).orElse(null)

        // 권한 확인 (프로젝트 멤버인지 확인)
        if (!projectUserRepository.existsByProjectIdAndUserId(projectId, currentUserId) &&
            (currentUser == null || !AccessControl.isAllowedIfGroupMember(project, currentUser))
        ) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val projectUsers = projectUserRepository.findByProjectId(projectId)
        val members = projectUsers.map { it.user }

        val result = mutableListOf<Map<String, Any>>()

        if (query.isBlank() && currentUser != null) {
            val locale = LocaleContextHolder.getLocale()
            val assignToMeText: String = messageSource.getMessage("issue.assignToMe", null, "나에게 할당하기", locale) ?: "나에게 할당하기"
            val pureName: String = currentUser.getPureNameOnly() ?: ""
            val loginId: String = currentUser.loginId ?: ""

            result.add(mapOf(
                "loginId" to loginId,
                "name" to assignToMeText,
                "pureNameOnly" to pureName,
                "avatarUrl" to "",
                "type" to "user"
            ))
        }

        members.forEach { user ->
            if (query.isBlank() || 
                user.loginId.contains(query, ignoreCase = true) || 
                user.name.contains(query, ignoreCase = true) ||
                (user.englishName?.contains(query, ignoreCase = true) == true)
            ) {
                result.add(mapOf(
                    "loginId" to user.loginId,
                    "name" to user.getDisplayName(),
                    "pureNameOnly" to user.getPureNameOnly(),
                    "avatarUrl" to user.avatarUrl,
                    "type" to "user"
                ))
            }
        }

        return ResponseEntity.ok(result)
    }
}
