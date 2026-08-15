package com.github.search5.yona.web

import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUser
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.pullrequest.PullRequest
import com.github.search5.yona.domain.pullrequest.PullRequestMergeResult
import com.github.search5.yona.domain.pullrequest.PullRequestService
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

class PullRequestControllerSpec : DescribeSpec({
    val pullRequestService = mockk<PullRequestService>()
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()

    val pullRequestController = PullRequestController(
        pullRequestService,
        projectRepository,
        projectUserRepository,
        userRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(pullRequestController).build()

    beforeTest {
        io.mockk.clearMocks(pullRequestService, projectRepository, projectUserRepository, userRepository)
    }

    describe("PullRequestController 웹 API 테스트") {
        val project = Project(id = 1L, name = "TestProject", projectScope = ProjectScope.PRIVATE)
        val fromProject = Project(id = 2L, name = "ForkProject", projectScope = ProjectScope.PRIVATE)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val managerUser = User(id = 20L, loginId = "manageruser", name = "관리자유저")
        
        val memberRole = Role(id = RoleType.MEMBER.roleType)
        val managerRole = Role(id = RoleType.MANAGER.roleType)

        val projectUser = ProjectUser(id = 100L, user = user, project = project, role = memberRole)
        val projectManagerUser = ProjectUser(id = 101L, user = managerUser, project = project, role = managerRole)

        val pullRequest = PullRequest(
            id = 50L,
            title = "PR 제목",
            body = "PR 본문",
            toProject = project,
            fromProject = fromProject,
            toBranch = "master",
            fromBranch = "feature",
            contributor = user,
            state = State.OPEN,
            number = 1L
        )

        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
        val managerAuth = UsernamePasswordAuthenticationToken("manageruser", "password")

        describe("GET /api/projects/{projectId}/pullrequests") {
            it("비공개 프로젝트일 때 프로젝트 멤버라면 200 OK와 PR 목록을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequests(1L, State.OPEN) } returns listOf(pullRequest)

                mockMvc.perform(get("/api/projects/1/pullrequests").param("state", "OPEN").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].title").value("PR 제목"))
            }
        }

        describe("GET /api/projects/{projectId}/pullrequests/{number}") {
            it("PR 번호로 풀 리퀘스트 상세 정보를 조회할 수 있어야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest

                mockMvc.perform(get("/api/projects/1/pullrequests/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.title").value("PR 제목"))
            }
        }

        describe("POST /api/projects/{projectId}/pullrequests") {
            it("프로젝트 멤버인 유저가 새 PR을 제출하면 201 Created를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.createPullRequest("PR 제목", "PR 본문", 2L, 1L, "feature", "master", user) } returns pullRequest

                val jsonContent = """
                    {
                        "title": "PR 제목",
                        "body": "PR 본문",
                        "fromProjectId": 2,
                        "fromBranch": "feature",
                        "toBranch": "master"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/api/projects/1/pullrequests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isCreated)
            }
        }

        describe("PUT /api/projects/{projectId}/pullrequests/{number}") {
            it("작성자가 PR의 제목과 본문을 수정하면 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every { pullRequestService.updatePullRequest(50L, "수정된 PR 제목", "수정된 PR 본문") } returns pullRequest

                val jsonContent = """
                    {
                        "title": "수정된 PR 제목",
                        "body": "수정된 PR 본문"
                    }
                """.trimIndent()

                mockMvc.perform(
                    put("/api/projects/1/pullrequests/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
            }
        }

        describe("POST /api/projects/{projectId}/pullrequests/{number}/merge") {
            it("프로젝트 멤버가 PR 머지를 시도하면 200 OK와 머지 결과를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                
                val mergeResult = PullRequestMergeResult(pullRequest = pullRequest)
                every { pullRequestService.merge(50L, user) } returns mergeResult

                mockMvc.perform(
                    post("/api/projects/1/pullrequests/1/merge")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
            }
        }

        describe("POST /api/projects/{projectId}/pullrequests/{number}/reviewers") {
            it("로그인한 프로젝트 멤버가 리뷰어로 참여하면 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.addReviewer(50L, user) } returns Unit

                mockMvc.perform(
                    post("/api/projects/1/pullrequests/1/reviewers")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
            }
        }

        describe("DELETE /api/projects/{projectId}/pullrequests/{number}/reviewers") {
            it("로그인한 프로젝트 멤버가 리뷰어 해제를 요청하면 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.removeReviewer(50L, user) } returns Unit

                mockMvc.perform(
                    delete("/api/projects/1/pullrequests/1/reviewers")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
            }
        }

        describe("POST /api/projects/{projectId}/pullrequests/{number}/merge - 리뷰어 부족 케이스") {
            it("최소 리뷰어 수 미달 시 LackingReviewerException이 던져지면 400 Bad Request를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.merge(50L, user) } throws com.github.search5.yona.domain.pullrequest.LackingReviewerException("리뷰어 부족")

                mockMvc.perform(
                    post("/api/projects/1/pullrequests/1/merge")
                        .principal(userAuth)
                )
                    .andExpect(status().isBadRequest)
            }
        }
    }
})
