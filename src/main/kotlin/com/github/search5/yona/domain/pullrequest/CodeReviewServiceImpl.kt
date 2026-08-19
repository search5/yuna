package com.github.search5.yona.domain.pullrequest

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.support.CodeRange
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserIdent
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.vcs.RepositoryService
import com.github.search5.yona.domain.notification.NotificationEvent
import com.github.search5.yona.domain.notification.NotificationEventRecorder
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.attachment.AttachmentService
import com.github.search5.yona.domain.comment.CommentService
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.watch.WatchService
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
    private val notificationEventRecorder: NotificationEventRecorder,
    private val commitCommentRepository: CommitCommentRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val projectUserRepository: ProjectUserRepository,
    private val attachmentService: AttachmentService,
    private val pullRequestCommitRepository: PullRequestCommitRepository,
    private val pullRequestEventRepository: PullRequestEventRepository,
    private val commentService: CommentService,
    private val watchService: WatchService
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

        publishNewReviewCommentNotification(project, targetThread, savedComment, currentUser)

        return savedComment
    }

    // yona NotificationEvent.forNewComment(sender, pullRequest, newComment)(PR 위) /
    // forNewCommitComment(project, comment, commitId, author)(PR 밖 커밋 위) 대응. 새 댓글이든
    // 기존 스레드에 대한 답글이든 legacy는 구분 없이 항상 이 알림을 발행한다(PullRequestApp.newComment/
    // CodeHistoryApp.newCommitComment 모두 스레드 신규/기존 분기 이후 조건 없이 호출).
    private fun publishNewReviewCommentNotification(project: Project, thread: CommentThread, comment: ReviewComment, sender: User) {
        val mentioned = commentService.extractMentionedUsers(comment.contents)
        val receivers = mutableSetOf<User>()
        receivers.addAll(mentioned)

        val pullRequest = thread.pullRequest
        val title: String
        if (pullRequest != null) {
            title = "[${pullRequest.toProject.name}] 풀 리퀘스트 #${pullRequest.number}에 새 리뷰 댓글이 등록되었습니다."
            receivers.addAll(
                watchService.findActualWatchers(
                    baseWatchers = emptySet(),
                    resourceType = ResourceType.PULL_REQUEST,
                    resourceId = pullRequest.id.toString(),
                    projectId = pullRequest.toProject.id,
                    eventType = EventType.NEW_REVIEW_COMMENT
                )
            )
        } else {
            // yona commit.getWatchers(project)(커밋 단위 감시자) 대응 — yuna는 커밋을 감시 대상으로
            // 등록하는 UI/데이터 모델이 없어(P1-25/46에서도 같은 제약 확인) 프로젝트 감시자로 대체한다.
            title = "[${project.name}] 커밋 리뷰에 새 댓글이 등록되었습니다."
            receivers.addAll(
                watchService.findActualWatchers(
                    baseWatchers = emptySet(),
                    resourceType = ResourceType.PROJECT,
                    resourceId = project.id.toString(),
                    projectId = project.id,
                    eventType = EventType.NEW_REVIEW_COMMENT
                )
            )
        }
        receivers.removeIf { it.id == sender.id }
        if (receivers.isEmpty()) return

        val notificationEvent = NotificationEvent(
            title = title,
            senderId = sender.id,
            created = Instant.now(),
            resourceType = ResourceType.REVIEW_COMMENT,
            resourceId = comment.id.toString(),
            eventType = EventType.NEW_REVIEW_COMMENT,
            newValue = comment.contents,
            receivers = receivers
        )
        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }
    }

    override fun deleteReviewComment(commentId: Long, currentUser: User) {
        val comment = reviewCommentRepository.findById(commentId)
            .orElseThrow { IllegalArgumentException("ReviewComment not found for id: $commentId") }

        val thread = comment.thread ?: throw IllegalStateException("Comment has no associated thread.")
        val threadId = thread.id ?: throw IllegalStateException("Thread has no id.")
        val projectId = thread.project?.id ?: thread.pullRequest?.toProject?.id

        if (!hasPermission(projectId, comment.author?.id, currentUser.id)) {
            throw IllegalArgumentException("Permission denied")
        }

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
        val saved = commitCommentRepository.save(commitComment)
        attachmentService.moveAll(
            ResourceType.USER,
            currentUser.id.toString(),
            ResourceType.COMMIT_COMMENT,
            saved.id.toString()
        )

        publishNewCommitCommentNotification(project, saved, currentUser)

        return saved
    }

    // yona NotificationEvent.forNewSVNCommitComment(project, codeComment, author) 대응.
    // legacy는 이벤트 타입을 NEW_REVIEW_COMMENT가 아니라 NEW_COMMENT로 남긴다(CommitComment는
    // ReviewComment와 다른 모델이라 별도 분기).
    private fun publishNewCommitCommentNotification(project: Project, comment: CommitComment, sender: User) {
        val mentioned = commentService.extractMentionedUsers(comment.contents)
        val receivers = mutableSetOf<User>()
        receivers.addAll(mentioned)
        // yona commit.getWatchers(project) 대응 — createReviewComment의 커밋 단위 분기와 동일한 이유로
        // 프로젝트 감시자로 대체한다.
        receivers.addAll(
            watchService.findActualWatchers(
                baseWatchers = emptySet(),
                resourceType = ResourceType.PROJECT,
                resourceId = project.id.toString(),
                projectId = project.id,
                eventType = EventType.NEW_COMMENT
            )
        )
        receivers.removeIf { it.id == sender.id }
        if (receivers.isEmpty()) return

        val notificationEvent = NotificationEvent(
            title = "[${project.name}] 커밋 리뷰에 새 댓글이 등록되었습니다.",
            senderId = sender.id,
            created = Instant.now(),
            resourceType = ResourceType.COMMIT_COMMENT,
            resourceId = comment.id.toString(),
            eventType = EventType.NEW_COMMENT,
            newValue = comment.contents,
            receivers = receivers
        )
        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }
    }

    override fun deleteCommitComment(commentId: Long, currentUser: User) {
        val comment = commitCommentRepository.findById(commentId)
            .orElseThrow { IllegalArgumentException("CommitComment not found for id: $commentId") }

        if (!hasPermission(comment.project?.id, comment.author?.id, currentUser.id)) {
            throw IllegalArgumentException("Permission denied")
        }

        commitCommentRepository.delete(comment)
    }

    private fun hasPermission(projectId: Long?, commentAuthorId: Long?, requestUserId: Long?): Boolean {
        if (requestUserId == null) return false
        if (commentAuthorId == requestUserId) return true
        if (projectId == null) return false
        return projectUserRepository.findByProjectIdAndUserId(projectId, requestUserId)
            .map { it.role.id == RoleType.MANAGER.roleType }
            .orElse(false)
    }

    override fun updateThreadState(
        threadId: Long,
        state: CommentThread.ThreadState,
        currentUser: User
    ): CommentThread {
        val thread = commentThreadRepository.findById(threadId)
            .orElseThrow { IllegalArgumentException("CommentThread not found for id: $threadId") }
        val oldState = thread.state
        if (oldState == state) {
            return thread
        }

        thread.state = state
        val saved = commentThreadRepository.save(thread)

        publishThreadStateChangedNotification(saved, oldState, currentUser)

        return saved
    }

    // yona NotificationEvent.afterStateChanged(CommentThread.ThreadState, CommentThread) 대응.
    private fun publishThreadStateChangedNotification(thread: CommentThread, oldState: CommentThread.ThreadState, sender: User) {
        val pullRequest = thread.pullRequest
        val title: String
        val watchers: Set<User>
        if (pullRequest != null) {
            title = "[${pullRequest.toProject.name}] 풀 리퀘스트 #${pullRequest.number}의 리뷰 스레드 상태가 변경되었습니다."
            watchers = watchService.findActualWatchers(
                baseWatchers = emptySet(),
                resourceType = ResourceType.PULL_REQUEST,
                resourceId = pullRequest.id.toString(),
                projectId = pullRequest.toProject.id,
                eventType = EventType.REVIEW_THREAD_STATE_CHANGED
            )
        } else {
            val project = thread.project ?: return
            title = "[${project.name}] 리뷰 스레드 상태가 변경되었습니다."
            // yona commit.getWatchers(project) 대응 — createReviewComment의 커밋 단위 분기와 동일한 이유로
            // 프로젝트 감시자로 대체한다.
            watchers = watchService.findActualWatchers(
                baseWatchers = emptySet(),
                resourceType = ResourceType.PROJECT,
                resourceId = project.id.toString(),
                projectId = project.id,
                eventType = EventType.REVIEW_THREAD_STATE_CHANGED
            )
        }

        val receivers = watchers.filterTo(mutableSetOf()) { it.id != sender.id }
        if (receivers.isEmpty()) return

        val notificationEvent = NotificationEvent(
            title = title,
            senderId = sender.id,
            created = Instant.now(),
            resourceType = ResourceType.COMMENT_THREAD,
            resourceId = thread.id.toString(),
            eventType = EventType.REVIEW_THREAD_STATE_CHANGED,
            oldValue = oldState.name,
            newValue = thread.state.name,
            receivers = receivers
        )
        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }
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
                // yona NotificationEvent.afterReviewed()의 oldValue = reviewAction.getOppositAction().name() 대응.
                oldValue = "CANCEL",
                newValue = "DONE"
            )

            val receivers = mutableSetOf<User>()
            receivers.add(pullRequest.contributor)
            receivers.addAll(pullRequest.reviewers)
            receivers.removeIf { it.id == reviewerId }
            notificationEvent.receivers = receivers

            notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }

            recordPullRequestEvent(pullRequest, reviewer.loginId, "DONE")
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
                oldValue = "DONE",
                newValue = "CANCEL"
            )

            val receivers = mutableSetOf<User>()
            receivers.add(pullRequest.contributor)
            receivers.addAll(pullRequest.reviewers)
            receivers.removeIf { it.id == reviewerId }
            notificationEvent.receivers = receivers

            notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }

            recordPullRequestEvent(pullRequest, reviewer.loginId, "CANCEL")
        }
    }

    // yona models/PullRequestEvent.java 대응 (P1-39) — 리뷰어 참여/해제 시점을 PR 타임라인에 기록.
    // draft-time 병합/취소(P1-40): 같은 리뷰어가 30초 내 연속으로 참여/해제를 반복하면 직전 이벤트를
    // 삭제하고 새 이벤트도 저장하지 않는다(legacy PullRequestEvent.needToDeleteEvent와 동일한 동작).
    private fun recordPullRequestEvent(pullRequest: PullRequest, senderLoginId: String?, newValue: String) {
        val event = PullRequestEvent(
            pullRequest = pullRequest,
            senderLoginId = senderLoginId,
            eventType = EventType.PULL_REQUEST_REVIEW_STATE_CHANGED,
            newValue = newValue,
            created = Instant.now()
        )
        pullRequestEventRepository.recordWithDraftMerge(event)
    }

    // yona CodeCommentThread.isOutdated() 대응 (P1-20)
    override fun isThreadOutdated(threadId: Long): Boolean {
        val thread = commentThreadRepository.findById(threadId).orElse(null) as? CodeCommentThread ?: return false
        return computeOutdated(thread)
    }

    private fun computeOutdated(thread: CodeCommentThread): Boolean {
        if (thread.codeRange.startLine == null || thread.commitId.isNullOrEmpty()) {
            return false
        }
        if (!thread.isOnPullRequest()) {
            return false
        }
        val pullRequest = thread.pullRequest!!
        if (pullRequest.mergedCommitIdFrom == null || pullRequest.mergedCommitIdTo == null) {
            return false
        }

        val commitId = thread.commitId!!

        if (thread.isCommitComment()) {
            return pullRequestCommitRepository
                .findFirstByPullRequestAndCommitIdOrderByCreatedDesc(pullRequest, commitId) == null
        }

        var path = thread.codeRange.path ?: ""
        if (path.startsWith("/")) {
            path = path.substring(1)
        }

        val repository = repositoryService.getRepository(pullRequest.toProject)

        return try {
            val unchangedFromMergeBase = noChangesBetween(
                repository, pullRequest.mergedCommitIdFrom!!, thread.prevCommitId, path
            )
            if (!unchangedFromMergeBase) {
                true
            } else {
                !noChangesBetween(repository, pullRequest.mergedCommitIdTo!!, commitId, path)
            }
        } catch (e: Exception) {
            // yona MissingObjectException 처리와 동일 — git 객체 조회 실패 시 outdated로 간주(안전한 기본값)
            true
        }
    }

    private fun noChangesBetween(repository: com.github.search5.yona.domain.vcs.PlayRepository, revA: String, revB: String, path: String): Boolean {
        val blobA = repository.getBlobId(revA, path)
        val blobB = repository.getBlobId(revB, path)
        return blobA == blobB
    }
}
