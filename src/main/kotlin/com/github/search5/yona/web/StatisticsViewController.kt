package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.user.UserRepository
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@Controller
class StatisticsViewController(
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val accessControl: AccessControl
) {

    @GetMapping("/{owner}/{projectName}/statistics")
    fun statistics(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        
        // AnonymousCheck: 익명(비로그인) 사용자 차단
        if (loginUser == null) {
            return "error/403"
        }

        // Project Permission Check
        if (project.projectScope != ProjectScope.PUBLIC) {
            if (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser)) {
                return "error/403"
            }
        }

        model.addAttribute("project", project)
        model.addAttribute("currentUser", loginUser)
        return "project/statistics"
    }
}
