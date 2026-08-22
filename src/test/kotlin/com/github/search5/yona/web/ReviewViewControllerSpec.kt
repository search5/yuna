package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.pullrequest.CodeReviewService
import com.github.search5.yona.domain.pullrequest.CommitComment
import com.github.search5.yona.domain.pullrequest.PullRequest
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
import io.mockk.clearMocks

class ReviewViewControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val userRepository = mockk<UserRepository>()
    val codeReviewService = mockk<CodeReviewService>()
    val accessControl = mockk<AccessControl>()

    val reviewViewController = ReviewViewController(
        projectRepository,
        pullRequestRepository,
        userRepository,
        codeReviewService,
        accessControl
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(reviewViewController).build()
    val auth = UsernamePasswordAuthenticationToken("gildong", "pass")

    beforeTest {
        clearMocks(projectRepository, pullRequestRepository, userRepository, codeReviewService, accessControl)
        every { accessControl.isProjectResourceCreatable(any(), any(), any()) } returns true
    }

    describe("ReviewViewController 뷰 관련 처리 및 비즈니스 흐름 검증") {
        val user = User(id = 1L, loginId = "gildong", name = "길동")

        it("Git 프로젝트의 커밋에 댓글 추가 시 ReviewComment가 생성되고 리다이렉트되어야 한다") {
            val project = Project(id = 10L, name = "yona-project", owner = "gildong", vcs = "GIT")
            val comment = ReviewComment(id = 300L, contents = "테스트 댓글")

            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("gildong", "yona-project") } returns Optional.of(project)
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
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("gildong", "yona-project") } returns Optional.of(project)
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
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("gildong", "yona-project") } returns Optional.of(project)
            every { codeReviewService.deleteReviewComment(300L, user) } returns Unit

            mockMvc.perform(
                delete("/gildong/yona-project/commit/abc1234/comments/300/delete")
                    .principal(auth)
            )
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/gildong/yona-project/commit/abc1234"))

            verify { codeReviewService.deleteReviewComment(300L, user) }
        }

        it("[Test-13-1-4] 타인의 Git 커밋 댓글 삭제 시 error/forbidden 뷰를 반환해야 한다") {
            val project = Project(id = 10L, name = "yona-project", owner = "gildong", vcs = "GIT")

            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("gildong", "yona-project") } returns Optional.of(project)
            every { codeReviewService.deleteReviewComment(300L, user) } throws IllegalArgumentException("Permission denied")

            mockMvc.perform(
                delete("/gildong/yona-project/commit/abc1234/comments/300/delete")
                    .principal(auth)
            )
                .andExpect(status().isOk)
                .andExpect(view().name("error/forbidden"))
        }

        it("SVN 프로젝트의 커밋 댓글 삭제 시 deleteCommitComment가 호출되어야 한다") {
            val project = Project(id = 10L, name = "yona-project", owner = "gildong", vcs = "SUBVERSION")

            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("gildong", "yona-project") } returns Optional.of(project)
            every { codeReviewService.deleteCommitComment(400L, user) } returns Unit

            mockMvc.perform(
                delete("/gildong/yona-project/commit/abc1234/comments/400/delete")
                    .principal(auth)
            )
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/gildong/yona-project/commit/abc1234"))

            verify { codeReviewService.deleteCommitComment(400L, user) }
        }

        it("[Test-13-1-5] 타인의 SVN 커밋 댓글 삭제 시 error/forbidden 뷰를 반환해야 한다") {
            val project = Project(id = 10L, name = "yona-project", owner = "gildong", vcs = "SUBVERSION")

            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("gildong", "yona-project") } returns Optional.of(project)
            every { codeReviewService.deleteCommitComment(400L, user) } throws IllegalArgumentException("Permission denied")

            mockMvc.perform(
                delete("/gildong/yona-project/commit/abc1234/comments/400/delete")
                    .principal(auth)
            )
                .andExpect(status().isOk)
                .andExpect(view().name("error/forbidden"))
        }

        // yona PullRequestApp.java:591 @IsCreatable(ResourceType.REVIEW_COMMENT) 대응 (P0-24).
        describe("POST /{owner}/{projectName}/pullRequest/{pullRequestId}/comments 권한 체크") {
            it("REVIEW_COMMENT 생성 권한이 없으면 error/forbidden 뷰를 반환하고 댓글을 생성하지 않아야 한다") {
                val project = Project(id = 10L, name = "yona-project", owner = "gildong", vcs = "GIT")
                every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("gildong", "yona-project") } returns Optional.of(project)
                every { accessControl.isProjectResourceCreatable(user, project, ResourceType.REVIEW_COMMENT) } returns false

                mockMvc.perform(
                    post("/gildong/yona-project/pullRequest/5/comments")
                        .param("contents", "댓글")
                        .principal(auth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/forbidden"))

                verify(exactly = 0) { codeReviewService.createReviewComment(any(), any(), any(), any(), any(), any(), any()) }
            }

            it("REVIEW_COMMENT 생성 권한이 있으면 정상적으로 댓글이 생성되고 리다이렉트되어야 한다") {
                val project = Project(id = 10L, name = "yona-project", owner = "gildong", vcs = "GIT")
                val pullRequest = PullRequest(id = 5L, number = 5L, toProject = project, fromProject = project, contributor = user)
                val comment = ReviewComment(id = 500L, contents = "댓글")
                every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("gildong", "yona-project") } returns Optional.of(project)
                every { accessControl.isProjectResourceCreatable(user, project, ResourceType.REVIEW_COMMENT) } returns true
                every { pullRequestRepository.findById(5L) } returns Optional.of(pullRequest)
                every {
                    codeReviewService.createReviewComment(
                        project = project, pullRequest = pullRequest, commitId = null,
                        contents = "댓글", codeRange = any(), threadId = null, currentUser = user
                    )
                } returns comment

                mockMvc.perform(
                    post("/gildong/yona-project/pullRequest/5/comments")
                        .param("contents", "댓글")
                        .principal(auth)
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/gildong/yona-project/pullRequest/5/changes#comment-500"))
            }
        }

        // yona CodeHistoryApp.java:189 @IsCreatable(ResourceType.COMMIT_COMMENT) 대응 (P0-24).
        describe("POST /{owner}/{projectName}/commit/{commitId}/comments 권한 체크") {
            it("COMMIT_COMMENT 생성 권한이 없으면 error/forbidden 뷰를 반환하고 댓글을 생성하지 않아야 한다") {
                val project = Project(id = 10L, name = "yona-project", owner = "gildong", vcs = "GIT")
                every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("gildong", "yona-project") } returns Optional.of(project)
                every { accessControl.isProjectResourceCreatable(user, project, ResourceType.COMMIT_COMMENT) } returns false

                mockMvc.perform(
                    post("/gildong/yona-project/commit/abc1234/comments")
                        .param("contents", "댓글")
                        .principal(auth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/forbidden"))

                verify(exactly = 0) { codeReviewService.createReviewComment(any(), any(), any(), any(), any(), any(), any()) }
                verify(exactly = 0) { codeReviewService.createCommitComment(any(), any(), any(), any(), any(), any(), any()) }
            }
        }
    }
})
