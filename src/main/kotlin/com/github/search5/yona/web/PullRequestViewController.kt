package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.enumeration.Operation
import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.pullrequest.CommentThreadRepository
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.domain.pullrequest.PullRequestService
import com.github.search5.yona.domain.vcs.RepositoryService
import com.github.search5.yona.domain.user.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam

@Controller
class PullRequestViewController(
    private val projectRepository: ProjectRepository,
    private val pullRequestService: PullRequestService,
    private val pullRequestRepository: PullRequestRepository,
    private val repositoryService: RepositoryService,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val commentThreadRepository: CommentThreadRepository,
    private val pullRequestCommitRepository: com.github.search5.yona.domain.pullrequest.PullRequestCommitRepository,
    private val issueRepository: com.github.search5.yona.domain.issue.IssueRepository,
    private val accessControl: AccessControl
) {
    // 이슈 자동 닫기 정규식 패턴 (대소문자 구분 없이 close(s/d), fix(es/ed), resolve(s/d) #숫자)
    private val closePattern = "(?i)(?:close[s|d]?|fix[e[s|d]]?|resolve[s|d]?)\\s+#(\\d+)".toRegex()


    @GetMapping("/{owner}/{projectName}/pulls")
    fun listPullRequests(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false, defaultValue = "open") state: String,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (!checkMemberAccess(project, loginUser)) {
            return "error/403"
        }

        val stateEnum = when (state.lowercase()) {
            "closed" -> State.CLOSED
            "all" -> State.ALL
            else -> State.OPEN
        }

        val pageable = PageRequest.of(page, 20, Sort.by(Sort.Direction.DESC, "id"))
        val prPage = if (stateEnum == State.ALL) {
            pullRequestRepository.findByToProject(project, pageable)
        } else {
            pullRequestRepository.findByToProjectAndState(project, stateEnum, pageable)
        }

        return renderList(model, project, loginUser, prPage, state)
    }

    // yona PullRequestApp.closedPullRequests 대응. CLOSED/MERGED 상태를 모두 "닫힌 PR"로 취급한다.
    @GetMapping("/{owner}/{projectName}/closedPullRequests")
    fun closedPullRequests(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (!checkMemberAccess(project, loginUser)) {
            return "error/403"
        }

        val pageable = PageRequest.of(page, 20, Sort.by(Sort.Direction.DESC, "id"))
        val prPage = pullRequestRepository.findByToProjectAndStateIn(project, listOf(State.CLOSED, State.MERGED), pageable)

        return renderList(model, project, loginUser, prPage, "closed")
    }

    // yona PullRequestApp.sentPullRequests 대응. 이 프로젝트가 출발지(fromProject)인 PR 목록.
    @GetMapping("/{owner}/{projectName}/sentPullRequests")
    fun sentPullRequests(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (!checkMemberAccess(project, loginUser)) {
            return "error/403"
        }

        val pageable = PageRequest.of(page, 20, Sort.by(Sort.Direction.DESC, "id"))
        val prPage = pullRequestRepository.findByFromProject(project, pageable)

        return renderList(model, project, loginUser, prPage, "sent")
    }

    private fun checkMemberAccess(
        project: com.github.search5.yona.domain.project.Project,
        loginUser: com.github.search5.yona.domain.user.User?
    ): Boolean {
        return accessControl.isAllowed(loginUser, project, Operation.READ)
    }

    private fun renderList(
        model: Model,
        project: com.github.search5.yona.domain.project.Project,
        loginUser: com.github.search5.yona.domain.user.User?,
        prPage: org.springframework.data.domain.Page<com.github.search5.yona.domain.pullrequest.PullRequest>,
        state: String
    ): String {
        model.addAttribute("project", project)
        model.addAttribute("prPage", prPage)
        model.addAttribute("state", state)
        model.addAttribute("currentUser", loginUser)
        return "pullrequest/list"
    }

    @GetMapping("/{owner}/{projectName}/pull/{number}")
    fun viewPullRequest(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        @RequestParam(required = false, defaultValue = "conversation") tab: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
            return "error/403"
        }

        val pullRequest = pullRequestService.getPullRequest(project.id!!, number) ?: return "error/404"

        val mergeResult = try {
            pullRequestService.attemptMerge(pullRequest.id!!)
        } catch (e: Exception) {
            null
        }

        if (tab == "changes") {
            val diffs = try {
                pullRequestService.getDiff(pullRequest)
            } catch (e: Exception) {
                emptyList()
            }
            model.addAttribute("diffs", diffs)
            
            val commentThreads = commentThreadRepository.findByPullRequest(pullRequest)
            model.addAttribute("commentThreads", commentThreads)
        } else if (tab == "conversation") {
            val commentThreads = commentThreadRepository.findByPullRequest(pullRequest)
                .sortedBy { it.createdDate }
            model.addAttribute("commentThreads", commentThreads)
        }

        val referredIssues = getReferredIssues(pullRequest)
        model.addAttribute("referredIssues", referredIssues)

        model.addAttribute("project", project)
        model.addAttribute("pr", pullRequest)
        model.addAttribute("mergeResult", mergeResult)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("tab", tab)

        return "pullrequest/view"
    }

    private fun getReferredIssues(pullRequest: com.github.search5.yona.domain.pullrequest.PullRequest): List<com.github.search5.yona.domain.issue.Issue> {
        val project = pullRequest.toProject
        val textsToSearch = mutableListOf<String>()

        textsToSearch.add(pullRequest.title)
        pullRequest.body?.let { textsToSearch.add(it) }

        val commits = pullRequestCommitRepository.findByPullRequest(pullRequest)
        for (commit in commits) {
            textsToSearch.add(commit.commitMessage)
        }

        val issueNumbers = mutableSetOf<Long>()
        for (text in textsToSearch) {
            closePattern.findAll(text).forEach { matchResult ->
                val numberStr = matchResult.groups[1]?.value
                if (numberStr != null) {
                    numberStr.toLongOrNull()?.let { issueNumbers.add(it) }
                }
            }
        }

        if (issueNumbers.isEmpty()) {
            return emptyList()
        }

        val result = mutableListOf<com.github.search5.yona.domain.issue.Issue>()
        for (number in issueNumbers) {
            val issue = issueRepository.findByProjectAndNumber(project, number)
            if (issue != null) {
                result.add(issue)
            }
        }
        return result
    }

    @GetMapping("/{owner}/{projectName}/pull/new")
    fun createPullRequestForm(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (loginUser == null || (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser))) {
            return "error/403"
        }

        // 브랜치 목록 획득
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
        model.addAttribute("branches", branches)
        model.addAttribute("defaultBranch", defaultBranch)
        model.addAttribute("currentUser", loginUser)

        return "pullrequest/create"
    }

    // yona PullRequestApp.editPullRequestForm 대응. 실제 제목/본문 수정은
    // PullRequestController.updatePullRequest(PUT /api/.../pullrequests/{number})가 처리하므로,
    // 여기서는 기존 값이 채워진 폼만 렌더링하고 동일한 권한 체크(작성자 또는 매니저)만 수행한다.
    @GetMapping("/{owner}/{projectName}/pull/{number}/edit")
    fun editPullRequestForm(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        val pullRequest = pullRequestService.getPullRequest(project.id!!, number) ?: return "error/404"

        if (!isManagerOrContributor(project, pullRequest.contributor.id, loginUser)) {
            return "error/403"
        }

        model.addAttribute("project", project)
        model.addAttribute("pr", pullRequest)
        model.addAttribute("currentUser", loginUser)

        return "pullrequest/edit"
    }

    private fun isManagerOrContributor(
        project: com.github.search5.yona.domain.project.Project,
        contributorId: Long?,
        user: com.github.search5.yona.domain.user.User?
    ): Boolean {
        if (user == null) return false
        if (contributorId == user.id) return true
        return projectUserRepository.findByProjectIdAndUserId(project.id!!, user.id!!)
            .map { it.role.id == com.github.search5.yona.domain.role.RoleType.MANAGER.roleType }
            .orElse(false)
    }

    @GetMapping(value = [
        "/{owner}/{projectName}/pull/{number}/changes",
        "/{owner}/{projectName}/pullRequest/{number}/changes"
    ])
    fun viewChanges(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        authentication: Authentication?,
        model: Model
    ): String {
        return viewChangesInternal(owner, projectName, number, null, authentication, model)
    }

    @GetMapping(value = [
        "/{owner}/{projectName}/pull/{number}/changes/{commitId}",
        "/{owner}/{projectName}/pullRequest/{number}/changes/{commitId}"
    ])
    fun viewSpecificChange(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        @PathVariable commitId: String,
        authentication: Authentication?,
        model: Model
    ): String {
        return viewChangesInternal(owner, projectName, number, commitId, authentication, model)
    }

    private fun viewChangesInternal(
        owner: String,
        projectName: String,
        number: Long,
        commitId: String?,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
            return "error/403"
        }

        val pullRequest = pullRequestService.getPullRequest(project.id!!, number) ?: return "error/404"

        val mergeResult = try {
            pullRequestService.attemptMerge(pullRequest.id!!)
        } catch (e: Exception) {
            null
        }

        val diffs = try {
            if (commitId.isNullOrEmpty()) {
                pullRequestService.getDiff(pullRequest)
            } else {
                pullRequestService.getDiff(pullRequest, commitId)
            }
        } catch (e: Exception) {
            emptyList()
        }

        val commentThreads = commentThreadRepository.findByPullRequest(pullRequest)
        val referredIssues = getReferredIssues(pullRequest)

        model.addAttribute("project", project)
        model.addAttribute("pr", pullRequest)
        model.addAttribute("diffs", diffs)
        model.addAttribute("commentThreads", commentThreads)
        model.addAttribute("referredIssues", referredIssues)
        model.addAttribute("mergeResult", mergeResult)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("commitId", commitId)
        model.addAttribute("tab", "changes")

        return "pullrequest/view"
    }
}
