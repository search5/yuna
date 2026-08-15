package com.github.search5.yona.web

import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
class AuthController(
    private val userService: UserService
) {

    @GetMapping("/login")
    fun redirectToLoginForm(
        @RequestParam(value = "error", required = false) error: String?,
        @RequestParam(value = "logout", required = false) logout: String?,
        redirectAttributes: org.springframework.web.servlet.mvc.support.RedirectAttributes
    ): String {
        if (error != null) {
            redirectAttributes.addAttribute("error", error)
        }
        if (logout != null) {
            redirectAttributes.addAttribute("logout", logout)
        }
        return "redirect:/users/loginform"
    }

    @GetMapping("/users/loginform")
    fun loginForm(
        @RequestParam(value = "error", required = false) error: String?,
        @RequestParam(value = "logout", required = false) logout: String?,
        model: Model
    ): String {
        if (error != null) {
            model.addAttribute("loginError", "아이디 또는 비밀번호가 올바르지 않습니다.")
        }
        if (logout != null) {
            model.addAttribute("logoutMessage", "성공적으로 로그아웃되었습니다.")
        }
        return "login"
    }

    @GetMapping("/users/logout")
    fun logout(): String {
        return "redirect:/logout"
    }

    @GetMapping(value = ["/signup", "/users/signupform"])
    fun signupForm(model: Model): String {
        model.addAttribute("user", User())
        return "signup"
    }

    @PostMapping(value = ["/signup", "/users/signup"])
    fun signup(
        @ModelAttribute("user") user: User,
        @RequestParam("retypedPassword") retypedPassword: String,
        bindingResult: BindingResult,
        model: Model
    ): String {
        if (userService.isLoginIdExist(user.loginId)) {
            bindingResult.rejectValue("loginId", "duplicate", "이미 존재하는 아이디입니다.")
        }
        if (user.password != retypedPassword) {
            model.addAttribute("passwordError", "비밀번호가 일치하지 않습니다.")
            return "signup"
        }
        if (bindingResult.hasErrors()) {
            return "signup"
        }

        val salt = java.util.UUID.randomUUID().toString().substring(0, 8)
        val hashed = hashPassword(user.password ?: "", salt)
        user.password = hashed
        user.passwordSalt = salt

        userService.createUser(user)
        return "redirect:/users/loginform?signupSuccess"
    }

    private fun hashPassword(password: String, salt: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        digest.reset()
        digest.update(salt.toByteArray(Charsets.UTF_8))
        var hashed = digest.digest(password.toByteArray(Charsets.UTF_8))
        for (i in 1 until 1024) {
            digest.reset()
            hashed = digest.digest(hashed)
        }
        return java.util.Base64.getEncoder().encodeToString(hashed)
    }
}
