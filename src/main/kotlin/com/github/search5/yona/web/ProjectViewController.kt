package com.github.search5.yona.web

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.enumeration.Operation
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectService
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.project.ProjectUser
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.vcs.RepositoryService
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.issue.IssueLabelService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Page
import com.github.search5.yona.domain.attachment.AttachmentRepository
import com.github.search5.yona.domain.attachment.AttachmentService
import com.github.search5.yona.domain.enumeration.ResourceType
import org.springframework.core.io.Resource
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.context.MessageSource
import java.util.Locale
import jakarta.servlet.http.HttpServletRequest
import com.github.search5.yona.domain.mail.MailService
import com.github.search5.yona.domain.support.MarkdownService
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.project.ProjectTransferRepository
import com.github.search5.yona.domain.role.RoleRepository
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.domain.milestone.MilestoneRepository
import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.watch.WatchService
import java.time.Instant
import com.github.search5.yona.domain.project.RecentProjectRepository
import com.github.search5.yona.domain.user.User

@Controller
class ProjectViewController(
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val repositoryService: RepositoryService,
    private val projectService: ProjectService,
    private val organizationUserRepository: OrganizationUserRepository,
    private val attachmentRepository: AttachmentRepository,
    private val attachmentService: AttachmentService,
    private val organizationRepository: OrganizationRepository,
    private val messageSource: MessageSource,
    private val mailService: MailService,
    private val markdownService: MarkdownService,
    private val roleRepository: RoleRepository,
    private val projectTransferRepository: ProjectTransferRepository,
    private val issueLabelService: IssueLabelService,
    private val issueRepository: IssueRepository,
    private val postingRepository: PostingRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val milestoneRepository: MilestoneRepository,
    private val watchService: WatchService,
    private val recentProjectRepository: RecentProjectRepository,
    private val accessControl: AccessControl
) {


    @GetMapping("/{owner:^(?!stylesheets|javascripts|images|bootstrap|assets|webjars)[a-zA-Z0-9_.-]+}/{projectName}")
    fun projectHome(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(value = "tabId", defaultValue = "readme") tabId: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
            return "error/403"
        }

        if (loginUser != null) {
            addVisitHistory(loginUser, project)
        }

        val projectUsers = projectUserRepository.findByProjectId(project.id!!)

        val histories = if (tabId != "readme" && tabId != "dashboard") {
            getProjectHistory(owner, project)
        } else {
            emptyList()
        }

        if (tabId == "dashboard") {
            getProjectDashboardData(project, model, projectUsers)
        }

        val readmeFileName = getReadmeFileName(project)
        val readmeHtml = if (tabId == "readme" && readmeFileName != null) {
            val content = getReadmeContent(project, readmeFileName)
            if (content != null) markdownService.render(content, true, project) else null
        } else {
            null
        }

        val isWatching = loginUser?.let {
            watchService.isWatching(it, ResourceType.PROJECT, project.id.toString())
        } ?: false
        val watcherCount = watchService.findWatchers(ResourceType.PROJECT, project.id.toString()).size

        model.addAttribute("project", project)
        model.addAttribute("projectUsers", projectUsers)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("tabId", tabId)
        model.addAttribute("histories", histories)
        model.addAttribute("readmeFileName", readmeFileName)
        model.addAttribute("readmeHtml", readmeHtml)
        model.addAttribute("isWatching", isWatching)
        model.addAttribute("watcherCount", watcherCount)

        return "project/home"
    }

    // P2-09에서 GitServletConfig와 공용으로 쓰도록 RecentProjectRepository.recordVisit()으로 승격
    private fun addVisitHistory(user: User, project: Project) {
        recentProjectRepository.recordVisit(user, project)
    }

    private fun getProjectHistory(ownerId: String, project: Project): List<HistoryDto> {
        val histories = mutableListOf<HistoryDto>()

        // 1. Commits
        if (project.isCodeEnabled) {
            try {
                val repository = repositoryService.getRepository(project)
                val commits = repository.getHistory(0, 10, null, null)
                for (commit in commits) {
                    val authorEmail = commit.getAuthorEmail()
                    val user = if (authorEmail != null) {
                        userRepository.findByEmail(authorEmail).orElse(null)
                    } else {
                        null
                    }
                    
                    val history = HistoryDto().apply {
                        this.who = user?.name ?: (commit.getAuthorName() ?: "Unknown")
                        this.userPageUrl = user?.let { "/user/${it.loginId}" } ?: "#"
                        this.userAvatarUrl = user?.avatarUrl ?: "/images/default-avatar-34.png"
                        this.whenInstant = commit.getCommitterDate()?.toInstant() ?: Instant.now()
                        this.where = project.name
                        this.what = "commit"
                        this.shortTitle = commit.getShortId()
                        this.how = commit.getShortMessage()
                        this.url = "/$ownerId/${project.name}/commit/${commit.getId()}"
                    }
                    histories.add(history)
                }
            } catch (e: Exception) {
                // NOOP
            }
        }

        // 2. Issues
        if (project.isIssueEnabled) {
            val pageable = PageRequest.of(0, 10, Sort.by("createdDate").descending())
            val issues = issueRepository.findByProject(project, pageable).content
            for (issue in issues) {
                val authorLoginId = issue.authorLoginId
                val author = if (authorLoginId != null) {
                    userRepository.findByLoginId(authorLoginId).orElse(null)
                } else {
                    null
                }
                val history = HistoryDto().apply {
                    this.who = issue.authorName ?: "Unknown"
                    this.userPageUrl = author?.let { "/user/${it.loginId}" } ?: "#"
                    this.userAvatarUrl = author?.avatarUrl ?: "/images/default-avatar-34.png"
                    this.whenInstant = issue.createdDate ?: Instant.now()
                    this.where = project.name
                    this.what = "issue"
                    this.shortTitle = "#${issue.number}"
                    this.how = issue.title
                    this.url = "/$ownerId/${project.name}/issue/${issue.number}"
                }
                histories.add(history)
            }
        }

        // 3. Postings
        if (project.isBoardEnabled) {
            val pageable = PageRequest.of(0, 10, Sort.by("createdDate").descending())
            val postings = postingRepository.findByProject(project, pageable).content
            for (posting in postings) {
                val authorLoginId = posting.authorLoginId
                val author = if (authorLoginId != null) {
                    userRepository.findByLoginId(authorLoginId).orElse(null)
                } else {
                    null
                }
                val history = HistoryDto().apply {
                    this.who = posting.authorName ?: "Unknown"
                    this.userPageUrl = author?.let { "/user/${it.loginId}" } ?: "#"
                    this.userAvatarUrl = author?.avatarUrl ?: "/images/default-avatar-34.png"
                    this.whenInstant = posting.createdDate ?: Instant.now()
                    this.where = project.name
                    this.what = "post"
                    this.shortTitle = "#${posting.number}"
                    this.how = posting.title
                    this.url = "/$ownerId/${project.name}/post/${posting.number}"
                }
                histories.add(history)
            }
        }

        // 4. PullRequests
        if (project.isPullRequestEnabled) {
            val pageable = PageRequest.of(0, 10, Sort.by("created").descending())
            val pullRequests = pullRequestRepository.findByToProject(project, pageable).content
            for (pull in pullRequests) {
                val contributor = pull.contributor
                val history = HistoryDto().apply {
                    this.who = contributor?.name ?: "Unknown"
                    this.userPageUrl = contributor?.let { "/user/${it.loginId}" } ?: "#"
                    this.userAvatarUrl = contributor?.avatarUrl ?: "/images/default-avatar-34.png"
                    this.whenInstant = pull.created ?: Instant.now()
                    this.where = project.name
                    this.what = "pullrequest"
                    this.shortTitle = "#${pull.number}"
                    this.how = pull.title ?: ""
                    this.url = "/$ownerId/${project.name}/pullRequest/${pull.number}"
                }
                histories.add(history)
            }
        }

        histories.sortByDescending { it.whenInstant }
        return histories
    }

    @GetMapping("/{owner}/{projectName}/members")
    fun projectMembers(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
            return "error/403"
        }

        val projectUsers = projectUserRepository.findByProjectId(project.id!!)

        model.addAttribute("project", project)
        model.addAttribute("projectUsers", projectUsers)
        model.addAttribute("currentUser", loginUser)

        return "project/members"
    }

    @GetMapping("/{owner}/{projectName}/setting")
    fun projectSetting(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (loginUser == null || !projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!)) {
            return "error/403"
        }

        // 설정 권한 검사 (MANAGER인지 여부)
        val isManager = projectUserRepository.findByProjectIdAndUserId(project.id!!, loginUser.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)


        if (!isManager) {
            return "error/403"
        }

        val repository = repositoryService.getRepository(project)
        val branches = try {
            repository.getRefNames().map { it.substringAfter("refs/heads/") }
        } catch (e: Exception) {
            emptyList()
        }
        val defaultBranch = try {
            repository.getDefaultBranch().substringAfter("refs/heads/")
        } catch (e: Exception) {
            "master"
        }

        model.addAttribute("project", project)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("branches", branches)
        model.addAttribute("defaultBranch", defaultBranch)

        return "project/setting"
    }

    @GetMapping("/{owner}/{projectName}/changeVCS")
    fun projectChangeVCSForm(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (loginUser == null || !projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!)) {
            return "error/403"
        }

        val isManager = projectUserRepository.findByProjectIdAndUserId(project.id!!, loginUser.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)

        if (!isManager) {
            return "error/403"
        }

        val nextVcs = if ((project.vcs ?: "GIT").uppercase() == "GIT") "SUBVERSION" else "GIT"

        model.addAttribute("project", project)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("nextVcs", nextVcs)

        return "project/change_vcs"
    }

    @PostMapping("/{owner}/{projectName}/changeVCS")
    @ResponseBody
    fun changeVCS(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?
    ): ResponseEntity<Void> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val isManager = projectUserRepository.findByProjectIdAndUserId(project.id!!, loginUser.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)

        if (!isManager) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        projectService.changeVCS(project.id!!)

        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{owner}/{projectName}/code/{branch}/download")
    fun downloadCode(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable branch: String,
        authentication: Authentication?,
        response: jakarta.servlet.http.HttpServletResponse
    ) {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: throw org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found")

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (project.isCodeAccessibleMemberOnly == true) {
            if (loginUser == null || (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser))) {
                throw org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
            }
        } else if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
            throw org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied")
        }

        val repository = repositoryService.getRepository(project)
        val decodedBranch = java.net.URLDecoder.decode(branch, "UTF-8")

        response.contentType = "application/zip"
        response.setHeader("Content-Disposition", "attachment; filename=\"$projectName-$branch.zip\"")

        repository.getArchive(response.outputStream, decodedBranch)
    }

    @GetMapping("/projectform")
    fun newProjectForm(
        authentication: Authentication?,
        model: Model
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "redirect:/users/loginform"

        // 사용자가 관리자로 속한 조직 목록 조회
        val orgUserList = organizationUserRepository.findByUserIdAndRoleId(loginUser.id!!, RoleType.ORG_ADMIN.roleType)
        val organizations = orgUserList.map { it.organization }

        model.addAttribute("currentUser", loginUser)
        model.addAttribute("organizations", organizations)

        return "project/create"
    }

    @PostMapping(value = ["/projectform", "/projects"])
    fun newProject(
        @RequestParam("owner") owner: String,
        @RequestParam("name") name: String,
        @RequestParam("overview") overview: String,
        @RequestParam("projectScope") projectScope: String,
        @RequestParam("vcs") vcs: String,
        @RequestParam(value = "code", defaultValue = "false") code: Boolean,
        @RequestParam(value = "issue", defaultValue = "false") issue: Boolean,
        @RequestParam(value = "pullRequest", defaultValue = "false") pullRequest: Boolean,
        @RequestParam(value = "review", defaultValue = "false") review: Boolean,
        @RequestParam(value = "milestone", defaultValue = "false") milestone: Boolean,
        @RequestParam(value = "board", defaultValue = "false") board: Boolean,
        authentication: Authentication?,
        model: Model
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "redirect:/users/loginform"

        try {
            val project = Project().apply {
                this.owner = owner.trim()
                this.name = name.trim()
                this.overview = overview.trim()
                this.projectScope = ProjectScope.valueOf(projectScope.uppercase())
                this.vcs = vcs.uppercase()
                this.isCodeEnabled = code
                this.isIssueEnabled = issue
                this.isPullRequestEnabled = pullRequest
                this.isReviewEnabled = review
                this.isMilestoneEnabled = milestone
                this.isBoardEnabled = board
            }

            val saved = projectService.createProject(project, loginUser)
            watchService.watch(loginUser, ResourceType.PROJECT, saved.id.toString())
            addVisitHistory(loginUser, saved)
            return "redirect:/${saved.owner}/${saved.name}"
        } catch (e: Exception) {
            val orgUserList = organizationUserRepository.findByUserIdAndRoleId(loginUser.id!!, RoleType.ORG_ADMIN.roleType)
            val organizations = orgUserList.map { it.organization }

            model.addAttribute("currentUser", loginUser)
            model.addAttribute("organizations", organizations)
            model.addAttribute("error", e.message ?: "프로젝트 생성 도중 오류가 발생했습니다.")
            return "project/create"
        }
    }

    // 3. 프로젝트 전체 목록 화면 (GET /projects)
    @GetMapping("/projects", produces = [MediaType.TEXT_HTML_VALUE])
    fun projects(
        @RequestParam(value = "filter", defaultValue = "") filter: String,
        @RequestParam(value = "pageNum", defaultValue = "1") pageNum: Int,
        authentication: Authentication?,
        model: Model
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }

        // 사용자가 조회할 수 있는 프로젝트 ID 목록 추출
        val projectIds = if (loginUser != null) {
            projectRepository.findAllowedProjectIdsForUser(loginUser.id!!)
        } else {
            projectRepository.findPublicProjectIds()
        }

        if (projectIds.isEmpty()) {
            model.addAttribute("projects", Page.empty<Project>())
            model.addAttribute("filter", filter)
            model.addAttribute("currentUser", loginUser)
            return "project/list"
        }

        val pageable = PageRequest.of(pageNum - 1, 25, Sort.by("createdDate").descending())
        val keyword = "%$filter%"
        val projectPage = projectRepository.searchProjects(projectIds, keyword, pageable)

        model.addAttribute("projects", projectPage)
        model.addAttribute("filter", filter)
        model.addAttribute("currentUser", loginUser)

        return "project/list"
    }

    // 3-1. 프로젝트 전체 목록 JSON API (GET /projects) - 레거시 호환 및 Typeahead 지원
    @GetMapping("/projects", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    fun projectsJson(
        @RequestParam(value = "query", defaultValue = "") query: String,
        @RequestParam(value = "filter", defaultValue = "") filter: String,
        authentication: Authentication?
    ): ResponseEntity<List<String>> {
        val user = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val searchKeyword = if (query.isNotBlank()) query else filter
        val pageable = PageRequest.of(0, 1000)

        val projectPage = if (user.isSiteManager) {
            projectRepository.findProjectsForAdmin(searchKeyword, pageable)
        } else {
            val allowedIds = projectRepository.findAllowedProjectIdsForUser(user.id!!)
            if (allowedIds.isEmpty()) {
                val publicIds = projectRepository.findPublicProjectIds()
                if (publicIds.isEmpty()) {
                    Page.empty()
                } else {
                    projectRepository.searchProjects(publicIds, searchKeyword, pageable)
                }
            } else {
                projectRepository.searchProjects(allowedIds, searchKeyword, pageable)
            }
        }

        val projectNames = projectPage.content.map { "${it.owner}/${it.name}" }
        val total = projectPage.totalElements

        val headers = org.springframework.http.HttpHeaders()
        headers.add("Content-Range", "items ${projectNames.size}/$total")

        return ResponseEntity.ok().headers(headers).body(projectNames)
    }

    // 4. 프로젝트 로고 이미지 조회 (GET /projects/{projectId}/logo)
    @GetMapping("/projects/{projectId}/logo")
    fun projectLogo(
        @PathVariable projectId: Long
    ): ResponseEntity<Resource> {
        val attachments = attachmentRepository.findByContainerTypeAndContainerId(
            ResourceType.PROJECT,
            projectId.toString()
        )
        val attachment = attachments.firstOrNull()

        if (attachment == null) {
            // 디폴트 프로젝트 이미지 반환
            val defaultImage = FileSystemResource("/Users/mzc01-search5/123/yuna/src/main/resources/static/images/project_default_logo.png")
            return if (defaultImage.exists()) {
                ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(defaultImage)
            } else {
                ResponseEntity.notFound().build()
            }
        }

        val file = attachmentService.getFile(attachment)
        if (!file.exists()) {
            return ResponseEntity.notFound().build()
        }

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(attachment.mimeType ?: "image/png"))
            .body(FileSystemResource(file))
    }

    // 5. 프로젝트 이관 설정 화면 (GET /{owner}/{projectName}/transfer)
    @GetMapping("/{owner}/{projectName}/transfer")
    fun transferForm(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "redirect:/users/loginform"

        val isManager = projectUserRepository.findByProjectIdAndUserId(project.id!!, loginUser.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)

        if (!isManager) {
            return "error/403"
        }

        model.addAttribute("project", project)
        model.addAttribute("currentUser", loginUser)
        return "project/transfer"
    }

    // 6. 프로젝트 이관 실행 API (PUT /{owner}/{projectName}/transfer)
    @PutMapping("/{owner}/{projectName}/transfer")
    @ResponseBody
    fun transferProject(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam("owner") destination: String,
        request: HttpServletRequest,
        authentication: Authentication?
    ): ResponseEntity<Void> {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val isManager = projectUserRepository.findByProjectIdAndUserId(project.id!!, loginUser.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)

        if (!isManager) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        // 대상 목적지(destination)가 유효한지 검증 (사용자 또는 조직)
        val destUser = userRepository.findByLoginId(destination).orElse(null)
        val destOrg = organizationRepository.findByName(destination).orElse(null)
        if (destUser == null && destOrg == null) {
            return ResponseEntity.badRequest().build()
        }

        // 자기 자신에게 이관 요청하는 것 차단
        if ((destUser != null && destUser.loginId == project.owner) || (destOrg != null && destOrg.name == project.owner)) {
            return ResponseEntity.badRequest().build()
        }

        val pt = projectService.requestNewTransfer(project.id!!, loginUser.id!!, destination)
        
        // 이관 요청 메일 발송
        sendTransferRequestMail(pt, request)

        val projectUrl = "/${project.owner}/${project.name}"
        return ResponseEntity.noContent()
            .header("Location", projectUrl)
            .build()
    }

    private fun sendTransferRequestMail(pt: com.github.search5.yona.domain.project.ProjectTransfer, request: HttpServletRequest) {
        try {
            val serverUrl = getServerUrl(request)
            val acceptUrl = "$serverUrl/project/transfer/${pt.id}/${pt.confirmKey}"
            
            // 다국어 메시지 조립
            val locale = Locale.getDefault()
            val hello = messageSource.getMessage("transfer.message.hello", arrayOf<Any>(pt.destination), locale)
            val detail = messageSource.getMessage("transfer.message.detail", arrayOf<Any>(pt.project.name, pt.newProjectName, pt.project.owner ?: "", pt.destination), locale)
            val link = messageSource.getMessage("transfer.message.link", null, locale)
            val deadline = messageSource.getMessage("transfer.message.deadline", null, locale)
            val thank = messageSource.getMessage("transfer.message.thank", null, locale)

            val markdownMessage = """
                $hello
                
                $detail
                $link
                
                $acceptUrl
                
                $deadline
                
                $thank
            """.trimIndent()

            val htmlContent = markdownService.render(markdownMessage, true, pt.project)
            val subject = "[${pt.project.name}] @${pt.sender.loginId} wants to transfer project"

            // 메일 전송 대상
            val bccEmails = mutableListOf<String>()
            
            val toUser = userRepository.findByLoginId(pt.destination).orElse(null)
            if (toUser != null && !toUser.email.isNullOrBlank()) {
                bccEmails.add(toUser.email)
            }

            val toOrg = organizationRepository.findByName(pt.destination).orElse(null)
            if (toOrg != null) {
                val orgUsers = organizationUserRepository.findByOrganizationId(toOrg.id!!)
                val admins = orgUsers.filter { it.role.id == RoleType.ORG_ADMIN.roleType }
                admins.forEach {
                    if (!it.user.email.isNullOrBlank()) {
                        bccEmails.add(it.user.email)
                    }
                }
            }

            // 개별적으로 HTML 이메일 발송
            bccEmails.distinct().forEach { email ->
                mailService.sendHtmlMail(email, "Yona", subject, htmlContent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getServerUrl(request: HttpServletRequest): String {
        val scheme = request.scheme
        val serverName = request.serverName
        val serverPort = request.serverPort
        return if (serverPort == 80 || serverPort == 443) {
            "$scheme://$serverName"
        } else {
            "$scheme://$serverName:$serverPort"
        }
    }

    // 7. 프로젝트 이관 승인 처리 (GET /project/transfer/{transferId}/{confirmKey})
    @GetMapping("/project/transfer/{transferId}/{confirmKey}")
    fun acceptTransfer(
        @PathVariable transferId: Long,
        @PathVariable confirmKey: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "redirect:/users/loginform"

        val ptOpt = projectTransferRepository.findById(transferId)
        if (!ptOpt.isPresent) {
            model.addAttribute("errorMessage", "존재하지 않는 이관 요청입니다.")
            return "error/404"
        }
        val pt = ptOpt.get()

        return try {
            val destination = pt.destination
            val newProjectName = pt.newProjectName
            
            projectService.acceptTransfer(transferId, confirmKey, loginUser.id!!)
            
            "redirect:/$destination/$newProjectName"
        } catch (e: Exception) {
            model.addAttribute("errorMessage", "이관 승인에 실패했습니다: ${e.message}")
            return "error/500"
        }
    }

    // 8. 프로젝트 삭제 설정 화면 (GET /{owner}/{projectName}/deleteform)
    @GetMapping("/{owner}/{projectName}/deleteform")
    fun deleteForm(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "redirect:/users/loginform"

        val isManager = projectUserRepository.findByProjectIdAndUserId(project.id!!, loginUser.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)

        if (!isManager) {
            return "error/403"
        }

        model.addAttribute("project", project)
        model.addAttribute("currentUser", loginUser)
        return "project/delete"
    }

    // 9. 프로젝트 삭제 실행 API (DELETE /{owner}/{projectName}/delete)
    @DeleteMapping("/{owner}/{projectName}/delete")
    @ResponseBody
    fun deleteProject(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?
    ): ResponseEntity<Void> {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val isManager = projectUserRepository.findByProjectIdAndUserId(project.id!!, loginUser.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)

        if (!isManager) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        projectService.deleteProject(project.id!!)

        return ResponseEntity.noContent()
            .header("Location", "/")
            .build()
    }

    // 10. 이슈 라벨 설정 화면 (GET /{owner}/{projectName}/issue/labelsform)
    @GetMapping("/{owner}/{projectName}/issue/labelsform")
    fun labelsForm(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "redirect:/users/loginform"

        val isManager = projectUserRepository.findByProjectIdAndUserId(project.id!!, loginUser.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)

        if (!isManager && !loginUser.isSiteManager) {
            return "error/403"
        }

        val labels = issueLabelService.getLabels(project.id!!)

        model.addAttribute("project", project)
        model.addAttribute("labels", labels)
        model.addAttribute("currentUser", loginUser)
        return "project/issuelabels"
    }

    // 11. 프로젝트 포크 화면 (GET /{ownerName}/{projectName}/newFork)
    @GetMapping("/{ownerName}/{projectName}/newFork")
    fun newFork(
        @PathVariable ownerName: String,
        @PathVariable projectName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val originalProject = projectRepository.findByOwnerAndNameOrPreviousPlace(ownerName, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "redirect:/users/loginform"

        // 사용자가 관리하는 조직 목록 조회
        val orgUserList = organizationUserRepository.findByUserIdAndRoleId(loginUser.id!!, RoleType.ORG_ADMIN.roleType)
        val organizations = orgUserList.map { it.organization }

        model.addAttribute("project", originalProject)
        model.addAttribute("organizations", organizations)
        model.addAttribute("currentUser", loginUser)

        return "project/fork"
    }

    // 12. 프로젝트 포크 실행 (POST /{ownerName}/{projectName}/fork)
    @PostMapping("/{ownerName}/{projectName}/fork")
    fun fork(
        @PathVariable ownerName: String,
        @PathVariable projectName: String,
        @RequestParam("owner") owner: String,
        @RequestParam("name") name: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val originalProject = projectRepository.findByOwnerAndNameOrPreviousPlace(ownerName, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "redirect:/users/loginform"

        val destination = owner.trim()
        val forkedProjectName = name.trim()

        // 동일한 소유자 밑에 같은 이름의 프로젝트가 이미 있는지 검사
        if (projectRepository.existsByOwnerAndName(destination, forkedProjectName)) {
            val orgUserList = organizationUserRepository.findByUserIdAndRoleId(loginUser.id!!, RoleType.ORG_ADMIN.roleType)
            val organizations = orgUserList.map { it.organization }

            model.addAttribute("project", originalProject)
            model.addAttribute("organizations", organizations)
            model.addAttribute("currentUser", loginUser)
            model.addAttribute("error", "이미 동일한 소유자 밑에 같은 이름의 프로젝트가 존재합니다.")
            return "project/fork"
        }

        return try {
            projectService.forkProject(
                projectId = originalProject.id!!,
                forkerId = loginUser.id!!,
                destinationOwner = destination,
                destinationName = forkedProjectName
            )
            "redirect:/$destination/$forkedProjectName"
        } catch (e: Exception) {
            val orgUserList = organizationUserRepository.findByUserIdAndRoleId(loginUser.id!!, RoleType.ORG_ADMIN.roleType)
            val organizations = orgUserList.map { it.organization }

            model.addAttribute("project", originalProject)
            model.addAttribute("organizations", organizations)
            model.addAttribute("currentUser", loginUser)
            model.addAttribute("error", e.message ?: "프로젝트 포크 실패")
            return "project/fork"
        }
    }

    private fun getProjectDashboardData(
        project: Project,
        model: Model,
        projectUsers: List<com.github.search5.yona.domain.project.ProjectUser>
    ) {
        val openIssues = issueRepository.findByProjectAndState(project, State.OPEN)
        val allIssues = issueRepository.findByProject(project)
        val totalOpenIssuesCount = openIssues.size.toDouble()

        // 1. Assignees
        val memberUsers = projectUsers.map { it.user }.toMutableSet()
        openIssues.forEach { issue ->
            issue.assignee?.user?.let { memberUsers.add(it) }
        }
        val assigneeList = memberUsers.map { user ->
            val count = openIssues.count { it.assignee?.user?.id == user.id }
            val percent = if (totalOpenIssuesCount > 0) (count / totalOpenIssuesCount * 100).toInt() else 0
            AssigneeDashboardDto(user, count, percent)
        }.filter { it.count > 0 }.sortedByDescending { it.count }

        val notAssignedIssuesCount = openIssues.count { it.assignee == null }
        val notAssignedIssuesPercent = if (totalOpenIssuesCount > 0) (notAssignedIssuesCount / totalOpenIssuesCount * 100).toInt() else 0

        // 2. Milestones
        val openMilestones = milestoneRepository.findByProjectAndState(project, State.OPEN)
        val milestoneList = openMilestones.map { milestone ->
            val openCount = openIssues.count { it.milestone?.id == milestone.id }
            val totalInMilestone = allIssues.count { it.milestone?.id == milestone.id }
            val closedInMilestone = allIssues.count { it.milestone?.id == milestone.id && it.state == State.CLOSED }
            val completionRate = if (totalInMilestone > 0) (closedInMilestone.toDouble() / totalInMilestone * 100).toInt() else 0
            MilestoneDashboardDto(milestone.id!!, milestone.title, openCount, completionRate)
        }.filter { it.openCount > 0 }.sortedByDescending { it.openCount }

        val noMilestoneIssuesCount = openIssues.count { it.milestone == null }

        // 3. PullRequests
        val openPullRequests = pullRequestRepository.findByToProjectAndState(project, State.OPEN, PageRequest.of(0, 10, Sort.by("created").descending())).content
        val totalOpenPullRequestsCount = pullRequestRepository.findByToProjectAndState(project, State.OPEN).size

        // 4. Labels
        val projectLabels = issueLabelService.getLabels(project.id!!)
        val labelCategories = projectLabels.groupBy { it.category }.map { (category, labels) ->
            val labelDtos = labels.map { label ->
                val count = openIssues.count { it.labels.any { l -> l.id == label.id } }
                LabelDashboardDto(label.id!!, label.name, count)
            }
            LabelCategoryDashboardDto(category.name, labelDtos)
        }

        model.addAttribute("openIssuesCount", openIssues.size)
        model.addAttribute("assigneeList", assigneeList)
        model.addAttribute("notAssignedIssuesCount", notAssignedIssuesCount)
        model.addAttribute("notAssignedIssuesPercent", notAssignedIssuesPercent)
        model.addAttribute("milestoneList", milestoneList)
        model.addAttribute("noMilestoneIssuesCount", noMilestoneIssuesCount)
        model.addAttribute("openPullRequests", openPullRequests)
        model.addAttribute("totalOpenPullRequestsCount", totalOpenPullRequestsCount)
        model.addAttribute("labelCategories", labelCategories)
    }

    data class AssigneeDashboardDto(
        val user: com.github.search5.yona.domain.user.User,
        val count: Int,
        val percent: Int
    )

    data class MilestoneDashboardDto(
        val id: Long,
        val title: String,
        val openCount: Int,
        val completionRate: Int
    )

    data class LabelDashboardDto(
        val id: Long,
        val name: String,
        val count: Int
    )

    data class LabelCategoryDashboardDto(
        val name: String,
        val labels: List<LabelDashboardDto>
    )

    private fun getReadmeFileName(project: Project): String? {
        try {
            val repo = repositoryService.getRepository(project)
            val baseFileName = "README.md"
            if (repo.isFile(baseFileName)) {
                return baseFileName
            }
            if (repo.isFile(baseFileName.lowercase())) {
                return baseFileName.lowercase()
            }
            if (repo.javaClass.simpleName.contains("Svn", ignoreCase = true)) {
                val svnPath = "/trunk/$baseFileName"
                if (repo.isFile(svnPath)) {
                    return svnPath
                }
                if (repo.isFile(svnPath.lowercase())) {
                    return svnPath.lowercase()
                }
            }
        } catch (e: Exception) {
            // NOOP
        }
        return null
    }

    private fun getReadmeContent(project: Project, fileName: String): String? {
        return try {
            val repo = repositoryService.getRepository(project)
            val bytes = repo.getRawFile("HEAD", fileName)
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
