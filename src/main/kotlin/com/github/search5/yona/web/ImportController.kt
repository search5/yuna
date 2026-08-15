package com.github.search5.yona.web

import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.project.*
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.io.File

@Controller
class ImportController(
    private val projectService: ProjectService,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val organizationUserRepository: OrganizationUserRepository,
    private val gitService: GitService
) {

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    @GetMapping("/_import")
    fun importForm(authentication: Authentication?, model: Model): String {
        val loginUser = getLoginUser(authentication) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        val orgUserList = organizationUserRepository.findByUserIdAndRoleId(loginUser.id!!, RoleType.ORG_ADMIN.roleType)
        
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("orgUserList", orgUserList)
        return "project/importing"
    }

    @PostMapping("/_import")
    fun newProject(
        @RequestParam url: String,
        @RequestParam owner: String,
        @RequestParam name: String,
        @RequestParam(required = false) overview: String?,
        @RequestParam(required = false, defaultValue = "PUBLIC") projectScope: ProjectScope,
        @RequestParam(required = false) authId: String?,
        @RequestParam(required = false) authPw: String?,
        @RequestParam(required = false, defaultValue = "true") code: Boolean,
        @RequestParam(required = false, defaultValue = "true") issue: Boolean,
        @RequestParam(required = false, defaultValue = "true") pullRequest: Boolean,
        @RequestParam(required = false, defaultValue = "true") review: Boolean,
        @RequestParam(required = false, defaultValue = "true") milestone: Boolean,
        @RequestParam(required = false, defaultValue = "true") board: Boolean,
        authentication: Authentication?,
        model: Model,
        response: jakarta.servlet.http.HttpServletResponse
    ): String {
        val loginUser = getLoginUser(authentication) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        val orgUserList = organizationUserRepository.findByUserIdAndRoleId(loginUser.id!!, RoleType.ORG_ADMIN.roleType)

        model.addAttribute("currentUser", loginUser)
        model.addAttribute("orgUserList", orgUserList)

        // URL 유효성 검사
        if (url.isBlank()) {
            response.status = jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST
            model.addAttribute("error", "URL은 비어있을 수 없습니다.")
            return "project/importing"
        }

        // 프로젝트 이름 중복 검사
        if (projectRepository.findByOwnerAndName(owner, name).isPresent) {
            response.status = jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST
            model.addAttribute("error", "이미 존재하는 프로젝트 이름입니다.")
            return "project/importing"
        }

        var clonedDir: File? = null
        try {
            // Git 리포지토리 복제
            clonedDir = gitService.cloneRepository(url, owner, name, authId, authPw)

            // 프로젝트 생성 및 저장
            val project = Project(
                name = name,
                owner = owner,
                overview = overview ?: "",
                projectScope = projectScope,
                isCodeEnabled = code,
                isIssueEnabled = issue,
                isPullRequestEnabled = pullRequest,
                isReviewEnabled = review,
                isMilestoneEnabled = milestone,
                isBoardEnabled = board
            )
            val savedProject = projectService.createProject(project, loginUser)

            return "redirect:/$owner/$name"
        } catch (e: Exception) {
            response.status = jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST
            // 복제 실패 시 생성 중인 디렉터리 삭제
            clonedDir?.let {
                if (it.exists()) {
                    it.deleteRecursively()
                }
            }
            model.addAttribute("error", "Git 클론 중 오류가 발생했습니다: ${e.message}")
            return "project/importing"
        }
    }
}
