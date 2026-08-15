package com.github.search5.yona.web

import com.github.search5.yona.domain.board.Posting
import com.github.search5.yona.domain.board.PostingService
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
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

class BoardControllerSpec : DescribeSpec({
    val postingService = mockk<PostingService>()
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()

    val boardController = BoardController(
        postingService,
        projectRepository,
        projectUserRepository,
        userRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(boardController)
        .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
        .build()

    beforeTest {
        io.mockk.clearMocks(postingService, projectRepository, projectUserRepository, userRepository)
    }

    describe("BoardController 웹 API 테스트") {
        val project = Project(id = 1L, name = "TestProject", projectScope = ProjectScope.PRIVATE)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val managerUser = User(id = 20L, loginId = "manageruser", name = "관리자유저")
        
        val memberRole = Role(id = RoleType.MEMBER.roleType)
        val managerRole = Role(id = RoleType.MANAGER.roleType)

        val projectUser = ProjectUser(id = 100L, user = user, project = project, role = memberRole)
        val projectManagerUser = ProjectUser(id = 101L, user = managerUser, project = project, role = managerRole)

        val posting = Posting(id = 50L, title = "포스트 제목", body = "포스트 내용", project = project, authorId = user.id, number = 1L)

        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
        val managerAuth = UsernamePasswordAuthenticationToken("manageruser", "password")
        val pageRequest = PageRequest.of(0, 25)

        describe("GET /api/projects/{projectId}/posts") {
            it("비공개 프로젝트일 때 프로젝트 멤버라면 200 OK와 게시판 목록을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingService.getPostings(1L, any()) } returns PageImpl(listOf(posting), pageRequest, 1)

                mockMvc.perform(get("/api/projects/1/posts").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.content[0].title").value("포스트 제목"))
            }
        }

        describe("GET /api/projects/{projectId}/posts/{postId}") {
            it("게시글 번호로 포스트 상세 정보를 조회할 수 있어야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { postingService.getPosting(1L, 1L) } returns posting
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true

                mockMvc.perform(get("/api/projects/1/posts/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.title").value("포스트 제목"))
            }
        }

        describe("POST /api/projects/{projectId}/posts") {
            it("새로운 글을 작성하면 201 Created를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingService.createPosting(1L, any(), 10L) } returns posting

                val jsonContent = """
                    {
                        "title": "포스트 제목",
                        "body": "포스트 내용",
                        "notice": false,
                        "readme": false
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/api/projects/1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isCreated)
            }
        }

        describe("PUT /api/projects/{projectId}/posts/{postId}") {
            it("작성자가 포스트를 수정하면 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { postingService.getPosting(1L, 1L) } returns posting
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every { postingService.updatePosting(1L, 1L, "수정된 포스트 제목", "수정된 포스트 내용", false, false, 10L) } returns posting

                val jsonContent = """
                    {
                        "title": "수정된 포스트 제목",
                        "body": "수정된 포스트 내용",
                        "notice": false,
                        "readme": false
                    }
                """.trimIndent()

                mockMvc.perform(
                    put("/api/projects/1/posts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
            }
        }

        describe("DELETE /api/projects/{projectId}/posts/{postId}") {
            it("관리자가 포스트를 삭제하면 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { postingService.getPosting(1L, 1L) } returns posting
                every { userRepository.findByLoginId("manageruser") } returns Optional.of(managerUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 20L) } returns Optional.of(projectManagerUser)
                every { postingService.deletePosting(1L, 1L, 20L) } returns Unit

                mockMvc.perform(delete("/api/projects/1/posts/1").principal(managerAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))
            }
        }
    }
})
