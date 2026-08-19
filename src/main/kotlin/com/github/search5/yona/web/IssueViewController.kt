package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueCommentRepository
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.issue.IssueLabelRepository
import com.github.search5.yona.domain.milestone.MilestoneService
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.project.ProjectService
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.watch.WatchService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import com.github.search5.yona.domain.user.FavoriteIssueRepository
import com.github.search5.yona.domain.attachment.AttachmentRepository
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import com.github.search5.yona.domain.project.RecentProjectRepository
import com.github.search5.yona.domain.user.User
import java.time.Instant
import java.time.ZoneId
 
import com.github.search5.yona.domain.vcs.RepositoryService
import com.github.search5.yona.domain.issue.IssueSpecification
import com.github.search5.yona.domain.issue.IssueService
import com.github.search5.yona.config.TemplateHelper
import com.github.search5.yona.domain.issue.IssueExcelService
import com.github.search5.yona.domain.issue.RecentIssueService

@Controller
class IssueViewController(
    private val projectRepository: ProjectRepository,
    private val projectService: ProjectService,
    private val issueRepository: IssueRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val watchService: WatchService,
    private val milestoneService: MilestoneService,
    private val issueLabelRepository: IssueLabelRepository,
    private val favoriteIssueRepository: FavoriteIssueRepository,
    private val attachmentRepository: AttachmentRepository,
    private val messageSource: MessageSource,
    private val recentProjectRepository: RecentProjectRepository,
    private val issueService: IssueService,
    private val templateHelper: TemplateHelper,
    private val issueExcelService: IssueExcelService,
    private val repositoryService: RepositoryService,
    private val recentIssueService: RecentIssueService
) {

    @GetMapping("/{owner}/{projectName}/issues")
    fun listIssues(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false, defaultValue = "OPEN") state: State,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false) pageNum: Int?,
        @RequestParam(required = false) filter: String?,
        @RequestParam(required = false) authorId: Long?,
        @RequestParam(required = false) assigneeId: Long?,
        @RequestParam(required = false) milestoneId: Long?,
        @RequestParam(required = false) commenterId: Long?,
        @RequestParam(required = false) labelIds: List<Long>?,
        @RequestParam(required = false) dueDate: String?,
        @RequestParam(required = false) format: String?,
        @RequestParam(required = false, defaultValue = "createdDate") orderBy: String,
        @RequestParam(required = false, defaultValue = "desc") orderDir: String,
        authentication: Authentication?,
        model: Model
    ): Any {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: run {
                model.addAttribute("messageKey", "error.forbidden.or.notfound")
                return "error/404"
            }

        // 권한 체크
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (!AccessControl.isAllowedToReadProject(loginUser, project)) {
            model.addAttribute("messageKey", "error.forbidden.or.notfound")
            return "error/403"
        }

        val actualPage = if (pageNum != null) {
            if (pageNum > 0) pageNum - 1 else 0
        } else {
            page
        }

        val sort = if (orderDir.equals("asc", ignoreCase = true)) {
            Sort.by(Sort.Direction.ASC, orderBy)
        } else {
            Sort.by(Sort.Direction.DESC, orderBy)
        }
        val pageable = PageRequest.of(actualPage, 20, sort)

        // Specification 생성 및 필터 적용
        val spec = IssueSpecification.filterIssues(
            project = project,
            state = state,
            filter = filter,
            authorId = authorId,
            assigneeId = assigneeId,
            milestoneId = milestoneId,
            commenterId = commenterId,
            labelIds = labelIds,
            dueDate = dueDate
        )

        if (format == "xls") {
            val allIssues = issueRepository.findAll(spec)
            val excelData = issueExcelService.excelFrom(allIssues)

            val zoneId = ZoneId.systemDefault()
            val dateStr = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmm")
                .withZone(zoneId)
                .format(Instant.now())
            val filename = "${project.name}_issues_${dateStr}.xls"
            val encodedFilename = java.net.URLEncoder.encode(filename, "UTF-8").replace("+", "%20")

            return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "application/vnd.ms-excel")
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''$encodedFilename")
                .body(excelData)
        }

        val issuePage = issueRepository.findAll(spec, pageable)

        val openIssuesCount = issueRepository.countByProjectAndState(project, State.OPEN)
        val closedIssuesCount = issueRepository.countByProjectAndState(project, State.CLOSED)

        // 퀵링크 카운트 조회
        var assignedToMeCount = 0L
        var authoredByMeCount = 0L
        var commentedByMeCount = 0L

        if (loginUser != null) {
            assignedToMeCount = issueRepository.count(IssueSpecification.filterIssues(
                project = project,
                state = state,
                filter = null,
                authorId = null,
                assigneeId = loginUser.id,
                milestoneId = null,
                commenterId = null,
                labelIds = null,
                dueDate = null
            ))
            authoredByMeCount = issueRepository.count(IssueSpecification.filterIssues(
                project = project,
                state = state,
                filter = null,
                authorId = loginUser.id,
                assigneeId = null,
                milestoneId = null,
                commenterId = null,
                labelIds = null,
                dueDate = null
            ))
            commentedByMeCount = issueRepository.count(IssueSpecification.filterIssues(
                project = project,
                state = state,
                filter = null,
                authorId = null,
                assigneeId = null,
                milestoneId = null,
                commenterId = loginUser.id,
                labelIds = null,
                dueDate = null
            ))
        }

        // 마일스톤 및 멤버 목록 (어드밴스드 검색 폼용)
        val milestones = milestoneService.getMilestones(project.id!!, State.OPEN)
        val projectUsers = projectUserRepository.findByProjectId(project.id!!)
        val members = projectUsers.map { it.user }
        val labels = issueLabelRepository.findByProject(project)

        model.addAttribute("project", project)
        model.addAttribute("issuePage", issuePage)
        model.addAttribute("state", state)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("filter", filter)
        model.addAttribute("orderBy", orderBy)
        model.addAttribute("orderDir", orderDir)
        model.addAttribute("openIssuesCount", openIssuesCount)
        model.addAttribute("closedIssuesCount", closedIssuesCount)

        model.addAttribute("authorId", authorId)
        model.addAttribute("assigneeId", assigneeId)
        model.addAttribute("milestoneId", milestoneId)
        model.addAttribute("commenterId", commenterId)
        model.addAttribute("labelIds", labelIds)
        model.addAttribute("dueDate", dueDate)

        model.addAttribute("assignedToMeCount", assignedToMeCount)
        model.addAttribute("authoredByMeCount", authoredByMeCount)
        model.addAttribute("commentedByMeCount", commentedByMeCount)

        model.addAttribute("milestones", milestones)
        model.addAttribute("members", members)
        model.addAttribute("labels", labels)
        model.addAttribute("templateHelper", templateHelper)

        return "issue/list"
    }

    @GetMapping("/{owner}/{projectName}/issue/{number}")
    fun viewIssue(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: run {
                model.addAttribute("messageKey", "error.forbidden.or.notfound")
                return "error/404"
            }

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (!AccessControl.isAllowedToReadProject(loginUser, project)) {
            model.addAttribute("messageKey", "error.forbidden.or.notfound")
            return "error/403"
        }

        val issue = issueRepository.findByProjectAndNumber(project, number) ?: return "error/404"
        val comments = issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(issue.id!!)

        if (loginUser != null) {
            try {
                recentIssueService.recordIssueVisit(loginUser, issue)
            } catch (e: Exception) {
                // NOOP: 방문 이력 기록 실패가 이슈 조회 자체를 막지 않아야 한다
            }
        }

        val isWatching = loginUser?.let {
            watchService.isWatching(it, ResourceType.ISSUE_POST, issue.id.toString())
        } ?: false

        val isWatchingProject = loginUser?.let {
            watchService.isWatching(it, ResourceType.PROJECT, project.id.toString())
        } ?: false

        val isFavoriteIssue = loginUser?.let {
            favoriteIssueRepository.findByUserIdAndIssueId(it.id!!, issue.id!!).isPresent
        } ?: false

        val isAllowedUpdate = AccessControl.isAllowedToUpdateIssue(loginUser, project, issue.authorLoginId)

        val attachments = attachmentRepository.findByContainerTypeAndContainerId(ResourceType.ISSUE_POST, issue.id.toString())
        val attachmentsJson = attachments.joinToString(prefix = "{\"attachments\":[", postfix = "]}", separator = ",") { attach ->
            val id = attach.id?.toString() ?: ""
            val mimeType = attach.mimeType ?: ""
            val name = attach.name.replace("\"", "\\\"").replace("\n", "\\n")
            val url = "/files/${attach.id}"
            val size = attach.size?.toString() ?: "0"
            """{"id":"$id","mimeType":"$mimeType","name":"$name","url":"$url","size":$size}"""
        }

        model.addAttribute("project", project)
        model.addAttribute("issue", issue)
        model.addAttribute("comments", comments)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("isWatching", isWatching)
        model.addAttribute("isWatchingProject", isWatchingProject)
        model.addAttribute("isFavoriteIssue", isFavoriteIssue)
        model.addAttribute("isAllowedUpdate", isAllowedUpdate)
        model.addAttribute("attachmentsJson", attachmentsJson)

        return "issue/view"
    }

    @GetMapping("/{owner}/{projectName}/issueform")
    fun createIssueForm(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false) parentIssueId: Long?,
        @RequestParam(required = false, defaultValue = "false") isFromGlobalMenuNew: Boolean,
        @RequestParam(required = false) bodyText: String? = null,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: run {
                model.addAttribute("messageKey", "error.forbidden.or.notfound")
                return "error/404"
            }

        val currentAuth = authentication ?: org.springframework.security.core.context.SecurityContextHolder.getContext().authentication
        val loginUser = currentAuth?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (!AccessControl.isProjectResourceCreatable(loginUser, project, ResourceType.ISSUE_POST)) {
            model.addAttribute("messageKey", "error.forbidden.or.notfound")
            return "error/403"
        }

        // 마일스톤 목록 가져오기
        val milestones = milestoneService.getMilestones(project.id!!, State.OPEN)

        // 프로젝트의 멤버 목록 가져오기
        val projectUsers = projectUserRepository.findByProjectId(project.id!!)
        val members = projectUsers.map { it.user }

        // 라벨 목록 가져오기
        val labels = issueLabelRepository.findByProject(project)
        val labelMap = labels.groupBy { it.category }

        // 1. 하위 태스크용 프로젝트 목록
        val movableProjects = projectUserRepository.findByUserId(loginUser!!.id!!).map { it.project }

        // 2. 부모 이슈 후보군 (부모가 없으며 오픈 상태인 이슈들)
        val parentCandidates = issueRepository.findByProjectAndState(project, State.OPEN)
            .filter { it.parent == null }

        // 3. 기존의 부모 이슈 정보
        val parentIssue = parentIssueId?.let { id ->
            issueRepository.findById(id).orElse(null)
        }

        val issueTemplate = bodyText ?: getIssueTemplate(project)
        model.addAttribute("issueTemplate", issueTemplate)

        model.addAttribute("project", project)
        model.addAttribute("milestones", milestones)
        model.addAttribute("members", members)
        model.addAttribute("labels", labels)
        model.addAttribute("labelMap", labelMap)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("movableProjects", movableProjects)
        model.addAttribute("parentCandidates", parentCandidates)
        model.addAttribute("parentIssue", parentIssue)
        model.addAttribute("isFromGlobalMenuNew", isFromGlobalMenuNew)

        return "issue/create"
    }

    @GetMapping("/{owner}/{projectName}/issue/{number}/editform")
    fun editIssueForm(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: run {
                model.addAttribute("messageKey", "error.forbidden.or.notfound")
                return "error/404"
            }

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        val issue = issueRepository.findByProjectAndNumber(project, number) ?: return "error/404"
        if (!AccessControl.isAllowedToUpdateIssue(loginUser, project, issue.authorLoginId)) {
            model.addAttribute("messageKey", "error.forbidden.or.notfound")
            return "error/403"
        }

        // 마일스톤 목록 가져오기
        val milestones = milestoneService.getMilestones(project.id!!, State.OPEN)

        // 프로젝트의 멤버 목록 가져오기
        val projectUsers = projectUserRepository.findByProjectId(project.id!!)
        val members = projectUsers.map { it.user }

        // 라벨 목록 가져오기
        val labels = issueLabelRepository.findByProject(project)
        val labelMap = labels.groupBy { it.category }

        // 1. 하위 태스크용 프로젝트 목록
        val movableProjects = projectUserRepository.findByUserId(loginUser!!.id!!).map { it.project }

        // 2. 부모 이슈 후보군 (자기 자신 제외 및 부모가 없는 오픈 상태인 이슈들)
        val parentCandidates = issueRepository.findByProjectAndState(project, State.OPEN)
            .filter { it.parent == null && it.id != issue.id }

        val attachments = attachmentRepository.findByContainerTypeAndContainerId(ResourceType.ISSUE_POST, issue.id.toString())
        val attachmentsJson = attachments.joinToString(prefix = "{\"attachments\":[", postfix = "]}", separator = ",") { attach ->
            val id = attach.id?.toString() ?: ""
            val mimeType = attach.mimeType ?: ""
            val name = attach.name.replace("\"", "\\\"").replace("\n", "\\n")
            val url = "/files/${attach.id}"
            val size = attach.size?.toString() ?: "0"
            """{"id":"$id","mimeType":"$mimeType","name":"$name","url":"$url","size":$size}"""
        }

        model.addAttribute("project", project)
        model.addAttribute("issue", issue)
        model.addAttribute("milestones", milestones)
        model.addAttribute("members", members)
        model.addAttribute("labels", labels)
        model.addAttribute("labelMap", labelMap)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("movableProjects", movableProjects)
        model.addAttribute("parentCandidates", parentCandidates)
        model.addAttribute("parentIssue", issue.parent)
        model.addAttribute("attachmentsJson", attachmentsJson)

        return "issue/edit"
    }

    @org.springframework.web.bind.annotation.PostMapping("/{owner}/{projectName}/issues")
    fun createIssue(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam title: String,
        @RequestParam body: String,
        @RequestParam(required = false) parentIssueId: Long?,
        @RequestParam(required = false) targetProjectId: Long?,
        @RequestParam(required = false) assigneeLoginId: String?,
        @RequestParam(required = false) milestoneId: Long?,
        @RequestParam(required = false) dueDate: String?,
        @RequestParam(required = false) labelIds: List<Long>?,
        @RequestParam(required = false, defaultValue = "false") isDraft: Boolean,
        @RequestParam(required = false) temporaryUploadFiles: String?,
        authentication: Authentication?
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "redirect:/users/loginform"

        if (!AccessControl.isProjectResourceCreatable(loginUser, project, ResourceType.ISSUE_POST)) {
            return "error/403"
        }

        val issue = Issue(
            title = title,
            body = body,
            project = project
        )
        issue.isDraft = isDraft
        issue.state = if (isDraft) State.DRAFT else State.OPEN

        if (parentIssueId != null) {
            val parentIssue = issueRepository.findById(parentIssueId).orElse(null)
            issue.parent = parentIssue
        }

        if (!dueDate.isNullOrEmpty()) {
            try {
                val localDate = java.time.LocalDate.parse(dueDate)
                val zone = java.time.ZoneId.systemDefault()
                val instant = localDate.atTime(23, 59, 59).atZone(zone).toInstant()
                issue.dueDate = instant
            } catch (e: Exception) {}
        }

        val assigneeUser = assigneeLoginId?.let { userRepository.findByLoginId(it).orElse(null) }

        val saved = issueService.createIssue(
            issue = issue,
            author = loginUser,
            assigneeUser = assigneeUser,
            milestoneId = milestoneId,
            labelIds = labelIds
        )

        if (!temporaryUploadFiles.isNullOrBlank()) {
            val fileIds = temporaryUploadFiles.split(",").mapNotNull { it.trim().toLongOrNull() }
            fileIds.forEach { fileId ->
                attachmentRepository.findById(fileId).ifPresent { attachment ->
                    attachment.containerType = ResourceType.ISSUE_POST
                    attachment.containerId = saved.id.toString()
                    attachmentRepository.save(attachment)
                }
            }
        }

        return "redirect:/$owner/$projectName/issue/${saved.number}"
    }

    @GetMapping("/user/issues/new")
    fun newDirectIssueForm(
        @RequestParam(required = false, defaultValue = "-1") commentId: Long,
        authentication: Authentication?,
        model: org.springframework.ui.Model
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "redirect:/users/loginform"

        val recentList = recentProjectRepository.findByUserIdOrderByVisitedDateDesc(loginUser.id!!)
        var project = recentList.firstOrNull()?.let {
            projectRepository.findById(it.projectId).orElse(null)
        }

        if (project == null) {
            val projectUsers = projectUserRepository.findByUserId(loginUser.id!!)
            val allUserProjects = projectUsers.map { it.project }
            project = allUserProjects.sortedByDescending { it.createdDate }.firstOrNull()
        }

        var bodyText: String? = null
        if (project != null && commentId != -1L) {
            val comment = issueCommentRepository.findById(commentId).orElse(null)
            if (comment != null) {
                bodyText = comment.contents + "\n\n_Originally posted by @" + comment.authorLoginId + " in " +
                        "/${project.owner}/${project.name}/issue/${comment.issue.number}#comment-${comment.id}_"
            }
        }

        return if (project != null) {
            createIssueForm(
                owner = project.owner!!,
                projectName = project.name!!,
                parentIssueId = null,
                isFromGlobalMenuNew = true,
                bodyText = bodyText,
                authentication = authentication,
                model = model
            )
        } else {
            val locale = LocaleContextHolder.getLocale()
            val msg = messageSource.getMessage("project.is.empty", null, "프로젝트가 존재하지 않습니다.", locale)
            model.addAttribute("warning", msg)
            "redirect:/"
        }
    }

    @GetMapping("/user/issues/new/mine")
    fun newDirectMyIssueForm(
        authentication: Authentication?,
        model: org.springframework.ui.Model
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "redirect:/users/loginform"

        var project = projectRepository.findByOwnerAndName(loginUser.loginId!!, "inbox").orElse(null)
        if (project == null) {
            project = projectRepository.findByOwnerAndName(loginUser.loginId!!, "_private").orElse(null)
        }

        if (project == null) {
            val myProjects = projectRepository.findByOwner(loginUser.loginId!!)
            val privateProjects = myProjects.filter { it.projectScope == ProjectScope.PRIVATE }
            project = privateProjects.sortedByDescending { it.createdDate }.firstOrNull()
        }

        if (project == null) {
            val myProjects = projectRepository.findByOwner(loginUser.loginId!!)
            val publicProjects = myProjects.filter { it.projectScope == ProjectScope.PUBLIC }
            project = publicProjects.sortedByDescending { it.createdDate }.firstOrNull()
        }

        return if (project != null) {
            createIssueForm(
                owner = project.owner!!,
                projectName = project.name!!,
                parentIssueId = null,
                isFromGlobalMenuNew = true,
                authentication = authentication,
                model = model
            )
        } else {
            "redirect:/"
        }
    }

    @org.springframework.web.bind.annotation.PostMapping("/{owner}/{projectName}/issues/massupdate")
    @org.springframework.transaction.annotation.Transactional
    fun massUpdate(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @org.springframework.web.bind.annotation.ModelAttribute form: IssueMassUpdateForm,
        authentication: Authentication?,
        @RequestParam(required = false, defaultValue = "false") delete: Boolean,
        @RequestParam(required = false, defaultValue = "false") isDueDateChanged: Boolean,
        @RequestParam(required = false) dueDate: String?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "redirect:/error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (loginUser == null || !loginUser.isMemberOf(project)) {
            return "redirect:/error/403"
        }

        val issueIds = form.issues.mapNotNull { it.id }
        if (issueIds.isNotEmpty()) {
            val issuesToUpdate = issueRepository.findAllById(issueIds)
            for (issue in issuesToUpdate) {
                if (issue.project.id != project.id) continue

                // 1. 삭제
                if (delete) {
                    if (AccessControl.isAllowedToUpdateIssue(loginUser, project, issue.authorLoginId)) {
                        issueRepository.delete(issue)
                    }
                    continue
                }

                // 2. 상태 변경
                if (!form.state.isNullOrEmpty()) {
                    try {
                        val newState = State.valueOf(form.state!!.uppercase())
                        issueService.changeState(issue.id!!, newState, loginUser.loginId)
                    } catch (e: Exception) {}
                }

                // 3. 담당자 변경
                if (form.assignee != null) {
                    val assigneeUserId = form.assignee?.id
                    if (assigneeUserId == null || assigneeUserId == -1L) {
                        issueService.changeAssignee(issue.id!!, null, loginUser.loginId)
                    } else {
                        val assigneeUser = userRepository.findById(assigneeUserId).orElse(null)
                        if (assigneeUser != null) {
                            issueService.changeAssignee(issue.id!!, assigneeUser, loginUser.loginId)
                        }
                    }
                }

                // 4. 마일스톤 변경
                if (form.milestone != null) {
                    val milestoneId = form.milestone?.id
                    if (milestoneId == null || milestoneId == -1L) {
                        issueService.changeMilestone(issue.id!!, null, loginUser.loginId)
                    } else {
                        issueService.changeMilestone(issue.id!!, milestoneId, loginUser.loginId)
                    }
                }

                // 5. 라벨 추가/삭제
                var labelsChanged = false
                if (form.attachingLabelIds.isNotEmpty()) {
                    val labelsToAdd = issueLabelRepository.findAllById(form.attachingLabelIds)
                    issue.labels.addAll(labelsToAdd)
                    labelsChanged = true
                }
                if (form.detachingLabelIds.isNotEmpty()) {
                    val labelsToRemove = issueLabelRepository.findAllById(form.detachingLabelIds)
                    issue.labels.removeAll(labelsToRemove.toSet())
                    labelsChanged = true
                }

                // 6. 마감일 변경
                if (isDueDateChanged) {
                    if (dueDate.isNullOrEmpty()) {
                        issue.dueDate = null
                    } else {
                        try {
                            val localDate = java.time.LocalDate.parse(dueDate)
                            val zone = java.time.ZoneId.systemDefault()
                            val instant = localDate.atTime(23, 59, 59).atZone(zone).toInstant()
                            issue.dueDate = instant
                        } catch (e: Exception) {}
                    }
                    labelsChanged = true // 혹은 마감일 저장용 트리거
                }

                if (labelsChanged) {
                    issue.updatedDate = Instant.now()
                    issueRepository.save(issue)
                }
            }
        }

        return "redirect:/$owner/$projectName/issues"
    }

    private fun getIssueTemplate(project: com.github.search5.yona.domain.project.Project): String {
        return try {
            val bytes = repositoryService.getRepository(project).getRawFile("HEAD", "ISSUE_TEMPLATE.md")
            if (bytes != null) String(bytes, java.nio.charset.StandardCharsets.UTF_8) else ""
        } catch (e: Exception) {
            ""
        }
    }

    @org.springframework.web.bind.annotation.PostMapping(value = ["/{owner}/{projectName}/issue/{number}/editform", "/{owner}/{projectName}/issue/{number}/edit"])
    fun editIssue(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        @org.springframework.web.bind.annotation.ModelAttribute request: IssueForm,
        authentication: Authentication?
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        val issue = issueRepository.findByProjectAndNumber(project, number)
            ?: return "error/404"

        if (issue.authorLoginId != loginUser.loginId && !projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!)) {
            return "error/403"
        }

        val assigneeUser = request.assigneeLoginId?.let { 
            if (it.isNotBlank()) userRepository.findByLoginId(it).orElse(null) else null 
        }

        issue.title = request.title
        issue.body = request.body ?: ""
        
        if (!request.dueDate.isNullOrBlank()) {
            try {
                val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                val localDate = java.time.LocalDate.parse(request.dueDate, formatter)
                issue.dueDate = java.time.ZonedDateTime.of(localDate, java.time.LocalTime.MIDNIGHT, java.time.ZoneId.systemDefault()).toInstant()
            } catch (e: Exception) {
                // ignore
            }
        } else {
            issue.dueDate = null
        }

        val parentIssue = request.parentIssueId?.let { issueRepository.findById(it).orElse(null) }
        issue.parent = parentIssue

        issueService.updateIssue(
            issueId = issue.id!!,
            title = request.title,
            body = request.body ?: "",
            updater = loginUser,
            assigneeUser = assigneeUser,
            milestoneId = request.milestoneId,
            labelIds = request.labelIds
        )

        return "redirect:/$owner/$projectName/issue/$number"
    }
}

class IssueMassUpdateForm {
    var issues: List<IssueIdForm> = mutableListOf()
    var state: String? = null
    var assignee: AssigneeIdForm? = null
    var milestone: MilestoneIdForm? = null
    var attachingLabelIds: List<Long> = mutableListOf()
    var detachingLabelIds: List<Long> = mutableListOf()
}

class IssueIdForm {
    var id: Long? = null
}

class AssigneeIdForm {
    var id: Long? = null
}

class MilestoneIdForm {
    var id: Long? = null
}

data class IssueForm(
    var title: String = "",
    var body: String? = "",
    var assigneeLoginId: String? = null,
    var milestoneId: Long? = null,
    var dueDate: String? = null,
    var labelIds: List<Long>? = null,
    var parentIssueId: Long? = null
)