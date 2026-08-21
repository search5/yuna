package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.enumeration.Operation
import com.github.search5.yona.domain.board.Posting
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.board.PostingService
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.user.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.board.PostingCommentRepository
import org.springframework.beans.factory.annotation.Value
import com.github.search5.yona.domain.vcs.BareCommit
import com.github.search5.yona.domain.watch.WatchService
import com.github.search5.yona.domain.attachment.AttachmentRepository
import com.github.search5.yona.domain.attachment.AttachmentService
import com.github.search5.yona.domain.issue.RecentIssueService
import tools.jackson.databind.ObjectMapper

@Controller
class BoardViewController(
    private val projectRepository: ProjectRepository,
    private val postingService: PostingService,
    private val postingRepository: PostingRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val postingCommentRepository: PostingCommentRepository,
    private val watchService: WatchService,
    private val attachmentRepository: AttachmentRepository,
    private val objectMapper: ObjectMapper,
    private val repositoryService: com.github.search5.yona.domain.vcs.RepositoryService,
    @Value("\${yuna.git.base-dir:/tmp/yuna/git}")
    private val gitBaseDir: String,
    private val recentIssueService: RecentIssueService,
    private val accessControl: AccessControl,
    private val attachmentService: AttachmentService
) {

    @GetMapping("/{owner}/{projectName}/posts")
    fun listPosts(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false) pageNum: Int?,
        @RequestParam(required = false) filter: String?,
        @RequestParam(required = false, defaultValue = "createdDate") orderBy: String,
        @RequestParam(required = false, defaultValue = "desc") orderDir: String,
        @RequestParam(required = false) labelIds: List<Long>?,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
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
        // yona AbstractPostingApp.java:35 ITEMS_PER_PAGE 대응 (P1-105) — 게시글 목록은 고정 15, 클라이언트 오버라이드 없음.
        val pageable = PageRequest.of(actualPage, ITEMS_PER_PAGE, sort)

        val labelFilter = labelIds?.filterNotNull()?.takeIf { it.isNotEmpty() }
        val postingPage = if (labelFilter != null) {
            postingRepository.findByProjectAndLabelIdsIn(
                project, labelFilter, if (filter.isNullOrBlank()) null else "%$filter%", pageable
            )
        } else if (!filter.isNullOrBlank()) {
            postingRepository.searchPostingsInProject(project, "%$filter%", pageable)
        } else {
            postingRepository.findByProject(project, pageable)
        }
        val notices = postingService.getNotices(project.id!!)

        model.addAttribute("project", project)
        model.addAttribute("postingPage", postingPage)
        model.addAttribute("notices", notices)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("filter", filter)
        model.addAttribute("orderBy", orderBy)
        model.addAttribute("orderDir", orderDir)
        model.addAttribute("labelIds", labelFilter ?: emptyList<Long>())

        return "board/list"
    }

    @GetMapping("/{owner}/{projectName}/post/{number}")
    fun viewPost(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
            return "error/403"
        }

        val posting = postingService.getPosting(project.id!!, number) ?: return "error/404"
        val comments = postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(posting.id!!)

        if (loginUser != null) {
            try {
                recentIssueService.recordPostingVisit(loginUser, posting)
            } catch (e: Exception) {
                // NOOP: 방문 이력 기록 실패가 게시글 조회 자체를 막지 않아야 한다
            }
        }

        val isWatching = loginUser?.let {
            watchService.isWatching(it, ResourceType.BOARD_POST, posting.id.toString())
        } ?: false

        val isAllowedUpdate = loginUser != null && (posting.authorLoginId == loginUser.loginId || projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) || accessControl.isAllowedIfGroupMember(project, loginUser))

        val attachments = attachmentRepository.findByContainerTypeAndContainerId(ResourceType.BOARD_POST, posting.id.toString())
        val attachmentsList = attachments.map { attach ->
            mapOf(
                "id" to (attach.id?.toString() ?: ""),
                "mimeType" to (attach.mimeType ?: ""),
                "name" to attach.name,
                "url" to "/files/${attach.id}",
                "size" to (attach.size?.toString() ?: "0")
            )
        }
        val attachmentsJson = objectMapper.writeValueAsString(mapOf("attachments" to attachmentsList))

        model.addAttribute("project", project)
        model.addAttribute("post", posting)
        model.addAttribute("comments", comments)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("isWatching", isWatching)
        model.addAttribute("isAllowedUpdate", isAllowedUpdate)
        model.addAttribute("attachmentsJson", attachmentsJson)

        return "board/view"
    }

    @GetMapping(value = ["/{owner}/{projectName}/post/new", "/{owner}/{projectName}/postform"])
    fun createPostForm(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false) readme: Boolean?,
        @RequestParam(required = false) issueTemplate: Boolean?,
        @RequestParam(required = false) branch: String?,
        @RequestParam(required = false) path: String?,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (loginUser == null || (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser))) {
            return "error/403"
        }

        val isAllowedToNotice = loginUser != null && (projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) || accessControl.isAllowedIfGroupMember(project, loginUser))

        var preparedPostBody = ""
        if (readme == true) {
            try {
                val bytes = repositoryService.getRepository(project).getRawFile("HEAD", "README.md")
                if (bytes != null) {
                    preparedPostBody = String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                }
            } catch (e: Exception) {}
        } else if (issueTemplate == true) {
            preparedPostBody = getIssueTemplate(project)
        } else if (!path.isNullOrBlank()) {
            try {
                val bytes = repositoryService.getRepository(project).getRawFile(branch ?: "HEAD", path)
                if (bytes != null) {
                    preparedPostBody = String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                }
            } catch (e: Exception) {}
        }

        model.addAttribute("project", project)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("isAllowedToNotice", isAllowedToNotice)
        model.addAttribute("readme", readme ?: false)
        model.addAttribute("preparedPostBody", preparedPostBody)

        return "board/create"
    }

    @GetMapping("/{owner}/{projectName}/post/{number}/editform")
    fun editPostForm(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (loginUser == null || (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser))) {
            return "error/403"
        }

        val posting = postingService.getPosting(project.id!!, number) ?: return "error/404"

        val isAllowedToNotice = loginUser != null && (projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) || accessControl.isAllowedIfGroupMember(project, loginUser))

        val attachments = attachmentRepository.findByContainerTypeAndContainerId(ResourceType.BOARD_POST, posting.id.toString())
        val attachmentsList = attachments.map { attach ->
            mapOf(
                "id" to (attach.id?.toString() ?: ""),
                "mimeType" to (attach.mimeType ?: ""),
                "name" to attach.name,
                "url" to "/files/${attach.id}",
                "size" to (attach.size?.toString() ?: "0")
            )
        }
        val attachmentsJson = objectMapper.writeValueAsString(mapOf("attachments" to attachmentsList))

        model.addAttribute("project", project)
        model.addAttribute("post", posting)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("isAllowedToNotice", isAllowedToNotice)
        model.addAttribute("attachmentsJson", attachmentsJson)

        return "board/edit"
    }

    @org.springframework.web.bind.annotation.PostMapping(value = ["/{owner}/{projectName}/post/{number}/editform", "/{owner}/{projectName}/post/{number}/edit"])
    fun editPost(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        @org.springframework.web.bind.annotation.ModelAttribute request: PostingForm,
        authentication: Authentication?
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        val posting = postingService.getPosting(project.id!!, number) ?: return "error/404"

        if (posting.authorLoginId != loginUser.loginId &&
            !projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) &&
            !accessControl.isAllowedIfGroupMember(project, loginUser)
        ) {
            return "error/403"
        }

        val isReadme = posting.readme ?: false
        if (isReadme) {
            try {
                val bare = BareCommit(project, loginUser, gitBaseDir)
                bare.commitTextFile("README.md", request.body ?: "", request.title)

                val readmes = postingRepository.findByProjectAndReadme(project, true)
                for (other in readmes) {
                    if (other.id != posting.id) {
                        other.readme = false
                        postingRepository.save(other)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // yona BoardApp.editPost의 isSelectedToSendNotificationMail() 대응 (P1-44) — 서비스 계층에 위임.
        postingService.updatePosting(
            projectId = project.id!!,
            number = number,
            title = request.title,
            body = request.body ?: "",
            notice = request.notice ?: false,
            readme = isReadme,
            authorId = loginUser.id!!,
            sendNotificationMail = request.sendNotificationMail ?: false
        )

        if (isReadme) {
            return "redirect:/$owner/$projectName"
        }
        return "redirect:/$owner/$projectName/post/$number"
    }

    @org.springframework.web.bind.annotation.PostMapping("/{owner}/{projectName}/posts")
    fun createPost(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @org.springframework.web.bind.annotation.ModelAttribute request: PostingForm,
        authentication: Authentication?
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        if (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) &&
            !accessControl.isAllowedIfGroupMember(project, loginUser)
        ) {
            return "error/403"
        }

        val posting = Posting(
            title = request.title,
            body = request.body ?: "",
            notice = request.notice ?: false,
            readme = request.readme ?: false,
            project = project
        )

        val saved = postingService.createPosting(project.id!!, posting, loginUser.id!!)

        if (!request.temporaryUploadFiles.isNullOrBlank()) {
            val fileIds = request.temporaryUploadFiles!!.split(",").mapNotNull { it.trim().toLongOrNull() }
            // yona Attachment.moveOnlySelected() 대응 (P0-22) — 소유권 검증 없이 요청받은 ID를
            // 그대로 재배선하지 않고, 실제로 이 로그인 사용자가 업로드한 임시 첨부만 옮긴다.
            attachmentService.moveOnlySelected(
                fromType = ResourceType.NOT_A_RESOURCE,
                fromId = "",
                toType = ResourceType.BOARD_POST,
                toId = saved.id.toString(),
                selectedIds = fileIds,
                moverLoginId = loginUser.loginId ?: ""
            )
        }

        if (saved.readme) {
            try {
                val bare = BareCommit(project, loginUser, gitBaseDir)
                bare.commitTextFile("README.md", saved.body ?: "", saved.title ?: "")
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return "redirect:/$owner/$projectName"
        }
        return "redirect:/$owner/$projectName/post/${saved.number}"
    }

    private fun getIssueTemplate(project: com.github.search5.yona.domain.project.Project): String {
        return try {
            val bytes = repositoryService.getRepository(project).getRawFile("HEAD", "ISSUE_TEMPLATE.md")
            if (bytes != null) String(bytes, java.nio.charset.StandardCharsets.UTF_8) else ""
        } catch (e: Exception) {
            ""
        }
    }

    companion object {
        // yona AbstractPostingApp.java:35 ITEMS_PER_PAGE 대응 (P1-105).
        private const val ITEMS_PER_PAGE = 15
    }
}

data class PostingForm(
    var title: String = "",
    var body: String? = "",
    var notice: Boolean? = false,
    var readme: Boolean? = false,
    var temporaryUploadFiles: String? = null,
    var sendNotificationMail: Boolean? = false
)

