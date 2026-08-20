package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.comment.CommentService
import com.github.search5.yona.domain.organization.OrganizationUserRepository
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
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUser
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.pullrequest.ReviewCommentRepository
import com.github.search5.yona.domain.pullrequest.CommitCommentRepository
import com.github.search5.yona.domain.milestone.MilestoneRepository

class CommentControllerSpec : DescribeSpec({
    val commentService = mockk<CommentService>()
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val issueCommentRepository = mockk<IssueCommentRepository>()
    val postingCommentRepository = mockk<PostingCommentRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    every { organizationUserRepository.findByOrganizationIdAndUserId(any(), any()) } returns Optional.empty()
    val userRepositoryForAccessControl = mockk<UserRepository>()
    val organizationRepositoryForAccessControl = mockk<OrganizationRepository>()
    val issueRepositoryForAccessControl = mockk<IssueRepository>()
    val postingRepositoryForAccessControl = mockk<PostingRepository>()
    val reviewCommentRepositoryForAccessControl = mockk<ReviewCommentRepository>()
    val commitCommentRepositoryForAccessControl = mockk<CommitCommentRepository>()
    val milestoneRepositoryForAccessControl = mockk<MilestoneRepository>()
    val accessControl = AccessControl(
        projectUserRepository, organizationUserRepository,
        userRepositoryForAccessControl, organizationRepositoryForAccessControl,
        issueRepositoryForAccessControl, postingRepositoryForAccessControl,
        reviewCommentRepositoryForAccessControl, commitCommentRepositoryForAccessControl,
        milestoneRepositoryForAccessControl
    )

    val commentController = CommentController(
        commentService,
        projectRepository,
        projectUserRepository,
        userRepository,
        issueRepository,
        postingRepository,
        issueCommentRepository,
        postingCommentRepository,
        accessControl
    )

    val mockMvc = MockMvcBuilders.standaloneSetup(commentController).build()

    beforeTest {
        io.mockk.clearMocks(
            commentService, projectRepository, projectUserRepository, userRepository,
            issueRepository, postingRepository, issueCommentRepository, postingCommentRepository
        )
    }

    describe("CommentController 웹 API 테스트") {
        val project = Project(id = 1L, name = "TestProject", projectScope = ProjectScope.PRIVATE)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val otherUser = User(id = 20L, loginId = "otheruser", name = "다른유저")
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
        val otherAuth = UsernamePasswordAuthenticationToken("otheruser", "password")

        val issue = Issue(id = 50L, number = 5L, title = "이슈", body = "내용", project = project, authorId = user.id)
        val posting = Posting(id = 60L, number = 6L, title = "포스트", body = "내용", project = project)

        val issueComment = IssueComment(id = 100L, contents = "이슈댓글", issue = issue, authorId = user.id, authorLoginId = user.loginId, authorName = user.name)
        val postingComment = PostingComment(id = 200L, contents = "게시판댓글", posting = posting, authorId = user.id, authorLoginId = user.loginId, authorName = user.name)

        user.projectUsers.add(ProjectUser(id = 900L, user = user, project = project, role = Role(id = RoleType.MEMBER.roleType)))

        describe("POST /api/projects/{projectId}/issues/{number}/comments (이슈 댓글 작성)") {
            it("권한이 있는 멤버가 호출 시 201 Created를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { commentService.createIssueComment(50L, "이슈댓글", user) } returns issueComment

                mockMvc.perform(
                    post("/api/projects/1/issues/5/comments")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"이슈댓글\"}")
                )
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$.contents").value("이슈댓글"))
            }

            it("권한이 없는 멤버가 호출 시 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 20L) } returns false

                mockMvc.perform(
                    post("/api/projects/1/issues/5/comments")
                        .principal(otherAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"이슈댓글\"}")
                )
                    .andExpect(status().isForbidden)
            }
        }

        describe("PUT /api/projects/{projectId}/issues/{number}/comments/{commentId} (이슈 댓글 수정)") {
            it("작성자 본인이 수정 시 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.empty()
                every { issueCommentRepository.findById(100L) } returns Optional.of(issueComment)
                
                val updatedComment = IssueComment(id = 100L, contents = "수정된이슈댓글", issue = issue, authorId = user.id, authorLoginId = user.loginId, authorName = user.name)
                every { commentService.updateIssueComment(100L, "수정된이슈댓글", user) } returns updatedComment

                mockMvc.perform(
                    put("/api/projects/1/issues/5/comments/100")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"수정된이슈댓글\"}")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.contents").value("수정된이슈댓글"))
            }

            it("타인이 수정 시 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 20L) } returns true
                every { projectUserRepository.findByProjectIdAndUserId(1L, 20L) } returns Optional.empty()
                every { issueCommentRepository.findById(100L) } returns Optional.of(issueComment)

                mockMvc.perform(
                    put("/api/projects/1/issues/5/comments/100")
                        .principal(otherAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"수정된이슈댓글\"}")
                )
                    .andExpect(status().isForbidden)
            }
        }

        describe("DELETE /api/projects/{projectId}/issues/{number}/comments/{commentId} (이슈 댓글 삭제)") {
            it("작성자가 삭제 시 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.empty()
                every { issueCommentRepository.findById(100L) } returns Optional.of(issueComment)
                every { commentService.deleteIssueComment(100L, user) } returns Unit

                mockMvc.perform(
                    delete("/api/projects/1/issues/5/comments/100")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
            }
        }

        describe("POST /api/projects/{projectId}/posts/{number}/comments (게시글 댓글 작성)") {
            it("권한이 있는 멤버가 호출 시 201 Created를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingRepository.findByProjectAndNumber(project, 6L) } returns posting
                every { commentService.createPostingComment(60L, "게시판댓글", user) } returns postingComment

                mockMvc.perform(
                    post("/api/projects/1/posts/6/comments")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"게시판댓글\"}")
                )
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$.contents").value("게시판댓글"))
            }
        }

        describe("PUT /api/projects/{projectId}/posts/{number}/comments/{commentId} (게시글 댓글 수정)") {
            it("작성자 본인이 수정 시 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.empty()
                every { postingCommentRepository.findById(200L) } returns Optional.of(postingComment)
                
                val updatedComment = PostingComment(id = 200L, contents = "수정된게시판댓글", posting = posting, authorId = user.id, authorLoginId = user.loginId, authorName = user.name)
                every { commentService.updatePostingComment(200L, "수정된게시판댓글", user) } returns updatedComment

                mockMvc.perform(
                    put("/api/projects/1/posts/6/comments/200")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"수정된게시판댓글\"}")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.contents").value("수정된게시판댓글"))
            }
        }

        describe("DELETE /api/projects/{projectId}/posts/{number}/comments/{commentId} (게시글 댓글 삭제)") {
            it("작성자가 삭제 시 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.empty()
                every { postingCommentRepository.findById(200L) } returns Optional.of(postingComment)
                every { commentService.deletePostingComment(200L, user) } returns Unit

                mockMvc.perform(
                    delete("/api/projects/1/posts/6/comments/200")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
            }
        }
    }
})
