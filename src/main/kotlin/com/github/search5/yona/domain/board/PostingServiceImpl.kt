package com.github.search5.yona.domain.board

import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.watch.WatchService
import com.github.search5.yona.domain.notification.NotificationEvent
import com.github.search5.yona.domain.notification.NotificationEventRepository
import com.github.search5.yona.domain.enumeration.EventType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import com.github.search5.yona.domain.attachment.AttachmentService
import com.github.search5.yona.domain.enumeration.ResourceType

@Service
@Transactional(readOnly = true)
class PostingServiceImpl(
    private val postingRepository: PostingRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val attachmentService: AttachmentService,
    private val postingCommentRepository: PostingCommentRepository,
    private val watchService: WatchService,
    private val notificationEventRepository: NotificationEventRepository,
    private val eventPublisher: ApplicationEventPublisher
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
        receivers.removeIf { it.id == actor.id }
        notificationEvent.receivers = receivers

        notificationEventRepository.save(notificationEvent)
        eventPublisher.publishEvent(notificationEvent)
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
        val isAuthoredByUpdater = posting.authorId == authorId

        posting.title = title.trim()
        posting.body = body
        posting.notice = notice
        posting.readme = readme
        posting.updatedDate = Instant.now()

        val saved = postingRepository.save(posting)

        // yona BoardApp.editPost의 isSelectedToSendNotificationMail() 대응 (P1-44).
        // 본인 글이 아니면 옵션과 무관하게 항상 발송하고, 본인 글이면 체크박스를 선택했을 때만 발송한다.
        if (sendNotificationMail || !isAuthoredByUpdater) {
            val updater = userRepository.findById(authorId).orElse(null)
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

                notificationEventRepository.save(notificationEvent)
                eventPublisher.publishEvent(notificationEvent)
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

        // 연관된 댓글의 첨부파일도 일괄 삭제
        val comments = postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(posting.id!!)
        for (comment in comments) {
            attachmentService.deleteAll(ResourceType.NONISSUE_COMMENT, comment.id.toString())
        }

        attachmentService.deleteAll(ResourceType.BOARD_POST, posting.id.toString())
        postingRepository.delete(posting)
    }
}
