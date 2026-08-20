package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

class ReviewApiControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val userRepository = mockk<UserRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val codeReviewService = mockk<CodeReviewService>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    every { organizationUserRepository.findByOrganizationIdAndUserId(any(), any()) } returns Optional.empty()
    val accessControl = AccessControl(projectUserRepository, organizationUserRepository)

    val reviewApiController = ReviewApiController(
        projectRepository,
        pullRequestRepository,
        userRepository,
        projectUserRepository,
        codeReviewService,
        accessControl
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(reviewApiController).build()
    val auth = UsernamePasswordAuthenticationToken("gildong", "pass")

    beforeTest {
        io.mockk.clearMocks(projectRepository, pullRequestRepository, userRepository, projectUserRepository, codeReviewService)
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
            every { projectUserRepository.existsByProjectIdAndUserId(10L, 1L) } returns true
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
            every { projectUserRepository.existsByProjectIdAndUserId(10L, 1L) } returns true
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

        // yona AccessControl.isProjectResourceAllowed()의 PULL_REQUEST Operation.ACCEPT 분기 대응 (P1-78).
        it("PUBLIC 프로젝트여도 멤버가 아니면 리뷰어로 등록할 수 없어야 한다(인가 우회 방지)") {
            val publicProject = Project(id = 11L, name = "public-project", owner = "owner", projectScope = ProjectScope.PUBLIC)
            val stranger = User(id = 2L, loginId = "stranger", name = "제3자")
            val strangerAuth = UsernamePasswordAuthenticationToken("stranger", "pass")

            every { userRepository.findByLoginId("stranger") } returns Optional.of(stranger)
            every { projectRepository.findByOwnerAndName("owner", "public-project") } returns Optional.of(publicProject)
            every { projectUserRepository.existsByProjectIdAndUserId(11L, 2L) } returns false

            mockMvc.perform(
                post("/api/owner/public-project/pullRequest/100/review")
                    .principal(strangerAuth)
            )
                .andExpect(view().name("error/403"))

            verify(exactly = 0) { codeReviewService.addReviewer(any(), any()) }
        }

        it("PUBLIC 프로젝트여도 멤버가 아니면 리뷰어를 해제할 수 없어야 한다(인가 우회 방지)") {
            val publicProject = Project(id = 11L, name = "public-project", owner = "owner", projectScope = ProjectScope.PUBLIC)
            val stranger = User(id = 2L, loginId = "stranger", name = "제3자")
            val strangerAuth = UsernamePasswordAuthenticationToken("stranger", "pass")

            every { userRepository.findByLoginId("stranger") } returns Optional.of(stranger)
            every { projectRepository.findByOwnerAndName("owner", "public-project") } returns Optional.of(publicProject)
            every { projectUserRepository.existsByProjectIdAndUserId(11L, 2L) } returns false

            mockMvc.perform(
                post("/api/owner/public-project/pullRequest/100/unreview")
                    .principal(strangerAuth)
            )
                .andExpect(view().name("error/403"))

            verify(exactly = 0) { codeReviewService.removeReviewer(any(), any()) }
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

        it("[Test-13-1-1] 타인의 REVIEW_COMMENT 삭제 API 호출 시 403 Forbidden을 반환해야 한다") {
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { codeReviewService.deleteReviewComment(200L, user) } throws IllegalArgumentException("Permission denied")

            mockMvc.perform(
                delete("/comments/REVIEW_COMMENT/200")
                    .principal(auth)
            )
                .andExpect(status().isForbidden)
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

        it("[Test-13-1-2] 타인의 COMMIT_COMMENT 삭제 API 호출 시 403 Forbidden을 반환해야 한다") {
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { codeReviewService.deleteCommitComment(200L, user) } throws IllegalArgumentException("Permission denied")

            mockMvc.perform(
                delete("/comments/COMMIT_COMMENT/200")
                    .principal(auth)
            )
                .andExpect(status().isForbidden)
        }
    }
})
