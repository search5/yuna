package com.github.search5.yona.web

import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.user.UserService
import com.github.search5.yona.domain.user.UserState
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

@Controller
class BootstrapSetupController(
    private val userRepository: UserRepository,
    private val userService: UserService
) {

    // 1. 최초 관리자 생성 화면 진입
    @GetMapping("/bootstrap-setup")
    fun setupForm(model: Model): String {
        // 이미 유저가 존재하면 메인 화면으로 리다이렉트
        if (userRepository.count() > 0L) {
            return "redirect:/"
        }
        return "bootstrap-setup"
    }

    // 2. 최초 관리자 생성 처리
    @PostMapping("/bootstrap-setup")
    fun setupAdmin(
        @RequestParam("loginId") loginId: String,
        @RequestParam("name") name: String,
        @RequestParam("email") email: String,
        @RequestParam("password") password: String,
        @RequestParam("retypedPassword") retypedPassword: String,
        model: Model
    ): String {
        if (userRepository.count() > 0L) {
            return "redirect:/"
        }

        if (loginId != "admin") {
            model.addAttribute("error", "최초 관리자 아이디는 admin이어야 합니다.")
            model.addAttribute("name", name)
            model.addAttribute("email", email)
            return "bootstrap-setup"
        }

        if (password != retypedPassword) {
            model.addAttribute("error", "입력한 두 비밀번호가 일치하지 않습니다.")
            model.addAttribute("name", name)
            model.addAttribute("email", email)
            return "bootstrap-setup"
        }

        try {
            val salt = UUID.randomUUID().toString().substring(0, 8)
            val hashedPassword = hashPassword(password, salt)

            val adminUser = User().apply {
                this.loginId = loginId
                this.name = name
                this.email = email
                this.password = hashedPassword
                this.passwordSalt = salt
                this.state = UserState.SITE_ADMIN // 최초 가입자는 사이트 총괄 관리자 권한 부여
                this.isGuest = false
            }

            userService.createUser(adminUser)
            return "bootstrap-restart"
        } catch (e: Exception) {
            model.addAttribute("error", "관리자 생성 실패: ${e.message}")
            model.addAttribute("name", name)
            model.addAttribute("email", email)
            return "bootstrap-setup"
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
}
