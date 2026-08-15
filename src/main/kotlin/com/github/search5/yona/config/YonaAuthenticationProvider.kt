package com.github.search5.yona.config

import com.github.search5.yona.domain.user.YonaUserDetails
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.util.Base64

@Component
class YonaAuthenticationProvider(
    private val userDetailsService: UserDetailsService
) : AuthenticationProvider {

    override fun authenticate(authentication: Authentication): Authentication {
        val loginId = authentication.name
        val password = authentication.credentials.toString()

        val userDetails = userDetailsService.loadUserByUsername(loginId) as YonaUserDetails
        val hashedInput = hashPassword(password, userDetails.passwordSalt)

        if (hashedInput != userDetails.password) {
            throw BadCredentialsException("비밀번호가 일치하지 않습니다.")
        }

        return UsernamePasswordAuthenticationToken(userDetails, password, userDetails.authorities)
    }

    override fun supports(authentication: Class<*>): Boolean {
        return UsernamePasswordAuthenticationToken::class.java.isAssignableFrom(authentication)
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
