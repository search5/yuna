package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.enumeration.Operation
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.vcs.RepositoryService
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@Controller
class BranchViewController(
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val repositoryService: RepositoryService,
    private val accessControl: AccessControl,
    private val pullRequestRepository: PullRequestRepository
) {

    @GetMapping("/{owner}/{projectName}/branches")
    fun branches(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (project.isCodeAccessibleMemberOnly == true) {
            if (loginUser == null || (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser))) {
                return "error/403"
            }
        } else if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
            return "error/403"
        }

        val vcsType = project.vcs?.uppercase() ?: "GIT"
        if (vcsType != "GIT") {
            return "error/403"
        }

        val repository = repositoryService.getRepository(project)
        val allBranches = repository.getBranches()
        val headBranch = repository.getHeadBranch()

        val filteredBranches = if (headBranch != null) {
            allBranches.filter { it.name != headBranch.name }
        } else {
            allBranches
        }

        // yona GitRepository.setTheLatestPullRequest() 대응 (그룹10 #157) — 브랜치별로 이 프로젝트로
        // 보낸 가장 최근 PR을 찾아 "보낸 코드" 컬럼에 링크로 보여준다.
        val pullRequestsByBranch = (filteredBranches + listOfNotNull(headBranch)).associate { branch ->
            branch.shortName to pullRequestRepository.findFirstByFromProjectAndFromBranchAndToProjectOrderByNumberDesc(
                project, branch.shortName, project
            )
        }

        // yona code/branches.scala.html:59-62 대응 — DELETE 또는 UPDATE 권한이 있을 때만 액션 컬럼(빈 th 포함) 자체를 렌더링한다.
        val showActionsColumn = accessControl.isAllowed(loginUser, project, Operation.DELETE) ||
            accessControl.isAllowed(loginUser, project, Operation.UPDATE)
        val canUpdate = accessControl.isAllowed(loginUser, project, Operation.UPDATE)
        val canDelete = accessControl.isAllowed(loginUser, project, Operation.DELETE)

        model.addAttribute("project", project)
        model.addAttribute("allBranches", filteredBranches)
        model.addAttribute("headBranch", headBranch)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("pullRequestsByBranch", pullRequestsByBranch)
        model.addAttribute("showActionsColumn", showActionsColumn)
        model.addAttribute("canUpdate", canUpdate)
        model.addAttribute("canDelete", canDelete)

        return "code/branches"
    }
}
