package com.github.search5.yona.web

import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.board.Posting
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.watch.WatchService
import com.github.search5.yona.domain.notification.UserProjectNotificationRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

class WatchControllerSpec : DescribeSpec({
    val watchService = mockk<WatchService>()
    val userRepository = mockk<UserRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val userProjectNotificationRepository = mockk<UserProjectNotificationRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()

    val watchController = WatchController(
        watchService = watchService,
        userRepository = userRepository,
        projectRepository = projectRepository,
        userProjectNotificationRepository = userProjectNotificationRepository,
        issueRepository = issueRepository,
        postingRepository = postingRepository
    )

    val mockMvc = MockMvcBuilders.standaloneSetup(watchController).build()

    beforeTest {
        io.mockk.clearMocks(
            watchService,
            userRepository,
            projectRepository,
            userProjectNotificationRepository,
            issueRepository,
            postingRepository
        )
    }

    describe("WatchController API 및 뷰 렌더링 테스트") {
        val project = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PUBLIC)
        val user1 = User(id = 10L, loginId = "user1", name = "유저1")
        val user2 = User(id = 20L, loginId = "user2", name = "유저2")

        describe("GET /-_-api/v1/owners/{owner}/projects/{projectName}/posts/{number}/watchers") {
            it("type이 issues일 때 해당 이슈의 감시자 JSON 정보를 올바르게 반환해야 한다") {
                val issue = Issue(id = 100L, number = 5L, title = "Test Issue", project = project)

                every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { watchService.findWatchers(ResourceType.ISSUE_POST, "100") } returns setOf(user1, user2)

                mockMvc.perform(get("/-_-api/v1/owners/owner/projects/TestProj/posts/5/watchers")
                    .param("type", "issues"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.totalWatchers").value(2))
                    .andExpect(jsonPath("$.watchersInList").value(2))
                    .andExpect(jsonPath("$.watchers[0].name").value("유저1"))
                    .andExpect(jsonPath("$.watchers[0].url").value("/user/user1"))
            }

            it("type이 posts일 때 해당 게시글의 감시자 JSON 정보를 올바르게 반환해야 한다") {
                val posting = Posting(id = 200L, number = 3L, title = "Test Post", project = project)

                every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
                every { postingRepository.findByProjectAndNumber(project, 3L) } returns posting
                every { watchService.findWatchers(ResourceType.BOARD_POST, "200") } returns setOf(user2)

                mockMvc.perform(get("/-_-api/v1/owners/owner/projects/TestProj/posts/3/watchers")
                    .param("type", "posts"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.totalWatchers").value(1))
                    .andExpect(jsonPath("$.watchersInList").value(1))
                    .andExpect(jsonPath("$.watchers[0].name").value("유저2"))
                    .andExpect(jsonPath("$.watchers[0].url").value("/user/user2"))
            }
        }

        describe("GET /{owner}/{projectName}/watchers") {
            it("프로젝트 감시자 뷰와 감시자 목록을 모델에 전달하여 200 OK를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
                every { watchService.findWatchers(ResourceType.PROJECT, "1") } returns setOf(user1)

                mockMvc.perform(get("/owner/TestProj/watchers"))
                    .andExpect(status().isOk)
                    .andExpect(model().attributeExists("project"))
                    .andExpect(model().attributeExists("watchers"))
                    .andExpect(view().name("project/watchers"))
            }
        }
    }
})

