package com.github.search5.yona.domain.apitoken

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface ApiTokenRepository : JpaRepository<ApiToken, Long> {
    // ApiTokenAuthenticationFilter는 서블릿 필터라 @Transactional 경계 밖에서 실행되므로, 세션이
    // 이미 닫힌 뒤 ApiTokenAuthorizer가 owner/scopes/scopedProjects를 읽으면 LazyInitializationException이
    // 난다(실제로 이렇게 처음 실패해 확인) — 조회 시점에 필요한 연관관계를 전부 즉시 로딩한다.
    @Query(
        "SELECT DISTINCT t FROM ApiToken t " +
            "LEFT JOIN FETCH t.owner " +
            "LEFT JOIN FETCH t.scopes " +
            "LEFT JOIN FETCH t.scopedProjects " +
            "WHERE t.tokenHash = :tokenHash"
    )
    fun findByTokenHash(@Param("tokenHash") tokenHash: String): Optional<ApiToken>
}
