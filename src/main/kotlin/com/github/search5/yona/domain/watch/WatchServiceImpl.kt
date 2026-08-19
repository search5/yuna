package com.github.search5.yona.domain.watch

import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.user.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class WatchServiceImpl(
    private val watchRepository: WatchRepository,
    private val unwatchRepository: UnwatchRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository
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

        // 4. yona Watch.findActualWatchers()의 allowedWatchersOnly 필터 대응 (P1-21):
        // 이 리소스를 읽을 권한이 없는 감시자는 실제 감시자 목록에서 제외한다.
        if (allowedWatchersOnly) {
            actualWatchers.retainAll { hasReadPermission(it, projectId) }
        }

        return actualWatchers
    }

    private fun hasReadPermission(user: User, projectId: Long?): Boolean {
        if (user.isSiteManager) return true
        // 프로젝트에 속하지 않은 전역 리소스는 yona AccessControl에서도 누구나 읽을 수 있다고 본다.
        if (projectId == null) return true

        val project = projectRepository.findById(projectId).orElse(null) ?: return false
        if (project.projectScope == ProjectScope.PUBLIC) return true
        return projectUserRepository.existsByProjectIdAndUserId(projectId, user.id ?: return false)
    }
}