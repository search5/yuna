package com.github.search5.yona.config.oauth2

import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.user.UserState
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class CustomOAuth2UserService(
    private val userRepository: UserRepository,
    private val delegate: DefaultOAuth2UserService = DefaultOAuth2UserService()
) : OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    @Transactional
    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oAuth2User = delegate.loadUser(userRequest)
        val registrationId = userRequest.clientRegistration.registrationId
        val attributes = oAuth2User.attributes

        val userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(registrationId, attributes)

        // 이메일 또는 loginId로 기존 가입 사용자 매핑
        var user = userRepository.findByEmail(userInfo.email).orElse(null)
            ?: userRepository.findByLoginId(userInfo.loginId).orElse(null)

        if (user == null) {
            // 미가입 사용자라면 자동 가입 처리 (createLocalUserWithOAuth)
            user = User(
                name = userInfo.name,
                loginId = userInfo.loginId,
                email = userInfo.email,
                state = UserState.ACTIVE,
                createdDate = Instant.now()
            )
            user = userRepository.save(user)
        }

        val authorities = listOf(SimpleGrantedAuthority("ROLE_${user.state.name}"))

        return YonaOAuth2User(user, attributes, authorities)
    }
}
