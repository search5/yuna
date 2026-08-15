package com.github.search5.yona.web

import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.board.Posting
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectService
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.user.UserState
import com.github.search5.yona.domain.role.RoleRepository
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.enumeration.State
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import org.springframework.http.ResponseEntity
import java.time.Instant
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

import com.github.search5.yona.domain.mail.MailService
import org.springframework.util.MultiValueMap
import tools.jackson.databind.ObjectMapper
import com.github.search5.yona.domain.support.YonaUpdateService
import com.github.search5.yona.domain.support.DiagnosticService
import org.springframework.web.multipart.MultipartFile
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType


@Controller
@RequestMapping(value = ["/site", "/sites"])
class SiteController(
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val issueRepository: IssueRepository,
    private val postingRepository: PostingRepository,
    private val projectService: ProjectService,
    private val mailService: MailService,
    private val diagnosticService: DiagnosticService,
    private val objectMapper: ObjectMapper,
    private val yonaUpdateService: YonaUpdateService,
    private val environment: org.springframework.core.env.Environment
) {

    private fun checkAdmin(authentication: Authentication?): User {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (loginUser == null || !loginUser.isSiteManager) {
            throw IllegalArgumentException("Unauthorized access")
        }
        return loginUser
    }


    @ExceptionHandler(IllegalArgumentException::class)
    fun handleUnauthorized(e: IllegalArgumentException): String {
        return "error/403"
    }

    // 1. 사용자 관리 화면
    @GetMapping("/userList")
    fun userList(
        @RequestParam(value = "page", defaultValue = "1") pageNum: Int,
        @RequestParam(value = "query", defaultValue = "") query: String,
        @RequestParam(value = "state", defaultValue = "ACTIVE") stateStr: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val currentUser = checkAdmin(authentication)
        val userState = UserState.of(stateStr) ?: UserState.ACTIVE

        val pageable = PageRequest.of(pageNum - 1, 25, Sort.by("name").ascending())
        val users = userRepository.findUsersForAdmin(userState, "%$query%", pageable)

        // 사이트 관리자의 총 수
        val adminCount = userRepository.countUsersForAdmin(UserState.SITE_ADMIN, "%%")

        model.addAttribute("users", users)
        model.addAttribute("userState", userState)
        model.addAttribute("query", query)
        model.addAttribute("currentUser", currentUser)
        model.addAttribute("adminCount", adminCount)

        return "site/userList"
    }

    // 2. 계정 잠금/해제 토글
    @PostMapping("/toggleAccountLock")
    fun toggleAccountLock(
        @RequestParam loginId: String,
        @RequestParam(value = "state", defaultValue = "ACTIVE") stateStr: String,
        @RequestParam(value = "query", defaultValue = "") query: String,
        authentication: Authentication?,
        redirectAttributes: RedirectAttributes
    ): String {
        checkAdmin(authentication)
        val targetUser = userRepository.findByLoginId(loginId).orElse(null)
        if (targetUser != null) {
            targetUser.state = if (targetUser.state == UserState.ACTIVE) UserState.LOCKED else UserState.ACTIVE
            targetUser.lastStateModifiedDate = Instant.now()
            userRepository.save(targetUser)
        }
        redirectAttributes.addAttribute("state", stateStr)
        redirectAttributes.addAttribute("query", query)
        return "redirect:/sites/userList"
    }

    // 3. 게스트 모드 토글
    @PostMapping("/toggleGuestMode")
    fun toggleGuestMode(
        @RequestParam loginId: String,
        @RequestParam(value = "state", defaultValue = "ACTIVE") stateStr: String,
        @RequestParam(value = "query", defaultValue = "") query: String,
        authentication: Authentication?,
        redirectAttributes: RedirectAttributes
    ): String {
        checkAdmin(authentication)
        val targetUser = userRepository.findByLoginId(loginId).orElse(null)
        if (targetUser != null) {
            targetUser.isGuest = !targetUser.isGuest
            userRepository.save(targetUser)
        }
        redirectAttributes.addAttribute("state", stateStr)
        redirectAttributes.addAttribute("query", query)
        return "redirect:/sites/userList"
    }

    // 4. 관리자 권한 토글
    @PostMapping("/toggleSiteAdminRole/{loginId}")
    fun toggleSiteAdminRole(
        @PathVariable loginId: String,
        @RequestParam(value = "state", defaultValue = "ACTIVE") stateStr: String,
        @RequestParam(value = "query", defaultValue = "") query: String,
        authentication: Authentication?,
        redirectAttributes: RedirectAttributes
    ): String {
        checkAdmin(authentication)
        val targetUser = userRepository.findByLoginId(loginId).orElse(null)
        if (targetUser != null) {
            targetUser.state = if (targetUser.state == UserState.SITE_ADMIN) UserState.ACTIVE else UserState.SITE_ADMIN
            targetUser.lastStateModifiedDate = Instant.now()
            userRepository.save(targetUser)
        }
        redirectAttributes.addAttribute("state", stateStr)
        redirectAttributes.addAttribute("query", query)
        return "redirect:/sites/userList"
    }

    // 5. 임시 비밀번호 강제 초기화
    @PostMapping("/users/{loginId}/reset-password")
    @ResponseBody
    fun resetUserPasswordBySiteManager(
        @PathVariable loginId: String,
        authentication: Authentication?
    ): ResponseEntity<Map<String, Any>> {
        return try {
            checkAdmin(authentication)
            val targetUser = userRepository.findByLoginId(loginId).orElse(null)
                ?: return ResponseEntity.status(404).body(mapOf("isSuccess" to false, "reason" to "USER_NOT_FOUND"))

            val newPassword = UUID.randomUUID().toString().substring(0, 6)
            val salt = UUID.randomUUID().toString().substring(0, 8)

            targetUser.passwordSalt = salt
            targetUser.password = hashPassword(newPassword, salt)
            userRepository.save(targetUser)

            ResponseEntity.ok(mapOf(
                "loginId" to targetUser.loginId,
                "name" to targetUser.name,
                "newPassword" to newPassword,
                "isSuccess" to true
            ))
        } catch (e: Exception) {
            ResponseEntity.status(403).body(mapOf("isSuccess" to false, "reason" to "FORBIDDEN"))
        }
    }

    private fun hashPassword(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.reset()
        digest.update(salt.toByteArray(Charsets.UTF_8))
        var hashed = digest.digest(password.toByteArray(Charsets.UTF_8))
        for (i in 1 until 1024) {
            digest.reset()
            hashed = digest.digest(hashed)
        }
        return Base64.getEncoder().encodeToString(hashed)
    }

    // 6. 회원 탈퇴 (논리 삭제)
    @DeleteMapping("/user/delete/{userId}")
    @ResponseBody
    fun deleteUser(
        @PathVariable userId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, Any>> {
        try {
            checkAdmin(authentication)
            if (isOnlyManager(userId)) {
                return ResponseEntity.status(403).body(mapOf("isSuccess" to false, "reason" to "ONLY_MANAGER"))
            }

            val targetUser = userRepository.findById(userId).orElse(null)
                ?: return ResponseEntity.status(404).body(mapOf("isSuccess" to false, "reason" to "USER_NOT_FOUND"))

            // 사용자의 프로젝트 멤버십 관계 수동 소거
            val projectUsers = projectUserRepository.findByUserId(userId)
            projectUserRepository.deleteAll(projectUsers)

            targetUser.state = UserState.DELETED
            targetUser.lastStateModifiedDate = Instant.now()
            userRepository.save(targetUser)

            return ResponseEntity.ok(mapOf("isSuccess" to true))
        } catch (e: Exception) {
            return ResponseEntity.status(403).body(mapOf("isSuccess" to false, "reason" to "FORBIDDEN"))
        }
    }

    private fun isOnlyManager(userId: Long): Boolean {
        // 이 사용자가 MANAGER인 프로젝트들 중, 해당 프로젝트 내 MANAGER 수가 1명 이하(자기자신 뿐)인 프로젝트가 있는지 확인
        val projectUsers = projectUserRepository.findByUserId(userId)
        val managedProjects = projectUsers.filter { it.role.id == RoleType.MANAGER.roleType }.map { it.project }
        for (project in managedProjects) {
            val managers = projectUserRepository.findByProjectId(project.id!!)
                .filter { it.role.id == RoleType.MANAGER.roleType }
            if (managers.size <= 1) {
                return true
            }
        }
        return false
    }

    // 7. 프로젝트 관리 화면
    @GetMapping("/projectList")
    fun projectList(
        @RequestParam(value = "page", defaultValue = "1") pageNum: Int,
        @RequestParam(value = "projectName", defaultValue = "") projectName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val currentUser = checkAdmin(authentication)
        val pageable = PageRequest.of(pageNum - 1, 25, Sort.by("name").ascending())
        val projects = projectRepository.findProjectsForAdmin("%$projectName%", pageable)

        model.addAttribute("projects", projects)
        model.addAttribute("projectName", projectName)
        model.addAttribute("currentUser", currentUser)

        return "site/projectList"
    }

    // 8. 프로젝트 강제 삭제
    @DeleteMapping("/project/delete/{projectId}")
    fun deleteProject(
        @PathVariable projectId: Long,
        authentication: Authentication?
    ): String {
        checkAdmin(authentication)
        projectService.deleteProject(projectId)
        return "redirect:/sites/projectList"
    }

    // 9. 이슈 관리 화면
    @GetMapping("/issueList")
    fun issueList(
        @RequestParam(value = "page", defaultValue = "1") pageNum: Int,
        @RequestParam(value = "state", defaultValue = "open") stateStr: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val currentUser = checkAdmin(authentication)
        val currentState = State.getValue(stateStr.lowercase())
        
        val pageable = PageRequest.of(pageNum - 1, 30, Sort.by("createdDate").descending())
        val issues = if (currentState == State.ALL) {
            issueRepository.findAll(pageable)
        } else {
            issueRepository.findByState(currentState, pageable)
        }

        model.addAttribute("issues", issues)
        model.addAttribute("currentState", currentState)
        model.addAttribute("state", stateStr)
        model.addAttribute("currentUser", currentUser)
        model.addAttribute("userRepository", userRepository)

        return "site/issueList"
    }

    // 10. 자유게시글 관리 화면
    @GetMapping("/postList")
    fun postList(
        @RequestParam(value = "page", defaultValue = "1") pageNum: Int,
        authentication: Authentication?,
        model: Model
    ): String {
        val currentUser = checkAdmin(authentication)
        val pageable = PageRequest.of(pageNum - 1, 30, Sort.by("createdDate").descending())
        val posts = postingRepository.findAll(pageable)

        model.addAttribute("posts", posts)
        model.addAttribute("currentUser", currentUser)
        model.addAttribute("userRepository", userRepository)
        model.addAttribute("message", "title.siteSetting")

        return "site/postList"
    }

    // 11. 메일 작성 화면
    @GetMapping("/mail")
    fun writeMail(
        authentication: Authentication?,
        model: Model
    ): String {
        val currentUser = checkAdmin(authentication)
        
        val notConfiguredItems = mutableListOf<String>()
        if (environment.getProperty("spring.mail.host").isNullOrBlank()) {
            notConfiguredItems.add("smtp.host")
        }
        if (environment.getProperty("spring.mail.username").isNullOrBlank()) {
            notConfiguredItems.add("smtp.user")
        }
        if (environment.getProperty("spring.mail.password").isNullOrBlank()) {
            notConfiguredItems.add("smtp.password")
        }

        val sender = environment.getProperty("spring.mail.properties.mail.smtp.from")
            ?: environment.getProperty("spring.mail.username")
            ?: "yona@yona.io"

        model.addAttribute("message", "title.sendMail")
        model.addAttribute("notConfiguredItems", notConfiguredItems)
        model.addAttribute("sender", sender)
        model.addAttribute("currentUser", currentUser)
        return "site/mail"
    }

    // 12. 메일 발송 수행
    @PostMapping("/mail")
    fun sendMail(
        @RequestParam("to") toEmail: String,
        @RequestParam("from") fromEmail: String,
        @RequestParam("subject") subject: String,
        @RequestParam("body") body: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val currentUser = checkAdmin(authentication)
        
        val notConfiguredItems = mutableListOf<String>()
        if (environment.getProperty("spring.mail.host").isNullOrBlank()) {
            notConfiguredItems.add("smtp.host")
        }
        if (environment.getProperty("spring.mail.username").isNullOrBlank()) {
            notConfiguredItems.add("smtp.user")
        }
        if (environment.getProperty("spring.mail.password").isNullOrBlank()) {
            notConfiguredItems.add("smtp.password")
        }

        var sended = false
        var errorMessage: String? = null
        try {
            mailService.sendHtmlMail(fromEmail, toEmail, toEmail, subject, body)
            sended = true
        } catch (e: Exception) {
            errorMessage = e.message ?: "Failed to send email"
        }

        model.addAttribute("message", "title.sendMail")
        model.addAttribute("notConfiguredItems", notConfiguredItems)
        model.addAttribute("sender", fromEmail)
        model.addAttribute("currentUser", currentUser)
        model.addAttribute("sended", sended)
        if (errorMessage != null) {
            model.addAttribute("errorMessage", errorMessage)
        }
        return "site/mail"
    }

    // 13. 대량 메일 발송 화면
    @GetMapping("/massmail")
    fun massMail(
        authentication: Authentication?,
        model: Model
    ): String {
        val currentUser = checkAdmin(authentication)
        model.addAttribute("currentUser", currentUser)
        model.addAttribute("message", "title.massMail")
        return "site/massMail"
    }

    // 14. 대량 메일 수신처 목록 조회 API
    @PostMapping("/mailList")
    @ResponseBody
    fun mailList(
        @RequestParam params: MultiValueMap<String, String>,
        authentication: Authentication?
    ): ResponseEntity<List<String>> {
        checkAdmin(authentication)
        val emails = mutableSetOf<String>()

        if (params.containsKey("all") && params.getFirst("all") == "true") {
            val users = userRepository.findAll()
            for (user in users) {
                if (!user.email.isNullOrBlank()) {
                    emails.add(user.email)
                }
            }
        } else {
            for ((key, values) in params) {
                if (key == "all") continue
                for (projName in values) {
                    val parts = projName.split("/")
                    if (parts.size == 2) {
                        val owner = parts[0]
                        val name = parts[1]
                        val project = projectRepository.findByOwnerAndName(owner, name).orElse(null)
                        if (project != null) {
                            val projectUsers = projectUserRepository.findByProjectId(project.id!!)
                            for (pu in projectUsers) {
                                if (!pu.user.email.isNullOrBlank()) {
                                    emails.add(pu.user.email)
                                }
                            }
                        }
                    }
                }
            }
        }

        return ResponseEntity.ok(emails.toList().sorted())
    }

    // 15. 데이터 관리 진입 화면
    @GetMapping("/data")
    fun data(
        authentication: Authentication?,
        model: Model
    ): String {
        val currentUser = checkAdmin(authentication)
        model.addAttribute("currentUser", currentUser)
        return "site/data"
    }

    // 16. 전체 데이터 백업 다운로드
    @GetMapping("/export")
    fun exportData(
        authentication: Authentication?
    ): ResponseEntity<ByteArray> {
        checkAdmin(authentication)
        
        val dataMap = mutableMapOf<String, Any>()
        
        dataMap["users"] = userRepository.findAll().map {
            mapOf(
                "loginId" to it.loginId,
                "name" to it.name,
                "email" to (it.email ?: ""),
                "isGuest" to it.isGuest,
                "state" to it.state.name
            )
        }
        
        dataMap["projects"] = projectRepository.findAll().map {
            mapOf(
                "name" to it.name,
                "owner" to (it.owner ?: ""),
                "vcs" to (it.vcs ?: "GIT"),
                "projectScope" to it.projectScope.name
            )
        }

        val jsonBytes = objectMapper.writeValueAsBytes(dataMap)
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"yona-data-${Instant.now().epochSecond}.json\"")
        
        return ResponseEntity.ok()
            .headers(headers)
            .body(jsonBytes)
    }

    // 17. 데이터 업로드 복원
    @PostMapping("/import")
    fun importData(
        @RequestParam("data") file: MultipartFile,
        authentication: Authentication?,
        redirectAttributes: RedirectAttributes
    ): String {
        checkAdmin(authentication)
        if (!file.isEmpty) {
            try {
                val dataMap = objectMapper.readValue(file.bytes, Map::class.java) as Map<*, *>
                
                val usersData = dataMap["users"] as? List<*> ?: emptyList<Any>()
                for (u in usersData) {
                    val uMap = u as? Map<*, *> ?: continue
                    val loginId = uMap["loginId"]?.toString() ?: continue
                    if (!userRepository.findByLoginId(loginId).isPresent) {
                        val newUser = User(
                            loginId = loginId,
                            name = uMap["name"]?.toString() ?: "",
                            email = uMap["email"]?.toString() ?: "",
                            isGuest = uMap["isGuest"]?.toString()?.toBoolean() ?: false,
                            state = UserState.of(uMap["state"]?.toString() ?: "ACTIVE") ?: UserState.ACTIVE
                        )
                        userRepository.save(newUser)
                    }
                }

                val projectsData = dataMap["projects"] as? List<*> ?: emptyList<Any>()
                for (p in projectsData) {
                    val pMap = p as? Map<*, *> ?: continue
                    val name = pMap["name"]?.toString() ?: continue
                    val owner = pMap["owner"]?.toString() ?: continue
                    val exist = projectRepository.findByOwnerAndName(owner, name).isPresent
                    if (!exist) {
                        val newProject = Project(
                            name = name,
                            owner = owner,
                            vcs = pMap["vcs"]?.toString() ?: "GIT",
                            projectScope = ProjectScope.valueOf(pMap["projectScope"]?.toString() ?: "PRIVATE")
                        )
                        projectRepository.save(newProject)
                    }
                }
            } catch (e: Exception) {
                return "error/400"
            }
        }
        return "redirect:/"
    }

    // 18. 아바타 없는 유저 리스트 조회 API
    @GetMapping("/noAvatarUsers")
    @ResponseBody
    fun noAvatarUsers(authentication: Authentication?): ResponseEntity<Map<String, Any>> {
        checkAdmin(authentication)
        val activeUsers = userRepository.findAll().filter { it.state == UserState.ACTIVE }
        val usersNode = activeUsers.map {
            mapOf(
                "loginId" to it.loginId,
                "name" to it.name,
                "email" to (it.email ?: "")
            )
        }
        return ResponseEntity.ok(mapOf("users" to usersNode))
    }

    // 19. 아바타 지정 API
    @PostMapping("/setAttachmentToUserAvatar")
    @ResponseBody
    fun setAttachmentToUserAvatar(
        @RequestBody body: Map<String, Any>,
        authentication: Authentication?
    ): ResponseEntity<Map<String, Any>> {
        checkAdmin(authentication)
        return ResponseEntity.ok(mapOf("status" to 200, "message" to "OK"))
    }

    // 20. 자가진단 분석 화면
    @GetMapping("/diagnostic")
    fun diagnose(
        authentication: Authentication?,
        model: Model
    ): String {
        val currentUser = checkAdmin(authentication)
        val errors = diagnosticService.checkAll()
        
        model.addAttribute("errors", errors)
        model.addAttribute("currentUser", currentUser)
        return "site/diagnostic"
    }

    // 21. 업데이트 확인 화면
    @GetMapping("/update")
    fun updatePage(
        authentication: Authentication?,
        model: Model
    ): String {
        val currentUser = checkAdmin(authentication)
        var exception: Exception? = null
        try {
            yonaUpdateService.refreshVersionToUpdate()
        } catch (e: Exception) {
            exception = e
        }

        val versionToUpdate = if (yonaUpdateService.isUpdateRequired()) yonaUpdateService.getLatestVersion() else null

        model.addAttribute("currentUser", currentUser)
        model.addAttribute("currentVersion", "1.15.0")
        model.addAttribute("latestVersion", yonaUpdateService.getLatestVersion())
        model.addAttribute("versionToUpdate", versionToUpdate)
        model.addAttribute("isUpdateRequired", yonaUpdateService.isUpdateRequired())
        model.addAttribute("releaseUrl", yonaUpdateService.getReleaseUrl())
        model.addAttribute("exception", exception)
        model.addAttribute("message", "title.siteSetting")
        return "site/update"
    }

    // 22. 업데이트 알림 무시 비동기 API
    @PostMapping("/unwatchUpdate")
    @ResponseBody
    fun unwatchUpdate(
        authentication: Authentication?
    ): ResponseEntity<Map<String, Any>> {
        checkAdmin(authentication)
        yonaUpdateService.isWatched = false
        return ResponseEntity.ok(mapOf("status" to 200, "message" to "OK"))
    }
}


