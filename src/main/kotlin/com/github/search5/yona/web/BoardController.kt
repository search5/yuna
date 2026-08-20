package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.board.Posting
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.board.PostingService
import com.github.search5.yona.domain.enumeration.Operation
import com.github.search5.yona.domain.issue.IssueLabelRepository
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

@RestController
@RequestMapping("/api/projects/{projectId}/posts")
class BoardController(
    private val postingService: PostingService,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val postingRepository: PostingRepository,
    private val issueLabelRepository: IssueLabelRepository,
    private val accessControl: AccessControl
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

    private fun isManagerOrAuthor(project: Project, authorId: Long?, user: User?): Boolean {
        if (user == null) return false
        if (authorId == user.id) return true
        return projectUserRepository.findByProjectIdAndUserId(project.id!!, user.id!!)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)
    }

    @GetMapping
    fun getPostings(
        @PathVariable projectId: Long,
        @PageableDefault(size = 25) pageable: Pageable,
        authentication: Authentication?
    ): ResponseEntity<Page<Posting>> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val page = postingService.getPostings(projectId, pageable)
        return ResponseEntity.ok(page)
    }

    @GetMapping("/{postId}")
    fun getPosting(
        @PathVariable projectId: Long,
        @PathVariable postId: Long,
        authentication: Authentication?
    ): ResponseEntity<Posting> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val posting = postingService.getPosting(projectId, postId)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return ResponseEntity.ok(posting)
    }

    @PostMapping
    fun createPosting(
        @PathVariable projectId: Long,
        @RequestBody request: CreatePostingRequest,
        authentication: Authentication?
    ): ResponseEntity<Posting> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!checkWritePermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val posting = Posting(
            title = request.title,
            body = request.body,
            notice = request.notice ?: false,
            readme = request.readme ?: false,
            project = project
        )

        val saved = postingService.createPosting(projectId, posting, user.id!!)
        return ResponseEntity.status(HttpStatus.CREATED).body(saved)
    }

    @PutMapping("/{postId}")
    fun updatePosting(
        @PathVariable projectId: Long,
        @PathVariable postId: Long,
        @RequestBody request: UpdatePostingRequest,
        authentication: Authentication?
    ): ResponseEntity<Posting> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val posting = postingService.getPosting(projectId, postId)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!isManagerOrAuthor(project, posting.authorId, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val updated = postingService.updatePosting(
            projectId = projectId,
            number = postId,
            title = request.title,
            body = request.body,
            notice = request.notice ?: false,
            readme = request.readme ?: false,
            authorId = user.id!!,
            sendNotificationMail = request.sendNotificationMail ?: false
        )

        return ResponseEntity.ok(updated)
    }

    // yona api.BoardApi.updatePostLabel 대응 — 게시글에 붙은 라벨 집합을 통째로 교체한다.
    @PutMapping("/{postId}/labels")
    fun updatePostLabels(
        @PathVariable projectId: Long,
        @PathVariable postId: Long,
        @RequestBody labelIds: List<Long>,
        authentication: Authentication?
    ): ResponseEntity<Posting> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val posting = postingService.getPosting(projectId, postId)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!isManagerOrAuthor(project, posting.authorId, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        posting.labels = issueLabelRepository.findAllById(labelIds).toMutableSet()
        val saved = postingRepository.save(posting)

        return ResponseEntity.ok(saved)
    }

    @DeleteMapping("/{postId}")
    fun deletePosting(
        @PathVariable projectId: Long,
        @PathVariable postId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val posting = postingService.getPosting(projectId, postId)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!isManagerOrAuthor(project, posting.authorId, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        postingService.deletePosting(projectId, postId, user.id!!)
        return ResponseEntity.ok(mapOf("status" to "success"))
    }

    data class CreatePostingRequest(
        val title: String,
        val body: String,
        val notice: Boolean?,
        val readme: Boolean?
    )

    data class UpdatePostingRequest(
        val title: String,
        val body: String,
        val notice: Boolean?,
        val readme: Boolean?,
        val sendNotificationMail: Boolean? = null
    )
}
