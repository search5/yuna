package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.organization.OrganizationUserRepository
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
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.pullrequest.ReviewCommentRepository
import com.github.search5.yona.domain.pullrequest.CommitCommentRepository
import com.github.search5.yona.domain.milestone.MilestoneRepository

class PullRequestControllerSpec : DescribeSpec({
    val pullRequestService = mockk<PullRequestService>()
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val pullRequestEventRepository = mockk<com.github.search5.yona.domain.pullrequest.PullRequestEventRepository>()
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

    val pullRequestController = PullRequestController(
        pullRequestService,
        projectRepository,
        projectUserRepository,
        userRepository,
        pullRequestEventRepository,
        accessControl
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(pullRequestController).build()

    beforeTest {
        io.mockk.clearMocks(pullRequestService, projectRepository, projectUserRepository, userRepository, pullRequestEventRepository)
    }

    describe("PullRequestController 웹 API 테스트") {
        val project = Project(id = 1L, name = "TestProject", projectScope = ProjectScope.PRIVATE)
        val fromProject = Project(id = 2L, name = "ForkProject", projectScope = ProjectScope.PRIVATE)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val managerUser = User(id = 20L, loginId = "manageruser", name = "관리자유저")
        
        val memberRole = Role(id = RoleType.MEMBER.roleType)
        val managerRole = Role(id = RoleType.MANAGER.roleType)

        // isMemberOf()/isManagerOf()는 project.id/role.id만 보고 .user는 읽지 않는다 — 여기서 user 자신을
        // .user로 넣으면 PullRequest.contributor로 그대로 직렬화될 때 user->projectUsers->user 순환 참조로
        // Jackson이 무한 재귀에 빠진다(IssueSharer.kt의 기존 @JsonIgnore 사례와 동일한 문제군). 멤버십 판정에는
        // 영향 없는 더미 User로 배선해 순환을 끊는다.
        val projectUser = ProjectUser(id = 100L, user = User(id = 999_910L, loginId = "_membership_placeholder"), project = project, role = memberRole)
        val projectManagerUser = ProjectUser(id = 101L, user = User(id = 999_911L, loginId = "_membership_placeholder"), project = project, role = managerRole)
        user.projectUsers.add(projectUser)
        managerUser.projectUsers.add(projectManagerUser)

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

        describe("GET /api/projects/{projectId}/pullrequests/{number}/timeline") {
            it("PR의 변경 이력을 시간순으로 반환해야 한다") {
                val prEvent = com.github.search5.yona.domain.pullrequest.PullRequestEvent(
                    id = 1L, pullRequest = pullRequest,
                    eventType = com.github.search5.yona.domain.enumeration.EventType.PULL_REQUEST_STATE_CHANGED,
                    oldValue = "OPEN", newValue = "MERGED"
                )
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pullRequest) } returns listOf(prEvent)

                mockMvc.perform(get("/api/projects/1/pullrequests/1/timeline").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].newValue").value("MERGED"))
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
                every { pullRequestService.updatePullRequest(50L, "수정된 PR 제목", "수정된 PR 본문", "feature", "master") } returns pullRequest

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

                verify(exactly = 1) { pullRequestService.updatePullRequest(50L, "수정된 PR 제목", "수정된 PR 본문", "feature", "master") }
            }

            // yona PullRequest.updateWith()의 from/toBranch 재할당 대응 (P1-68).
            it("요청에 fromBranch/toBranch가 포함되면 브랜치 재할당까지 서비스에 전달해야 한다") {
                val rebranched = PullRequest(
                    id = 50L, title = "수정된 PR 제목", body = "수정된 PR 본문",
                    toProject = project, fromProject = fromProject,
                    toBranch = "release", fromBranch = "hotfix",
                    contributor = user, state = State.OPEN, number = 1L
                )

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every { pullRequestService.updatePullRequest(50L, "수정된 PR 제목", "수정된 PR 본문", "hotfix", "release") } returns rebranched

                val jsonContent = """
                    {
                        "title": "수정된 PR 제목",
                        "body": "수정된 PR 본문",
                        "fromBranch": "hotfix",
                        "toBranch": "release"
                    }
                """.trimIndent()

                mockMvc.perform(
                    put("/api/projects/1/pullrequests/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.fromBranch").value("hotfix"))
                    .andExpect(jsonPath("$.toBranch").value("release"))
            }

            it("브랜치를 재할당했을 때 동일 조합의 PR이 이미 열려있으면 409 Conflict를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every {
                    pullRequestService.updatePullRequest(50L, "수정된 PR 제목", "수정된 PR 본문", "hotfix", "release")
                } throws com.github.search5.yona.domain.pullrequest.DuplicatedPullRequestException("중복된 PR")

                val jsonContent = """
                    {
                        "title": "수정된 PR 제목",
                        "body": "수정된 PR 본문",
                        "fromBranch": "hotfix",
                        "toBranch": "release"
                    }
                """.trimIndent()

                mockMvc.perform(
                    put("/api/projects/1/pullrequests/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isConflict)
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

        // yona AccessControl.isProjectResourceAllowed()의 PULL_REQUEST Operation.ACCEPT 분기 대응 (P1-78).
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

            it("PUBLIC 프로젝트여도 멤버가 아니면 리뷰어로 등록할 수 없어야 한다(인가 우회 방지)") {
                val publicProject = Project(id = 2L, name = "PublicProject", projectScope = ProjectScope.PUBLIC)
                val otherUser = User(id = 30L, loginId = "otheruser", name = "외부유저")
                val otherAuth = UsernamePasswordAuthenticationToken("otheruser", "password")

                every { projectRepository.findById(2L) } returns Optional.of(publicProject)
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.existsByProjectIdAndUserId(2L, 30L) } returns false

                mockMvc.perform(
                    post("/api/projects/2/pullrequests/1/reviewers")
                        .principal(otherAuth)
                )
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { pullRequestService.addReviewer(any(), any()) }
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

            it("PUBLIC 프로젝트여도 멤버가 아니면 리뷰어를 해제할 수 없어야 한다(인가 우회 방지)") {
                val publicProject = Project(id = 2L, name = "PublicProject", projectScope = ProjectScope.PUBLIC)
                val otherUser = User(id = 30L, loginId = "otheruser", name = "외부유저")
                val otherAuth = UsernamePasswordAuthenticationToken("otheruser", "password")

                every { projectRepository.findById(2L) } returns Optional.of(publicProject)
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.existsByProjectIdAndUserId(2L, 30L) } returns false

                mockMvc.perform(
                    delete("/api/projects/2/pullrequests/1/reviewers")
                        .principal(otherAuth)
                )
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { pullRequestService.removeReviewer(any(), any()) }
            }
        }

        describe("DELETE /api/projects/{projectId}/pullrequests/{number}/fromBranch") {
            it("PR 작성자가 원본 브랜치 삭제를 요청하면 200 OK와 갱신된 PR을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.deleteFromBranch(50L) } returns pullRequest

                mockMvc.perform(
                    delete("/api/projects/1/pullrequests/1/fromBranch")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
            }
        }

        describe("POST /api/projects/{projectId}/pullrequests/{number}/fromBranch") {
            it("PR 작성자가 원본 브랜치 복원을 요청하면 200 OK와 갱신된 PR을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.restoreFromBranch(50L) } returns pullRequest

                mockMvc.perform(
                    post("/api/projects/1/pullrequests/1/fromBranch")
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
