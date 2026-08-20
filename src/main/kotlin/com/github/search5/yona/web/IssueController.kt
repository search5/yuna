package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.issue.Assignee
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.issue.IssueService
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import com.github.search5.yona.domain.attachment.AttachmentService
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.issue.IssueCommentRepository
import com.github.search5.yona.domain.issue.IssueEvent
import com.github.search5.yona.domain.issue.IssueEventRepository

@RestController
@RequestMapping("/api/projects/{projectId}/issues")
class IssueController(
    private val issueService: IssueService,
    private val issueRepository: IssueRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val attachmentService: AttachmentService,
    private val issueCommentRepository: IssueCommentRepository,
    private val issueEventRepository: IssueEventRepository
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

    private fun checkWritePermission(project: Project, user: User?): Boolean {
        if (user == null) return false
        return projectUserRepository.existsByProjectIdAndUserId(project.id!!, user.id!!) ||
            AccessControl.isAllowedIfGroupMember(project, user)
    }

    private fun isManagerOrAuthor(project: Project, authorId: Long?, user: User?): Boolean {
        if (user == null) return false
        if (authorId == user.id) return true
        return projectUserRepository.findByProjectIdAndUserId(project.id!!, user.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)
    }

    @GetMapping
    fun getIssues(
        @PathVariable projectId: Long,
        @RequestParam(required = false) state: State?,
        @PageableDefault(size = 25) pageable: Pageable,
        authentication: Authentication?
    ): ResponseEntity<Page<Issue>> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val page = if (state != null) {
            issueRepository.findByProjectAndState(project, state, pageable)
        } else {
            issueRepository.findByProject(project, pageable)
        }
        return ResponseEntity.ok(page)
    }

    @GetMapping("/{number}")
    fun getIssue(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Issue> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return ResponseEntity.ok(issue)
    }

    // yona Issue.getTimeline() / conf/routes "issue/$number/timeline" 대응 (P1-07)
    @GetMapping("/{number}/timeline")
    fun getTimeline(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<List<IssueEvent>> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return ResponseEntity.ok(issueEventRepository.findByIssueOrderByCreatedAsc(issue))
    }

    @PostMapping
    fun createIssue(
        @PathVariable projectId: Long,
        @RequestBody request: CreateIssueRequest,
        authentication: Authentication?
    ): ResponseEntity<Issue> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkWritePermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val assigneeUser = request.assigneeId?.let { userRepository.findById(it).orElse(null) }

        val issue = Issue(
            title = request.title,
            body = request.body ?: "",
            project = project
        )

        val saved = issueService.createIssue(
            issue = issue,
            author = user,
            assigneeUser = assigneeUser,
            milestoneId = request.milestoneId,
            labelIds = request.labelIds
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(saved)
    }

    @PutMapping("/{number}")
    fun updateIssue(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @RequestBody request: UpdateIssueRequest,
        authentication: Authentication?
    ): ResponseEntity<Issue> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!isManagerOrAuthor(project, issue.authorId, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val assigneeUser = request.assigneeId?.let { userRepository.findById(it).orElse(null) }

        val updated = issueService.updateIssue(
            issueId = issue.id!!,
            title = request.title,
            body = request.body,
            updater = user,
            assigneeUser = assigneeUser,
            milestoneId = request.milestoneId,
            labelIds = request.labelIds
        )

        return ResponseEntity.ok(updated)
    }

    @DeleteMapping("/{number}")
    fun deleteIssue(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!isManagerOrAuthor(project, issue.authorId, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        // 연관된 댓글의 첨부파일도 일괄 삭제
        val comments = issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(issue.id!!)
        for (comment in comments) {
            attachmentService.deleteAll(ResourceType.ISSUE_COMMENT, comment.id.toString())
        }

        attachmentService.deleteAll(ResourceType.ISSUE_POST, issue.id.toString())
        issueRepository.delete(issue)
        return ResponseEntity.ok(mapOf("status" to "success"))
    }

    @PostMapping("/{number}/state")
    fun changeState(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @RequestParam state: State,
        authentication: Authentication?
    ): ResponseEntity<Issue> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        
        val isAssignee = issue.assignee?.user?.id == user.id
        if (!isManagerOrAuthor(project, issue.authorId, user) && !isAssignee) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val updated = issueService.changeState(issue.id!!, state, user.loginId)
        return ResponseEntity.ok(updated)
    }

    data class CreateIssueRequest(
        val title: String,
        val body: String?,
        val milestoneId: Long?,
        val assigneeId: Long?,
        val labelIds: List<Long>?
    )

    data class UpdateIssueRequest(
        val title: String,
        val body: String,
        val milestoneId: Long?,
        val assigneeId: Long?,
        val labelIds: List<Long>?
    )
}
