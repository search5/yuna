package com.github.search5.yona.domain.apitoken

import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.user.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

@Service
class ApiTokenServiceImpl(
    private val apiTokenRepository: ApiTokenRepository,
    private val projectRepository: ProjectRepository
) : ApiTokenService {

    @Transactional(readOnly = true)
    override fun listByOwner(owner: User): List<ApiToken> = apiTokenRepository.findByOwner(owner)

    @Transactional
    override fun issue(
        owner: User,
        name: String,
        allRepositories: Boolean,
        scopedProjectIds: List<Long>,
        scopePermissions: Map<ApiTokenScopeGroup, ApiTokenPermission>,
        expiresInDays: Long
    ): IssuedApiToken {
        require(name.isNotBlank()) { "토큰 이름은 필수입니다." }
        // 갭 분석 4번("만료일 상한 없음") 해소 — GitHub Fine-grained PAT의 "최대 1년" 제약 대응.
        require(expiresInDays in 1..MAX_EXPIRES_IN_DAYS) {
            "만료일은 1일 이상 ${MAX_EXPIRES_IN_DAYS}일 이하여야 합니다."
        }

        val scopedProjects = if (allRepositories) {
            mutableSetOf()
        } else {
            scopedProjectIds.mapNotNull { projectRepository.findById(it).orElse(null) }.toMutableSet()
        }

        // LdapUserProvisioningService.generateSalt()와 동일한 SecureRandom 기반 패턴(비밀번호가
        // 아닌 토큰 원문이라 해싱 없이 그대로 노출하되, 저장은 ApiTokenHasher.hashApiToken()으로).
        val rawToken = generateRawToken()
        val token = ApiToken(
            owner = owner,
            name = name.trim(),
            tokenHash = hashApiToken(rawToken),
            allRepositories = allRepositories,
            scopedProjects = scopedProjects,
            expiresAt = Instant.now().plus(expiresInDays, ChronoUnit.DAYS)
        )
        scopePermissions.forEach { (group, permission) ->
            if (permission != ApiTokenPermission.NONE) {
                token.scopes.add(ApiTokenScope(apiToken = token, scopeGroup = group, permission = permission))
            }
        }

        val saved = apiTokenRepository.save(token)
        return IssuedApiToken(saved, rawToken)
    }

    @Transactional
    override fun revoke(owner: User, tokenId: Long) {
        val token = apiTokenRepository.findById(tokenId).orElse(null) ?: return
        if (token.owner?.id != owner.id) return
        apiTokenRepository.delete(token)
    }

    private fun generateRawToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        const val MAX_EXPIRES_IN_DAYS = 366L
    }
}
