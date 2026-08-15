package com.github.search5.yona.web

import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.pullrequest.CodeReviewService
import com.github.search5.yona.domain.pullrequest.CommentThread
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.domain.support.CodeRange
import com.github.search5.yona.domain.user.UserRepository
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*

@Controller
class CodeReviewController(
    private val projectRepository: ProjectRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val userRepository: UserRepository,
    private val codeReviewService: CodeReviewService
) {

    @PostMapping("/{owner}/{projectName}/pullRequest/{pullRequestId}/comments")
    fun newPullRequestComment(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable pullRequestId: Long,
        @RequestParam(required = false) commitId: String?,
        @RequestParam contents: String,
        @RequestParam(value = "thread.id", required = false) threadId: Long?,
        codeRangeReq: CodeRangeRequest,
        authentication: Authentication?
    ): String {
        val user = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: throw IllegalStateException("User not authenticated")

        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"

        val pullRequest = pullRequestRepository.findById(pullRequestId).orElse(null)
            ?: return "error/404"

        val codeRange = codeRangeReq.toCodeRange()

        val comment = codeReviewService.createReviewComment(
            project = project,
            pullRequest = pullRequest,
            commitId = commitId,
            contents = contents,
            codeRange = codeRange,
            threadId = threadId,
            currentUser = user
        )

        return if (commitId != null) {
            "redirect:/$owner/$projectName/pullRequest/${pullRequest.number}/commit/$commitId#comment-${comment.id}"
        } else {
            "redirect:/$owner/$projectName/pullRequest/${pullRequest.number}/changes#comment-${comment.id}"
        }
    }

    @PostMapping("/{owner}/{projectName}/commit/{commitId}/comments")
    fun newCommitComment(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable commitId: String,
        @RequestParam contents: String,
        @RequestParam(value = "thread.id", required = false) threadId: Long?,
        codeRangeReq: CodeRangeRequest,
        authentication: Authentication?
    ): String {
        val user = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: throw IllegalStateException("User not authenticated")

        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"

        val codeRange = codeRangeReq.toCodeRange()

        val comment = codeReviewService.createReviewComment(
            project = project,
            pullRequest = null,
            commitId = commitId,
            contents = contents,
            codeRange = codeRange,
            threadId = threadId,
            currentUser = user
        )

        return "redirect:/$owner/$projectName/commit/$commitId#comment-${comment.id}"
    }

    @DeleteMapping("/comments/{type}/{id}")
    @ResponseBody
    fun deleteComment(
        @PathVariable type: String,
        @PathVariable id: Long,
        authentication: Authentication?
    ): ResponseEntity<Unit> {
        val user = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return ResponseEntity.status(401).build()

        if (type.uppercase() == "REVIEW_COMMENT") {
            codeReviewService.deleteReviewComment(id, user)
        }
        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/{owner}/{projectName}/commit/{commitId}/comments/{id}/delete")
    fun deleteCommitCommentRedirect(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable commitId: String,
        @PathVariable id: Long,
        authentication: Authentication?
    ): String {
        val user = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: throw IllegalStateException("User not authenticated")

        codeReviewService.deleteReviewComment(id, user)
        return "redirect:/$owner/$projectName/commit/$commitId"
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

data class CodeRangeRequest(
    val path: String? = null,
    val startSide: String? = null,
    val startLine: Int? = null,
    val startColumn: Int? = null,
    val endSide: String? = null,
    val endLine: Int? = null,
    val endColumn: Int? = null
) {
    fun toCodeRange(): CodeRange? {
        if (startLine == null) return null
        return CodeRange(
            path = path,
            startSide = startSide?.let { CodeRange.Side.valueOf(it.uppercase()) },
            startLine = startLine,
            startColumn = startColumn,
            endSide = endSide?.let { CodeRange.Side.valueOf(it.uppercase()) },
            endLine = endLine,
            endColumn = endColumn
        )
    }
}
