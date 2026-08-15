package com.github.search5.yona.domain.watch

import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.user.User

interface WatchService {
    fun watch(user: User, resourceType: ResourceType, resourceId: String)
    fun unwatch(user: User, resourceType: ResourceType, resourceId: String)
    fun isWatching(user: User, resourceType: ResourceType, resourceId: String): Boolean
    fun findWatchers(resourceType: ResourceType, resourceId: String): Set<User>
    fun findUnwatchers(resourceType: ResourceType, resourceId: String): Set<User>
    fun findActualWatchers(
        baseWatchers: Set<User>,
        resourceType: ResourceType,
        resourceId: String,
        projectId: Long?,
        allowedWatchersOnly: Boolean = true
    ): Set<User>
}
