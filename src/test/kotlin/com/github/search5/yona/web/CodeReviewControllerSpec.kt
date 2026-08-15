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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

class CodeReviewControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val userRepository = mockk<UserRepository>()
    val codeReviewService = mockk<CodeReviewService>()

    val codeReviewController = CodeReviewController(
        projectRepository,
        pullRequestRepository,
        userRepository,
        codeReviewService
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(codeReviewController).build()
    val auth = UsernamePasswordAuthenticationToken("gildong", "pass")

    beforeTest {
        io.mockk.clearMocks(projectRepository, pullRequestRepository, userRepository, codeReviewService)
    }

    describe("CodeReviewController 리뷰어 등록 및 해제 TDD 검증") {
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

        it("리뷰어 등록 API 호출 시 302 리다이렉트와 서비스 메소드가 정상 호출되어야 한다 (TDD Red 예상)") {
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

        it("리뷰어 해제 API 호출 시 302 리다이렉트와 서비스 메소드가 정상 호출되어야 한다 (TDD Red 예상)") {
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
    }
})
