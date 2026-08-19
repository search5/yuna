package com.github.search5.yona.domain.watch

import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.user.User

interface WatchService {
    fun watch(user: User, resourceType: ResourceType, resourceId: String)
    fun unwatch(user: User, resourceType: ResourceType, resourceId: String)
    fun isWatching(user: User, resourceType: ResourceType, resourceId: String): Boolean
    fun findWatchers(resourceType: ResourceType, resourceId: String): Set<User>
    fun findUnwatchers(resourceType: ResourceType, resourceId: String): Set<User>

    // eventType: yona Watch.filterReceivers()의 UserProjectNotification 뮤트 필터 대응 (P1-22).
    // 프로젝트를 통해서만(명시적 리소스 감시/기본 감시자가 아님) 감시 중인 사용자에게만 적용된다.
    fun findActualWatchers(
        baseWatchers: Set<User>,
        resourceType: ResourceType,
        resourceId: String,
        projectId: Long?,
        allowedWatchersOnly: Boolean = true,
        eventType: EventType? = null
    ): Set<User>
}
