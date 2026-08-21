package com.github.search5.yona.domain.comment

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.enumeration.Operation
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.issue.IssueComment
import com.github.search5.yona.domain.issue.IssueCommentRepository
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.notification.NotificationEvent
import com.github.search5.yona.domain.notification.NotificationEventRecorder
import com.github.search5.yona.domain.board.PostingComment
import com.github.search5.yona.domain.board.PostingCommentRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.watch.WatchService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.regex.Pattern

@Service
@Transactional
class CommentServiceImpl(
    private val issueRepository: IssueRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val postingRepository: PostingRepository,
    private val postingCommentRepository: PostingCommentRepository,
    private val userRepository: UserRepository,
    private val notificationEventRecorder: NotificationEventRecorder,
    private val eventPublisher: ApplicationEventPublisher,
    private val watchService: WatchService,
    private val accessControl: AccessControl,
    private val organizationRepository: OrganizationRepository,
    private val organizationUserRepository: OrganizationUserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository
) : CommentService {

    // yona models/User.java:66 LOGIN_ID_PATTERN_ALLOW_FORWARD_SLASH(문자 클래스 내 "-"의 range
    // 파싱 모호성을 피하려 하이픈을 클래스 끝으로 옮김 — 문자 집합(영숫자/하이픈/슬래시) 자체는
    // 동일) + NotificationEvent.java:1518 getMentionedUsers()의 매칭 패턴 대응(P1-126,
    // owner/project 형식의 그룹 멘션을 포착하려면 '/'를 허용해야 한다).
    private val mentionPattern = Pattern.compile("@[a-zA-Z0-9/-]+([_.][a-z_.A-Z0-9/-]+)*")

    override fun createIssueComment(
        issueId: Long,
        contents: String,
        author: User,
        parentCommentId: Long?
    ): IssueComment {
        val issue = issueRepository.findById(issueId).orElseThrow { IllegalArgumentException("Issue not found") }
        
        var parentComment: IssueComment? = null
        if (parentCommentId != null) {
            parentComment = issueCommentRepository.findById(parentCommentId).orElse(null)
        }

        val comment = IssueComment(
            contents = contents,
            createdDate = Instant.now(),
            authorId = author.id,
            authorLoginId = author.loginId,
            authorName = author.name,
            projectId = issue.project.id,
            issue = issue,
            parentComment = parentComment
        )
        val savedComment = issueCommentRepository.save(comment)

        val mentionedUsers = extractMentionedUsers(contents)
        val title = "[${issue.project.name}] 이슈 #${issue.number}에 새 댓글이 등록되었습니다."
        val notificationEvent = NotificationEvent(
            title = title,
            senderId = author.id,
            created = Instant.now(),
            resourceType = ResourceType.ISSUE_COMMENT,
            resourceId = savedComment.id.toString(),
            eventType = EventType.NEW_COMMENT,
            newValue = contents
        )

        // 감시자(Watch) 추가
        val authorUser = issue.authorId?.let { userRepository.findById(it).orElse(null) }
        val baseWatchers = if (authorUser != null) setOf(authorUser) else emptySet()
        val receivers = watchService.findActualWatchers(
            baseWatchers = baseWatchers,
            resourceType = ResourceType.ISSUE_POST,
            resourceId = issue.id.toString(),
            projectId = issue.project.id,
            eventType = notificationEvent.eventType
        ).toMutableSet()
        receivers.removeIf { it.id == author.id }

        // 멘션된 사용자들 추가
        receivers.addAll(mentionedUsers)
        notificationEvent.receivers = receivers

        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }

        return savedComment
    }

    override fun createPostingComment(
        postingId: Long,
        contents: String,
        author: User,
        parentCommentId: Long?
    ): PostingComment {
        val posting = postingRepository.findById(postingId).orElseThrow { IllegalArgumentException("Posting not found") }
        
        var parentComment: PostingComment? = null
        if (parentCommentId != null) {
            parentComment = postingCommentRepository.findById(parentCommentId).orElse(null)
        }

        val comment = PostingComment(
            contents = contents,
            createdDate = Instant.now(),
            authorId = author.id,
            authorLoginId = author.loginId,
            authorName = author.name,
            projectId = posting.project.id,
            posting = posting,
            parentComment = parentComment
        )
        val savedComment = postingCommentRepository.save(comment)

        // yona AbstractPosting.save()/update()의 numOfComments = computeNumOfComments() 대응 (P1-19)
        posting.numOfComments = postingCommentRepository.countByPostingId(posting.id!!)
        postingRepository.save(posting)

        val mentionedUsers = extractMentionedUsers(contents)
        val title = "[${posting.project.name}] 게시글 #${posting.number}에 새 댓글이 등록되었습니다."
        val notificationEvent = NotificationEvent(
            title = title,
            senderId = author.id,
            created = Instant.now(),
            resourceType = ResourceType.NONISSUE_COMMENT,
            resourceId = savedComment.id.toString(),
            eventType = EventType.NEW_COMMENT,
            newValue = contents
        )

        // 게시글(BOARD_POST) 감시 연동
        val authorUser = posting.authorId?.let { userRepository.findById(it).orElse(null) }
        val baseWatchers = if (authorUser != null) setOf(authorUser) else emptySet()
        val receivers = watchService.findActualWatchers(
            baseWatchers = baseWatchers,
            resourceType = ResourceType.BOARD_POST,
            resourceId = posting.id.toString(),
            projectId = posting.project.id,
            eventType = notificationEvent.eventType
        ).toMutableSet()
        receivers.removeIf { it.id == author.id }

        receivers.addAll(mentionedUsers)
        notificationEvent.receivers = receivers

        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }

        return savedComment
    }

    // yona NotificationEvent.java:1517-1528 getMentionedUsers() 대응 (P1-126). 개별 사용자 멘션뿐
    // 아니라 조직 이름(@orgname → 조직 멤버 전원)과 owner/project 형식(@owner/project → 프로젝트
    // 멤버 전원)도 확장한다. 기존 게스트 계정 제외 정책은 확장된 멤버에도 동일하게 적용한다.
    // 조직/프로젝트 멤버는 엔티티의 in-memory 컬렉션(org.organizationUsers 등) 대신 리포지토리로
    // 직접 조회한다 — 같은 트랜잭션 안에서 방금 저장된 멤버가 부모 엔티티의 이미 초기화된(비어있는)
    // 컬렉션에 반영되지 않는 Hibernate 1차 캐시 함정을 피하기 위함이다.
    override fun extractMentionedUsers(contents: String): Set<User> {
        val users = mutableSetOf<User>()
        val matcher = mentionPattern.matcher(contents)
        while (matcher.find()) {
            val mentionWord = matcher.group().substring(1)

            organizationRepository.findByName(mentionWord).ifPresent { org ->
                organizationUserRepository.findByOrganizationId(org.id!!).forEach { users.add(it.user) }
            }

            if (mentionWord.contains("/")) {
                val lastSlash = mentionWord.lastIndexOf("/")
                val projectName = mentionWord.substring(lastSlash + 1)
                val ownerLoginId = mentionWord.substring(0, lastSlash)
                projectRepository.findByOwnerAndName(ownerLoginId, projectName).ifPresent { project ->
                    projectUserRepository.findByProjectId(project.id!!).forEach { users.add(it.user) }
                }
            }

            val user = userRepository.findByLoginId(mentionWord).orElse(null)
            if (user != null) {
                users.add(user)
            }
        }
        users.removeIf { it.isGuest }
        return users
    }

    override fun updateIssueComment(commentId: Long, contents: String, author: User): IssueComment {
        val comment = issueCommentRepository.findById(commentId)
            .orElseThrow { IllegalArgumentException("IssueComment not found: $commentId") }
        if (!accessControl.isAllowed(author, comment.issue.project, comment, Operation.UPDATE)) {
            throw IllegalArgumentException("Permission denied")
        }
        comment.contents = contents
        return issueCommentRepository.save(comment)
    }

    override fun deleteIssueComment(commentId: Long, author: User) {
        val comment = issueCommentRepository.findById(commentId)
            .orElseThrow { IllegalArgumentException("IssueComment not found: $commentId") }
        if (!accessControl.isAllowed(author, comment.issue.project, comment, Operation.DELETE)) {
            throw IllegalArgumentException("Permission denied")
        }
        issueCommentRepository.delete(comment)
    }

    override fun updatePostingComment(commentId: Long, contents: String, author: User): PostingComment {
        val comment = postingCommentRepository.findById(commentId)
            .orElseThrow { IllegalArgumentException("PostingComment not found: $commentId") }
        if (!accessControl.isAllowed(author, comment.posting.project, comment, Operation.UPDATE)) {
            throw IllegalArgumentException("Permission denied")
        }
        comment.contents = contents
        return postingCommentRepository.save(comment)
    }

    override fun deletePostingComment(commentId: Long, author: User) {
        val comment = postingCommentRepository.findById(commentId)
            .orElseThrow { IllegalArgumentException("PostingComment not found: $commentId") }
        if (!accessControl.isAllowed(author, comment.posting.project, comment, Operation.DELETE)) {
            throw IllegalArgumentException("Permission denied")
        }
        val posting = comment.posting
        postingCommentRepository.delete(comment)

        // yona AbstractPosting.save()/update()의 numOfComments = computeNumOfComments() 대응 (P1-19)
        posting.numOfComments = postingCommentRepository.countByPostingId(posting.id!!)
        postingRepository.save(posting)
    }
}