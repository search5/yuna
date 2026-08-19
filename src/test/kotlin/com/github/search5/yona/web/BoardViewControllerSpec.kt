package com.github.search5.yona.web

import com.github.search5.yona.domain.board.Posting
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.board.PostingService
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.board.PostingCommentRepository
import com.github.search5.yona.domain.watch.WatchService
import com.github.search5.yona.domain.attachment.AttachmentRepository
import com.github.search5.yona.domain.enumeration.ResourceType
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import tools.jackson.databind.ObjectMapper
import java.util.Optional

class BoardViewControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val postingService = mockk<PostingService>()
    val postingRepository = mockk<PostingRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val postingCommentRepository = mockk<PostingCommentRepository>()
    val watchService = mockk<WatchService>()
    val attachmentRepository = mockk<AttachmentRepository>()
    val repositoryService = mockk<com.github.search5.yona.domain.vcs.RepositoryService>()
    val objectMapper = ObjectMapper()

    val boardViewController = BoardViewController(
        projectRepository,
        postingService,
        postingRepository,
        projectUserRepository,
        userRepository,
        postingCommentRepository,
        watchService,
        attachmentRepository,
        objectMapper,
        repositoryService,
        "/tmp/yuna/git"
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(boardViewController)
        .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
        .build()

    beforeTest {
        io.mockk.clearMocks(projectRepository, postingService, postingRepository, projectUserRepository, userRepository,
            postingCommentRepository, watchService, attachmentRepository)
    }

    describe("BoardViewController 템플릿 연동 테스트") {
        val project = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PRIVATE)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val posting = Posting(id = 5L, title = "게시물 제목", project = project, number = 1L)

        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
        val pageRequest = PageRequest.of(0, 20)

        describe("GET /{owner}/{projectName}/posts") {
            it("비공개 프로젝트일 때 멤버라면 200 OK와 board/list 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingRepository.findByProject(project, any<Pageable>()) } returns PageImpl(listOf(posting), pageRequest, 1)
                every { postingService.getNotices(1L) } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/posts").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("board/list"))
                    .andExpect(model().attributeExists("project", "postingPage", "notices"))
            }

            it("프로젝트 멤버가 아닐 경우 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

                mockMvc.perform(get("/owner/TestProj/posts").principal(userAuth))
                    .andExpect(view().name("error/403"))
            }
        }

        describe("GET /{owner}/{projectName}/post/{number}") {
            it("멤버라면 200 OK와 board/view 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingService.getPosting(1L, 1L) } returns posting
                every { postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(5L) } returns emptyList()
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.BOARD_POST, "5") } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/post/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("board/view"))
                    .andExpect(model().attributeExists("project", "post"))
            }
        }

        describe("GET /{owner}/{projectName}/post/new") {
            it("멤버라면 200 OK와 board/create 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true

                mockMvc.perform(get("/owner/TestProj/post/new").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("board/create"))
                    .andExpect(model().attributeExists("project"))
            }
        }
    }
})
