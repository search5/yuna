package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.vcs.RepositoryService
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Controller
class BranchApiController(
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val repositoryService: RepositoryService,
    private val accessControl: AccessControl
) {

    @PostMapping("/{owner}/{projectName}/code/{branch}/setAsDefault")
    fun setAsDefault(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable branch: String,
        authentication: Authentication?
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (loginUser == null || (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser))) {
            return "error/403"
        }

        val repository = repositoryService.getRepository(project)
        val decodedBranchName = URLDecoder.decode(branch.trimStart('/'), StandardCharsets.UTF_8.name())

        repository.setDefaultBranch(decodedBranchName)

        return "redirect:/$owner/$projectName/branches"
    }

    @DeleteMapping("/{owner}/{projectName}/code/{branch}")
    fun deleteBranch(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable branch: String,
        authentication: Authentication?
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (loginUser == null || !projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!)) {
            return "error/403"
        }

        val repository = repositoryService.getRepository(project)
        val decodedBranchName = URLDecoder.decode(branch.trimStart('/'), StandardCharsets.UTF_8.name())

        repository.deleteBranch(decodedBranchName)

        return "redirect:/$owner/$projectName/branches"
    }
}
