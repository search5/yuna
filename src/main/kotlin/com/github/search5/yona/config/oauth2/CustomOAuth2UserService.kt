package com.github.search5.yona.config.oauth2

import com.github.search5.yona.domain.user.LinkedAccount
import com.github.search5.yona.domain.user.LinkedAccountRepository
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

/**
 * yona의 models/LinkedAccount.java + UserCredential.java(다중 provider 연동) 대응.
 * yona의 UserCredential 계층은 play-authenticate 플러그인 산물이라 이식하지 않고,
 * User에 직접 LinkedAccount(providerKey+providerUserId)를 연결하는 단순한 구조로
 * 동일한 핵심 기능(여러 OAuth 제공자를 한 계정에 연결)을 구현했다.
 */
@Service
class CustomOAuth2UserService(
    private val userRepository: UserRepository,
    private val linkedAccountRepository: LinkedAccountRepository,
    private val delegate: DefaultOAuth2UserService = DefaultOAuth2UserService()
) : OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    @Transactional
    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oAuth2User = delegate.loadUser(userRequest)
        val registrationId = userRequest.clientRegistration.registrationId
        val attributes = oAuth2User.attributes

        val userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(registrationId, attributes)

        // 1) 이미 이 provider+providerUserId로 연결된 계정이 있으면 그대로 사용 (가장 신뢰할 수 있는 매칭)
        val existingLink = linkedAccountRepository
            .findByProviderKeyAndProviderUserId(registrationId, userInfo.id)
            .orElse(null)

        val user = if (existingLink != null) {
            existingLink.user
        } else {
            // 2) 처음 보는 provider 계정이면 이메일/loginId로 기존 가입 사용자를 찾아 "연결"하거나,
            //    없으면 신규 가입 처리
            val resolvedUser = userRepository.findByEmail(userInfo.email).orElse(null)
                ?: userRepository.findByLoginId(userInfo.loginId).orElse(null)
                ?: userRepository.save(
                    User(
                        name = userInfo.name,
                        loginId = userInfo.loginId,
                        email = userInfo.email,
                        state = UserState.ACTIVE,
                        createdDate = Instant.now()
                    )
                )

            linkedAccountRepository.save(
                LinkedAccount(user = resolvedUser, providerKey = registrationId, providerUserId = userInfo.id)
            )
            resolvedUser
        }

        val authorities = listOf(SimpleGrantedAuthority("ROLE_${user.state.name}"))

        return YonaOAuth2User(user, attributes, authorities)
    }
}
