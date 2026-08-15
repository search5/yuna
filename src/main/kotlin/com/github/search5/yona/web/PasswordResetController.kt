package com.github.search5.yona.web

import com.github.search5.yona.domain.mail.MailService
import com.github.search5.yona.domain.user.PasswordResetService
import com.github.search5.yona.domain.user.UserRepository
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
class PasswordResetController(
    private val passwordResetService: PasswordResetService,
    private val userRepository: UserRepository,
    private val mailService: MailService
) {

    private fun getServerUrl(request: HttpServletRequest): String {
        val scheme = request.scheme
        val serverName = request.serverName
        val serverPort = request.serverPort
        return if (serverPort == 80 || serverPort == 443) {
            "$scheme://$serverName"
        } else {
            "$scheme://$serverName:$serverPort"
        }
    }

    // 1. 비밀번호 찾기 메일 신청 화면 (GET /lostPassword)
    @GetMapping("/lostPassword")
    fun lostPasswordForm(model: Model): String {
        return "user/lostPassword"
    }

    // 2. 비밀번호 찾기 메일 발송 처리 (POST /lostPassword)
    @PostMapping("/lostPassword")
    fun requestResetPasswordEmail(
        @RequestParam("loginId") loginId: String,
        @RequestParam("emailAddress") emailAddress: String,
        request: HttpServletRequest,
        model: Model
    ): String {
        val user = userRepository.findByLoginId(loginId).orElse(null)
        
        if (user == null || user.email != emailAddress) {
            model.addAttribute("errorMessage", "입력한 아이디와 이메일 주소가 일치하지 않습니다.")
            return "user/lostPassword"
        }

        val hashString = passwordResetService.generateResetHash(loginId)
        passwordResetService.addHashToResetTable(loginId, hashString)

        val serverUrl = getServerUrl(request)
        val resetPasswordUrl = "$serverUrl/user/reset-password?hash=$hashString"
        val htmlContent = """
            <h3>[Yona] 비밀번호 재설정 안내</h3>
            <p>아래 링크를 클릭하여 새로운 비밀번호를 설정해 주세요:</p>
            <p><a href="$resetPasswordUrl">$resetPasswordUrl</a></p>
            <p>이 링크는 생성 후 1시간 동안만 유효합니다.</p>
        """.trimIndent()

        return try {
            mailService.sendHtmlMail(user.email, user.name, "[Yona] 비밀번호 재설정 요청", htmlContent)
            model.addAttribute("successMessage", "비밀번호 재설정 링크가 포함된 메일이 발송되었습니다.")
            "user/lostPassword"
        } catch (e: Exception) {
            model.addAttribute("errorMessage", "메일 발송에 실패했습니다: ${e.message}")
            "user/lostPassword"
        }
    }

    // 3. 비밀번호 재설정 링크 접속 화면 (GET /user/reset-password)
    @GetMapping("/user/reset-password")
    fun resetPasswordForm(
        @RequestParam("hash") hash: String,
        model: Model
    ): String {
        val isValid = passwordResetService.isValidResetHash(hash)
        return if (isValid) {
            model.addAttribute("hash", hash)
            "user/resetPassword"
        } else {
            model.addAttribute("errorMessage", "잘못되거나 이미 만료된 재설정 링크입니다.")
            "user/resetPassword"
        }
    }

    // 4. 비밀번호 재설정 처리 (POST /user/reset-password)
    @PostMapping("/user/reset-password")
    fun resetPassword(
        @RequestParam("hashString") hashString: String,
        @RequestParam("password") newPassword: String,
        @RequestParam("retypedPassword") retypedPassword: String,
        redirectAttributes: RedirectAttributes,
        model: Model
    ): String {
        if (newPassword != retypedPassword) {
            model.addAttribute("hash", hashString)
            model.addAttribute("errorMessage", "입력한 두 비밀번호가 일치하지 않습니다.")
            return "user/resetPassword"
        }

        val success = passwordResetService.resetPassword(hashString, newPassword)
        return if (success) {
            redirectAttributes.addFlashAttribute("logoutMessage", "비밀번호가 성공적으로 변경되었습니다. 새로운 비밀번호로 로그인해 주세요.")
            "redirect:/users/loginform"
        } else {
            model.addAttribute("hash", hashString)
            model.addAttribute("errorMessage", "비밀번호 재설정에 실패했습니다. 링크 만료 여부를 확인하십시오.")
            "user/resetPassword"
        }
    }
}
