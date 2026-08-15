package com.github.search5.yona.domain.notification

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.enumeration.EventType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserProjectNotificationRepository : JpaRepository<UserProjectNotification, Long> {
    fun findByUserAndProjectAndNotificationType(user: User, project: Project, notificationType: EventType): UserProjectNotification?
    fun findByUser(user: User): List<UserProjectNotification>
    fun deleteByUserAndProject(user: User, project: Project)
    fun findByProjectIdAndNotificationTypeAndAllowed(projectId: Long, notificationType: EventType, allowed: Boolean): List<UserProjectNotification>
}
