package com.github.search5.yona.domain.issue

import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.milestone.MilestoneRepository
import com.github.search5.yona.domain.notification.NotificationEvent
import com.github.search5.yona.domain.notification.NotificationEventRecorder
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.support.HistoryUtil
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
    private val notificationEventRecorder: NotificationEventRecorder,
    private val eventPublisher: ApplicationEventPublisher,
    private val issueCommentRepository: IssueCommentRepository,
    private val watchService: WatchService,
    private val issueEventRepository: IssueEventRepository,
    private val issueLabelService: IssueLabelService
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

        publishNewIssueNotification(savedIssue, author)

        return savedIssue
    }

    // yona NotificationEvent.afterNewIssue(issue)(forNewIssue(issue, sender)) 대응. 이슈를 새로 만들 때뿐
    // 아니라, 다른 프로젝트로 이동했을 때도 "그 프로젝트에 새로 생긴 이슈"로서 동일한 형식의 알림을
    // 다시 발행한다(moveIssue(), P1-48) — sender만 다르다(생성 시=작성자, 이동 시=이동을 실행한 사용자).
    private fun publishNewIssueNotification(issue: Issue, sender: User) {
        val title = "[${issue.project.name}] 신규 이슈 등록: #${issue.number} ${issue.title}"
        val notificationEvent = NotificationEvent(
            title = title,
            senderId = sender.id,
            created = Instant.now(),
            resourceType = ResourceType.ISSUE_POST,
            resourceId = issue.id.toString(),
            eventType = EventType.NEW_ISSUE,
            newValue = title
        )

        val receivers = watchService.findActualWatchers(
            baseWatchers = setOf(sender),
            resourceType = ResourceType.ISSUE_POST,
            resourceId = issue.id.toString(),
            projectId = issue.project.id,
            eventType = notificationEvent.eventType
        ).toMutableSet()
        receivers.removeIf { it.id == sender.id }
        notificationEvent.receivers = receivers

        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }
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
        val oldBody = issue.body
        val oldLabelNames = issue.labels.map { it.name }.sorted()

        issue.title = title
        issue.body = (body)
        issue.updatedDate = Instant.now()

        // yona AbstractPostingApp.editPosting()의 history 갱신 대응 (P2-02).
        if ((oldBody ?: "") != body) {
            issue.history = HistoryUtil.appendHistory(
                originalBody = oldBody,
                newBody = body,
                updaterName = updater.name,
                updaterLoginId = updater.loginId ?: "",
                updatedDate = issue.updatedDate,
                existingHistory = issue.history
            )
        }

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

        val savedIssue = issueRepository.save(issue)

        if (oldBody != body) {
            recordIssueEvent(savedIssue, EventType.ISSUE_BODY_CHANGED, updater.loginId!!, oldBody, body)

            // yona NotificationEvent.afterIssueBodyChanged() 대응 — 지금까지 yuna는 ISSUE_BODY_CHANGED를
            // IssueEvent(타임라인)에만 기록하고 알림 메일은 보내지 않고 있었다(PostingServiceImpl의
            // POSTING_BODY_CHANGED와 달리 누락돼 있던 부분). getMandatoryReceivers()의 "본문에서 새로
            // @멘션된 사용자 추가" 세부 로직은 이 파일의 다른 이슈 이벤트들과 마찬가지로 watcher 기반
            // 통지로 단순화한다(멘션 감지는 댓글 쪽(CommentServiceImpl)에만 있고 이슈 본문 수정에는
            // 아직 없다 — 별도 항목).
            val bodyChangedTitle = "[${savedIssue.project.name}] 이슈 #${savedIssue.number} 본문 수정: ${savedIssue.title}"
            val bodyChangedEvent = NotificationEvent(
                title = bodyChangedTitle,
                senderId = updater.id,
                created = Instant.now(),
                resourceType = ResourceType.ISSUE_POST,
                resourceId = savedIssue.id.toString(),
                eventType = EventType.ISSUE_BODY_CHANGED,
                oldValue = oldBody,
                newValue = body
            )
            val bodyChangedReceivers = watchService.findActualWatchers(
                baseWatchers = setOf(updater),
                resourceType = ResourceType.ISSUE_POST,
                resourceId = savedIssue.id.toString(),
                projectId = savedIssue.project.id,
                eventType = bodyChangedEvent.eventType
            ).toMutableSet()
            bodyChangedReceivers.removeIf { it.id == updater.id }
            bodyChangedEvent.receivers = bodyChangedReceivers

            notificationEventRecorder.record(bodyChangedEvent)?.let { eventPublisher.publishEvent(it) }
        }

        val newLabelNames = savedIssue.labels.map { it.name }.sorted()
        if (oldLabelNames != newLabelNames) {
            recordIssueEvent(
                savedIssue,
                EventType.ISSUE_LABEL_CHANGED,
                updater.loginId!!,
                oldLabelNames.joinToString(", "),
                newLabelNames.joinToString(", "),
                skipWaypoint = false
            )
        }

        return savedIssue
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
            projectId = issue.project.id,
            eventType = notificationEvent.eventType
        ).toMutableSet()
        if (updater != null) {
            receivers.removeIf { it.id == updater.id }
        }
        notificationEvent.receivers = receivers

        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }

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
            projectId = issue.project.id,
            eventType = notificationEvent.eventType
        ).toMutableSet()
        if (updater != null) {
            receivers.removeIf { it.id == updater.id }
        }
        notificationEvent.receivers = receivers

        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }

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
            projectId = issue.project.id,
            eventType = notificationEvent.eventType
        ).toMutableSet()
        if (updater != null) {
            receivers.removeIf { it.id == updater.id }
        }
        notificationEvent.receivers = receivers

        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }

        recordIssueEvent(savedIssue, EventType.ISSUE_MILESTONE_CHANGED, updaterLoginId, oldMilestone?.title, issue.milestone?.title)

        return savedIssue
    }

    // yona IssueApp.editIssue()의 hasTargetProject()/moveIssueToOtherProject()/addIssueMovedNotification()
    // 대응 (P1-48). 권한 확인(대상 프로젝트 생성권한/원본 이슈 수정권한)은 이 저장소의 다른 서비스
    // 메서드들과 동일하게 컨트롤러 쪽 책임이라 여기서는 하지 않는다 — 다만 yona의 editIssue()는
    // 그 순서가 반대(이동을 먼저 수행한 뒤 마지막에 editPosting()에서 UPDATE 권한을 확인)라 권한이
    // 없어도 이동 자체는 일부 반영되는 허점이 있었다. 그대로 재현하면 인가 우회 취약점을 그대로
    // 들여오는 셈이라, yuna 컨트롤러는 이동을 호출하기 전에 두 권한을 모두 먼저 확인하도록
    // 순서를 바로잡았다(관찰 가능한 정상 동작 자체는 legacy와 동일).
    override fun moveIssue(issueId: Long, targetProjectId: Long, mover: User): Issue {
        val issue = issueRepository.findById(issueId).orElseThrow { IllegalArgumentException("Issue not found: $issueId") }
        val previous = issue.project

        // yona isRequestedToOtherProject() 대응 — 같은 프로젝트면 아무 것도 하지 않는다.
        if (previous.id == targetProjectId) {
            return issue
        }

        val targetProject = projectRepository.findById(targetProjectId)
            .orElseThrow { IllegalArgumentException("Project not found: $targetProjectId") }

        // yona editIssue()의 "Set<User> fromWatchers = originalIssue.getWatchers()" 대응 — 이동
        // 시점(=기존 프로젝트 기준)의 감시자를 미리 캡처해둔다. 이동 후에 계산하면 새 프로젝트의
        // 뮤트/권한 설정을 기준으로 잘못 계산되므로 반드시 이동 직전에 캡처해야 한다.
        val baseWatchers = mutableSetOf<User>()
        issue.authorId?.let { authorId -> userRepository.findById(authorId).ifPresent { baseWatchers.add(it) } }
        issue.assignee?.user?.let { baseWatchers.add(it) }
        baseWatchers.addAll(issue.voters)
        val fromWatchers = watchService.findActualWatchers(
            baseWatchers = baseWatchers,
            resourceType = ResourceType.ISSUE_POST,
            resourceId = issue.id.toString(),
            projectId = previous.id,
            eventType = EventType.ISSUE_MOVED
        )

        // yona isFromMyOwnPrivateProject() 대응.
        val fromOwnPrivateProject = previous.isPrivate && previous.owner.equals(mover.loginId, ignoreCase = true)

        moveIssueAndSubtasksToProject(issue, targetProject, mover)

        if (fromOwnPrivateProject) {
            // yona editIssue() preUpdateHook의 isFromMyOwnPrivateProject() 분기 대응 — 알림 없이
            // 이력만 비운다(자신의 비공개 프로젝트에서 옮겨질 때 그 안의 편집 이력이 노출되지 않도록).
            issue.history = ""
        } else if (!issue.isDraft) {
            // yona addIssueMovedNotification()의 "!issue.isDraft"면 ISSUE_MOVED+NEW_ISSUE를 모두
            // 발행하는 것과 동일 — 초안(draft)이면 어느 쪽도 발행하지 않는다.
            publishIssueMovedNotification(previous, issue, fromWatchers, mover)
            publishNewIssueNotification(issue, mover)
        }

        return issueRepository.save(issue)
    }

    // yona moveIssueToOtherProject()/moveSubtaskToOtherProject() 대응 — 이슈 자신과 직계 서브태스크를
    // 모두 대상 프로젝트로 옮긴다. 서브태스크는(legacy와 동일하게) 별도 알림을 받지 않는다.
    private fun moveIssueAndSubtasksToProject(issue: Issue, targetProject: Project, mover: User) {
        updateIssueToOtherProject(issue, targetProject, mover)
        for (subtask in issueRepository.findByParentId(issue.id!!)) {
            updateIssueToOtherProject(subtask, targetProject, mover)
            issueRepository.save(subtask)
        }
    }

    // yona updateIssueToOtherProject() 대응.
    private fun updateIssueToOtherProject(issue: Issue, targetProject: Project, mover: User) {
        issue.project = targetProject
        targetProject.lastIssueNumber = targetProject.lastIssueNumber + 1
        projectRepository.save(targetProject)
        issue.number = targetProject.lastIssueNumber
        issue.createdDate = Instant.now()
        issue.updatedDate = Instant.now()
        issue.milestone = null

        for (comment in issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(issue.id!!)) {
            comment.projectId = targetProject.id
            issueCommentRepository.save(comment)
        }

        // yona "UserApp.currentUser().isMemberOf(toOtherProject) && issue.labels.size() > 0"의
        // transferLabels()/그 외에는 라벨을 비우는 분기 대응.
        val originalLabels = issue.labels.toSet()
        issue.labels = if (mover.isMemberOf(targetProject) && originalLabels.isNotEmpty()) {
            issueLabelService.transferLabelsForIssue(originalLabels, targetProject).toMutableSet()
        } else {
            mutableSetOf()
        }

        issueRepository.save(issue)
    }

    // yona NotificationEvent.afterIssueMoved(previous, issue, fromWatchers) 대응. title은
    // formatReplyTitle(issue)와 동일하게 이동 "이후"(새 프로젝트로 옮겨진 뒤) 시점의 값을 쓴다.
    // legacy와 마찬가지로 mover 자신을 수신자에서 제외하지 않는다 — 본인이 watcher/author/assignee/
    // voter였다면 자신이 실행한 이동에 대한 알림도 함께 받는다(다른 알림들과 달리 self-제외가 없음).
    private fun publishIssueMovedNotification(previous: Project, issue: Issue, receivers: Set<User>, mover: User) {
        val notificationEvent = NotificationEvent(
            title = "Re: [${issue.project.name}] ${issue.title} (#${issue.number})",
            senderId = mover.id,
            created = Instant.now(),
            resourceType = ResourceType.ISSUE_POST,
            resourceId = issue.id.toString(),
            eventType = EventType.ISSUE_MOVED,
            oldValue = "${previous.owner}/${previous.name}",
            newValue = "${issue.project.owner}/${issue.project.name}"
        )
        notificationEvent.receivers = receivers.toMutableSet()

        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }
    }

    // yona models/IssueEvent.java의 add()/addWithoutSkipEvent() 대응(draft-time 병합/취소, P1-38).
    private fun recordIssueEvent(
        issue: Issue,
        eventType: EventType,
        senderLoginId: String,
        oldValue: String?,
        newValue: String?,
        skipWaypoint: Boolean = true
    ) {
        val issueEvent = IssueEvent(
            issue = issue,
            senderLoginId = senderLoginId,
            senderEmail = userRepository.findByLoginId(senderLoginId).map { it.email }.orElse(null),
            oldValue = oldValue,
            newValue = newValue,
            created = Instant.now(),
            eventType = eventType
        )
        issueEventRepository.recordWithDraftMerge(issueEvent, skipWaypoint)
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