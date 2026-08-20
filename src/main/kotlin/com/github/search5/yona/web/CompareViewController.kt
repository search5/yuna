package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.pullrequest.CommentThreadRepository
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.vcs.RepositoryService
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@Controller
class CompareViewController(
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val repositoryService: RepositoryService,
    private val commentThreadRepository: CommentThreadRepository,
    private val accessControl: AccessControl
) {

    @GetMapping("/{owner}/{projectName}/compare/{revA:.+}..{revB:.+}")
    fun compare(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable revA: String,
        @PathVariable revB: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        
        // 권한 검증
        if (project.projectScope != ProjectScope.PUBLIC || project.isCodeAccessibleMemberOnly == true) {
            if (loginUser == null || (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser))) {
                return "error/403"
            }
        }

        val repository = repositoryService.getRepository(project)
        val commitA = repository.getCommit(revA)
        val commitB = repository.getCommit(revB)

        if (commitA == null || commitB == null) {
            return "error/404"
        }

        val commentThreads = commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(project, revB)

        model.addAttribute("project", project)
        model.addAttribute("commitA", commitA)
        model.addAttribute("commitB", commitB)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("commentThreads", commentThreads)

        val vcsType = project.vcs?.uppercase() ?: "GIT"
        return if (vcsType == "SUBVERSION" || vcsType == "SVN") {
            val patch = repository.getPatch(revA, revB) ?: return "error/404"
            model.addAttribute("patch", patch)
            "code/compare_svn"
        } else {
            val diffs = repository.getDiff(revA, revB) ?: return "error/404"
            model.addAttribute("diffs", diffs)
            "code/compare"
        }
    }
}
