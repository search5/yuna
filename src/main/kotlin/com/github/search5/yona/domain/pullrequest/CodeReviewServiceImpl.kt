package com.github.search5.yona.domain.pullrequest

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.support.CodeRange
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserIdent
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.vcs.RepositoryService
import com.github.search5.yona.domain.notification.NotificationEvent
import com.github.search5.yona.domain.notification.NotificationEventRepository
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.enumeration.EventType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class CodeReviewServiceImpl(
    private val commentThreadRepository: CommentThreadRepository,
    private val reviewCommentRepository: ReviewCommentRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val repositoryService: RepositoryService,
    private val userRepository: UserRepository,
    private val notificationEventRepository: NotificationEventRepository,
    private val commitCommentRepository: CommitCommentRepository,
    private val eventPublisher: ApplicationEventPublisher
) : CodeReviewService {

    override fun createReviewComment(
        project: Project,
        pullRequest: PullRequest?,
        commitId: String?,
        contents: String,
        codeRange: CodeRange?,
        threadId: Long?,
        currentUser: User
    ): ReviewComment {
        val userIdent = UserIdent(currentUser)
        val comment = ReviewComment(
            contents = contents,
            createdDate = Instant.now(),
            author = userIdent
        )

        val targetThread: CommentThread
        if (threadId == null) {
            if (codeRange != null && codeRange.startLine != null) {
                val thread = CodeCommentThread()
                if (commitId != null) {
                    thread.commitId = commitId
                } else if (pullRequest != null) {
                    thread.commitId = pullRequest.mergedCommitIdTo
                    thread.prevCommitId = pullRequest.mergedCommitIdFrom ?: ""
                }

                thread.commitId?.let { cid ->
                    try {
                        val commit = repositoryService.getRepository(project).getCommit(cid)
                        val codeAuthor = commit?.getAuthor()
                        if (codeAuthor != null && codeAuthor.id != null && !codeAuthor.isGuest) {
                            thread.codeAuthors.add(codeAuthor)
                        }
                    } catch (e: Exception) {
                        // VCS에서 커밋 작성자 로딩 실패 시 예외를 조용히 처리하거나 로깅
                    }
                }

                thread.codeRange = codeRange
                targetThread = thread
            } else {
                val thread = NonRangedCodeCommentThread()
                if (commitId != null) {
                    thread.commitId = commitId
                } else if (pullRequest != null) {
                    thread.commitId = pullRequest.mergedCommitIdTo
                    thread.prevCommitId = pullRequest.mergedCommitIdFrom ?: ""
                }
                targetThread = thread
            }

            targetThread.project = project
            targetThread.state = CommentThread.ThreadState.OPEN
            targetThread.createdDate = comment.createdDate
            targetThread.author = userIdent
            targetThread.pullRequest = pullRequest

            commentThreadRepository.save(targetThread)
        } else {
            targetThread = commentThreadRepository.findById(threadId)
                .orElseThrow { IllegalArgumentException("CommentThread not found for id: $threadId") }
        }

        comment.thread = targetThread
        targetThread.reviewComments.add(comment)

        val savedComment = reviewCommentRepository.save(comment)

        if (pullRequest != null) {
            pullRequestRepository.save(pullRequest)
        }

        return savedComment
    }

    override fun deleteReviewComment(commentId: Long, currentUser: User) {
        val comment = reviewCommentRepository.findById(commentId)
            .orElseThrow { IllegalArgumentException("ReviewComment not found for id: $commentId") }

        val thread = comment.thread ?: throw IllegalStateException("Comment has no associated thread.")
        val threadId = thread.id ?: throw IllegalStateException("Thread has no id.")

        thread.removeComment(comment)
        reviewCommentRepository.delete(comment)

        val remainingComments = reviewCommentRepository.findByThreadIdOrderByCreatedDateAsc(threadId)
        if (remainingComments.isEmpty()) {
            commentThreadRepository.delete(thread)
        }
    }

    override fun createCommitComment(
        project: Project,
        commitId: String,
        contents: String,
        path: String?,
        line: Int?,
        side: CodeRange.Side?,
        currentUser: User
    ): CommitComment {
        val userIdent = UserIdent(currentUser)
        val commitComment = CommitComment(
            project = project,
            commitId = commitId,
            contents = contents,
            path = path,
            line = line,
            side = side,
            createdDate = Instant.now(),
            author = userIdent
        )
        return commitCommentRepository.save(commitComment)
    }

    override fun deleteCommitComment(commentId: Long, currentUser: User) {
        val comment = commitCommentRepository.findById(commentId)
            .orElseThrow { IllegalArgumentException("CommitComment not found for id: $commentId") }
        commitCommentRepository.delete(comment)
    }

    override fun updateThreadState(
        threadId: Long,
        state: CommentThread.ThreadState,
        currentUser: User
    ): CommentThread {
        val thread = commentThreadRepository.findById(threadId)
            .orElseThrow { IllegalArgumentException("CommentThread not found for id: $threadId") }

        thread.state = state
        return commentThreadRepository.save(thread)
    }

    override fun addReviewer(pullRequestId: Long, reviewerId: Long) {
        val pullRequest = pullRequestRepository.findById(pullRequestId)
            .orElseThrow { IllegalArgumentException("PullRequest not found") }
        val reviewer = userRepository.findById(reviewerId)
            .orElseThrow { IllegalArgumentException("User not found") }

        if (pullRequest.reviewers.add(reviewer)) {
            pullRequestRepository.save(pullRequest)

            val title = "[${pullRequest.toProject.name}] 풀 리퀘스트 #${pullRequest.number}에 리뷰어로 참여했습니다."
            val notificationEvent = NotificationEvent(
                title = title,
                senderId = reviewerId,
                created = Instant.now(),
                resourceType = ResourceType.PULL_REQUEST,
                resourceId = pullRequestId.toString(),
                eventType = EventType.PULL_REQUEST_REVIEW_STATE_CHANGED,
                newValue = "DONE"
            )

            val receivers = mutableSetOf<User>()
            receivers.add(pullRequest.contributor)
            receivers.addAll(pullRequest.reviewers)
            receivers.removeIf { it.id == reviewerId }
            notificationEvent.receivers = receivers

            notificationEventRepository.save(notificationEvent)
            eventPublisher.publishEvent(notificationEvent)
        }
    }

    override fun removeReviewer(pullRequestId: Long, reviewerId: Long) {
        val pullRequest = pullRequestRepository.findById(pullRequestId)
            .orElseThrow { IllegalArgumentException("PullRequest not found") }
        val reviewer = userRepository.findById(reviewerId)
            .orElseThrow { IllegalArgumentException("User not found") }

        if (pullRequest.reviewers.remove(reviewer)) {
            pullRequestRepository.save(pullRequest)

            val title = "[${pullRequest.toProject.name}] 풀 리퀘스트 #${pullRequest.number}의 리뷰어 참여를 취소했습니다."
            val notificationEvent = NotificationEvent(
                title = title,
                senderId = reviewerId,
                created = Instant.now(),
                resourceType = ResourceType.PULL_REQUEST,
                resourceId = pullRequestId.toString(),
                eventType = EventType.PULL_REQUEST_REVIEW_STATE_CHANGED,
                newValue = "CANCEL"
            )

            val receivers = mutableSetOf<User>()
            receivers.add(pullRequest.contributor)
            receivers.addAll(pullRequest.reviewers)
            receivers.removeIf { it.id == reviewerId }
            notificationEvent.receivers = receivers

            notificationEventRepository.save(notificationEvent)
            eventPublisher.publishEvent(notificationEvent)
        }
    }
}
