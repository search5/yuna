package com.github.search5.yona.web

import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.pullrequest.PullRequest
import com.github.search5.yona.domain.pullrequest.PullRequestMergeResult
import com.github.search5.yona.domain.pullrequest.PullRequestService
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/projects/{projectId}/pullrequests")
class PullRequestController(
    private val pullRequestService: PullRequestService,
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
        return projectUserRepository.existsByProjectIdAndUserId(project.id!!, user.id!!)
    }

    private fun checkWritePermission(project: Project, user: User?): Boolean {
        if (user == null) return false
        return projectUserRepository.existsByProjectIdAndUserId(project.id!!, user.id!!)
    }

    private fun isManagerOrContributor(project: Project, contributorId: Long?, user: User?): Boolean {
        if (user == null) return false
        if (contributorId == user.id) return true
        return projectUserRepository.findByProjectIdAndUserId(project.id!!, user.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)
    }

    @GetMapping
    fun getPullRequests(
        @PathVariable projectId: Long,
        @RequestParam(required = false) state: State?,
        authentication: Authentication?
    ): ResponseEntity<List<PullRequest>> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pullRequests = pullRequestService.getPullRequests(projectId, state)
        return ResponseEntity.ok(pullRequests)
    }

    @GetMapping("/{number}")
    fun getPullRequest(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<PullRequest> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(pullRequest)
    }

    @PostMapping
    fun createPullRequest(
        @PathVariable projectId: Long,
        @RequestBody request: CreatePullRequestRequest,
        authentication: Authentication?
    ): ResponseEntity<PullRequest> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkWritePermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pullRequest = pullRequestService.createPullRequest(
            title = request.title,
            body = request.body,
            fromProjectId = request.fromProjectId,
            toProjectId = projectId,
            fromBranch = request.fromBranch,
            toBranch = request.toBranch,
            contributor = user
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(pullRequest)
    }

    @PutMapping("/{number}")
    fun updatePullRequest(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @RequestBody request: UpdatePullRequestRequest,
        authentication: Authentication?
    ): ResponseEntity<PullRequest> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        if (!isManagerOrContributor(project, pullRequest.contributor.id, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val updated = pullRequestService.updatePullRequest(
            pullRequestId = pullRequest.id!!,
            title = request.title,
            body = request.body
        )

        return ResponseEntity.ok(updated)
    }

    @PostMapping("/{number}/merge")
    fun mergePullRequest(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<PullRequestMergeResult> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkWritePermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        val result = pullRequestService.merge(pullRequest.id!!, user)
        return ResponseEntity.ok(result)
    }

    @PostMapping("/{number}/state")
    fun changeState(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @RequestParam state: State,
        authentication: Authentication?
    ): ResponseEntity<PullRequest> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        if (!isManagerOrContributor(project, pullRequest.contributor.id, user) && !checkWritePermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val updated = pullRequestService.changeState(pullRequest.id!!, state)
        return ResponseEntity.ok(updated)
    }

    // yona PullRequestApp.deleteFromBranch 대응
    @DeleteMapping("/{number}/fromBranch")
    fun deleteFromBranch(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<PullRequest> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        if (!isManagerOrContributor(project, pullRequest.contributor.id, user) && !checkWritePermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val updated = pullRequestService.deleteFromBranch(pullRequest.id!!)
        return ResponseEntity.ok(updated)
    }

    // yona PullRequestApp.restoreFromBranch 대응
    @PostMapping("/{number}/fromBranch")
    fun restoreFromBranch(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<PullRequest> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        if (!isManagerOrContributor(project, pullRequest.contributor.id, user) && !checkWritePermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val updated = pullRequestService.restoreFromBranch(pullRequest.id!!)
        return ResponseEntity.ok(updated)
    }

    data class CreatePullRequestRequest(
        val title: String,
        val body: String?,
        val fromProjectId: Long,
        val fromBranch: String,
        val toBranch: String
    )

    @PostMapping("/{number}/reviewers")
    fun addReviewer(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Unit> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        pullRequestService.addReviewer(pullRequest.id!!, user)
        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/{number}/reviewers")
    fun removeReviewer(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Unit> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        pullRequestService.removeReviewer(pullRequest.id!!, user)
        return ResponseEntity.ok().build()
    }

    data class UpdatePullRequestRequest(
        val title: String,
        val body: String?
    )
}
