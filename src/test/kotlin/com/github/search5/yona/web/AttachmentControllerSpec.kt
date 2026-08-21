package com.github.search5.yona.web

import com.github.search5.yona.domain.attachment.Attachment
import com.github.search5.yona.domain.attachment.AttachmentRepository
import com.github.search5.yona.domain.attachment.AttachmentService
import com.github.search5.yona.domain.enumeration.Operation
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.milestone.MilestoneRepository
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.config.security.AccessControl
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk

import org.hamcrest.Matchers.containsString
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.io.File
import java.time.Instant
import java.util.Optional

class AttachmentControllerSpec : DescribeSpec({
    val attachmentService = mockk<AttachmentService>()
    val attachmentRepository = mockk<AttachmentRepository>()
    val userRepository = mockk<UserRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val milestoneRepository = mockk<MilestoneRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val accessControl = mockk<AccessControl>()

    val attachmentController = AttachmentController(
        attachmentService,
        attachmentRepository,
        userRepository,
        issueRepository,
        postingRepository,
        milestoneRepository,
        projectUserRepository,
        accessControl
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(attachmentController).build()

    beforeTest {
        io.mockk.clearMocks(
            attachmentService,
            attachmentRepository,
            userRepository,
            issueRepository,
            postingRepository,
            milestoneRepository,
            projectUserRepository,
            accessControl
        )
    }

    describe("AttachmentController API 단위 테스트") {
        val loginUser = User(id = 1L, loginId = "tester", name = "테스터")
        val userAuth = UsernamePasswordAuthenticationToken("tester", "password")

        val attachment = Attachment(
            id = 100L,
            name = "test-file.txt",
            hash = "somehash123",
            containerType = ResourceType.ISSUE_POST,
            containerId = "10",
            mimeType = "text/plain",
            size = 12L,
            createdDate = Instant.now(),
            ownerLoginId = "tester"
        )

        describe("POST /files (파일 업로드)") {
            it("로그인된 사용자가 파일을 전송하면 201 Created와 함께 파일 정보 JSON을 반환한다") {
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every {
                    attachmentService.store(
                        any(),
                        "test-file.txt",
                        ResourceType.NOT_A_RESOURCE,
                        "",
                        "tester"
                    )
                } returns attachment
                every { attachmentRepository.existsByHash("somehash123") } returns false

                val multipartFile = MockMultipartFile(
                    "filePath",
                    "test-file.txt",
                    "text/plain",
                    "Hello World".toByteArray()
                )

                mockMvc.perform(
                    multipart("/files")
                        .file(multipartFile)
                        .principal(userAuth)
                )
                    .andExpect(status().isCreated)
                    .andExpect(header().string("Location", "/files/100"))
                    .andExpect(jsonPath("$.id").value("100"))
                    .andExpect(jsonPath("$.name").value("test-file.txt"))
                    .andExpect(jsonPath("$.url").value("/files/100"))
            }
        }

        describe("GET /files/{id} (파일 다운로드)") {
            it("존재하는 파일 ID로 요청하면 파일 스트림과 적절한 헤더를 반환한다") {
                every { attachmentRepository.findById(100L) } returns Optional.of(attachment)
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { accessControl.isAllowedAttachment(loginUser, attachment, Operation.READ) } returns true

                val tempFile = File.createTempFile("yuna-test", "txt")
                tempFile.writeText("Hello World")
                tempFile.deleteOnExit()

                every { attachmentService.getFile(attachment) } returns tempFile

                mockMvc.perform(
                    get("/files/100")
                        .param("action", "download")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(header().string("Content-Type", "text/plain"))
                    .andExpect(header().string("Content-Disposition", containsString("filename=\"test-file.txt\"")))
                    .andExpect(content().string("Hello World"))
            }

            it("If-None-Match 헤더 값이 파일 ETag와 일치하면 304 Not Modified를 반환한다") {
                every { attachmentRepository.findById(100L) } returns Optional.of(attachment)
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { accessControl.isAllowedAttachment(loginUser, attachment, Operation.READ) } returns true

                val eTag = "\"somehash123-inline\""

                mockMvc.perform(
                    get("/files/100")
                        .header("If-None-Match", eTag)
                        .principal(userAuth)
                )
                    .andExpect(status().isNotModified)
            }

            it("읽기 권한이 없으면 403 Forbidden을 반환한다 (P1-96, 보안)") {
                every { attachmentRepository.findById(100L) } returns Optional.of(attachment)
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { accessControl.isAllowedAttachment(loginUser, attachment, Operation.READ) } returns false

                mockMvc.perform(
                    get("/files/100")
                        .principal(userAuth)
                )
                    .andExpect(status().isForbidden)
            }
        }

        describe("POST /files/{id} (파일 삭제)") {
            it("삭제 요청 파라미터 _method=delete를 전달하면 파일을 삭제하고 성공 메시지를 반환한다") {
                val project = mockk<com.github.search5.yona.domain.project.Project>()
                val issue = mockk<com.github.search5.yona.domain.issue.Issue>()
                every { issue.project } returns project
                every { issue.authorLoginId } returns "tester"
                every { issueRepository.findById(10L) } returns Optional.of(issue)
                every { accessControl.isAllowedToUpdateIssue(loginUser, project, "tester") } returns true

                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { attachmentRepository.findById(100L) } returns Optional.of(attachment)
                every { attachmentService.delete(attachment) } returns Unit
                every { attachmentRepository.existsByHash("somehash123") } returns false

                mockMvc.perform(
                    post("/files/100")
                        .param("_method", "delete")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(content().string(containsString("removed successfully")))
            }

            // yona AccessControl.java:250-263 isProjectResourceAllowed()의 ATTACHMENT 케이스(컨테이너의
            // UPDATE 권한으로 위임) 대응 (P1-130). 업로더 본인이 아니어도 커밋/리뷰 댓글의 UPDATE 권한이
            // 있는(=프로젝트 멤버) 사용자면 첨부파일을 삭제할 수 있어야 한다.
            it("커밋 댓글 첨부파일은 업로더 본인이 아니어도 컨테이너 UPDATE 권한이 있으면 삭제할 수 있다") {
                val commitCommentAttachment = Attachment(
                    id = 200L,
                    name = "commit-attach.png",
                    hash = "commithash456",
                    containerType = ResourceType.COMMIT_COMMENT,
                    containerId = "50",
                    mimeType = "image/png",
                    size = 34L,
                    createdDate = Instant.now(),
                    ownerLoginId = "someone-else"
                )

                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every { attachmentRepository.findById(200L) } returns Optional.of(commitCommentAttachment)
                every {
                    accessControl.isAllowedAttachment(loginUser, commitCommentAttachment, Operation.UPDATE)
                } returns true
                every { attachmentService.delete(commitCommentAttachment) } returns Unit
                every { attachmentRepository.existsByHash("commithash456") } returns false

                mockMvc.perform(
                    post("/files/200")
                        .param("_method", "delete")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(content().string(containsString("removed successfully")))
            }
        }

        describe("GET /files (파일 목록 조회)") {
            it("컨테이너 타입과 ID로 조회 시 첨부된 파일 목록 JSON을 반환한다") {
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every {
                    accessControl.isAllowedAttachment(loginUser, match { it.containerType == ResourceType.ISSUE_POST && it.containerId == "10" }, Operation.READ)
                } returns true
                every {
                    attachmentRepository.findByContainerTypeAndContainerId(
                        ResourceType.ISSUE_POST,
                        "10"
                    )
                } returns listOf(attachment)

                mockMvc.perform(
                    get("/files")
                        .param("containerType", "ISSUE_POST")
                        .param("containerId", "10")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.attachments[0].id").value("100"))
                    .andExpect(jsonPath("$.attachments[0].name").value("test-file.txt"))
            }

            it("컨테이너 읽기 권한이 없으면 403 Forbidden을 반환한다 (P1-96, 보안)") {
                every { userRepository.findByLoginId("tester") } returns Optional.of(loginUser)
                every {
                    accessControl.isAllowedAttachment(loginUser, match { it.containerType == ResourceType.ISSUE_POST && it.containerId == "10" }, Operation.READ)
                } returns false

                mockMvc.perform(
                    get("/files")
                        .param("containerType", "ISSUE_POST")
                        .param("containerId", "10")
                        .principal(userAuth)
                )
                    .andExpect(status().isForbidden)
            }
        }
    }
})
