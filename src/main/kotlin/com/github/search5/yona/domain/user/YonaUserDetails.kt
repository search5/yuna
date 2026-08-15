package com.github.search5.yona.domain.user

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

class YonaUserDetails(
    val id: Long,
    val loginId: String,
    private val passwordVal: String,
    val passwordSalt: String,
    private val authoritiesVal: Collection<GrantedAuthority>
) : UserDetails {
    override fun getAuthorities(): Collection<GrantedAuthority> = authoritiesVal
    override fun getPassword(): String = passwordVal
    override fun getUsername(): String = loginId
    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = true
}
