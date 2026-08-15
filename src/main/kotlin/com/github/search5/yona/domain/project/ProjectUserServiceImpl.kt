package com.github.search5.yona.domain.project

import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.notification.NotificationEvent
import com.github.search5.yona.domain.notification.NotificationEventRepository
import com.github.search5.yona.domain.role.RoleRepository
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional(readOnly = true)
class ProjectUserServiceImpl(
    private val projectUserRepository: ProjectUserRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val notificationEventRepository: NotificationEventRepository
) : ProjectUserService {

    override fun getProjectMembers(projectId: Long): List<ProjectUser> {
        return projectUserRepository.findByProjectId(projectId)
    }

    @Transactional
    override fun enroll(projectId: Long, userId: Long) {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Project with ID $projectId not found") }
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User with ID $userId not found") }

        user.enroll(project)
        userRepository.save(user)

        // 가입 신청 알림 생성
        val managers = getProjectManagers(projectId)
        val notiEvent = NotificationEvent(
            title = "${project.name} 프로젝트에 ${user.loginId}님이 가입 신청을 보냈습니다.",
            senderId = userId,
            receivers = managers.toMutableSet(),
            created = Instant.now(),
            resourceType = ResourceType.PROJECT,
            resourceId = projectId.toString(),
            eventType = EventType.MEMBER_ENROLL_REQUEST,
            newValue = "REQUEST",
            oldValue = "CANCEL"
        )
        notificationEventRepository.save(notiEvent)
    }

    @Transactional
    override fun cancelEnroll(projectId: Long, userId: Long) {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Project with ID $projectId not found") }
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User with ID $userId not found") }

        user.cancelEnroll(project)
        userRepository.save(user)

        // 가입 취소 알림 생성
        val managers = getProjectManagers(projectId)
        val notiEvent = NotificationEvent(
            title = "${project.name} 프로젝트에 ${user.loginId}님의 가입 신청이 취소되었습니다.",
            senderId = userId,
            receivers = managers.toMutableSet(),
            created = Instant.now(),
            resourceType = ResourceType.PROJECT,
            resourceId = projectId.toString(),
            eventType = EventType.MEMBER_ENROLL_REQUEST,
            newValue = "CANCEL",
            oldValue = "REQUEST"
        )
        notificationEventRepository.save(notiEvent)
    }

    @Transactional
    override fun acceptMemberRequest(projectId: Long, userId: Long, approverId: Long) {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Project with ID $projectId not found") }
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User with ID $userId not found") }

        if (projectUserRepository.existsByProjectIdAndUserId(projectId, userId)) {
            // 이미 가입이 완료된 유저라면 대기 신청 목록에서만 소거
            user.cancelEnroll(project)
            userRepository.save(user)
            return
        }

        user.cancelEnroll(project)
        userRepository.save(user)

        val memberRole = roleRepository.findById(RoleType.MEMBER.roleType)
            .orElseThrow { IllegalStateException("MEMBER role not found") }

        val projectUser = ProjectUser(
            project = project,
            user = user,
            role = memberRole
        )
        projectUserRepository.save(projectUser)

        // 승인 완료 알림 발송
        val notiEvent = NotificationEvent(
            title = "${project.name} 프로젝트 가입 신청이 승인되었습니다.",
            senderId = approverId,
            receivers = mutableSetOf(user),
            created = Instant.now(),
            resourceType = ResourceType.PROJECT,
            resourceId = projectId.toString(),
            eventType = EventType.MEMBER_ENROLL_ACCEPT,
            newValue = "ACCEPT",
            oldValue = "REQUEST"
        )
        notificationEventRepository.save(notiEvent)
    }

    @Transactional
    override fun rejectMemberRequest(projectId: Long, userId: Long, rejecterId: Long) {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Project with ID $projectId not found") }
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User with ID $userId not found") }

        user.cancelEnroll(project)
        userRepository.save(user)
    }

    @Transactional
    override fun addMember(projectId: Long, loginId: String, updaterId: Long) {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Project with ID $projectId not found") }
        val targetUser = userRepository.findByLoginId(loginId)
            .orElseThrow { IllegalArgumentException("User with LoginID $loginId not found") }

        if (projectUserRepository.existsByProjectIdAndUserId(projectId, targetUser.id!!)) {
            throw IllegalArgumentException("User is already a member of this project")
        }

        // 대기 신청에 있던 유저라면 대기 목록에서 소거
        if (targetUser.enrolledProjects.contains(project)) {
            targetUser.cancelEnroll(project)
            userRepository.save(targetUser)
        }

        val memberRole = roleRepository.findById(RoleType.MEMBER.roleType)
            .orElseThrow { IllegalStateException("MEMBER role not found") }

        val projectUser = ProjectUser(
            project = project,
            user = targetUser,
            role = memberRole
        )
        projectUserRepository.save(projectUser)

        // 추가 알림 발송 (가입 완료)
        val notiEvent = NotificationEvent(
            title = "${project.name} 프로젝트에 멤버로 추가되었습니다.",
            senderId = updaterId,
            receivers = mutableSetOf(targetUser),
            created = Instant.now(),
            resourceType = ResourceType.PROJECT,
            resourceId = projectId.toString(),
            eventType = EventType.MEMBER_ENROLL_ACCEPT,
            newValue = "ACCEPT",
            oldValue = "NONE"
        )
        notificationEventRepository.save(notiEvent)
    }

    @Transactional
    override fun updateMemberRole(projectId: Long, userId: Long, newRoleId: Long, updaterId: Long) {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Project with ID $projectId not found") }
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User with ID $userId not found") }

        // 소유자는 강등 불가
        if (project.owner == user.loginId) {
            throw IllegalArgumentException("Owner's role cannot be modified or degraded")
        }

        val projectUser = projectUserRepository.findByProjectIdAndUserId(projectId, userId)
            .orElseThrow { IllegalArgumentException("User is not a member of this project") }

        val newRole = roleRepository.findById(newRoleId)
            .orElseThrow { IllegalArgumentException("Role with ID $newRoleId not found") }

        projectUser.role = newRole
        projectUserRepository.save(projectUser)
    }

    @Transactional
    override fun removeMember(projectId: Long, userId: Long, removerId: Long) {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Project with ID $projectId not found") }
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User with ID $userId not found") }

        // 소유자는 탈퇴 불가
        if (project.owner == user.loginId) {
            throw IllegalArgumentException("Owner cannot leave or be removed from the project")
        }

        projectUserRepository.deleteByProjectIdAndUserId(projectId, userId)
    }

    private fun getProjectManagers(projectId: Long): List<User> {
        return projectUserRepository.findByProjectId(projectId)
            .filter { it.role.id == RoleType.MANAGER.roleType }
            .map { it.user }
    }
}
