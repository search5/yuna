package com.github.search5.yona.web

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.pullrequest.CodeReviewService
import com.github.search5.yona.domain.pullrequest.PullRequest
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

class ReviewApiControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val userRepository = mockk<UserRepository>()
    val codeReviewService = mockk<CodeReviewService>()

    val reviewApiController = ReviewApiController(
        projectRepository,
        pullRequestRepository,
        userRepository,
        codeReviewService
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(reviewApiController).build()
    val auth = UsernamePasswordAuthenticationToken("gildong", "pass")

    beforeTest {
        io.mockk.clearMocks(projectRepository, pullRequestRepository, userRepository, codeReviewService)
    }

    describe("ReviewApiController API 매핑 및 로직 검증") {
        val user = User(id = 1L, loginId = "gildong", name = "길동")
        val project = Project(id = 10L, name = "yona-project", owner = "gildong")
        val pullRequest = PullRequest(
            id = 100L,
            number = 5L,
            title = "PR 제목",
            toProject = project,
            fromProject = project,
            contributor = user
        )

        it("리뷰어 등록 API 호출 시 302 리다이렉트와 서비스 메소드가 정상 호출되어야 한다") {
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { projectRepository.findByOwnerAndName("gildong", "yona-project") } returns Optional.of(project)
            every { pullRequestRepository.findById(100L) } returns Optional.of(pullRequest)
            every { codeReviewService.addReviewer(100L, 1L) } returns Unit

            mockMvc.perform(
                post("/api/gildong/yona-project/pullRequest/100/review")
                    .principal(auth)
            )
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/gildong/yona-project/pullRequest/5"))

            verify { codeReviewService.addReviewer(100L, 1L) }
        }

        it("리뷰어 해제 API 호출 시 302 리다이렉트와 서비스 메소드가 정상 호출되어야 한다") {
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { projectRepository.findByOwnerAndName("gildong", "yona-project") } returns Optional.of(project)
            every { pullRequestRepository.findById(100L) } returns Optional.of(pullRequest)
            every { codeReviewService.removeReviewer(100L, 1L) } returns Unit

            mockMvc.perform(
                post("/api/gildong/yona-project/pullRequest/100/unreview")
                    .principal(auth)
            )
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/gildong/yona-project/pullRequest/5"))

            verify { codeReviewService.removeReviewer(100L, 1L) }
        }

        it("REVIEW_COMMENT 삭제 API 호출 시 status ok와 deleteReviewComment 서비스 메소드가 정상 호출되어야 한다") {
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { codeReviewService.deleteReviewComment(200L, user) } returns Unit

            mockMvc.perform(
                delete("/comments/REVIEW_COMMENT/200")
                    .principal(auth)
            )
                .andExpect(status().isOk)

            verify { codeReviewService.deleteReviewComment(200L, user) }
        }

        it("COMMIT_COMMENT 삭제 API 호출 시 status ok와 deleteCommitComment 서비스 메소드가 정상 호출되어야 한다") {
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { codeReviewService.deleteCommitComment(200L, user) } returns Unit

            mockMvc.perform(
                delete("/comments/COMMIT_COMMENT/200")
                    .principal(auth)
            )
                .andExpect(status().isOk)

            verify { codeReviewService.deleteCommitComment(200L, user) }
        }
    }
})
