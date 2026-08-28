package com.github.search5.yona.web

import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.pullrequest.PullRequest
import com.github.search5.yona.domain.pullrequest.PullRequestMergeResult
import com.github.search5.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

// yona-wiki P3-02 Step5 — PullRequestApiController(/api/v1/projects/{owner}/{project}/pull-requests)도
// IssueRestApiController와 동일하게 owner/project 이름을 숫자 projectId로 바꿔 기존
// PullRequestController에 위임하는 얇은 어댑터다. 이 스펙은 위임/404 처리만 검증한다 — 머지/리뷰어
// 등록 자체의 업무 로직(Git 머지, 리뷰어 수 검증 등)은 PullRequestControllerSpec/
// PullRequestServiceSpec에서 이미 검증됨. 스코프 토큰 403은 별도 통합테스트에서 다룬다.
class PullRequestApiControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val pullRequestController = mockk<PullRequestController>()

    val controller = PullRequestApiController(projectRepository, pullRequestController)
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    beforeTest {
        clearMocks(projectRepository, pullRequestController)
    }

    val project = Project(id = 1L, owner = "yona", name = "yuna", projectScope = ProjectScope.PUBLIC)
    val contributor = User(id = 10L, loginId = "contributor", name = "기여자")

    describe("GET /api/v1/projects/{owner}/{project}/pull-requests") {
        it("프로젝트가 없으면 404를 반환한다") {
            every { projectRepository.findByOwnerAndName("yona", "unknown") } returns Optional.empty()

            mockMvc.perform(get("/api/v1/projects/yona/unknown/pull-requests"))
                .andExpect(status().isNotFound)
        }

        it("PullRequestController.getPullRequests에 위임한다") {
            val pr = PullRequest(id = 3L, number = 1L, title = "PR", fromProject = project, toProject = project, contributor = contributor)
            every { projectRepository.findByOwnerAndName("yona", "yuna") } returns Optional.of(project)
            every { pullRequestController.getPullRequests(1L, null, any()) } returns ResponseEntity.ok(listOf(pr))

            mockMvc.perform(get("/api/v1/projects/yona/yuna/pull-requests"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].id").value(3))

            verify(exactly = 1) { pullRequestController.getPullRequests(1L, null, any()) }
        }
    }

    describe("POST /api/v1/projects/{owner}/{project}/pull-requests") {
        it("PullRequestController.createPullRequest에 위임한다") {
            val pr = PullRequest(id = 3L, number = 1L, title = "새 PR", fromProject = project, toProject = project, contributor = contributor)
            every { projectRepository.findByOwnerAndName("yona", "yuna") } returns Optional.of(project)
            every { pullRequestController.createPullRequest(1L, any(), any()) } returns ResponseEntity.status(HttpStatus.CREATED).body(pr)

            mockMvc.perform(
                post("/api/v1/projects/yona/yuna/pull-requests")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"새 PR","body":"내용","fromProjectId":1,"fromBranch":"feature","toBranch":"main"}""")
            ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").value(3))
        }
    }

    describe("GET /api/v1/projects/{owner}/{project}/pull-requests/{number}") {
        it("PullRequestController.getPullRequest에 위임한다") {
            val pr = PullRequest(id = 3L, number = 1L, title = "PR", fromProject = project, toProject = project, contributor = contributor)
            every { projectRepository.findByOwnerAndName("yona", "yuna") } returns Optional.of(project)
            every { pullRequestController.getPullRequest(1L, 1L, any()) } returns ResponseEntity.ok(pr)

            mockMvc.perform(get("/api/v1/projects/yona/yuna/pull-requests/1"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.number").value(1))
        }
    }

    describe("POST /api/v1/projects/{owner}/{project}/pull-requests/{number}/merge") {
        it("PullRequestController.mergePullRequest에 위임한다") {
            val pr = PullRequest(id = 3L, number = 1L, title = "PR", fromProject = project, toProject = project, contributor = contributor)
            val mergeResult = PullRequestMergeResult(pullRequest = pr)
            every { projectRepository.findByOwnerAndName("yona", "yuna") } returns Optional.of(project)
            every { pullRequestController.mergePullRequest(1L, 1L, any()) } returns ResponseEntity.ok(mergeResult)

            mockMvc.perform(post("/api/v1/projects/yona/yuna/pull-requests/1/merge"))
                .andExpect(status().isOk)

            verify(exactly = 1) { pullRequestController.mergePullRequest(1L, 1L, any()) }
        }
    }

    describe("POST /api/v1/projects/{owner}/{project}/pull-requests/{number}/reviewers") {
        it("PullRequestController.addReviewer에 위임한다") {
            every { projectRepository.findByOwnerAndName("yona", "yuna") } returns Optional.of(project)
            every { pullRequestController.addReviewer(1L, 1L, any()) } returns ResponseEntity.ok().build()

            mockMvc.perform(post("/api/v1/projects/yona/yuna/pull-requests/1/reviewers"))
                .andExpect(status().isOk)

            verify(exactly = 1) { pullRequestController.addReviewer(1L, 1L, any()) }
        }
    }
})
