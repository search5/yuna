package com.github.search5.yona.web

import com.github.search5.yona.domain.board.Posting
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.board.PostingService
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
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
import com.github.search5.yona.domain.watch.WatchService
import com.github.search5.yona.domain.attachment.AttachmentRepository
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
    private val objectMapper: ObjectMapper
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
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (project.projectScope != ProjectScope.PUBLIC) {
            if (loginUser == null || !projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!)) {
                return "error/403"
            }
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

        val postingPage = if (!filter.isNullOrBlank()) {
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
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (project.projectScope != ProjectScope.PUBLIC) {
            if (loginUser == null || !projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!)) {
                return "error/403"
            }
        }

        val posting = postingService.getPosting(project.id!!, number) ?: return "error/404"
        val comments = postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(posting.id!!)

        val isWatching = loginUser?.let {
            watchService.isWatching(it, ResourceType.BOARD_POST, posting.id.toString())
        } ?: false

        val isAllowedUpdate = loginUser != null && (posting.authorLoginId == loginUser.loginId || projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!))

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
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (loginUser == null || !projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!)) {
            return "error/403"
        }

        val isAllowedToNotice = loginUser != null && projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!)
        model.addAttribute("project", project)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("isAllowedToNotice", isAllowedToNotice)

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
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (loginUser == null || !projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!)) {
            return "error/403"
        }

        val posting = postingService.getPosting(project.id!!, number) ?: return "error/404"

        val isAllowedToNotice = loginUser != null && projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!)

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
}
