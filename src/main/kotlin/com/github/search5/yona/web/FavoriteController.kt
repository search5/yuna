package com.github.search5.yona.web

import com.github.search5.yona.domain.user.FavoriteService
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
class FavoriteController(
    private val favoriteService: FavoriteService,
    private val userRepository: UserRepository
) {

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    @PostMapping("/-_-api/v1/favoriteProjects/{projectId}")
    fun toggleFavoriteProject(
        @PathVariable projectId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, Any>> {
        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val isFavored = favoriteService.toggleFavoriteProject(user.id!!, projectId)
        
        return ResponseEntity.ok(mapOf(
            "projectId" to projectId.toString(),
            "favored" to isFavored
        ))
    }

    @GetMapping("/-_-api/v1/favoriteProjects")
    fun getFavoriteProjects(authentication: Authentication?): ResponseEntity<Map<String, Any>> {
        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val list = favoriteService.getFavoriteProjects(user.id!!)
        
        val projectsJson = list.map {
            mapOf(
                "projectId" to it.project.id,
                "projectName" to it.projectName,
                "owner" to it.owner
            )
        }
        val projectIds = list.map { it.project.id }

        return ResponseEntity.ok(mapOf(
            "projectIds" to projectIds,
            "projects" to projectsJson
        ))
    }

    @PostMapping("/-_-api/v1/favoriteIssues/{issueId}")
    fun toggleFavoriteIssue(
        @PathVariable issueId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, Any>> {
        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val isFavored = favoriteService.toggleFavoriteIssue(user.id!!, issueId)
        
        val message = if (isFavored) "이슈가 즐겨찾기에 추가되었습니다." else "이슈가 즐겨찾기에서 삭제되었습니다."
        
        return ResponseEntity.ok(mapOf(
            "issueId" to issueId.toString(),
            "favored" to isFavored,
            "message" to message
        ))
    }

    @GetMapping("/-_-api/v1/favoriteIssues")
    fun getFavoriteIssues(authentication: Authentication?): ResponseEntity<Map<String, Any>> {
        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val list = favoriteService.getFavoriteIssues(user.id!!)
        
        val issuesJson = list.map {
            mapOf(
                "issueId" to it.issue.id,
                "issueTitle" to it.issue.title,
                "issueAuthorName" to (it.issue.authorName ?: "알수없음")
            )
        }
        val issueIds = list.map { it.issue.id }

        return ResponseEntity.ok(mapOf(
            "projectIds" to issueIds,
            "projects" to issuesJson
        ))
    }

    @PostMapping("/-_-api/v1/favoriteOrganizations/{organizationId}")
    fun toggleFavoriteOrganization(
        @PathVariable organizationId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, Any>> {
        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val isFavored = favoriteService.toggleFavoriteOrganization(user.id!!, organizationId)
        
        return ResponseEntity.ok(mapOf(
            "organizationId" to organizationId.toString(),
            "favored" to isFavored
        ))
    }

    @GetMapping("/-_-api/v1/favoriteOrganizations")
    fun getFavoriteOrganizations(authentication: Authentication?): ResponseEntity<Map<String, Any>> {
        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val list = favoriteService.getFavoriteOrganizations(user.id!!)
        
        val orgsJson = list.map {
            mapOf(
                "organizationId" to it.organization.id,
                "organizationName" to it.organizationName
            )
        }
        val orgIds = list.map { it.organization.id }

        return ResponseEntity.ok(mapOf(
            "organizationIds" to orgIds,
            "organizations" to orgsJson
        ))
    }
}
