package com.github.search5.yona.domain.mail

import com.github.search5.yona.domain.attachment.AttachmentService
import com.github.search5.yona.domain.comment.CommentService
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueComment
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.issue.IssueService
import com.github.search5.yona.domain.board.Posting
import com.github.search5.yona.domain.board.PostingComment
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.Optional

class IncomingMailProcessingServiceSpec : DescribeSpec({
    val originalEmailRepository = mockk<OriginalEmailRepository>()
    val userRepository = mockk<UserRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val issueService = mockk<IssueService>()
    val commentService = mockk<CommentService>()
    val attachmentService = mockk<AttachmentService>(relaxed = true)

    val service = IncomingMailProcessingService(
        originalEmailRepository, userRepository, projectRepository,
        issueRepository, postingRepository, issueService, commentService, attachmentService,
        inboundBaseAddress = "yona@example.com"
    )

    val sender = User(id = 1L, loginId = "gildong", name = "길동", email = "gildong@example.com")
    val project = Project(id = 10L, name = "hive", owner = "dlab", projectScope = ProjectScope.PUBLIC)

    beforeTest {
        io.mockk.clearMocks(originalEmailRepository, userRepository, projectRepository, issueRepository, postingRepository, issueService, commentService, attachmentService)
        every { originalEmailRepository.existsByMessageId(any()) } returns false
        every { userRepository.findByEmail("gildong@example.com") } returns Optional.of(sender)
    }

    fun baseMessage(
        messageId: String = "<msg1@mail.example.com>",
        recipients: List<String> = listOf("yona+dlab/hive@example.com"),
        inReplyTo: String? = null,
        references: String? = null,
        attachments: List<InboundAttachment> = emptyList()
    ) = InboundEmailMessage(
        messageId = messageId,
        subject = "메일로 만든 이슈",
        fromAddress = "gildong@example.com",
        fromName = "길동",
        recipientAddresses = recipients,
        inReplyTo = inReplyTo,
        references = references,
        textBody = "메일 본문 내용",
        attachments = attachments
    )

    describe("IncomingMailProcessingService.process") {
        it("이미 처리된 messageId면 Duplicate를 반환하고 아무 것도 생성하지 않아야 한다") {
            every { originalEmailRepository.existsByMessageId("<dup@mail.example.com>") } returns true

            val result = service.process(baseMessage(messageId = "<dup@mail.example.com>"))

            result shouldBe listOf(IncomingMailOutcome.Duplicate)
            verify(exactly = 0) { issueService.createIssue(any(), any(), any(), any(), any()) }
        }

        it("발신자가 등록된 사용자가 아니면 UnknownSender를 반환해야 한다") {
            every { userRepository.findByEmail("unknown@example.com") } returns Optional.empty()

            val result = service.process(baseMessage().copy(fromAddress = "unknown@example.com"))

            result shouldBe listOf(IncomingMailOutcome.UnknownSender)
        }

        it("수신 주소 중 yona로 향한 것이 없으면 빈 목록을 반환해야 한다") {
            val result = service.process(baseMessage(recipients = listOf("someone-else@example.com")))

            result shouldBe emptyList()
        }

        it("연관 스레드가 없으면 새 이슈를 생성해야 한다") {
            every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
            val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = "메일 본문 내용", project = project, number = 1L)
            every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
            every { originalEmailRepository.save(any()) } returnsArgument 0

            val result = service.process(baseMessage())

            result shouldBe listOf(IncomingMailOutcome.IssueCreated(100L, "dlab", "hive"))
            val captured = slot<OriginalEmail>()
            verify(exactly = 1) { originalEmailRepository.save(capture(captured)) }
            captured.captured.messageId shouldBe "<msg1@mail.example.com>"
            captured.captured.resourceType shouldBe ResourceType.ISSUE_POST
            captured.captured.resourceId shouldBe "100"
        }

        it("In-Reply-To가 같은 프로젝트의 기존 이슈를 가리키면 새 이슈 대신 댓글을 생성해야 한다") {
            every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)

            val originalIssueEmail = OriginalEmail(
                id = 1L, messageId = "<original@mail.example.com>",
                resourceType = ResourceType.ISSUE_POST, resourceId = "50"
            )
            every { originalEmailRepository.findByMessageId("<original@mail.example.com>") } returns Optional.of(originalIssueEmail)

            val existingIssue = Issue(id = 50L, title = "기존 이슈", body = "...", project = project, number = 3L)
            every { issueRepository.findById(50L) } returns Optional.of(existingIssue)

            val savedComment = IssueComment(id = 200L, contents = "메일 본문 내용", issue = existingIssue)
            every { commentService.createIssueComment(50L, "메일 본문 내용", sender) } returns savedComment
            every { originalEmailRepository.save(any()) } returnsArgument 0

            val result = service.process(baseMessage(inReplyTo = "<original@mail.example.com>"))

            result shouldBe listOf(IncomingMailOutcome.IssueCommentCreated(200L, 50L))
            verify(exactly = 0) { issueService.createIssue(any(), any(), any(), any(), any()) }
        }

        it("References가 같은 프로젝트의 기존 게시글을 가리키면 게시글 댓글을 생성해야 한다") {
            every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)

            val originalPostingEmail = OriginalEmail(
                id = 2L, messageId = "<original-post@mail.example.com>",
                resourceType = ResourceType.BOARD_POST, resourceId = "70"
            )
            every { originalEmailRepository.findByMessageId("<original-post@mail.example.com>") } returns Optional.of(originalPostingEmail)

            val existingPosting = Posting(id = 70L, title = "기존 게시글", body = "...", project = project, number = 4L)
            every { postingRepository.findById(70L) } returns Optional.of(existingPosting)

            val savedComment = PostingComment(id = 300L, contents = "메일 본문 내용", posting = existingPosting)
            every { commentService.createPostingComment(70L, "메일 본문 내용", sender) } returns savedComment
            every { originalEmailRepository.save(any()) } returnsArgument 0

            val result = service.process(baseMessage(references = "<original-post@mail.example.com>"))

            result shouldBe listOf(IncomingMailOutcome.PostingCommentCreated(300L, 70L))
        }

        it("이슈 생성 권한이 없으면 Rejected를 반환해야 한다") {
            val privateProject = Project(id = 20L, name = "secret", owner = "dlab", projectScope = ProjectScope.PRIVATE)
            every { projectRepository.findByOwnerAndName("dlab", "secret") } returns Optional.of(privateProject)

            val result = service.process(baseMessage(recipients = listOf("yona+dlab/secret@example.com")))

            result.size shouldBe 1
            result[0].shouldBeInstanceOf<IncomingMailOutcome.Rejected>()
        }

        it("프로젝트를 찾을 수 없으면 Rejected를 반환해야 한다") {
            every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.empty()

            val result = service.process(baseMessage())

            result.size shouldBe 1
            result[0].shouldBeInstanceOf<IncomingMailOutcome.Rejected>()
        }

        it("detail이 owner/project 형식이 아니면(세그먼트 부족) Rejected를 반환해야 한다") {
            val result = service.process(baseMessage(recipients = listOf("yona+dlab@example.com")))

            result.size shouldBe 1
            result[0].shouldBeInstanceOf<IncomingMailOutcome.Rejected>()
        }

        describe("첨부파일 저장 (P1-29, yona CreationViaEmail.saveAttachments() 대응)") {
            it("새 이슈 생성 시 메일에 첨부된 파일을 이슈에 연결해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = "메일 본문 내용", project = project, number = 1L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val attachment = InboundAttachment(fileName = "screenshot.png", contentType = "image/png", bytes = byteArrayOf(1, 2, 3))

                service.process(baseMessage(attachments = listOf(attachment)))

                verify(exactly = 1) {
                    attachmentService.store(any(), "screenshot.png", ResourceType.ISSUE_POST, "100", "gildong")
                }
            }

            it("기존 이슈에 댓글을 생성할 때도 첨부파일을 그 이슈에 연결해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val originalIssueEmail = OriginalEmail(
                    id = 1L, messageId = "<original@mail.example.com>",
                    resourceType = ResourceType.ISSUE_POST, resourceId = "50"
                )
                every { originalEmailRepository.findByMessageId("<original@mail.example.com>") } returns Optional.of(originalIssueEmail)
                val existingIssue = Issue(id = 50L, title = "기존 이슈", body = "...", project = project, number = 3L)
                every { issueRepository.findById(50L) } returns Optional.of(existingIssue)
                val savedComment = IssueComment(id = 200L, contents = "메일 본문 내용", issue = existingIssue)
                every { commentService.createIssueComment(50L, "메일 본문 내용", sender) } returns savedComment
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val attachment = InboundAttachment(fileName = "log.txt", contentType = "text/plain", bytes = byteArrayOf(9))

                service.process(baseMessage(inReplyTo = "<original@mail.example.com>", attachments = listOf(attachment)))

                verify(exactly = 1) {
                    attachmentService.store(any(), "log.txt", ResourceType.ISSUE_POST, "50", "gildong")
                }
            }

            it("첨부파일이 없으면 AttachmentService를 호출하지 않아야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = "메일 본문 내용", project = project, number = 1L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0

                service.process(baseMessage())

                verify(exactly = 0) { attachmentService.store(any(), any(), any(), any(), any()) }
            }
        }
    }
})
