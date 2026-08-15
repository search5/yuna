package com.github.search5.yona.web

import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.vcs.RepositoryService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Controller
class BranchController(
    private val projectRepository: ProjectRepository,
    private val repositoryService: RepositoryService
) {

    @GetMapping("/projects/{owner}/{projectName}/branches")
    fun branches(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"

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

        return "code/branches"
    }

    @PostMapping("/projects/{owner}/{projectName}/code/{branch}/setAsDefault")
    fun setAsDefault(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable branch: String
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"

        val repository = repositoryService.getRepository(project)
        val decodedBranchName = URLDecoder.decode(branch.trimStart('/'), StandardCharsets.UTF_8.name())
        
        repository.setDefaultBranch(decodedBranchName)

        return "redirect:/projects/$owner/$projectName/branches"
    }

    @DeleteMapping("/projects/{owner}/{projectName}/code/{branch}")
    fun deleteBranch(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable branch: String
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"

        val repository = repositoryService.getRepository(project)
        val decodedBranchName = URLDecoder.decode(branch.trimStart('/'), StandardCharsets.UTF_8.name())

        repository.deleteBranch(decodedBranchName)

        return "redirect:/projects/$owner/$projectName/branches"
    }
}
