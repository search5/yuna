package com.github.search5.yona.domain.apitoken

import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.project.Project
import java.time.Instant

// yona-wiki P3-02 Step2 — "ApiToken + 대상 ResourceType + 요청 저장소 + 필요 권한" 조합의 허용 여부를
// 판정하는 순수 함수. Spring 빈으로 만들지 않은 이유는 DB/HTTP 등 어떤 인프라도 필요 없는 순수 판정
// 로직이라(ApiTokenAuthenticationFilterSpec처럼 단위 테스트로만 충분히 검증 가능) 굳이 서비스로
// 감싸 DI 오버헤드를 지울 이유가 없어서다 — 필요해지면(예: Step4+ 컨트롤러에서 재사용) 언제든
// 얇은 @Service 래퍼를 얹을 수 있다.
object ApiTokenAuthorizer {

    fun isAuthorized(
        token: ApiToken,
        resourceType: ResourceType,
        project: Project?,
        requiredPermission: ApiTokenPermission,
        now: Instant = Instant.now()
    ): Boolean {
        if (requiredPermission == ApiTokenPermission.NONE) return true

        val expiresAt = token.expiresAt ?: return false
        if (!expiresAt.isAfter(now)) return false

        val group = resourceType.toApiTokenScopeGroup() ?: return false
        val grantedPermission = token.scopes.find { it.scopeGroup == group }?.permission ?: ApiTokenPermission.NONE
        if (grantedPermission.ordinal < requiredPermission.ordinal) return false

        if (project != null && !token.allRepositories) {
            val projectId = project.id ?: return false
            if (token.scopedProjects.none { it.id == projectId }) return false
        }

        return true
    }
}
