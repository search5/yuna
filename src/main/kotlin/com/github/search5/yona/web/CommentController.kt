package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.comment.CommentService
import com.github.search5.yona.domain.board.Posting
import com.github.search5.yona.domain.board.PostingComment
import com.github.search5.yona.domain.board.PostingCommentRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueComment
import com.github.search5.yona.domain.issue.IssueCommentRepository
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
class CommentController(
    private val commentService: CommentService,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val issueRepository: IssueRepository,
    private val postingRepository: PostingRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val postingCommentRepository: PostingCommentRepository,
    private val accessControl: AccessControl
) {

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    private fun checkReadPermission(project: Project, user: User?): Boolean {
        if (project.projectScope == ProjectScope.PUBLIC) return true
        if (user == null) return false
        return projectUserRepository.existsByProjectIdAndUserId(project.id!!, user.id!!) ||
            accessControl.isAllowedIfGroupMember(project, user)
    }

    // 이슈 댓글 생성
    @PostMapping("/api/projects/{projectId}/issues/{number}/comments")
    fun createIssueComment(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @RequestBody request: CommentRequest,
        authentication: Authentication?
    ): ResponseEntity<IssueComment> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        val savedComment = commentService.createIssueComment(issue.id!!, request.contents, user)
        return ResponseEntity.status(HttpStatus.CREATED).body(savedComment)
    }

    // 이슈 댓글 수정
    @PutMapping("/api/projects/{projectId}/issues/{number}/comments/{commentId}")
    fun updateIssueComment(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @PathVariable commentId: Long,
        @RequestBody request: CommentRequest,
        authentication: Authentication?
    ): ResponseEntity<IssueComment> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val comment = issueCommentRepository.findById(commentId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val isManager = projectUserRepository.findByProjectIdAndUserId(project.id!!, user.id!!)
            .map { it.role.id == com.github.search5.yona.domain.role.RoleType.MANAGER.roleType }
            .orElse(false)

        if (comment.authorId != user.id && !isManager) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val updated = commentService.updateIssueComment(commentId, request.contents, user)
        return ResponseEntity.ok(updated)
    }

    // 이슈 댓글 삭제
    @DeleteMapping("/api/projects/{projectId}/issues/{number}/comments/{commentId}")
    fun deleteIssueComment(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @PathVariable commentId: Long,
        authentication: Authentication?
    ): ResponseEntity<Unit> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val comment = issueCommentRepository.findById(commentId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val isManager = projectUserRepository.findByProjectIdAndUserId(project.id!!, user.id!!)
            .map { it.role.id == com.github.search5.yona.domain.role.RoleType.MANAGER.roleType }
            .orElse(false)

        if (comment.authorId != user.id && !isManager) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        commentService.deleteIssueComment(commentId, user)
        return ResponseEntity.ok().build()
    }

    // 게시판 댓글 생성
    @PostMapping("/api/projects/{projectId}/posts/{number}/comments")
    fun createPostingComment(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @RequestBody request: CommentRequest,
        authentication: Authentication?
    ): ResponseEntity<PostingComment> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val posting = postingRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        val savedComment = commentService.createPostingComment(posting.id!!, request.contents, user)
        return ResponseEntity.status(HttpStatus.CREATED).body(savedComment)
    }

    // 게시판 댓글 수정
    @PutMapping("/api/projects/{projectId}/posts/{number}/comments/{commentId}")
    fun updatePostingComment(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @PathVariable commentId: Long,
        @RequestBody request: CommentRequest,
        authentication: Authentication?
    ): ResponseEntity<PostingComment> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val comment = postingCommentRepository.findById(commentId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val isManager = projectUserRepository.findByProjectIdAndUserId(project.id!!, user.id!!)
            .map { it.role.id == com.github.search5.yona.domain.role.RoleType.MANAGER.roleType }
            .orElse(false)

        if (comment.authorId != user.id && !isManager) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val updated = commentService.updatePostingComment(commentId, request.contents, user)
        return ResponseEntity.ok(updated)
    }

    // 게시판 댓글 삭제
    @DeleteMapping("/api/projects/{projectId}/posts/{number}/comments/{commentId}")
    fun deletePostingComment(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @PathVariable commentId: Long,
        authentication: Authentication?
    ): ResponseEntity<Unit> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val comment = postingCommentRepository.findById(commentId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val isManager = projectUserRepository.findByProjectIdAndUserId(project.id!!, user.id!!)
            .map { it.role.id == com.github.search5.yona.domain.role.RoleType.MANAGER.roleType }
            .orElse(false)

        if (comment.authorId != user.id && !isManager) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        commentService.deletePostingComment(commentId, user)
        return ResponseEntity.ok().build()
    }

    data class CommentRequest(
        val contents: String = ""
    )
}
