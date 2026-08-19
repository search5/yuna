package com.github.search5.yona.web

import com.github.search5.yona.domain.board.PostingCommentRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.issue.IssueCommentRepository
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.watch.WatchRepository
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

// yona ProjectApp.mentionList() 대응 (P1-14, P1-42). mentionListAtCommitDiff/mentionListAtPullRequest
// (커밋/PR 화면 전용 변형, 커밋 작성자/코드댓글 작성자/PR 기여자 등 추가 후보 소스)는
// 이번 패스에서도 다루지 않음 - P1-43으로 분리(아래 백로그 참고).
@RestController
class MentionController(
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val organizationUserRepository: OrganizationUserRepository,
    private val issueRepository: IssueRepository,
    private val userRepository: UserRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val postingRepository: PostingRepository,
    private val postingCommentRepository: PostingCommentRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val watchRepository: WatchRepository
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
        @RequestParam(required = false) number: Long?,
        @RequestParam(required = false) resourceType: String?,
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
            result["result"] = buildUserMentionList(project, query, loginUser, number, resourceType)
        }

        if (mentionType.equals("issue", ignoreCase = true)) {
            result["result"] = buildIssueMentionList(project, query)
        }

        return ResponseEntity.ok(result)
    }

    // yona addProjectMemberList/addGroupMemberList/addProjectAuthorsAndWatchersList/addSharers/
    // addSearchedUsers + collectedUsersToMentionList/addProjectNameToMentionList/addOrganizationNameToMentionList 대응
    private fun buildUserMentionList(
        project: Project,
        query: String,
        loginUser: User?,
        number: Long?,
        resourceType: String?
    ): List<Map<String, String>> {
        val candidates = LinkedHashMap<Long, User>()

        fun addCandidate(user: User?) {
            val id = user?.id ?: return
            candidates.putIfAbsent(id, user)
        }

        if (query.isBlank() || project.projectScope != ProjectScope.PUBLIC) {
            collectAuthorAndCommenter(project, number, resourceType).forEach { addCandidate(it) }
            projectUserRepository.findByProjectId(project.id!!).forEach { addCandidate(it.user) }
            project.organization?.let { org ->
                organizationUserRepository.findByOrganizationId(org.id!!).forEach { addCandidate(it.user) }
            }
            collectProjectAuthorsAndWatchers(project).forEach { addCandidate(it) }
            collectSharers(project, number, resourceType).forEach { addCandidate(it) }
        } else {
            userRepository.searchUsers(query, PageRequest.of(0, ISSUE_MENTION_SHOW_LIMIT)).content.forEach { addCandidate(it) }
        }

        // yona: userList.remove(currentUser); userList.add(currentUser) — 나를 항상 맨 뒤로 보낸다.
        val loginUserId = loginUser?.id
        if (loginUserId != null) {
            candidates.remove(loginUserId)
            candidates[loginUserId] = loginUser
        }

        val userMentions = candidates.values
            .filter { it.loginId.isNotBlank() && it.loginId != MENTION_ADMIN_LOGIN_ID }
            .map { user ->
                mapOf(
                    "loginid" to user.loginId,
                    "searchText" to "${user.name}${user.loginId}",
                    "name" to user.name,
                    "image" to user.avatarUrl
                )
            }
            .toMutableList()

        addProjectNameToMentionList(userMentions, project)
        addOrganizationNameToMentionList(userMentions, project)

        return userMentions
    }

    // yona collectAuthorAndCommenter 대응 - 댓글 작성자를 최근 순으로, 마지막에 게시물 작성자를 추가한다.
    private fun collectAuthorAndCommenter(project: Project, number: Long?, resourceType: String?): List<User> {
        if (number == null || resourceType == null) return emptyList()

        val (commenterLoginIdsAsc, authorLoginId) = when {
            resourceType.equals("ISSUE_POST", ignoreCase = true) -> {
                val issue = issueRepository.findByProjectAndNumber(project, number) ?: return emptyList()
                val comments = issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(issue.id!!)
                comments.mapNotNull { it.authorLoginId } to issue.authorLoginId
            }
            resourceType.equals("BOARD_POST", ignoreCase = true) -> {
                val posting = postingRepository.findByProjectAndNumber(project, number) ?: return emptyList()
                val comments = postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(posting.id!!)
                comments.mapNotNull { it.authorLoginId } to posting.authorLoginId
            }
            else -> return emptyList()
        }

        // yona: 댓글을 오래된 순으로 순회하며 등장할 때마다 리스트 끝으로 옮기고(remove+add), 마지막에 전체를 뒤집는다
        // => 결과적으로 최근에 댓글을 남긴 사람이 맨 앞에 온다.
        val ordered = LinkedHashSet<String>()
        commenterLoginIdsAsc.forEach {
            ordered.remove(it)
            ordered.add(it)
        }
        val loginIds = ordered.toList().reversed().toMutableList()
        if (authorLoginId != null && !loginIds.contains(authorLoginId)) {
            loginIds.add(authorLoginId)
        }

        return loginIds.mapNotNull { userRepository.findByLoginId(it).orElse(null) }
    }

    // yona Project.findAuthorsAndWatchers 대응 - 프로젝트의 이슈/게시글/PR 작성자와 프로젝트 워처를 모은다.
    private fun collectProjectAuthorsAndWatchers(project: Project): List<User> {
        val authorIds = LinkedHashSet<Long>()
        issueRepository.findByProject(project).forEach { it.authorId?.let { id -> authorIds.add(id) } }
        postingRepository.findByProject(project).forEach { it.authorId?.let { id -> authorIds.add(id) } }
        pullRequestRepository.findByToProject(project).forEach { pr -> pr.contributor.id?.let { authorIds.add(it) } }

        val watcherIds = watchRepository
            .findByResourceTypeAndResourceId(ResourceType.PROJECT, project.id.toString())
            .mapNotNull { it.user.id }

        val allIds = LinkedHashSet<Long>()
        allIds.addAll(authorIds)
        allIds.addAll(watcherIds)

        return allIds.mapNotNull { userRepository.findById(it).orElse(null) }
    }

    // yona addSharers 대응 - 이슈 공유자 목록(현재는 ISSUE_POST만 공유 기능이 있음)
    private fun collectSharers(project: Project, number: Long?, resourceType: String?): List<User> {
        if (number == null || !resourceType.equals("ISSUE_POST", ignoreCase = true)) return emptyList()
        val issue = issueRepository.findByProjectAndNumber(project, number) ?: return emptyList()
        return issue.sharers.map { it.user }
    }

    // yona addProjectNameToMentionList 대응 - "@project all:" 특수 멘션 항목을 추가한다.
    private fun addProjectNameToMentionList(users: MutableList<Map<String, String>>, project: Project) {
        val entry = mapOf(
            "loginid" to "${project.owner}/${project.name}",
            "username" to project.name,
            "name" to "@project all:",
            "searchText" to "${project.owner}/${project.name}/project/member/all",
            "image" to "/projects/${project.id}/logo"
        )
        if (users.size > 9) {
            val index = if (project.organization != null) 8 else 9
            users.add(index.coerceAtMost(users.size), entry)
        } else {
            users.add(entry)
        }
    }

    // yona addOrganizationNameToMentionList 대응 - "@group all:" 특수 멘션 항목을 추가한다.
    // (legacy는 size>9일 때 인덱스 9에 추가 + 항상 끝에도 추가해 중복이 생기는 버그가 있어, 여기서는 한 번만 추가한다.)
    private fun addOrganizationNameToMentionList(users: MutableList<Map<String, String>>, project: Project) {
        val organization = project.organization ?: return
        val entry = mapOf(
            "loginid" to organization.name,
            "username" to organization.name,
            "name" to "@group all: ",
            "searchText" to "${organization.name}/group/org/member/all",
            "image" to "/organizations/${organization.id}/logo"
        )
        if (users.size > 9) {
            users.add(9.coerceAtMost(users.size), entry)
        } else {
            users.add(entry)
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
