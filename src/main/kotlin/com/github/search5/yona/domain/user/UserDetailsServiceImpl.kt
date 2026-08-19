package com.github.search5.yona.domain.user

import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class UserDetailsServiceImpl(
    private val userRepository: UserRepository
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findByLoginId(username)
            .orElseThrow { UsernameNotFoundException("사용자를 찾을 수 없습니다: $username") }

        val authorities = mutableListOf(SimpleGrantedAuthority("ROLE_${user.state.name}"))
        if (username == "admin" || user.state == UserState.SITE_ADMIN) {
            if (authorities.none { it.authority == "ROLE_SITE_ADMIN" }) {
                authorities.add(SimpleGrantedAuthority("ROLE_SITE_ADMIN"))
            }
            if (authorities.none { it.authority == "ROLE_ADMIN" }) {
                authorities.add(SimpleGrantedAuthority("ROLE_ADMIN"))
            }
        }

        return YonaUserDetails(
            id = user.id ?: 0L,
            loginId = user.loginId,
            passwordVal = user.password ?: "",
            passwordSalt = user.passwordSalt ?: "",
            authoritiesVal = authorities,
            state = user.state
        )
    }
}
