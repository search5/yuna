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
import com.github.search5.yona.domain.board.PostingCommentRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.issue.IssueCommentRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.pullrequest.CodeCommentThread
import com.github.search5.yona.domain.pullrequest.CodeReviewService
import com.github.search5.yona.domain.pullrequest.CommentThreadRepository
import com.github.search5.yona.domain.pullrequest.CommitComment
import com.github.search5.yona.domain.pullrequest.CommitCommentRepository
import com.github.search5.yona.domain.pullrequest.ReviewComment
import com.github.search5.yona.domain.pullrequest.ReviewCommentRepository
import com.github.search5.yona.domain.support.CodeRange
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserIdent
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
    val commentThreadRepository = mockk<CommentThreadRepository>()
    val commitCommentRepository = mockk<CommitCommentRepository>()
    val codeReviewService = mockk<CodeReviewService>()
    val mailService = mockk<MailService>(relaxed = true)
    val issueCommentRepository = mockk<IssueCommentRepository>()
    val postingCommentRepository = mockk<PostingCommentRepository>()
    val reviewCommentRepository = mockk<ReviewCommentRepository>()

    val service = IncomingMailProcessingService(
        originalEmailRepository, userRepository, projectRepository,
        issueRepository, postingRepository, issueService, commentService, attachmentService,
        commentThreadRepository, commitCommentRepository, codeReviewService, mailService,
        issueCommentRepository, postingCommentRepository, reviewCommentRepository,
        inboundBaseAddress = "yona@example.com"
    )

    val sender = User(id = 1L, loginId = "gildong", name = "길동", email = "gildong@example.com")
    val project = Project(id = 10L, name = "hive", owner = "dlab", projectScope = ProjectScope.PUBLIC)

    beforeTest {
        io.mockk.clearMocks(
            originalEmailRepository, userRepository, projectRepository, issueRepository, postingRepository,
            issueService, commentService, attachmentService, commentThreadRepository, commitCommentRepository,
            codeReviewService, mailService, issueCommentRepository, postingCommentRepository, reviewCommentRepository
        )
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

        describe("HTML 본문 보존 및 cid 인라인 이미지 치환 (P1-47, yona CreationViaEmail.postprocessForHTML/replaceCidWithAttachments 대응)") {
            it("새 이슈 생성 시 HTML 본문의 cid: 참조를 저장된 첨부파일 URL로 치환해 본문을 갱신해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val htmlBody = "<p>사진: <img src=\"cid:image1\"></p>"
                val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = htmlBody, project = project, number = 1L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0
                every { issueRepository.findById(100L) } returns Optional.of(savedIssue)
                every { issueRepository.save(any()) } returnsArgument 0

                val savedAttachment = com.github.search5.yona.domain.attachment.Attachment(
                    id = 999L, name = "photo.png", containerType = ResourceType.ISSUE_POST, containerId = "100"
                )
                every {
                    attachmentService.store(any(), "photo.png", ResourceType.ISSUE_POST, "100", "gildong")
                } returns savedAttachment

                val attachment = InboundAttachment(
                    fileName = "photo.png", contentType = "image/png", bytes = byteArrayOf(1, 2, 3), contentId = "image1"
                )
                val message = baseMessage(attachments = listOf(attachment)).copy(textBody = htmlBody, isHtml = true)

                service.process(message)

                val bodySlot = slot<Issue>()
                verify(exactly = 1) { issueRepository.save(capture(bodySlot)) }
                bodySlot.captured.body shouldBe "<p>사진: <img src=\"/files/999\"></p>"
            }

            it("cid에 매칭되는 첨부파일이 없으면 이슈 본문을 갱신하지 않아야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val htmlBody = "<p>서식 있는 본문</p>"
                val savedIssue = Issue(id = 101L, title = "메일로 만든 이슈", body = htmlBody, project = project, number = 2L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val message = baseMessage().copy(textBody = htmlBody, isHtml = true)

                service.process(message)

                verify(exactly = 0) { issueRepository.save(any()) }
            }
        }

        describe("리뷰 댓글/커밋 댓글 스레드로의 메일 답장 (P1-30, yona EmailHandler.getThreads() 대응)") {
            it("COMMENT_THREAD(코드리뷰 스레드)를 가리키는 답장이면 그 스레드에 리뷰 댓글을 추가해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)

                val originalReviewEmail = OriginalEmail(
                    id = 3L, messageId = "<review@mail.example.com>",
                    resourceType = ResourceType.COMMENT_THREAD, resourceId = "60"
                )
                every { originalEmailRepository.findByMessageId("<review@mail.example.com>") } returns Optional.of(originalReviewEmail)

                val thread = CodeCommentThread(id = 60L, project = project, codeRange = CodeRange(path = "a.kt", startLine = 1))
                every { commentThreadRepository.findById(60L) } returns Optional.of(thread)

                val savedReviewComment = ReviewComment(id = 400L, contents = "메일 본문 내용", author = UserIdent(sender))
                every {
                    codeReviewService.createReviewComment(project, null, null, "메일 본문 내용", null, 60L, sender)
                } returns savedReviewComment
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(inReplyTo = "<review@mail.example.com>"))

                result shouldBe listOf(IncomingMailOutcome.ReviewCommentCreated(400L, 60L))
            }

            it("COMMIT_COMMENT를 가리키는 답장이면 같은 커밋/경로/라인에 새 커밋 댓글을 추가해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)

                val originalCommitEmail = OriginalEmail(
                    id = 4L, messageId = "<commit@mail.example.com>",
                    resourceType = ResourceType.COMMIT_COMMENT, resourceId = "80"
                )
                every { originalEmailRepository.findByMessageId("<commit@mail.example.com>") } returns Optional.of(originalCommitEmail)

                val originalCommitComment = CommitComment(
                    id = 80L, project = project, commitId = "abc123", path = "b.kt", line = 5,
                    contents = "원본 댓글", author = UserIdent(sender)
                )
                every { commitCommentRepository.findById(80L) } returns Optional.of(originalCommitComment)

                val savedCommitComment = CommitComment(id = 500L, project = project, commitId = "abc123", contents = "메일 본문 내용", author = UserIdent(sender))
                every {
                    codeReviewService.createCommitComment(project, "abc123", "메일 본문 내용", "b.kt", 5, null, sender)
                } returns savedCommitComment
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(inReplyTo = "<commit@mail.example.com>"))

                result shouldBe listOf(IncomingMailOutcome.CommitCommentCreated(500L))
            }
        }

        describe("리뷰 댓글/커밋 댓글 답장의 첨부파일/cid 치환 (P1-59, yona saveReviewComment()의 saveAttachments/replaceCidWithAttachments 대응)") {
            it("코드리뷰 스레드 답장의 첨부파일은 스레드가 아니라 새로 생성된 리뷰 댓글(REVIEW_COMMENT)에 연결돼야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)

                val originalReviewEmail = OriginalEmail(
                    id = 3L, messageId = "<review@mail.example.com>",
                    resourceType = ResourceType.COMMENT_THREAD, resourceId = "60"
                )
                every { originalEmailRepository.findByMessageId("<review@mail.example.com>") } returns Optional.of(originalReviewEmail)

                val thread = CodeCommentThread(id = 60L, project = project, codeRange = CodeRange(path = "a.kt", startLine = 1))
                every { commentThreadRepository.findById(60L) } returns Optional.of(thread)

                val savedReviewComment = ReviewComment(id = 400L, contents = "메일 본문 내용", author = UserIdent(sender))
                every {
                    codeReviewService.createReviewComment(project, null, null, "메일 본문 내용", null, 60L, sender)
                } returns savedReviewComment
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val attachment = InboundAttachment(fileName = "diagram.png", contentType = "image/png", bytes = byteArrayOf(1, 2, 3))

                service.process(baseMessage(inReplyTo = "<review@mail.example.com>", attachments = listOf(attachment)))

                verify(exactly = 1) {
                    attachmentService.store(any(), "diagram.png", ResourceType.REVIEW_COMMENT, "400", "gildong")
                }
            }

            it("커밋 댓글 답장의 첨부파일은 새로 생성된 커밋 댓글(COMMIT_COMMENT)에 연결돼야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)

                val originalCommitEmail = OriginalEmail(
                    id = 4L, messageId = "<commit@mail.example.com>",
                    resourceType = ResourceType.COMMIT_COMMENT, resourceId = "80"
                )
                every { originalEmailRepository.findByMessageId("<commit@mail.example.com>") } returns Optional.of(originalCommitEmail)

                val originalCommitComment = CommitComment(
                    id = 80L, project = project, commitId = "abc123", path = "b.kt", line = 5,
                    contents = "원본 댓글", author = UserIdent(sender)
                )
                every { commitCommentRepository.findById(80L) } returns Optional.of(originalCommitComment)

                val savedCommitComment = CommitComment(id = 500L, project = project, commitId = "abc123", contents = "메일 본문 내용", author = UserIdent(sender))
                every {
                    codeReviewService.createCommitComment(project, "abc123", "메일 본문 내용", "b.kt", 5, null, sender)
                } returns savedCommitComment
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val attachment = InboundAttachment(fileName = "trace.log", contentType = "text/plain", bytes = byteArrayOf(9))

                service.process(baseMessage(inReplyTo = "<commit@mail.example.com>", attachments = listOf(attachment)))

                verify(exactly = 1) {
                    attachmentService.store(any(), "trace.log", ResourceType.COMMIT_COMMENT, "500", "gildong")
                }
            }

            it("코드리뷰 스레드 답장의 HTML 본문에 있는 cid: 참조를 저장된 첨부파일 URL로 치환해 리뷰 댓글을 갱신해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)

                val originalReviewEmail = OriginalEmail(
                    id = 3L, messageId = "<review@mail.example.com>",
                    resourceType = ResourceType.COMMENT_THREAD, resourceId = "60"
                )
                every { originalEmailRepository.findByMessageId("<review@mail.example.com>") } returns Optional.of(originalReviewEmail)

                val thread = CodeCommentThread(id = 60L, project = project, codeRange = CodeRange(path = "a.kt", startLine = 1))
                every { commentThreadRepository.findById(60L) } returns Optional.of(thread)

                val htmlBody = "<p>스크린샷: <img src=\"cid:shot1\"></p>"
                val savedReviewComment = ReviewComment(id = 401L, contents = htmlBody, author = UserIdent(sender))
                every {
                    codeReviewService.createReviewComment(project, null, null, any(), null, 60L, sender)
                } returns savedReviewComment
                every { originalEmailRepository.save(any()) } returnsArgument 0
                every { reviewCommentRepository.findById(401L) } returns Optional.of(savedReviewComment)
                every { reviewCommentRepository.save(any()) } returnsArgument 0

                val savedAttachment = com.github.search5.yona.domain.attachment.Attachment(
                    id = 998L, name = "shot.png", containerType = ResourceType.REVIEW_COMMENT, containerId = "401"
                )
                every {
                    attachmentService.store(any(), "shot.png", ResourceType.REVIEW_COMMENT, "401", "gildong")
                } returns savedAttachment

                val attachment = InboundAttachment(
                    fileName = "shot.png", contentType = "image/png", bytes = byteArrayOf(1, 2, 3), contentId = "shot1"
                )
                val message = baseMessage(inReplyTo = "<review@mail.example.com>", attachments = listOf(attachment))
                    .copy(textBody = htmlBody, isHtml = true)

                service.process(message)

                val bodySlot = slot<ReviewComment>()
                verify(exactly = 1) { reviewCommentRepository.save(capture(bodySlot)) }
                bodySlot.captured.contents shouldBe "<p>스크린샷: <img src=\"/files/998\"></p>"
            }
        }

        describe("help 자동응답 및 실패 사유 회신 (P1-31, yona EmailHandler.getHelpMessage/reply 대응)") {
            it("수신 주소 detail이 'help'면 도움말 회신을 보내고 다른 처리는 하지 않아야 한다") {
                val result = service.process(baseMessage(recipients = listOf("yona+help@example.com")))

                result shouldBe listOf(IncomingMailOutcome.HelpRequested)
                verify(exactly = 1) {
                    mailService.sendReply(
                        toEmail = "gildong@example.com",
                        toName = "길동",
                        subject = "메일로 만든 이슈",
                        textContent = any(),
                        inReplyToMessageId = "<msg1@mail.example.com>"
                    )
                }
                verify(exactly = 0) { issueService.createIssue(any(), any(), any(), any(), any()) }
            }

            it("처리 중 거부(Rejected)된 대상이 있으면 실패 사유를 요약한 회신을 보내야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "secret") } returns Optional.of(
                    Project(id = 99L, name = "secret", owner = "dlab", projectScope = ProjectScope.PRIVATE)
                )

                service.process(baseMessage(recipients = listOf("yona+dlab/secret@example.com")))

                val captured = slot<String>()
                verify(exactly = 1) {
                    mailService.sendReply(
                        toEmail = "gildong@example.com",
                        toName = "길동",
                        subject = "메일로 만든 이슈",
                        textContent = capture(captured),
                        inReplyToMessageId = "<msg1@mail.example.com>"
                    )
                }
                captured.captured.contains("프로젝트를 찾을 수 없거나 권한이 없습니다") shouldBe true
            }

            it("정상적으로 이슈가 생성되면 회신 메일을 보내지 않아야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = "메일 본문 내용", project = project, number = 1L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0

                service.process(baseMessage())

                verify(exactly = 0) { mailService.sendReply(any(), any(), any(), any(), any()) }
            }
        }

        describe("수신 주소 detail에 리소스 경로 직접 명시(owner/project/resourceType/id) 지원 (P1-32, yona EmailHandler.getResourceFromDetail 대응)") {
            it("detail이 owner/project/issue_post/50 형식이면 In-Reply-To 없이도 그 이슈에 바로 댓글을 달아야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val existingIssue = Issue(id = 50L, title = "기존 이슈", body = "...", project = project, number = 3L)
                every { issueRepository.findById(50L) } returns Optional.of(existingIssue)
                val savedComment = IssueComment(id = 200L, contents = "메일 본문 내용", issue = existingIssue)
                every { commentService.createIssueComment(50L, "메일 본문 내용", sender) } returns savedComment
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(recipients = listOf("yona+dlab/hive/issue_post/50@example.com")))

                result shouldBe listOf(IncomingMailOutcome.IssueCommentCreated(200L, 50L))
                verify(exactly = 0) { issueService.createIssue(any(), any(), any(), any(), any()) }
            }

            it("직접 지정한 리소스가 다른 프로젝트 소속이면 무시하고 일반 새 이슈 생성으로 처리해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val otherProject = Project(id = 20L, name = "other", owner = "other-owner", projectScope = ProjectScope.PUBLIC)
                val issueInOtherProject = Issue(id = 60L, title = "다른 프로젝트 이슈", body = "...", project = otherProject, number = 1L)
                every { issueRepository.findById(60L) } returns Optional.of(issueInOtherProject)
                val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = "메일 본문 내용", project = project, number = 1L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(recipients = listOf("yona+dlab/hive/issue_post/60@example.com")))

                result shouldBe listOf(IncomingMailOutcome.IssueCreated(100L, "dlab", "hive"))
            }

            it("알 수 없는 리소스 타입 세그먼트는 무시하고 일반 새 이슈 생성으로 처리해야 한다") {
                every { projectRepository.findByOwnerAndName("dlab", "hive") } returns Optional.of(project)
                val savedIssue = Issue(id = 100L, title = "메일로 만든 이슈", body = "메일 본문 내용", project = project, number = 1L)
                every { issueService.createIssue(any(), sender, null, null, null) } returns savedIssue
                every { originalEmailRepository.save(any()) } returnsArgument 0

                val result = service.process(baseMessage(recipients = listOf("yona+dlab/hive/unknown_type/99@example.com")))

                result shouldBe listOf(IncomingMailOutcome.IssueCreated(100L, "dlab", "hive"))
            }
        }
    }
})
