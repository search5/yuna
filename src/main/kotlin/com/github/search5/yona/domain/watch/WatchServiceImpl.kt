package com.github.search5.yona.domain.watch

import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.user.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class WatchServiceImpl(
    private val watchRepository: WatchRepository,
    private val unwatchRepository: UnwatchRepository
) : WatchService {

    override fun watch(user: User, resourceType: ResourceType, resourceId: String) {
        val watch = watchRepository.findByUserAndResourceTypeAndResourceId(user, resourceType, resourceId)
        if (watch == null) {
            watchRepository.save(Watch(user = user, resourceType = resourceType, resourceId = resourceId))
        }
        val unwatch = unwatchRepository.findByUserAndResourceTypeAndResourceId(user, resourceType, resourceId)
        if (unwatch != null) {
            unwatchRepository.delete(unwatch)
        }
    }

    override fun unwatch(user: User, resourceType: ResourceType, resourceId: String) {
        val unwatch = unwatchRepository.findByUserAndResourceTypeAndResourceId(user, resourceType, resourceId)
        if (unwatch == null) {
            unwatchRepository.save(Unwatch(user = user, resourceType = resourceType, resourceId = resourceId))
        }
        val watch = watchRepository.findByUserAndResourceTypeAndResourceId(user, resourceType, resourceId)
        if (watch != null) {
            watchRepository.delete(watch)
        }
    }

    override fun isWatching(user: User, resourceType: ResourceType, resourceId: String): Boolean {
        val watch = watchRepository.findByUserAndResourceTypeAndResourceId(user, resourceType, resourceId)
        val unwatch = unwatchRepository.findByUserAndResourceTypeAndResourceId(user, resourceType, resourceId)
        return watch != null && unwatch == null
    }

    override fun findWatchers(resourceType: ResourceType, resourceId: String): Set<User> {
        return watchRepository.findByResourceTypeAndResourceId(resourceType, resourceId)
            .map { it.user }
            .toSet()
    }

    override fun findUnwatchers(resourceType: ResourceType, resourceId: String): Set<User> {
        return unwatchRepository.findByResourceTypeAndResourceId(resourceType, resourceId)
            .map { it.user }
            .toSet()
    }

    override fun findActualWatchers(
        baseWatchers: Set<User>,
        resourceType: ResourceType,
        resourceId: String,
        projectId: Long?,
        allowedWatchersOnly: Boolean
    ): Set<User> {
        val actualWatchers = mutableSetOf<User>()
        actualWatchers.addAll(baseWatchers)

        // 1. 프로젝트 감시자 추가
        if (projectId != null) {
            actualWatchers.addAll(findWatchers(ResourceType.PROJECT, projectId.toString()))
        }

        // 2. 해당 리소스 감시자 추가
        actualWatchers.addAll(findWatchers(resourceType, resourceId))

        // 3. 해당 리소스 비감시자 제외
        actualWatchers.removeAll(findUnwatchers(resourceType, resourceId))

        return actualWatchers
    }
}