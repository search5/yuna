package com.github.search5.yona.web

import com.github.search5.yona.domain.user.Email
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.user.UserService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.Base64

@RestController
class UserController(
    private val userService: UserService,
    private val userRepository: UserRepository
) {

    private fun getLoginUserId(authentication: Authentication?): Long {
        if (authentication == null) throw IllegalArgumentException("Unauthorized")
        val user = userRepository.findByLoginId(authentication.name)
            .orElseThrow { IllegalArgumentException("User not found") }
        return user.id!!
    }

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

    @GetMapping("/api/users")
    fun searchUsers(
        @RequestParam("query") query: String,
        request: HttpServletRequest
    ): ResponseEntity<List<Map<String, String>>> {
        val referer = request.getHeader("referer") ?: ""
        // JSON API 형식 검증
        if (query.isEmpty()) {
            return ResponseEntity.ok(emptyList())
        }

        val users = userRepository.findAll().filter {
            (it.loginId.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true)) &&
                    it.state != com.github.search5.yona.domain.user.UserState.DELETED
        }.take(10)

        val result = users.map { user ->
            val avatarUrl = "/images/default-avatar-128.png"
            val sb = StringBuilder()
            sb.append("<img class='mention_image' src='$avatarUrl'>")
            sb.append("<b class='mention_name'>${user.name}</b>")
            sb.append("<span class='mention_username'> @${user.loginId}</span>")

            mapOf(
                "info" to sb.toString(),
                "loginId" to user.loginId
            )
        }

        return ResponseEntity.ok(result)
    }

    @PostMapping("/api/users/emails")
    fun addEmail(
        @RequestParam("email") email: String,
        authentication: Authentication?
    ): ResponseEntity<Map<String, Any>> {
        return try {
            val userId = getLoginUserId(authentication)
            val savedEmail = userService.addEmail(userId, email)
            ResponseEntity.ok(mapOf("status" to "success", "emailId" to (savedEmail.id ?: 0L)))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to add email")))
        }
    }

    @DeleteMapping("/api/users/emails/{emailId}")
    fun deleteEmail(
        @PathVariable emailId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        return try {
            val userId = getLoginUserId(authentication)
            userService.deleteEmail(userId, emailId)
            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to delete email")))
        }
    }

    @PostMapping("/api/users/emails/{emailId}/send-verification")
    fun sendValidationEmail(
        @PathVariable emailId: Long,
        request: HttpServletRequest,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        return try {
            val userId = getLoginUserId(authentication)
            val serverUrl = getServerUrl(request)
            userService.sendValidationEmail(userId, emailId, serverUrl)
            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to send verification email")))
        }
    }

    @GetMapping("/user/email/confirm/{emailId}/{token}", produces = ["text/html;charset=UTF-8"])
    fun confirmEmailLegacy(
        @PathVariable emailId: Long,
        @PathVariable token: String
    ): ResponseEntity<String> {
        return confirmEmail(emailId, token)
    }

    @GetMapping("/user/emails/{emailId}/confirm", produces = ["text/html;charset=UTF-8"])
    fun confirmEmail(
        @PathVariable emailId: Long,
        @RequestParam("token") token: String
    ): ResponseEntity<String> {
        val success = userService.confirmEmail(emailId, token)
        return if (success) {
            ResponseEntity.ok("<h3>이메일 인증이 완료되었습니다.</h3><p><a href='/login'>로그인 화면으로 이동</a></p>")
        } else {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body("<h3>잘못되거나 만료된 인증 토큰입니다.</h3>")
        }
    }

    @PostMapping("/api/users/emails/{emailId}/set-main")
    fun setAsMainEmail(
        @PathVariable emailId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        return try {
            val userId = getLoginUserId(authentication)
            userService.setAsMainEmail(userId, emailId)
            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to set main email")))
        }
    }

    @GetMapping("/verify/{loginId}/{verificationCode}", produces = ["text/html;charset=UTF-8"])
    fun verifyUserLegacy(
        @PathVariable loginId: String,
        @PathVariable verificationCode: String
    ): ResponseEntity<String> {
        return verifyUser(loginId, verificationCode)
    }

    @GetMapping("/user/verify", produces = ["text/html;charset=UTF-8"])
    fun verifyUser(
        @RequestParam("loginId") loginId: String,
        @RequestParam("code") code: String
    ): ResponseEntity<String> {
        val success = userService.verifyUser(loginId, code)
        return if (success) {
            ResponseEntity.ok("<h3>회원가입 계정 인증이 완료되었습니다.</h3><p><a href='/login'>로그인 화면으로 이동</a></p>")
        } else {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body("<h3>잘못되거나 만료된 인증 링크입니다.</h3>")
        }
    }

    @PostMapping("/api/users/profile/update")
    fun updateProfile(
        @RequestParam("name") name: String,
        @RequestParam("email") email: String,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        return try {
            val userId = getLoginUserId(authentication)
            val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("User not found") }
            
            if (name.isEmpty()) {
                return ResponseEntity.badRequest().body(mapOf("error" to "이름은 필수 항목입니다."))
            }

            if (user.email != email && userService.isEmailExist(email)) {
                return ResponseEntity.badRequest().body(mapOf("error" to "이미 사용 중인 이메일 주소입니다."))
            }

            user.name = name.trim()
            user.email = email.trim()
            userRepository.save(user)

            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to update profile")))
        }
    }

    @PostMapping("/api/users/token/reset")
    fun resetApiToken(
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        return try {
            val userId = getLoginUserId(authentication)
            val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("User not found") }
            
            // 신규 API 토큰 생성 (Sha256)
            val rawToken = LocalDateTime.now().toString() + user.loginId
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(rawToken.toByteArray(Charsets.UTF_8))
            val newToken = Base64.getEncoder().encodeToString(hash)
            
            user.token = newToken
            userRepository.save(user)
            
            ResponseEntity.ok(mapOf("status" to "success", "token" to newToken))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to reset token")))
        }
    }

    @PostMapping("/api/users/password/change")
    fun changePassword(
        @RequestBody request: ChangePasswordRequest,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        return try {
            val userId = getLoginUserId(authentication)
            val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("User not found") }

            // 현재 비밀번호 검증
            val hashedOld = hashPassword(request.oldPassword, user.passwordSalt ?: "")
            if (user.password != hashedOld) {
                return ResponseEntity.badRequest().body(mapOf("error" to "현재 비밀번호가 일치하지 않습니다."))
            }

            // 새 비밀번호 일치 확인
            if (request.password != request.retypedPassword) {
                return ResponseEntity.badRequest().body(mapOf("error" to "입력한 새 비밀번호가 일치하지 않습니다."))
            }

            if (request.password.length < 4) {
                return ResponseEntity.badRequest().body(mapOf("error" to "비밀번호는 4자 이상이어야 합니다."))
            }

            // 비밀번호 재설정
            val newSalt = java.util.UUID.randomUUID().toString().substring(0, 8)
            val newHashed = hashPassword(request.password, newSalt)
            
            user.passwordSalt = newSalt
            user.password = newHashed
            userRepository.save(user)

            ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Failed to change password")))
        }
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

    data class ChangePasswordRequest(
        val oldPassword: String,
        val password: String,
        val retypedPassword: String
    )
}
