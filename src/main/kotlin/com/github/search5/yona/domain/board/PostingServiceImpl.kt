package com.github.search5.yona.domain.board

import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.TitleHeadService
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.watch.WatchService
import com.github.search5.yona.domain.notification.NotificationEvent
import com.github.search5.yona.domain.notification.NotificationEventRecorder
import com.github.search5.yona.domain.enumeration.EventType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import com.github.search5.yona.domain.attachment.AttachmentService
import com.github.search5.yona.domain.comment.CommentService
import com.github.search5.yona.domain.mention.MentionService
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.support.HistoryUtil

@Service
@Transactional(readOnly = true)
class PostingServiceImpl(
    private val postingRepository: PostingRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val attachmentService: AttachmentService,
    private val postingCommentRepository: PostingCommentRepository,
    private val watchService: WatchService,
    private val notificationEventRecorder: NotificationEventRecorder,
    private val eventPublisher: ApplicationEventPublisher,
    private val titleHeadService: TitleHeadService,
    private val commentService: CommentService,
    // yona AbstractPosting.updateMention() 대응 (P2-41).
    private val mentionService: MentionService
) : PostingService {

    // yona NotificationEvent.afterNewPost/afterResourceDeleted 대응 (P1-18)
    private fun publishNotification(
        posting: Posting,
        actor: User,
        eventType: EventType,
        title: String
    ) {
        val notificationEvent = NotificationEvent(
            title = title,
            senderId = actor.id,
            created = Instant.now(),
            resourceType = ResourceType.BOARD_POST,
            resourceId = posting.id.toString(),
            eventType = eventType,
            newValue = title
        )

        val receivers = watchService.findActualWatchers(
            baseWatchers = setOf(actor),
            resourceType = ResourceType.BOARD_POST,
            resourceId = posting.id.toString(),
            projectId = posting.project.id,
            eventType = eventType
        ).toMutableSet()
        // yona NotificationEvent.java:1380-1385 getReceivers(abstractPosting, except)의
        // getMentionedUsers(body) 대응 (P1-127). 신규 게시글 본문의 @멘션도 수신자에 포함한다.
        receivers.addAll(commentService.extractMentionedUsers(posting.body ?: ""))
        receivers.removeIf { it.id == actor.id }
        notificationEvent.receivers = receivers

        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }
    }

    override fun getPostings(projectId: Long, pageable: Pageable): Page<Posting> {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("프로젝트를 찾을 수 없습니다.") }
        return postingRepository.findByProject(project, pageable)
    }

    override fun getNotices(projectId: Long): List<Posting> {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("프로젝트를 찾을 수 없습니다.") }
        return postingRepository.findByProjectAndNotice(project, true)
    }

    override fun getPosting(projectId: Long, number: Long): Posting? {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("프로젝트를 찾을 수 없습니다.") }
        return postingRepository.findByProjectAndNumber(project, number)
    }

    @Transactional
    override fun createPosting(projectId: Long, posting: Posting, authorId: Long): Posting {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("프로젝트를 찾을 수 없습니다.") }
        val author = userRepository.findById(authorId)
            .orElseThrow { IllegalArgumentException("사용자를 찾을 수 없습니다.") }

        // 일련번호 증가 및 프로젝트 영속화
        project.lastPostingNumber = project.lastPostingNumber + 1
        projectRepository.save(project)

        posting.project = project
        posting.number = project.lastPostingNumber
        posting.authorId = author.id
        posting.authorLoginId = author.loginId
        posting.authorName = author.name
        posting.createdDate = Instant.now()
        posting.updatedDate = Instant.now()

        val saved = postingRepository.save(posting)

        // yona AbstractPosting.save()의 updateMention() 대응 (P2-41).
        mentionService.update(ResourceType.BOARD_POST, saved.id.toString(), commentService.extractMentionedUsers(saved.body ?: ""))

        // yona AbstractPosting.save()의 TitleHead.saveTitleHeadKeyword() 대응 (P1-103).
        titleHeadService.saveTitleHeadKeyword(project, saved.title)

        val title = "[${project.name}] 새 게시글: ${saved.title}"
        publishNotification(saved, author, EventType.NEW_POSTING, title)

        return saved
    }

    @Transactional
    override fun updatePosting(
        projectId: Long,
        number: Long,
        title: String,
        body: String,
        notice: Boolean,
        readme: Boolean,
        authorId: Long,
        sendNotificationMail: Boolean
    ): Posting {
        val posting = getPosting(projectId, number)
            ?: throw IllegalArgumentException("포스팅을 찾을 수 없습니다.")

        val originalBody = posting.body
        val originalTitle = posting.title
        val isAuthoredByUpdater = posting.authorId == authorId
        val updater = userRepository.findById(authorId).orElse(null)

        posting.title = title.trim()
        posting.body = body
        posting.notice = notice
        posting.readme = readme
        posting.updatedDate = Instant.now()

        // yona AbstractPostingApp.editPosting()의 "posting.updatedByAuthorId = UserApp.currentUser().id"
        // 대응 (P2-02) — history 유무와 무관하게 편집이 있을 때마다 항상 갱신된다.
        if (updater != null) {
            posting.updatedByAuthorId = updater.id
            posting.updatedByAuthorLoginId = updater.loginId
            posting.updatedByAuthorName = updater.name
        }

        // yona AbstractPostingApp.editPosting()의 history 갱신 대응 (P2-02).
        if (updater != null && (originalBody ?: "") != body) {
            posting.history = HistoryUtil.appendHistory(
                originalBody = originalBody,
                newBody = body,
                updaterName = updater.name,
                updaterLoginId = updater.loginId,
                updatedDate = posting.updatedDate,
                existingHistory = posting.history
            )
        }

        val saved = postingRepository.save(posting)

        // yona AbstractPosting.update()의 updateMention() 대응 (P2-41).
        mentionService.update(ResourceType.BOARD_POST, saved.id.toString(), commentService.extractMentionedUsers(saved.body ?: ""))

        // yona AbstractPostingApp.editPosting()의 TitleHead.saveTitleHeadKeyword()/deleteTitleHeadKeyword()
        // 대응 (P1-103). 제목이 안 바뀌었어도 legacy와 동일하게 매 수정마다 무조건 두 호출을 모두 실행한다.
        titleHeadService.saveTitleHeadKeyword(saved.project, saved.title)
        titleHeadService.deleteTitleHeadKeyword(saved.project, originalTitle)

        // yona BoardApp.editPost의 isSelectedToSendNotificationMail() 대응 (P1-44).
        // 본인 글이 아니면 옵션과 무관하게 항상 발송하고, 본인 글이면 체크박스를 선택했을 때만 발송한다.
        if (sendNotificationMail || !isAuthoredByUpdater) {
            if (updater != null) {
                val title2 = "[${saved.project.name}] 게시글 수정: ${saved.title}"
                val notificationEvent = NotificationEvent(
                    title = title2,
                    senderId = updater.id,
                    created = Instant.now(),
                    resourceType = ResourceType.BOARD_POST,
                    resourceId = saved.id.toString(),
                    eventType = EventType.POSTING_BODY_CHANGED,
                    oldValue = originalBody,
                    newValue = saved.body
                )
                val receivers = watchService.findActualWatchers(
                    baseWatchers = setOf(updater),
                    resourceType = ResourceType.BOARD_POST,
                    resourceId = saved.id.toString(),
                    projectId = saved.project.id,
                    eventType = notificationEvent.eventType
                ).toMutableSet()
                receivers.removeIf { it.id == updater.id }
                notificationEvent.receivers = receivers

                notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }
            }
        }

        return saved
    }

    @Transactional
    override fun deletePosting(projectId: Long, number: Long, authorId: Long) {
        val posting = getPosting(projectId, number)
            ?: throw IllegalArgumentException("포스팅을 찾을 수 없습니다.")
        val actor = userRepository.findById(authorId)
            .orElseThrow { IllegalArgumentException("사용자를 찾을 수 없습니다.") }

        // yona NotificationEvent.afterResourceDeleted(): 실제 삭제 전에 알림을 발행한다.
        val title = "[${posting.project.name}] 게시글 삭제: ${posting.title}"
        publishNotification(posting, actor, EventType.RESOURCE_DELETED, title)

        deletePostingCascade(posting)
    }

    // yona Project.delete()의 posting 삭제 루프(posting.delete()) 대응 (P0-19). PostingComment.posting
    // FK가 nullable=false라 반드시 먼저 삭제해야 postingRepository.delete(posting)가 FK 제약 위반 없이
    // 성공한다.
    override fun deletePostingCascade(posting: Posting) {
        // 연관된 댓글의 첨부파일도 일괄 삭제
        val comments = postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(posting.id!!)
        for (comment in comments) {
            attachmentService.deleteAll(ResourceType.NONISSUE_COMMENT, comment.id.toString())
        }

        attachmentService.deleteAll(ResourceType.BOARD_POST, posting.id.toString())
        // yona AbstractPosting.delete()의 TitleHead.deleteTitleHeadKeyword() 대응 (P1-103).
        titleHeadService.deleteTitleHeadKeyword(posting.project, posting.title)
        // 답글(parentComment)이 원 댓글보다 항상 나중에 생성되므로, 생성일 역순으로 지우면
        // 답글이 부모보다 먼저 삭제돼 자기참조 FK(parent_comment_id) 위반을 피할 수 있다.
        postingCommentRepository.deleteAll(comments.asReversed())
        postingRepository.delete(posting)
    }
}
