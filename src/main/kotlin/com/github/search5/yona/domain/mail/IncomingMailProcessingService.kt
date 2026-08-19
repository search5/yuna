package com.github.search5.yona.domain.mail

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.attachment.AttachmentService
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.comment.CommentService
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.issue.IssueService
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * yona의 mailbox/EmailHandler.java + CreationViaEmail.java 대응 핵심 라우팅/생성 로직.
 * IMAP 연결(IncomingMailPoller)과 분리되어 있어, 실제 메일 서버 없이도 단위테스트로
 * 검증 가능하다.
 *
 * 의도적으로 다루지 않는 범위(follow-up, docs/PARITY_BACKLOG.md 참고):
 *  - MIME multipart/HTML 본문 파싱, cid 이미지 치환 (텍스트 본문만 처리 — 첨부파일 저장은 P1-29에서 구현됨)
 *  - 코드리뷰/커밋 댓글 스레드로의 답장(REVIEW_COMMENT, COMMENT_THREAD)
 *  - "help" 자동응답, 수신 거부 사유 회신 메일
 *  - 수신 주소 detail에 리소스 경로를 직접 명시하는 방식(owner/project/issue/5)
 *  - 한 이메일이 여러 프로젝트로 발송된 경우, OriginalEmail은 최초 성공 리소스 1건만 기록
 */
