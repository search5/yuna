package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
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
    private val accessControl: AccessControl
) {

    @GetMapping("/{owner}/{projectName}/branches")
    fun branches(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (project.projectScope != ProjectScope.PUBLIC || project.isCodeAccessibleMemberOnly == true) {
            if (loginUser == null || (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser))) {
                return "error/403"
            }
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

        model.addAttribute("project", project)
        model.addAttribute("allBranches", filteredBranches)
        model.addAttribute("headBranch", headBranch)
        model.addAttribute("currentUser", loginUser)

        return "code/branches"
    }
}
