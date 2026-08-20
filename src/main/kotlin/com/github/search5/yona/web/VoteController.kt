package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.issue.IssueService
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.issue.IssueCommentRepository
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.server.ResponseStatusException

@Controller
class VoteController(
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val issueService: IssueService,
    private val issueRepository: IssueRepository,
    private val issueCommentRepository: IssueCommentRepository
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

    @PostMapping(value = ["/{owner}/{projectName}/issue/{issueNumber}/vote", "/{owner}/{projectName}/issues/{issueNumber}/vote"])
    fun vote(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable issueNumber: Long,
        authentication: Authentication?
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found")

        val user = getLoginUser(authentication)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required")

        if (!checkReadPermission(project, user)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
        }

        val issue = issueRepository.findByProjectAndNumber(project, issueNumber)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found")

        issueService.voteIssue(issue.id!!, user)

        return "redirect:/$owner/$projectName/issue/$issueNumber"
    }

    @PostMapping(value = ["/{owner}/{projectName}/issue/{issueNumber}/unvote", "/{owner}/{projectName}/issues/{issueNumber}/unvote"])
    fun unvote(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable issueNumber: Long,
        authentication: Authentication?
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found")

        val user = getLoginUser(authentication)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required")

        if (!checkReadPermission(project, user)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
        }

        val issue = issueRepository.findByProjectAndNumber(project, issueNumber)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found")

        issueService.unvoteIssue(issue.id!!, user)

        return "redirect:/$owner/$projectName/issue/$issueNumber"
    }

    @PostMapping(value = ["/{owner}/{projectName}/issue/{issueNumber}/comment/{commentId}/vote", "/{owner}/{projectName}/issues/{issueNumber}/comment/{commentId}/vote"])
    fun voteComment(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable issueNumber: Long,
        @PathVariable commentId: Long,
        authentication: Authentication?
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found")

        val user = getLoginUser(authentication)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required")

        if (!checkReadPermission(project, user)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
        }

        val comment = issueCommentRepository.findById(commentId).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found")

        issueService.voteComment(comment.id!!, user)

        return "redirect:/$owner/$projectName/issue/$issueNumber"
    }

    @PostMapping(value = ["/{owner}/{projectName}/issue/{issueNumber}/comment/{commentId}/unvote", "/{owner}/{projectName}/issues/{issueNumber}/comment/{commentId}/unvote"])
    fun unvoteComment(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable issueNumber: Long,
        @PathVariable commentId: Long,
        authentication: Authentication?
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found")

        val user = getLoginUser(authentication)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required")

        if (!checkReadPermission(project, user)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
        }

        val comment = issueCommentRepository.findById(commentId).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found")

        issueService.unvoteComment(comment.id!!, user)

        return "redirect:/$owner/$projectName/issue/$issueNumber"
    }
}
