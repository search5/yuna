package com.github.search5.yona.domain.issue

import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.milestone.MilestoneRepository
import com.github.search5.yona.domain.notification.NotificationEvent
import com.github.search5.yona.domain.notification.NotificationEventRepository
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.watch.WatchService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class IssueServiceImpl(
    private val issueRepository: IssueRepository,
    private val userRepository: UserRepository,
    private val milestoneRepository: MilestoneRepository,
    private val projectRepository: ProjectRepository,
    private val issueLabelRepository: IssueLabelRepository,
    private val notificationEventRepository: NotificationEventRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val issueCommentRepository: IssueCommentRepository,
    private val watchService: WatchService,
    private val issueEventRepository: IssueEventRepository
) : IssueService {

    override fun createIssue(
        issue: Issue,
        author: User,
        assigneeUser: User?,
        milestoneId: Long?,
        labelIds: List<Long>?
    ): Issue {
        val project = issue.project
        project.lastIssueNumber = project.lastIssueNumber + 1
        projectRepository.save(project)

        issue.number = project.lastIssueNumber
        issue.createdDate = Instant.now()
        issue.updatedDate = Instant.now()
        issue.authorId = author.id
        issue.authorLoginId = author.loginId
        issue.authorName = author.name
        
        if (assigneeUser != null) {
            issue.assignee = Assignee(user = assigneeUser, project = project)
        }

        if (milestoneId != null) {
            val milestone = milestoneRepository.findById(milestoneId).orElse(null)
            issue.milestone = milestone
        }

        if (!labelIds.isNullOrEmpty()) {
            val labels = issueLabelRepository.findAllById(labelIds)
            issue.labels = labels.toMutableSet()
        }

        val savedIssue = issueRepository.save(issue)

        val title = "[${project.name}] 신규 이슈 등록: #${issue.number} ${issue.title}"
        val notificationEvent = NotificationEvent(
            title = title,
            senderId = author.id,
            created = Instant.now(),
            resourceType = ResourceType.ISSUE_POST,
            resourceId = savedIssue.id.toString(),
            eventType = EventType.NEW_ISSUE,
            newValue = title
        )

        // 감시자(Watch) 추가
        val receivers = watchService.findActualWatchers(
            baseWatchers = setOf(author),
            resourceType = ResourceType.ISSUE_POST,
            resourceId = savedIssue.id.toString(),
            projectId = project.id
        ).toMutableSet()
        receivers.removeIf { it.id == author.id }
        notificationEvent.receivers = receivers

        notificationEventRepository.save(notificationEvent)
        eventPublisher.publishEvent(notificationEvent)

        return savedIssue
    }

    override fun updateIssue(
        issueId: Long,
        title: String,
        body: String,
        updater: User,
        assigneeUser: User?,
        milestoneId: Long?,
        labelIds: List<Long>?
    ): Issue {
        val issue = issueRepository.findById(issueId).orElseThrow { IllegalArgumentException("Issue not found") }
        issue.title = title
        issue.body = (body)
        issue.updatedDate = Instant.now()

        if (assigneeUser != null) {
            issue.assignee = Assignee(user = assigneeUser, project = issue.project)
        } else {
            issue.assignee = null
        }

        if (milestoneId != null) {
            val milestone = milestoneRepository.findById(milestoneId).orElse(null)
            issue.milestone = milestone
        } else {
            issue.milestone = null
        }

        if (labelIds != null) {
            val labels = issueLabelRepository.findAllById(labelIds)
            issue.labels = labels.toMutableSet()
        } else {
            issue.labels.clear()
        }

        return issueRepository.save(issue)
    }

    override fun changeState(issueId: Long, newState: State, updaterLoginId: String): Issue {
        val issue = issueRepository.findById(issueId).orElseThrow { IllegalArgumentException("Issue not found") }
        val oldState = issue.state
        if (oldState == newState) {
            return issue
        }

        issue.state = newState
        issue.updatedDate = Instant.now()
        val savedIssue = issueRepository.save(issue)

        val updater = userRepository.findByLoginId(updaterLoginId).orElse(null)
        val title = "[${issue.project.name}] 이슈 #${issue.number} 상태 변경: $oldState -> $newState"
        val notificationEvent = NotificationEvent(
            title = title,
            senderId = updater?.id,
            created = Instant.now(),
            resourceType = ResourceType.ISSUE_STATE,
            resourceId = savedIssue.id.toString(),
            eventType = EventType.ISSUE_STATE_CHANGED,
            oldValue = oldState.toString(),
            newValue = newState.toString()
        )

        // 감시자(Watch) 추가
        val authorUser = issue.authorId?.let { userRepository.findById(it).orElse(null) }
        val baseWatchers = if (authorUser != null) setOf(authorUser) else emptySet()
        val receivers = watchService.findActualWatchers(
            baseWatchers = baseWatchers,
            resourceType = ResourceType.ISSUE_POST,
            resourceId = savedIssue.id.toString(),
            projectId = issue.project.id
        ).toMutableSet()
        if (updater != null) {
            receivers.removeIf { it.id == updater.id }
        }
        notificationEvent.receivers = receivers

        notificationEventRepository.save(notificationEvent)
        eventPublisher.publishEvent(notificationEvent)

        recordIssueEvent(savedIssue, EventType.ISSUE_STATE_CHANGED, updaterLoginId, oldState.toString(), newState.toString())

        return savedIssue
    }

    override fun changeAssignee(issueId: Long, newAssigneeUser: User?, updaterLoginId: String): Issue {
        val issue = issueRepository.findById(issueId).orElseThrow { IllegalArgumentException("Issue not found") }
        val oldAssignee = issue.assignee?.user
        if (oldAssignee?.id == newAssigneeUser?.id) {
            return issue
        }

        if (newAssigneeUser == null) {
            issue.assignee = null
        } else {
            issue.assignee = Assignee(user = newAssigneeUser, project = issue.project)
        }
        issue.updatedDate = Instant.now()
        val savedIssue = issueRepository.save(issue)

        val updater = userRepository.findByLoginId(updaterLoginId).orElse(null)
        val title = "[${issue.project.name}] 이슈 #${issue.number} 담당자 변경"
        val notificationEvent = NotificationEvent(
            title = title,
            senderId = updater?.id,
            created = Instant.now(),
            resourceType = ResourceType.ISSUE_ASSIGNEE,
            resourceId = savedIssue.id.toString(),
            eventType = EventType.ISSUE_ASSIGNEE_CHANGED,
            oldValue = oldAssignee?.name,
            newValue = newAssigneeUser?.name
        )

        // 감시자(Watch) 추가
        val authorUser = issue.authorId?.let { userRepository.findById(it).orElse(null) }
        val baseWatchers = if (authorUser != null) setOf(authorUser) else emptySet()
        val receivers = watchService.findActualWatchers(
            baseWatchers = baseWatchers,
            resourceType = ResourceType.ISSUE_POST,
            resourceId = savedIssue.id.toString(),
            projectId = issue.project.id
        ).toMutableSet()
        if (updater != null) {
            receivers.removeIf { it.id == updater.id }
        }
        notificationEvent.receivers = receivers

        notificationEventRepository.save(notificationEvent)
        eventPublisher.publishEvent(notificationEvent)

        recordIssueEvent(savedIssue, EventType.ISSUE_ASSIGNEE_CHANGED, updaterLoginId, oldAssignee?.name, newAssigneeUser?.name)

        return savedIssue
    }

    override fun changeMilestone(issueId: Long, newMilestoneId: Long?, updaterLoginId: String): Issue {
        val issue = issueRepository.findById(issueId).orElseThrow { IllegalArgumentException("Issue not found") }
        val oldMilestone = issue.milestone
        if (oldMilestone?.id == newMilestoneId) {
            return issue
        }

        if (newMilestoneId == null) {
            issue.milestone = null
        } else {
            val milestone = milestoneRepository.findById(newMilestoneId).orElse(null)
            issue.milestone = milestone
        }
        issue.updatedDate = Instant.now()
        val savedIssue = issueRepository.save(issue)

        val updater = userRepository.findByLoginId(updaterLoginId).orElse(null)
        val title = "[${issue.project.name}] 이슈 #${issue.number} 마일스톤 변경"
        val notificationEvent = NotificationEvent(
            title = title,
            senderId = updater?.id,
            created = Instant.now(),
            resourceType = ResourceType.ISSUE_MILESTONE,
            resourceId = savedIssue.id.toString(),
            eventType = EventType.ISSUE_MILESTONE_CHANGED,
            oldValue = oldMilestone?.title,
            newValue = issue.milestone?.title
        )

        // 감시자(Watch) 추가
        val authorUser = issue.authorId?.let { userRepository.findById(it).orElse(null) }
        val baseWatchers = if (authorUser != null) setOf(authorUser) else emptySet()
        val receivers = watchService.findActualWatchers(
            baseWatchers = baseWatchers,
            resourceType = ResourceType.ISSUE_POST,
            resourceId = savedIssue.id.toString(),
            projectId = issue.project.id
        ).toMutableSet()
        if (updater != null) {
            receivers.removeIf { it.id == updater.id }
        }
        notificationEvent.receivers = receivers

        notificationEventRepository.save(notificationEvent)
        eventPublisher.publishEvent(notificationEvent)

        recordIssueEvent(savedIssue, EventType.ISSUE_MILESTONE_CHANGED, updaterLoginId, oldMilestone?.title, issue.milestone?.title)

        return savedIssue
    }

    // yona models/IssueEvent.java 대응(간소화 - draft-time 병합/취소 최적화는 제외, P1-07).
    private fun recordIssueEvent(issue: Issue, eventType: EventType, senderLoginId: String, oldValue: String?, newValue: String?) {
        val issueEvent = IssueEvent(
            issue = issue,
            senderLoginId = senderLoginId,
            senderEmail = userRepository.findByLoginId(senderLoginId).map { it.email }.orElse(null),
            oldValue = oldValue,
            newValue = newValue,
            created = Instant.now(),
            eventType = eventType
        )
        issueEventRepository.save(issueEvent)
    }

    override fun voteIssue(issueId: Long, user: User) {
        val issue = issueRepository.findById(issueId)
            .orElseThrow { IllegalArgumentException("Issue not found: $issueId") }
        val dbUser = userRepository.findById(user.id!!)
            .orElseThrow { IllegalArgumentException("User not found: ${user.id}") }

        if (issue.voters.any { it.id == dbUser.id }) {
            throw IllegalStateException("이미 투표하였습니다.")
        }
        issue.voters.add(dbUser)
        issueRepository.save(issue)
    }

    override fun unvoteIssue(issueId: Long, user: User) {
        val issue = issueRepository.findById(issueId)
            .orElseThrow { IllegalArgumentException("Issue not found: $issueId") }
        val dbUser = userRepository.findById(user.id!!)
            .orElseThrow { IllegalArgumentException("User not found: ${user.id}") }

        val voter = issue.voters.find { it.id == dbUser.id }
            ?: throw IllegalStateException("투표하지 않은 이슈입니다.")
        issue.voters.remove(voter)
        issueRepository.save(issue)
    }

    override fun voteComment(commentId: Long, user: User) {
        val comment = issueCommentRepository.findById(commentId)
            .orElseThrow { IllegalArgumentException("IssueComment not found: $commentId") }
        val dbUser = userRepository.findById(user.id!!)
            .orElseThrow { IllegalArgumentException("User not found: ${user.id}") }

        if (comment.voters.any { it.id == dbUser.id }) {
            throw IllegalStateException("이미 투표하였습니다.")
        }
        comment.voters.add(dbUser)
        issueCommentRepository.save(comment)
    }

    override fun unvoteComment(commentId: Long, user: User) {
        val comment = issueCommentRepository.findById(commentId)
            .orElseThrow { IllegalArgumentException("IssueComment not found: $commentId") }
        val dbUser = userRepository.findById(user.id!!)
            .orElseThrow { IllegalArgumentException("User not found: ${user.id}") }

        val voter = comment.voters.find { it.id == dbUser.id }
            ?: throw IllegalStateException("투표하지 않은 댓글입니다.")
        comment.voters.remove(voter)
        issueCommentRepository.save(comment)
    }
}