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
    private val issueEventRepository: IssueEventRepository,
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

    // yona AccessControl.java:250-259,274-279 대응 (P1-82). 이슈 단건 READ는 프로젝트 수준
    // 권한(checkReadPermission)에 더해, 프로젝트 멤버가 아니어도 IssueSharer로 공유받은
    // 사용자에게 READ를 허용한다.
    private fun checkReadPermission(project: Project, issue: Issue, user: User?): Boolean {
        if (checkReadPermission(project, user)) return true
        if (user == null) return false
        return accessControl.isAllowedIfSharer(issue, user)
    }

    private fun checkWritePermission(project: Project, user: User?): Boolean {
        if (user == null) return false
        return projectUserRepository.existsByProjectIdAndUserId(project.id!!, user.id!!) ||
            accessControl.isAllowedIfGroupMember(project, user)
    }

    // yona AccessControl.java:244-248의 "user.isManagerOf(project) || isAllowedIfAuthor(user, resource)
    // || isAllowedIfAssignee(user, resource)" 대응 (P2-12). 담당자(assignee)는 operation과 무관하게
    // author와 동급 쓰기 권한을 갖는다 — 프로젝트 멤버 여부와도 무관하다(:398-406 isAllowedIfAssignee()).
    private fun isManagerOrAuthorOrAssignee(project: Project, issue: Issue, user: User?): Boolean {
        if (user == null) return false
        if (issue.authorId == user.id) return true
        if (issue.assignee?.user?.id == user.id) return true
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
        if (!checkReadPermission(project, issue, user)) {
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
        if (!checkReadPermission(project, issue, user)) {
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
            labelIds = request.labelIds,
            isDraft = request.isDraft
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
        if (!isManagerOrAuthorOrAssignee(project, issue, user)) {
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

    // yona IssueApp.editIssue()의 hasTargetProject() 분기 대응 (P1-48). yona는 이 권한 확인을
    // editPosting() 안에서(즉 실제 이동이 이미 일어난 뒤에) 하지만, yuna는 이동을 호출하기 전에
    // 원본 이슈 수정권한 + 대상 프로젝트 생성권한을 모두 먼저 확인한다(관찰 가능한 정상 동작은
    // legacy와 동일하되, legacy의 "권한 없어도 이동은 일부 반영되는" 인가 우회 허점은 들여오지 않는다).
    @PostMapping("/{number}/move")
    fun moveIssue(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @RequestBody request: MoveIssueRequest,
        authentication: Authentication?
    ): ResponseEntity<Issue> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!isManagerOrAuthorOrAssignee(project, issue, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val targetProject = projectRepository.findById(request.targetProjectId).orElse(null)
            ?: return ResponseEntity.badRequest().build()

        if (!accessControl.isProjectResourceCreatable(user, targetProject, ResourceType.ISSUE_POST)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val moved = issueService.moveIssue(issue.id!!, request.targetProjectId, user)

        return ResponseEntity.ok(moved)
    }

    // yona IssueApp.editIssue()의 "if (issue.isPublish) { ... }" 발행 전환 대응 (P1-65).
    @PostMapping("/{number}/publish")
    fun publishIssue(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Issue> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!isManagerOrAuthorOrAssignee(project, issue, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val published = issueService.publishIssue(issue.id!!, user)

        return ResponseEntity.ok(published)
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
        if (!isManagerOrAuthorOrAssignee(project, issue, user)) {
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

        if (!isManagerOrAuthorOrAssignee(project, issue, user)) {
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
        val labelIds: List<Long>?,
        // yona AbstractPosting.isPublish 대응 (P1-65). true면 초안(DRAFT)으로 생성한다.
        val isDraft: Boolean = false
    )

    data class UpdateIssueRequest(
        val title: String,
        val body: String,
        val milestoneId: Long?,
        val assigneeId: Long?,
        val labelIds: List<Long>?
    )

    data class MoveIssueRequest(
        val targetProjectId: Long
    )
}
