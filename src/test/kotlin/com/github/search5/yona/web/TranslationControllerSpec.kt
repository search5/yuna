package com.github.search5.yona.web

import com.github.search5.yona.domain.board.Posting
import com.github.search5.yona.domain.board.PostingComment
import com.github.search5.yona.domain.board.PostingCommentRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueComment
import com.github.search5.yona.domain.issue.IssueCommentRepository
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.support.MarkdownService
import com.github.search5.yona.domain.support.TranslationService
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.*

class TranslationControllerSpec : DescribeSpec({
    val translationService = mockk<TranslationService>()
    val projectRepository = mockk<ProjectRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val issueCommentRepository = mockk<IssueCommentRepository>()
    val postingCommentRepository = mockk<PostingCommentRepository>()
    val markdownService = mockk<MarkdownService>()
    val userRepository = mockk<UserRepository>()

    val controller = TranslationController(
        translationService,
        projectRepository,
        issueRepository,
        postingRepository,
        issueCommentRepository,
        postingCommentRepository,
        markdownService,
        userRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    beforeTest {
        io.mockk.clearMocks(
            translationService,
            projectRepository,
            issueRepository,
            postingRepository,
            issueCommentRepository,
            postingCommentRepository,
            markdownService,
            userRepository
        )
    }

    describe("TranslationController 단위 테스트") {
        val user = User(id = 1L, loginId = "testuser", name = "테스트유저", email = "test@example.com")
        val auth = UsernamePasswordAuthenticationToken("testuser", "password")
        val project = Project(id = 10L, name = "testproject", owner = "testowner")

        describe("POST /-_-api/v1/translation") {
            it("이슈 번역 요청을 정상적으로 처리해야 한다") {
                val issue = Issue(id = 100L, title = "한글 타이틀", body = "한글 내용", project = project)

                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findByOwnerAndName("testowner", "testproject") } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue
                every { translationService.translate(any()) } returns "Translated Title\n\nTranslated Body"
                every { markdownService.render("Translated Title\n\nTranslated Body") } returns "<p>Translated Title</p><p>Translated Body</p>"

                val requestJson = """
                    {
                        "owner": "testowner",
                        "projectName": "testproject",
                        "type": "issue",
                        "number": 1
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/-_-api/v1/translation")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.translated").value("<p>Translated Title</p><p>Translated Body</p>"))
            }

            it("번역 API 연동 설정이 안되어 있다면 412 status를 응답해야 한다") {
                val issue = Issue(id = 100L, title = "한글 타이틀", body = "한글 내용", project = project)

                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findByOwnerAndName("testowner", "testproject") } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue
                every { translationService.translate(any()) } returns null

                val requestJson = """
                    {
                        "owner": "testowner",
                        "projectName": "testproject",
                        "type": "issue",
                        "number": 1
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/-_-api/v1/translation")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                )
                    .andExpect(status().isPreconditionFailed)
                    .andExpect(jsonPath("$.error").value("Precondition Failed"))
            }

            it("이슈 댓글 번역 요청을 정상적으로 처리해야 한다") {
                val comment = IssueComment(id = 200L, contents = "댓글 내용", issue = mockk())

                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findByOwnerAndName("testowner", "testproject") } returns Optional.of(project)
                every { issueCommentRepository.findById(200L) } returns Optional.of(comment)
                every { translationService.translate("댓글 내용") } returns "Translated Comment"
                every { markdownService.render("Translated Comment") } returns "<p>Translated Comment</p>"

                val requestJson = """
                    {
                        "owner": "testowner",
                        "projectName": "testproject",
                        "type": "issue-comment",
                        "number": 200
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/-_-api/v1/translation")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.translated").value("<p>Translated Comment</p>"))
            }
        }
    }
})