@Service
class IncomingMailProcessingService(
    private val originalEmailRepository: OriginalEmailRepository,
    private val userRepository: UserRepository,
    private val projectRepository: com.github.search5.yona.domain.project.ProjectRepository,
    private val issueRepository: IssueRepository,
    private val postingRepository: PostingRepository,
    private val issueService: IssueService,
    private val commentService: CommentService,
    private val attachmentService: AttachmentService,
    @Value("\${yuna.mailbox.imap.address:}")
    private val inboundBaseAddress: String
) {
    private val logger = LoggerFactory.getLogger(IncomingMailProcessingService::class.java)

    private data class ResolvedThread(
        val resourceType: ResourceType,
        val resourceId: String,
        val projectId: Long?
    )

    @Transactional
    fun process(message: InboundEmailMessage): List<IncomingMailOutcome> {
        if (originalEmailRepository.existsByMessageId(message.messageId)) {
            return listOf(IncomingMailOutcome.Duplicate)
        }

        val sender = userRepository.findByEmail(message.fromAddress).orElse(null)
            ?: return listOf(IncomingMailOutcome.UnknownSender)

        val targets = message.recipientAddresses
            .mapNotNull { runCatching { EmailAddressDetail.of(it) }.getOrNull() }
            .filter { it.isToYona(inboundBaseAddress) && it.detail.isNotBlank() }

        if (targets.isEmpty()) {
            return emptyList()
        }

        val threads = resolveThreads(message)
        val outcomes = targets.map { target -> processTarget(target, threads, sender, message) }

        outcomes.firstOrNull { it.isCreated() }?.let { saveOriginalEmail(message.messageId, it) }

        return outcomes
    }

    private fun IncomingMailOutcome.isCreated(): Boolean = this is IncomingMailOutcome.IssueCreated ||
        this is IncomingMailOutcome.IssueCommentCreated ||
        this is IncomingMailOutcome.PostingCommentCreated

    private fun processTarget(
        target: EmailAddressDetail,
        threads: List<ResolvedThread>,
        sender: User,
        message: InboundEmailMessage
    ): IncomingMailOutcome {
        val segments = target.detail.split("/")
        if (segments.size < 2) {
            return IncomingMailOutcome.Rejected("잘못된 수신 주소 형식: $target")
        }
        val owner = segments[0]
        val projectName = segments[1]

        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
        if (project == null || !AccessControl.isAllowedToReadProject(sender, project)) {
            return IncomingMailOutcome.Rejected("프로젝트를 찾을 수 없거나 권한이 없습니다: $owner/$projectName")
        }

        val thread = threads.firstOrNull { it.projectId == project.id }
        val outcome = if (thread != null) {
            createComment(thread, sender, message.textBody)
        } else {
            createIssue(project, owner, projectName, sender, message)
        }
        attachAttachments(outcome, message.attachments, sender)
        return outcome
    }

    // yona CreationViaEmail.saveAttachments() 대응 (P1-29). cid 이미지 치환은 다루지 않고
    // 첨부파일을 생성된 리소스에 그대로 연결하는 것까지만 구현한다.
    private fun attachAttachments(outcome: IncomingMailOutcome, attachments: List<InboundAttachment>, sender: User) {
        if (attachments.isEmpty()) return
        val (resourceType, resourceId) = when (outcome) {
            is IncomingMailOutcome.IssueCreated -> ResourceType.ISSUE_POST to outcome.issueId.toString()
            is IncomingMailOutcome.IssueCommentCreated -> ResourceType.ISSUE_POST to outcome.issueId.toString()
            is IncomingMailOutcome.PostingCommentCreated -> ResourceType.BOARD_POST to outcome.postingId.toString()
            else -> return
        }
        for (attachment in attachments) {
            try {
                attachmentService.store(
                    attachment.bytes.inputStream(),
                    attachment.fileName,
                    resourceType,
                    resourceId,
                    sender.loginId
                )
            } catch (e: Exception) {
                logger.warn("메일 첨부파일 저장 실패: fileName=${attachment.fileName}", e)
            }
        }
    }

    private fun resolveThreads(message: InboundEmailMessage): List<ResolvedThread> {
        val messageIds = (MessageIdParser.parse(message.inReplyTo) + MessageIdParser.parse(message.references)).toSet()

        return messageIds.mapNotNull { id ->
            val originalEmail = originalEmailRepository.findByMessageId(id).orElse(null) ?: return@mapNotNull null
            resolveResourceProject(originalEmail.resourceType, originalEmail.resourceId)?.let { projectId ->
                ResolvedThread(originalEmail.resourceType, originalEmail.resourceId, projectId)
            }
        }
    }

    private fun resolveResourceProject(resourceType: ResourceType, resourceId: String): Long? {
        val id = resourceId.toLongOrNull() ?: return null
        return when (resourceType) {
            ResourceType.ISSUE_POST -> issueRepository.findById(id).orElse(null)?.project?.id
            ResourceType.BOARD_POST -> postingRepository.findById(id).orElse(null)?.project?.id
            else -> {
                logger.debug("아직 지원하지 않는 스레드 리소스 타입이라 스킵: $resourceType")
                null
            }
        }
    }

    private fun createComment(thread: ResolvedThread, sender: User, body: String): IncomingMailOutcome {
        return when (thread.resourceType) {
            ResourceType.ISSUE_POST -> {
                val comment = commentService.createIssueComment(thread.resourceId.toLong(), body, sender)
                IncomingMailOutcome.IssueCommentCreated(comment.id!!, thread.resourceId.toLong())
            }
            ResourceType.BOARD_POST -> {
                val comment = commentService.createPostingComment(thread.resourceId.toLong(), body, sender)
                IncomingMailOutcome.PostingCommentCreated(comment.id!!, thread.resourceId.toLong())
            }
            else -> IncomingMailOutcome.Rejected("지원하지 않는 스레드 타입: ${thread.resourceType}")
        }
    }

    private fun createIssue(
        project: Project,
        owner: String,
        projectName: String,
        sender: User,
        message: InboundEmailMessage
    ): IncomingMailOutcome {
        if (!AccessControl.isProjectResourceCreatable(sender, project, ResourceType.ISSUE_POST)) {
            return IncomingMailOutcome.Rejected("이슈 생성 권한이 없습니다: $owner/$projectName")
        }

        val issue = Issue(title = message.subject, body = message.textBody, project = project)
        val saved = issueService.createIssue(issue, sender, null, null, null)
        return IncomingMailOutcome.IssueCreated(saved.id!!, owner, projectName)
    }

    private fun saveOriginalEmail(messageId: String, outcome: IncomingMailOutcome) {
        val (resourceType, resourceId) = when (outcome) {
            is IncomingMailOutcome.IssueCreated -> ResourceType.ISSUE_POST to outcome.issueId.toString()
            is IncomingMailOutcome.IssueCommentCreated -> ResourceType.ISSUE_COMMENT to outcome.commentId.toString()
            is IncomingMailOutcome.PostingCommentCreated -> ResourceType.NONISSUE_COMMENT to outcome.commentId.toString()
            else -> return
        }
        originalEmailRepository.save(OriginalEmail(messageId = messageId, resourceType = resourceType, resourceId = resourceId))
    }
}
