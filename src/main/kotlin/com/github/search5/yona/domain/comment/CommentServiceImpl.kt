package com.github.search5.yona.domain.comment

import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.issue.IssueComment
import com.github.search5.yona.domain.issue.IssueCommentRepository
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.notification.NotificationEvent
import com.github.search5.yona.domain.notification.NotificationEventRecorder
import com.github.search5.yona.domain.board.PostingComment
import com.github.search5.yona.domain.board.PostingCommentRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.watch.WatchService
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.role.RoleType
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
    private val projectUserRepository: ProjectUserRepository
) : CommentService {

    private val mentionPattern = Pattern.compile("@([a-zA-Z0-9_\\\\-\\\\.]+) ")

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

    override fun extractMentionedUsers(contents: String): Set<User> {
        val users = mutableSetOf<User>()
        val matcher = mentionPattern.matcher(contents)
        while (matcher.find()) {
            val loginId = matcher.group(1)
            val user = userRepository.findByLoginId(loginId).orElse(null)
            if (user != null && !user.isGuest) {
                users.add(user)
            }
        }
        return users
    }

    override fun updateIssueComment(commentId: Long, contents: String, author: User): IssueComment {
        val comment = issueCommentRepository.findById(commentId)
            .orElseThrow { IllegalArgumentException("IssueComment not found: $commentId") }
        if (!hasPermission(comment.projectId, comment.authorId, author.id)) {
            throw IllegalArgumentException("Permission denied")
        }
        comment.contents = contents
        return issueCommentRepository.save(comment)
    }

    override fun deleteIssueComment(commentId: Long, author: User) {
        val comment = issueCommentRepository.findById(commentId)
            .orElseThrow { IllegalArgumentException("IssueComment not found: $commentId") }
        if (!hasPermission(comment.projectId, comment.authorId, author.id)) {
            throw IllegalArgumentException("Permission denied")
        }
        issueCommentRepository.delete(comment)
    }

    override fun updatePostingComment(commentId: Long, contents: String, author: User): PostingComment {
        val comment = postingCommentRepository.findById(commentId)
            .orElseThrow { IllegalArgumentException("PostingComment not found: $commentId") }
        if (!hasPermission(comment.projectId, comment.authorId, author.id)) {
            throw IllegalArgumentException("Permission denied")
        }
        comment.contents = contents
        return postingCommentRepository.save(comment)
    }

    override fun deletePostingComment(commentId: Long, author: User) {
        val comment = postingCommentRepository.findById(commentId)
            .orElseThrow { IllegalArgumentException("PostingComment not found: $commentId") }
        if (!hasPermission(comment.projectId, comment.authorId, author.id)) {
            throw IllegalArgumentException("Permission denied")
        }
        val posting = comment.posting
        postingCommentRepository.delete(comment)

        // yona AbstractPosting.save()/update()의 numOfComments = computeNumOfComments() 대응 (P1-19)
        posting.numOfComments = postingCommentRepository.countByPostingId(posting.id!!)
        postingRepository.save(posting)
    }

    private fun hasPermission(projectId: Long?, commentAuthorId: Long?, requestUserId: Long?): Boolean {
        if (requestUserId == null) return false
        if (commentAuthorId == requestUserId) return true
        if (projectId == null) return false
        return projectUserRepository.findByProjectIdAndUserId(projectId, requestUserId)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)
    }
}