package com.github.search5.yona.web

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.issue.IssueLabelRepository
import com.github.search5.yona.domain.issue.IssueCommentRepository
import com.github.search5.yona.domain.board.Posting
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.board.PostingCommentRepository
import com.github.search5.yona.domain.milestone.Milestone
import com.github.search5.yona.domain.milestone.MilestoneRepository
import com.github.search5.yona.domain.attachment.AttachmentRepository
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.enumeration.State
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.RestTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpEntity
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Controller
@RequestMapping("/migration")
class MigrationController(
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val organizationUserRepository: OrganizationUserRepository,
    private val issueRepository: IssueRepository,
    private val issueLabelRepository: IssueLabelRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val postingRepository: PostingRepository,
    private val postingCommentRepository: PostingCommentRepository,
    private val milestoneRepository: MilestoneRepository,
    private val attachmentRepository: AttachmentRepository,
    @Value("\${github.client.id:}") private val clientId: String = "",
    @Value("\${github.client.secret:}") private val clientSecret: String = "",
    @Value("\${github.allow.migration:false}") private val allowMigration: Boolean = false
) {

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneId.systemDefault())
    private val yonaServer = "/"

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    private fun getOAuthToken(code: String): String {
        val restTemplate = RestTemplate()
        val url = "https://github.com/login/oauth/access_token"
        val headers = HttpHeaders()
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED

        val map = LinkedMultiValueMap<String, String>()
        map.add("client_id", clientId)
        map.add("client_secret", clientSecret)
        map.add("code", code)

        val entity = HttpEntity(map, headers)
        return try {
            val response = restTemplate.postForEntity(url, entity, Map::class.java)
            val body = response.body
            (body?.get("access_token") as? String) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    // 1. 마이그레이션 홈 화면
    @GetMapping
    fun migrationHome(
        @RequestParam(value = "code", required = false) code: String?,
        authentication: Authentication?,
        model: Model
    ): String {
        if (!allowMigration) {
            return "error/403"
        }

        val currentUser = getLoginUser(authentication) ?: return "redirect:/users/loginform"
        model.addAttribute("currentUser", currentUser)

        if (!code.isNullOrBlank()) {
            val token = getOAuthToken(code)
            model.addAttribute("token", token)
            model.addAttribute("code", code)
        } else {
            model.addAttribute("token", "")
            model.addAttribute("code", "")
        }

        return "migration/home"
    }

    // 2. 권한 있는 프로젝트 목록 조회 API
    @GetMapping("/projects")
    @ResponseBody
    fun getMigrationProjects(authentication: Authentication?): ResponseEntity<Any> {
        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val sourceProjects = mutableSetOf<Project>()

        // 사용자가 MANAGER인 프로젝트들
        val userProjects = projectUserRepository.findByUserId(user.id!!)
            .filter { it.role.id == RoleType.MANAGER.roleType }
            .map { it.project }
        sourceProjects.addAll(userProjects)

        // 사용자가 ORG_ADMIN인 단체의 프로젝트들
        val orgUsers = organizationUserRepository.findByUserIdAndRoleId(user.id!!, RoleType.ORG_ADMIN.roleType)
        for (ou in orgUsers) {
            sourceProjects.addAll(ou.organization.projects)
        }

        val sortedList = sourceProjects.sortedWith(compareBy({ it.owner }, { it.name }))
        val projectsJson = sortedList.map { p ->
            mapOf(
                "owner" to (p.owner ?: ""),
                "projectName" to p.name,
                "private" to (p.projectScope != ProjectScope.PUBLIC),
                "members" to p.projectUsers.size,
                "full_name" to "${p.owner}/${p.name}"
            )
        }

        return ResponseEntity.ok(projectsJson)
    }

    // 3. 개별 프로젝트 상세 카운트 및 담당자 목록 조회
    @GetMapping("/{owner}/projects/{projectName}")
    @ResponseBody
    fun getMigrationProjectDetail(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val assignees = project.projectUsers.map { pu ->
            mapOf(
                "name" to (pu.user.name ?: ""),
                "login" to pu.user.loginId,
                "email" to (pu.user.email ?: "")
            )
        }

        val issueCount = issueRepository.countByProject(project)
        val postCount = postingRepository.countByProject(project)
        val milestoneCount = milestoneRepository.countByProject(project)

        val result = mapOf(
            "owner" to (project.owner ?: ""),
            "projectName" to project.name,
            "full_name" to "${project.owner}/${project.name}",
            "assignees" to assignees,
            "memberCount" to project.projectUsers.size,
            "issueCount" to issueCount,
            "postCount" to postCount,
            "milestoneCount" to milestoneCount
        )

        return ResponseEntity.ok(result)
    }

    // 4. 프로젝트 라벨 조회
    @GetMapping("/{owner}/projects/{projectName}/labels")
    @ResponseBody
    fun exportLabels(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val labelList = issueLabelRepository.findByProject(project)
        val labelsMap = labelList.associate { label ->
            label.id.toString() to mapOf(
                "id" to label.id,
                "name" to label.name,
                "categoryId" to label.category.id,
                "categoryName" to label.category.name
            )
        }

        return ResponseEntity.ok(mapOf("labels" to labelsMap))
    }

    // 5. 이슈 라벨 매핑 조회
    @GetMapping("/{owner}/projects/{projectName}/issuelabel")
    @ResponseBody
    fun exportIssueLabelPairs(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val issues = issueRepository.findByProject(project)
        val pairs = mutableListOf<Map<String, Any>>()
        for (issue in issues) {
            for (label in issue.labels) {
                pairs.add(
                    mapOf(
                        "issue_id" to (issue.id ?: 0L),
                        "issue_label_id" to (label.id ?: 0L)
                    )
                )
            }
        }

        return ResponseEntity.ok(mapOf("issueLabelPairs" to pairs))
    }

    // 6. 마일스톤 조회
    @GetMapping("/{owner}/projects/{projectName}/milestones")
    @ResponseBody
    fun exportMilestones(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val milestones = milestoneRepository.findByProject(project)
        val milestonesJson = milestones.map { m ->
            val node = mutableMapOf<String, Any?>()
            node["id"] = m.id
            node["title"] = m.title
            node["state"] = m.state.name.lowercase()
            node["description"] = m.contents

            if (m.dueDate != null) {
                node["due_on"] = formatter.format(m.dueDate)
            }

            mapOf("milestone" to node)
        }

        return ResponseEntity.ok(mapOf("milestones" to milestonesJson))
    }

    // 7. 이슈 조회
    @GetMapping("/{owner}/projects/{projectName}/issues")
    @ResponseBody
    fun exportIssues(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(value = "withWikiCommit", defaultValue = "false") withWikiCommit: Boolean,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val issues = issueRepository.findByProject(project)
        val issuesJson = issues.map { issue ->
            val node = mutableMapOf<String, Any?>()
            node["id"] = issue.id
            node["title"] = issue.title

            val originalIssueLink = "$yonaServer${project.owner}/${project.name}/issue/${issue.number}"
            val body = if (withWikiCommit) {
                addOriginalAuthorName(
                    relativeLinksToWikiCommitPath(issue.body ?: ""),
                    issue.authorLoginId ?: "",
                    issue.authorName ?: "",
                    "이슈",
                    originalIssueLink
                )
            } else {
                addOriginalAuthorName(
                    relativeLinksToAbsolutePath(issue.body ?: ""),
                    issue.authorLoginId ?: "",
                    issue.authorName ?: "",
                    "이슈",
                    originalIssueLink
                )
            }

            val sb = StringBuilder(body)
            if (withWikiCommit) {
                addAttachmentsStringUsingWikiCommit(sb, ResourceType.ISSUE_POST, issue.id.toString())
            } else {
                addAttachmentsString(sb, ResourceType.ISSUE_POST, issue.id.toString())
            }
            node["body"] = sb.toString()

            if (issue.createdDate != null) {
                node["created_at"] = formatter.format(issue.createdDate)
            }
            node["assignee"] = issue.assignee?.user?.loginId
            node["milestone"] = issue.milestone?.title
            node["milestoneId"] = issue.milestone?.id
            node["closed"] = (issue.state == State.CLOSED)

            val rawComments = issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(issue.id!!)
            val comments = rawComments.map { comment ->
                val commentNode = mutableMapOf<String, Any?>()
                if (comment.createdDate != null) {
                    commentNode["created_at"] = formatter.format(comment.createdDate)
                }

                val commentBody = if (withWikiCommit) {
                    addOriginalAuthorName(
                        relativeLinksToWikiCommitPath(comment.contents),
                        comment.authorLoginId ?: "",
                        comment.authorName ?: "",
                        "코멘트",
                        "$originalIssueLink#comment-${comment.id}"
                    )
                } else {
                    addOriginalAuthorName(
                        relativeLinksToAbsolutePath(comment.contents),
                        comment.authorLoginId ?: "",
                        comment.authorName ?: "",
                        "코멘트",
                        "$originalIssueLink#comment-${comment.id}"
                    )
                }

                val csb = StringBuilder(commentBody)
                if (withWikiCommit) {
                    addAttachmentsStringUsingWikiCommit(csb, ResourceType.ISSUE_COMMENT, comment.id.toString())
                } else {
                    addAttachmentsString(csb, ResourceType.ISSUE_COMMENT, comment.id.toString())
                }
                commentNode["body"] = csb.toString()
                commentNode
            }

            mapOf(
                "issue" to node,
                "comments" to comments
            )
        }

        return ResponseEntity.ok(mapOf("issues" to issuesJson))
    }

    // 8. 게시판 포스팅 조회
    @GetMapping("/{owner}/projects/{projectName}/posts")
    @ResponseBody
    fun exportPosts(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(value = "withWikiCommit", defaultValue = "false") withWikiCommit: Boolean,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val postings = postingRepository.findByProject(project)
        val postsJson = postings.map { post ->
            val node = mutableMapOf<String, Any?>()
            node["title"] = post.title

            val originalPostingLink = "$yonaServer${project.owner}/${project.name}/post/${post.number}"
            val body = if (withWikiCommit) {
                addOriginalAuthorName(
                    relativeLinksToWikiCommitPath(post.body ?: ""),
                    post.authorLoginId ?: "",
                    post.authorName ?: "",
                    "게시글",
                    originalPostingLink
                )
            } else {
                addOriginalAuthorName(
                    relativeLinksToAbsolutePath(post.body ?: ""),
                    post.authorLoginId ?: "",
                    post.authorName ?: "",
                    "게시글",
                    originalPostingLink
                )
            }

            val sb = StringBuilder(body)
            if (withWikiCommit) {
                addAttachmentsStringUsingWikiCommit(sb, ResourceType.BOARD_POST, post.id.toString())
            } else {
                addAttachmentsString(sb, ResourceType.BOARD_POST, post.id.toString())
            }
            node["body"] = sb.toString()

            if (post.createdDate != null) {
                node["created_at"] = formatter.format(post.createdDate)
            }

            val rawComments = postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(post.id!!)
            val comments = rawComments.map { comment ->
                val commentNode = mutableMapOf<String, Any?>()
                if (comment.createdDate != null) {
                    commentNode["created_at"] = formatter.format(comment.createdDate)
                }

                val commentBody = if (withWikiCommit) {
                    addOriginalAuthorName(
                        relativeLinksToWikiCommitPath(comment.contents),
                        comment.authorLoginId ?: "",
                        comment.authorName ?: "",
                        "코멘트",
                        "$originalPostingLink#comment-${comment.id}"
                    )
                } else {
                    addOriginalAuthorName(
                        relativeLinksToAbsolutePath(comment.contents),
                        comment.authorLoginId ?: "",
                        comment.authorName ?: "",
                        "코멘트",
                        "$originalPostingLink#comment-${comment.id}"
                    )
                }

                val csb = StringBuilder(commentBody)
                if (withWikiCommit) {
                    addAttachmentsStringUsingWikiCommit(csb, ResourceType.NONISSUE_COMMENT, comment.id.toString())
                } else {
                    addAttachmentsString(csb, ResourceType.NONISSUE_COMMENT, comment.id.toString())
                }
                commentNode["body"] = csb.toString()
                commentNode
            }

            mapOf(
                "issue" to node,
                "comments" to comments
            )
        }

        return ResponseEntity.ok(mapOf("issues" to postsJson))
    }

    private fun addOriginalAuthorName(
        bodyText: String,
        authorLoginId: String,
        authorName: String,
        type: String,
        link: String
    ): String {
        return "@$authorLoginId ($authorName) 님이 작성한 [$type]($link)입니다. \n\\---\n\n$bodyText"
    }

    private fun relativeLinksToAbsolutePath(text: String): String {
        return text.replace(Regex("(<img src=['\"])/([^'\"<>]+)(['\"]>)")) { matchResult ->
            "${matchResult.groupValues[1]}$yonaServer${matchResult.groupValues[2]}${matchResult.groupValues[3]}"
        }.replace(Regex("\\[([^\\]]*)\\]\\(/([^\\)]*)\\)")) { matchResult ->
            "[${matchResult.groupValues[1]}]($yonaServer${matchResult.groupValues[2]})"
        }
    }

    private fun relativeLinksToWikiCommitPath(text: String): String {
        return text.replace(Regex("(<img src=['\"])/([^'\"<>]+)(['\"]>)")) { matchResult ->
            "${matchResult.groupValues[1]}$yonaServer${matchResult.groupValues[2]}${matchResult.groupValues[3]}"
        }.replace(Regex("\\[([^\\]]*)\\]\\(/([^\\)]*)\\)")) { matchResult ->
            "[${matchResult.groupValues[1]}](../wiki/${matchResult.groupValues[2]}/$text)"
        }
    }

    private fun addAttachmentsString(sb: java.lang.StringBuilder, type: ResourceType, id: String) {
        val attachments = attachmentRepository.findByContainerTypeAndContainerId(type, id)
        if (attachments.isNotEmpty()) {
            sb.append("\n\n--- attachments ---")
            for (attachment in attachments) {
                sb.append("\n[${attachment.name}]($yonaServer" + "files/${attachment.id})")
            }
        }
    }

    private fun addAttachmentsStringUsingWikiCommit(sb: java.lang.StringBuilder, type: ResourceType, id: String) {
        val attachments = attachmentRepository.findByContainerTypeAndContainerId(type, id)
        if (attachments.isNotEmpty()) {
            sb.append("\n\n--- attachments ---")
            for (attachment in attachments) {
                val cleanName = attachment.name.replace("#", "%23")
                sb.append("\n[${attachment.name}](../wiki/files/${attachment.id}/$cleanName)")
            }
        }
    }
}
