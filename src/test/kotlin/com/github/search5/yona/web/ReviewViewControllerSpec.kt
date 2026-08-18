package com.github.search5.yona.web

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.pullrequest.CodeReviewService
import com.github.search5.yona.domain.pullrequest.CommitComment
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.domain.pullrequest.ReviewComment
import com.github.search5.yona.domain.support.CodeRange
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

class ReviewViewControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val userRepository = mockk<UserRepository>()
    val codeReviewService = mockk<CodeReviewService>()

    val reviewViewController = ReviewViewController(
        projectRepository,
        pullRequestRepository,
        userRepository,
        codeReviewService
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(reviewViewController).build()
    val auth = UsernamePasswordAuthenticationToken("gildong", "pass")

    beforeTest {
        io.mockk.clearMocks(projectRepository, pullRequestRepository, userRepository, codeReviewService)
    }

    describe("ReviewViewController 뷰 관련 처리 및 비즈니스 흐름 검증") {
        val user = User(id = 1L, loginId = "gildong", name = "길동")
        
        it("Git 프로젝트의 커밋에 댓글 추가 시 ReviewComment가 생성되고 리다이렉트되어야 한다") {
            val project = Project(id = 10L, name = "yona-project", owner = "gildong", vcs = "GIT")
            val comment = ReviewComment(id = 300L, contents = "테스트 댓글")
            
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { projectRepository.findByOwnerAndName("gildong", "yona-project") } returns Optional.of(project)
            every {
                codeReviewService.createReviewComment(
                    project = project,
                    pullRequest = null,
                    commitId = "abc1234",
                    contents = "테스트 댓글",
                    codeRange = any(),
                    threadId = null,
                    currentUser = user
                )
            } returns comment

            mockMvc.perform(
                post("/gildong/yona-project/commit/abc1234/comments")
                    .param("contents", "테스트 댓글")
                    .param("path", "src/main.kt")
                    .param("startLine", "10")
                    .param("startSide", "B")
                    .principal(auth)
            )
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/gildong/yona-project/commit/abc1234#comment-300"))

            verify {
                codeReviewService.createReviewComment(
                    project = project,
                    pullRequest = null,
                    commitId = "abc1234",
                    contents = "테스트 댓글",
                    codeRange = match { it.path == "src/main.kt" && it.startLine == 10 && it.startSide == CodeRange.Side.B },
                    threadId = null,
                    currentUser = user
                )
            }
        }

        it("SVN 프로젝트의 커밋에 댓글 추가 시 CommitComment가 생성되고 리다이렉트되어야 한다") {
            val project = Project(id = 10L, name = "yona-project", owner = "gildong", vcs = "SUBVERSION")
            val comment = CommitComment(id = 400L, contents = "테스트 SVN 댓글")

            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { projectRepository.findByOwnerAndName("gildong", "yona-project") } returns Optional.of(project)
            every {
                codeReviewService.createCommitComment(
                    project = project,
                    commitId = "abc1234",
                    contents = "테스트 SVN 댓글",
                    path = "src/main.kt",
                    line = 10,
                    side = CodeRange.Side.B,
                    currentUser = user
                )
            } returns comment

            mockMvc.perform(
                post("/gildong/yona-project/commit/abc1234/comments")
                    .param("contents", "테스트 SVN 댓글")
                    .param("path", "src/main.kt")
                    .param("startLine", "10")
                    .param("startSide", "B")
                    .principal(auth)
            )
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/gildong/yona-project/commit/abc1234#comment-400"))

            verify {
                codeReviewService.createCommitComment(
                    project = project,
                    commitId = "abc1234",
                    contents = "테스트 SVN 댓글",
                    path = "src/main.kt",
                    line = 10,
                    side = CodeRange.Side.B,
                    currentUser = user
                )
            }
        }

        it("Git 프로젝트의 커밋 댓글 삭제 시 deleteReviewComment가 호출되어야 한다") {
            val project = Project(id = 10L, name = "yona-project", owner = "gildong", vcs = "GIT")
            
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { projectRepository.findByOwnerAndName("gildong", "yona-project") } returns Optional.of(project)
            every { codeReviewService.deleteReviewComment(300L, user) } returns Unit

            mockMvc.perform(
                delete("/gildong/yona-project/commit/abc1234/comments/300/delete")
                    .principal(auth)
            )
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/gildong/yona-project/commit/abc1234"))

            verify { codeReviewService.deleteReviewComment(300L, user) }
        }

        it("[Test-13-1-4] 타인의 Git 커밋 댓글 삭제 시 error/403 뷰를 반환해야 한다") {
            val project = Project(id = 10L, name = "yona-project", owner = "gildong", vcs = "GIT")
            
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { projectRepository.findByOwnerAndName("gildong", "yona-project") } returns Optional.of(project)
            every { codeReviewService.deleteReviewComment(300L, user) } throws IllegalArgumentException("Permission denied")

            mockMvc.perform(
                delete("/gildong/yona-project/commit/abc1234/comments/300/delete")
                    .principal(auth)
            )
                .andExpect(status().isOk)
                .andExpect(view().name("error/403"))
        }

        it("SVN 프로젝트의 커밋 댓글 삭제 시 deleteCommitComment가 호출되어야 한다") {
            val project = Project(id = 10L, name = "yona-project", owner = "gildong", vcs = "SUBVERSION")

            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { projectRepository.findByOwnerAndName("gildong", "yona-project") } returns Optional.of(project)
            every { codeReviewService.deleteCommitComment(400L, user) } returns Unit

            mockMvc.perform(
                delete("/gildong/yona-project/commit/abc1234/comments/400/delete")
                    .principal(auth)
            )
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/gildong/yona-project/commit/abc1234"))

            verify { codeReviewService.deleteCommitComment(400L, user) }
        }

        it("[Test-13-1-5] 타인의 SVN 커밋 댓글 삭제 시 error/403 뷰를 반환해야 한다") {
            val project = Project(id = 10L, name = "yona-project", owner = "gildong", vcs = "SUBVERSION")

            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { projectRepository.findByOwnerAndName("gildong", "yona-project") } returns Optional.of(project)
            every { codeReviewService.deleteCommitComment(400L, user) } throws IllegalArgumentException("Permission denied")

            mockMvc.perform(
                delete("/gildong/yona-project/commit/abc1234/comments/400/delete")
                    .principal(auth)
            )
                .andExpect(status().isOk)
                .andExpect(view().name("error/403"))
        }
    }
})
