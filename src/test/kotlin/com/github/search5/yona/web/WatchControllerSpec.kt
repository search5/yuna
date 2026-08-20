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
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.enumeration.Operation
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.*
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import org.springframework.security.core.Authentication

class WatchControllerSpec : DescribeSpec({
    val watchService = mockk<WatchService>()
    val userRepository = mockk<UserRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val userProjectNotificationRepository = mockk<UserProjectNotificationRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val accessControl = mockk<AccessControl>()

    val watchController = WatchController(
        watchService = watchService,
        userRepository = userRepository,
        projectRepository = projectRepository,
        userProjectNotificationRepository = userProjectNotificationRepository,
        issueRepository = issueRepository,
        postingRepository = postingRepository,
        pullRequestRepository = pullRequestRepository,
        accessControl = accessControl
    )

    val mockMvc = MockMvcBuilders.standaloneSetup(watchController).build()

    beforeTest {
        clearMocks(
            watchService,
            userRepository,
            projectRepository,
            userProjectNotificationRepository,
            issueRepository,
            postingRepository,
            pullRequestRepository,
            accessControl
        )
    }

    describe("WatchController API 및 뷰 렌더링 테스트") {
        val project = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PUBLIC)
        val user1 = User(id = 10L, loginId = "user1", name = "유저1")
        val user2 = User(id = 20L, loginId = "user2", name = "유저2")
        val auth = mockk<Authentication>()

        describe("익명 사용자 및 비로그인 차단 검증") {
            it("로그인 정보(Authentication)가 없을 때 401 Unauthorized를 반환해야 한다") {
                mockMvc.perform(post("/watch")
                    .param("resource.type", "PROJECT")
                    .param("resource.id", "1"))
                    .andExpect(status().isUnauthorized)
            }
        }

        describe("권한 체크 및 차단 검증") {
            it("프로젝트 읽기 권한이 없는 경우 403 Forbidden을 반환해야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { accessControl.isAllowed(user1, project, Operation.WATCH) } returns false

                mockMvc.perform(post("/watch")
                    .principal(auth)
                    .param("resource.type", "PROJECT")
                    .param("resource.id", "1"))
                    .andExpect(status().isForbidden)
            }
        }

        describe("이슈/프로젝트 감시 등록/해제 API 동작 검증") {
            it("/watch 호출 시 정상적으로 프로젝트를 감시 등록해야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { accessControl.isAllowed(user1, project, Operation.WATCH) } returns true
                every { watchService.watch(user1, ResourceType.PROJECT, "1") } just Runs

                mockMvc.perform(post("/watch")
                    .principal(auth)
                    .param("resource.type", "PROJECT")
                    .param("resource.id", "1"))
                    .andExpect(status().isOk)

                verify(exactly = 1) { watchService.watch(user1, ResourceType.PROJECT, "1") }
            }

            it("/unwatch 호출 시 정상적으로 이슈 감시를 해제하고 302 Redirect 해야 한다") {
                val issue = Issue(id = 100L, number = 5L, title = "Test Issue", project = project)

                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { issueRepository.findById(100L) } returns Optional.of(issue)
                every { accessControl.isAllowed(user1, project, Operation.WATCH) } returns true
                every { watchService.unwatch(user1, ResourceType.ISSUE_POST, "100") } just Runs

                mockMvc.perform(post("/unwatch")
                    .principal(auth)
                    .header("Referer", "/referred-page")
                    .param("resource.type", "ISSUE_POST")
                    .param("resource.id", "100"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/referred-page"))

                verify(exactly = 1) { watchService.unwatch(user1, ResourceType.ISSUE_POST, "100") }
            }

            it("/{owner}/{projectName}/watch 호출 시 해당 프로젝트를 감시 등록해야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
                every { accessControl.isAllowed(user1, project, Operation.WATCH) } returns true
                every { watchService.watch(user1, ResourceType.PROJECT, "1") } just Runs

                mockMvc.perform(post("/owner/TestProj/watch")
                    .principal(auth))
                    .andExpect(status().isOk)

                verify(exactly = 1) { watchService.watch(user1, ResourceType.PROJECT, "1") }
            }

            it("/{owner}/{projectName}/unwatch 호출 시 감시를 해제하고 연관 알림설정을 삭제해야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
                every { accessControl.isAllowed(user1, project, Operation.WATCH) } returns true
                every { watchService.unwatch(user1, ResourceType.PROJECT, "1") } just Runs
                every { userProjectNotificationRepository.deleteByUserAndProject(user1, project) } just Runs

                mockMvc.perform(post("/owner/TestProj/unwatch")
                    .principal(auth))
                    .andExpect(status().isOk)

                verify(exactly = 1) { watchService.unwatch(user1, ResourceType.PROJECT, "1") }
                verify(exactly = 1) { userProjectNotificationRepository.deleteByUserAndProject(user1, project) }
            }

            it("/watch/toggle/{projectId}/{notificationType} 호출 시 알림 설정을 정상 토글해야 한다") {
                every { auth.name } returns "user1"
                every { userRepository.findByLoginId("user1") } returns Optional.of(user1)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { accessControl.isAllowed(user1, project, Operation.WATCH) } returns true
                every { watchService.isWatching(user1, ResourceType.PROJECT, "1") } returns true
                every { userProjectNotificationRepository.findByUserAndProjectAndNotificationType(user1, project, any()) } returns null
                every { userProjectNotificationRepository.save(any()) } returns mockk()

                mockMvc.perform(post("/watch/toggle/1/NEW_ISSUE")
                    .principal(auth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))
            }
        }

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


