package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.pullrequest.CodeReviewService
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.domain.support.CodeRange
import com.github.search5.yona.domain.user.UserRepository
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*

@Controller
class ReviewViewController(
    private val projectRepository: ProjectRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val userRepository: UserRepository,
    private val codeReviewService: CodeReviewService,
    private val accessControl: AccessControl
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

        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        // yona PullRequestApp.java:591 @IsCreatable(ResourceType.REVIEW_COMMENT) 대응 (P0-24).
        // 권한 체크가 전혀 없어 프로젝트 멤버십/READ 권한과 무관하게 로그인한 임의 사용자가
        // 비공개 프로젝트의 PR에도 리뷰 댓글을 달 수 있던 취약점.
        if (!accessControl.isProjectResourceCreatable(user, project, ResourceType.REVIEW_COMMENT)) {
            return "error/403"
        }

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

        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        // yona CodeHistoryApp.java:189 @IsCreatable(ResourceType.COMMIT_COMMENT) 대응 (P0-24,
        // newPullRequestComment와 같은 파일에서 함께 발견). SVN/Git 분기와 무관하게 커밋 댓글
        // 생성 자체는 COMMIT_COMMENT 권한으로 게이트된다(CodeHistoryController.createComment의
        // JSON API 경로는 이미 이 체크가 있음 — 이 화면(폼 제출) 경로만 빠져 있었음).
        if (!accessControl.isProjectResourceCreatable(user, project, ResourceType.COMMIT_COMMENT)) {
            return "error/403"
        }

        val isSvn = project.vcs?.uppercase() == "SUBVERSION" || project.vcs?.uppercase() == "SVN"
        val commentId = if (isSvn) {
            val sideVal = codeRangeReq.startSide?.let { CodeRange.Side.valueOf(it.uppercase()) }
            val comment = codeReviewService.createCommitComment(
                project = project,
                commitId = commitId,
                contents = contents,
                path = codeRangeReq.path,
                line = codeRangeReq.startLine,
                side = sideVal,
                currentUser = user
            )
            comment.id
        } else {
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
            comment.id
        }

        return "redirect:/$owner/$projectName/commit/$commitId#comment-$commentId"
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

        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val isSvn = project.vcs?.uppercase() == "SUBVERSION" || project.vcs?.uppercase() == "SVN"
        try {
            if (isSvn) {
                codeReviewService.deleteCommitComment(id, user)
            } else {
                codeReviewService.deleteReviewComment(id, user)
            }
        } catch (e: IllegalArgumentException) {
            if (e.message == "Permission denied") {
                return "error/403"
            }
            throw e
        }
        return "redirect:/$owner/$projectName/commit/$commitId"
    }
}
