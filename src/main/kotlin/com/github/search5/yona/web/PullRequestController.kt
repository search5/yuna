package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.enumeration.Operation
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.pullrequest.CodeReviewService
import com.github.search5.yona.domain.vcs.FileDiff
import com.github.search5.yona.domain.pullrequest.PullRequest
import com.github.search5.yona.domain.pullrequest.PullRequestEvent
import com.github.search5.yona.domain.pullrequest.PullRequestEventRepository
import com.github.search5.yona.domain.pullrequest.PullRequestMergeResult
import com.github.search5.yona.domain.pullrequest.PullRequestService
import com.github.search5.yona.domain.pullrequest.ReviewComment
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
    private val userRepository: UserRepository,
    private val pullRequestEventRepository: PullRequestEventRepository,
    private val accessControl: AccessControl,
    private val codeReviewService: CodeReviewService
) {

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    private fun checkReadPermission(project: Project, user: User?): Boolean {
        return accessControl.isAllowed(user, project, Operation.READ)
    }

    private fun checkWritePermission(project: Project, user: User?): Boolean {
        if (user == null) return false
        return projectUserRepository.existsByProjectIdAndUserId(project.id!!, user.id!!) ||
            accessControl.isAllowedIfGroupMember(project, user)
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
        // yona-wiki P3-02 4라운드(Step8.5 서버 보강) — `gh pr list --author` 대응.
        @RequestParam(required = false) author: String?,
        // yona-wiki P3-02 Step8.6 항목4(2026-09-01, 우선순위 4위) — `gh pr list --assignee/--label`
        // 대응. PullRequest에 assignee/labels 필드가 신설돼 이제 Issue와 동일하게 지원 가능하다.
        // PR 목록은 프로젝트당 크지 않아(기존 author 필터와 동일하게) 신규 리포지토리 쿼리 없이
        // 인메모리 필터링으로 처리한다.
        @RequestParam(required = false) assignee: String?,
        @RequestParam(required = false) label: String?,
        authentication: Authentication?
    ): ResponseEntity<List<PullRequest>> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pullRequests = pullRequestService.getPullRequests(projectId, state)
        val filtered = pullRequests
            .let { list -> if (author != null) list.filter { it.contributor.loginId == author } else list }
            .let { list -> if (assignee != null) list.filter { it.assignee?.user?.loginId == assignee } else list }
            .let { list -> if (label != null) list.filter { pr -> pr.labels.any { it.name == label } } else list }
        return ResponseEntity.ok(filtered)
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

    // yona models/PullRequestEvent.java 타임라인 조회 대응 (P1-08)
    @GetMapping("/{number}/timeline")
    fun getTimeline(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<List<PullRequestEvent>> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pullRequest))
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
        // yona PullRequestApp.java:254 @IsCreatable(ResourceType.FORK) 대응 (P1-141) — checkWritePermission
        // (멤버/그룹멤버 전용)만 쓰면 공개 프로젝트에서도 비멤버 로그인 사용자가 PR을 보낼 수 없어 yona보다
        // 과도하게 제한됨. AccessControl.isProjectResourceCreatable()이 이미 FORK를 공개 프로젝트
        // 비멤버 허용 타입에 포함하고 있어 그대로 재사용한다.
        if (!accessControl.isProjectResourceCreatable(user, project, ResourceType.FORK)) {
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

        if (!accessControl.isAllowed(user, project, pullRequest, Operation.UPDATE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val updated = pullRequestService.updatePullRequest(
            pullRequestId = pullRequest.id!!,
            title = request.title,
            body = request.body,
            fromBranch = request.fromBranch ?: pullRequest.fromBranch,
            toBranch = request.toBranch ?: pullRequest.toBranch
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

        val updated = pullRequestService.changeState(pullRequest.id!!, state, user.loginId)
        return ResponseEntity.ok(updated)
    }

    // yona-wiki P3-02 4라운드(Step8.5 서버 보강) — `gh pr diff` 대응. PullRequestViewController.
    // viewChangesInternal()이 화면 렌더링에 쓰는 것과 동일한 pullRequestService.getDiff()를
    // JSON으로 그대로 노출한다(신규 서비스 로직 없음).
    @GetMapping("/{number}/diff")
    fun getDiff(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<List<FileDiff>> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        val diffs = try {
            pullRequestService.getDiff(pullRequest)
        } catch (e: Exception) {
            emptyList()
        }
        return ResponseEntity.ok(diffs)
    }

    // yona-wiki P3-02 4라운드(Step8.5 서버 보강) — `gh pr comment` 대응. yona PR은 "PR 전체에 붙는
    // 일반 댓글"과 "코드 라인 단위 리뷰 댓글"을 CodeReviewService.createReviewComment() 한 메서드로
    // 함께 처리한다(commitId/codeRange가 둘 다 null이면 PR 전체에 붙는 NonRangedCodeCommentThread로
    // 귀결됨, CodeReviewServiceImpl 참고) - ReviewViewController.newPullRequestComment()와 같은
    // 서비스를 재사용하되, 그 메서드는 브라우저 폼 제출 전용(redirect 응답, CodeRangeRequest 등
    // 리뷰 UI 전용 파라미터 필요)이라 이 REST API는 JSON 요청/응답에 맞춰 새로 얇게 감싼다.
    @PostMapping("/{number}/comments")
    fun addComment(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @RequestBody request: PullRequestCommentRequest,
        authentication: Authentication?
    ): ResponseEntity<ReviewComment> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        // ReviewViewController.newPullRequestComment()의 IsCreatable(ResourceType.REVIEW_COMMENT)
        // 권한 체크와 동일하다(P0-24 대응 원본 그대로).
        if (!accessControl.isProjectResourceCreatable(user, project, ResourceType.REVIEW_COMMENT)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        val comment = codeReviewService.createReviewComment(
            project = project,
            pullRequest = pullRequest,
            commitId = null,
            contents = request.body,
            codeRange = null,
            threadId = null,
            currentUser = user
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(comment)
    }

    data class PullRequestCommentRequest(
        val body: String
    )

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

    // yona AccessControl.isProjectResourceAllowed()의 PULL_REQUEST Operation.ACCEPT 분기
    // (user.isMemberOf(project) || isAllowedIfGroupMember(project, user)) 대응 (P1-78). 리뷰어
    // 등록/해제는 이 ACCEPT 권한을 요구한다 - 프로젝트 멤버가 아니면 PUBLIC 프로젝트라도 불가.
    @PostMapping("/{number}/reviewers")
    fun addReviewer(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<Unit> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkWritePermission(project, user)) {
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
        if (!checkWritePermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        pullRequestService.removeReviewer(pullRequest.id!!, user)
        return ResponseEntity.ok().build()
    }


    // yona-wiki P3-02 Step8.6 항목4(2026-09-01, 우선순위 4위) — PR 담당자 지정/해제.
    // addReviewer/removeReviewer와 동일한 권한 체크(checkWritePermission) 패턴을 그대로 따른다.
    @PutMapping("/{number}/assignee")
    fun setAssignee(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @RequestBody request: SetAssigneeRequest,
        authentication: Authentication?
    ): ResponseEntity<PullRequest> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkWritePermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        val assigneeUser = userRepository.findById(request.userId).orElse(null)
            ?: return ResponseEntity.badRequest().build()

        val updated = pullRequestService.setAssignee(pullRequest.id!!, assigneeUser)
        return ResponseEntity.ok(updated)
    }

    @DeleteMapping("/{number}/assignee")
    fun removeAssignee(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        authentication: Authentication?
    ): ResponseEntity<PullRequest> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkWritePermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        val updated = pullRequestService.setAssignee(pullRequest.id!!, null)
        return ResponseEntity.ok(updated)
    }

    // yona-wiki P3-02 Step8.6 항목4(2026-09-01, 우선순위 4위) — PR 라벨 추가/제거. 라벨 정의 자체는
    // 만들지 않고 프로젝트에 이미 있는 IssueLabel(web/LabelRestApiController.kt로 CRUD)을 참조만 한다.
    @PostMapping("/{number}/labels")
    fun addLabel(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @RequestBody request: AddPullRequestLabelRequest,
        authentication: Authentication?
    ): ResponseEntity<PullRequest> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkWritePermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        return try {
            val updated = pullRequestService.addLabel(pullRequest.id!!, request.labelId)
            ResponseEntity.ok(updated)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    @DeleteMapping("/{number}/labels/{labelId}")
    fun removeLabel(
        @PathVariable projectId: Long,
        @PathVariable number: Long,
        @PathVariable labelId: Long,
        authentication: Authentication?
    ): ResponseEntity<PullRequest> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkWritePermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val pullRequest = pullRequestService.getPullRequest(projectId, number)
            ?: return ResponseEntity.notFound().build()

        val updated = pullRequestService.removeLabel(pullRequest.id!!, labelId)
        return ResponseEntity.ok(updated)
    }

    data class UpdatePullRequestRequest(
        val title: String,
        val body: String?,
        // yona PullRequest.updateWith() 대응 (P1-68). null이면 기존 브랜치를 유지한다.
        val fromBranch: String? = null,
        val toBranch: String? = null
    )

    // yona-wiki P3-02 Step8.6 항목4(2026-09-01, 우선순위 4위) — PR 담당자/라벨 CRUD 요청 DTO.
    data class SetAssigneeRequest(val userId: Long)

    data class AddPullRequestLabelRequest(val labelId: Long)
}
