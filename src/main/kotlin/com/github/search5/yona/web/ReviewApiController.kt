package com.github.search5.yona.web

import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.pullrequest.CodeReviewService
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.domain.user.UserRepository
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*

@Controller
class ReviewApiController(
    private val projectRepository: ProjectRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val userRepository: UserRepository,
    private val codeReviewService: CodeReviewService
) {

    @DeleteMapping("/comments/{type}/{id}")
    fun deleteComment(
        @PathVariable type: String,
        @PathVariable id: Long,
        authentication: Authentication?
    ): ResponseEntity<Unit> {
        val user = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return ResponseEntity.status(401).build()

        try {
            if (type.uppercase() == "COMMIT_COMMENT") {
                codeReviewService.deleteCommitComment(id, user)
            } else if (type.uppercase() == "REVIEW_COMMENT") {
                codeReviewService.deleteReviewComment(id, user)
            }
        } catch (e: IllegalArgumentException) {
            if (e.message == "Permission denied") {
                return ResponseEntity.status(403).build()
            }
            throw e
        }
        return ResponseEntity.ok().build()
    }

    @PostMapping("/api/{owner}/{projectName}/pullRequest/{pullRequestId}/review")
    fun review(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable pullRequestId: Long,
        authentication: Authentication?
    ): String {
        val user = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: throw IllegalStateException("User not authenticated")

        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"

        val pullRequest = pullRequestRepository.findById(pullRequestId).orElse(null)
            ?: return "error/404"

        codeReviewService.addReviewer(pullRequestId, user.id!!)

        return "redirect:/$owner/$projectName/pullRequest/${pullRequest.number}"
    }

    @PostMapping("/api/{owner}/{projectName}/pullRequest/{pullRequestId}/unreview")
    fun unreview(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable pullRequestId: Long,
        authentication: Authentication?
    ): String {
        val user = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: throw IllegalStateException("User not authenticated")

        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"

        val pullRequest = pullRequestRepository.findById(pullRequestId).orElse(null)
            ?: return "error/404"

        codeReviewService.removeReviewer(pullRequestId, user.id!!)

        return "redirect:/$owner/$projectName/pullRequest/${pullRequest.number}"
    }
}
