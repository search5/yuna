package com.github.search5.yona.web

import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private const val MENTION_ADMIN_LOGIN_ID = "admin"
private const val ISSUE_MENTION_SHOW_LIMIT = 20

// yona ProjectApp.mentionList() 대응 (P1-14). mentionListAtCommitDiff/mentionListAtPullRequest
// (커밋/PR 화면 전용 변형, 커밋 작성자/코드댓글 작성자/PR 기여자 등 추가 후보 소스)와
// addProjectAuthorsAndWatchersList/addSharers("@project all"/"@group all" 특수 항목 포함)는
// 이번 패스에서 다루지 않음 - P1-42/43으로 분리(아래 백로그 참고).
@RestController
class MentionController(
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val organizationUserRepository: OrganizationUserRepository,
    private val issueRepository: IssueRepository,
    private val userRepository: UserRepository
) {

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    private fun checkReadPermission(project: Project, user: User?): Boolean {
        if (project.projectScope == ProjectScope.PUBLIC) return true
        if (user == null) return false
        return projectUserRepository.existsByProjectIdAndUserId(project.id!!, user.id!!)
    }

    @GetMapping("/api/{owner}/{projectName}/mentionList")
    fun mentionList(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false, defaultValue = "") query: String,
        @RequestParam(required = false, defaultValue = "") mentionType: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val loginUser = getLoginUser(authentication)
        if (!checkReadPermission(project, loginUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val result = mutableMapOf<String, List<Map<String, String>>>()

        if (mentionType.equals("user", ignoreCase = true)) {
            result["result"] = buildUserMentionList(project, query, loginUser)
        }

        if (mentionType.equals("issue", ignoreCase = true)) {
            result["result"] = buildIssueMentionList(project, query)
        }

        return ResponseEntity.ok(result)
    }

    // yona addProjectMemberList/addGroupMemberList/addSearchedUsers + collectedUsersToMentionList 대응
    private fun buildUserMentionList(project: Project, query: String, loginUser: User?): List<Map<String, String>> {
        val candidates = LinkedHashMap<Long, User>()

        fun addCandidate(user: User) {
            val id = user.id ?: return
            candidates.putIfAbsent(id, user)
        }

        if (query.isBlank() || project.projectScope != ProjectScope.PUBLIC) {
            projectUserRepository.findByProjectId(project.id!!).forEach { addCandidate(it.user) }
            project.organization?.let { org ->
                organizationUserRepository.findByOrganizationId(org.id!!).forEach { addCandidate(it.user) }
            }
        } else {
            userRepository.searchUsers(query, PageRequest.of(0, ISSUE_MENTION_SHOW_LIMIT)).content.forEach { addCandidate(it) }
        }

        // yona: userList.remove(currentUser); userList.add(currentUser) — 나를 항상 맨 뒤로 보낸다.
        val loginUserId = loginUser?.id
        if (loginUserId != null) {
            candidates.remove(loginUserId)
            candidates[loginUserId] = loginUser
        }

        return candidates.values
            .filter { it.loginId.isNotBlank() && it.loginId != MENTION_ADMIN_LOGIN_ID }
            .map { user ->
                mapOf(
                    "loginid" to user.loginId,
                    "searchText" to "${user.name}${user.loginId}",
                    "name" to user.name,
                    "image" to user.avatarUrl
                )
            }
    }

    // yona getIssueList/collectedIssuesToMap/getMentionIssueList 대응
    private fun buildIssueMentionList(project: Project, query: String): List<Map<String, String>> {
        val issues = issueRepository.findForMention(project, query.trim(), PageRequest.of(0, ISSUE_MENTION_SHOW_LIMIT))
        return issues.map { issue ->
            mapOf(
                "name" to "${issue.number}${issue.title}",
                "issueNo" to (issue.number?.toString() ?: ""),
                "title" to issue.title
            )
        }
    }
}
