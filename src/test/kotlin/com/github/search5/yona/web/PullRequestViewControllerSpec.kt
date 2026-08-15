package com.github.search5.yona.web

import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.pullrequest.CommentThreadRepository
import com.github.search5.yona.domain.pullrequest.PullRequest
import com.github.search5.yona.domain.pullrequest.PullRequestMergeResult
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.domain.pullrequest.PullRequestService
import com.github.search5.yona.domain.vcs.PlayRepository
import com.github.search5.yona.domain.vcs.RepositoryService
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.pullrequest.PullRequestCommitRepository
import com.github.search5.yona.domain.issue.IssueRepository
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
import java.util.Optional

class PullRequestViewControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val pullRequestService = mockk<PullRequestService>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val repositoryService = mockk<RepositoryService>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val commentThreadRepository = mockk<CommentThreadRepository>()
    val pullRequestCommitRepository = mockk<PullRequestCommitRepository>()
    val issueRepository = mockk<IssueRepository>()

    val pullRequestViewController = PullRequestViewController(
        projectRepository,
        pullRequestService,
        pullRequestRepository,
        repositoryService,
        projectUserRepository,
        userRepository,
        commentThreadRepository,
        pullRequestCommitRepository,
        issueRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(pullRequestViewController)
        .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
        .build()

    beforeTest {
        io.mockk.clearMocks(
            projectRepository,
            pullRequestService,
            pullRequestRepository,
            repositoryService,
            projectUserRepository,
            userRepository,
            commentThreadRepository,
            pullRequestCommitRepository,
            issueRepository
        )
    }

    describe("PullRequestViewController 템플릿 연동 테스트") {
        val project = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PRIVATE)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val pullRequest = PullRequest(
            id = 50L,
            title = "PR 테스트 제목",
            body = "PR 본문",
            toProject = project,
            fromProject = project,
            toBranch = "master",
            fromBranch = "feature",
            contributor = user,
            state = State.OPEN,
            number = 1L
        )

        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
        val pageRequest = PageRequest.of(0, 20)

        describe("GET /{owner}/{projectName}/pulls") {
            it("비공개 프로젝트일 때 멤버라면 200 OK와 pullrequest/list 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestRepository.findByToProjectAndState(project, State.OPEN, any<Pageable>()) } returns PageImpl(listOf(pullRequest), pageRequest, 1)

                mockMvc.perform(get("/owner/TestProj/pulls").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("pullrequest/list"))
                    .andExpect(model().attributeExists("project", "prPage", "state"))
            }

            it("프로젝트 멤버가 아닐 경우 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

                mockMvc.perform(get("/owner/TestProj/pulls").principal(userAuth))
                    .andExpect(view().name("error/403"))
            }
        }

        describe("GET /{owner}/{projectName}/pull/{number}") {
            it("멤버라면 200 OK와 pullrequest/view 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                
                val mergeResult = PullRequestMergeResult(pullRequest = pullRequest)
                every { pullRequestService.attemptMerge(50L) } returns mergeResult
                every { commentThreadRepository.findByPullRequest(pullRequest) } returns emptyList()
                every { pullRequestCommitRepository.findByPullRequest(pullRequest) } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/pull/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("pullrequest/view"))
                    .andExpect(model().attributeExists("project", "pr", "mergeResult"))
            }
        }

        describe("GET /{owner}/{projectName}/pull/{number}/changes") {
            it("멤버라면 200 OK와 pullrequest/view 뷰를 반환하고 diffs 모델을 주입해야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                
                val mergeResult = PullRequestMergeResult(pullRequest = pullRequest)
                every { pullRequestService.attemptMerge(50L) } returns mergeResult
                every { pullRequestService.getDiff(pullRequest) } returns emptyList()
                every { commentThreadRepository.findByPullRequest(pullRequest) } returns emptyList()
                every { pullRequestCommitRepository.findByPullRequest(pullRequest) } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/pull/1/changes").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("pullrequest/view"))
                    .andExpect(model().attributeExists("project", "pr", "diffs", "mergeResult"))
            }
        }

        describe("GET /{owner}/{projectName}/pullRequest/{number}/changes/{commitId}") {
            it("멤버라면 200 OK와 특정 커밋 변경 사항을 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                
                val mergeResult = PullRequestMergeResult(pullRequest = pullRequest)
                every { pullRequestService.attemptMerge(50L) } returns mergeResult
                every { pullRequestService.getDiff(pullRequest, "abcdefg") } returns emptyList()
                every { commentThreadRepository.findByPullRequest(pullRequest) } returns emptyList()
                every { pullRequestCommitRepository.findByPullRequest(pullRequest) } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/pullRequest/1/changes/abcdefg").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("pullrequest/view"))
                    .andExpect(model().attributeExists("project", "pr", "diffs", "commitId"))
            }
        }

        describe("GET /{owner}/{projectName}/pull/new") {
            it("멤버라면 200 OK와 pullrequest/create 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                
                val playRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns listOf("refs/heads/master", "refs/heads/feature")
                every { playRepo.getDefaultBranch() } returns "refs/heads/master"

                mockMvc.perform(get("/owner/TestProj/pull/new").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("pullrequest/create"))
                    .andExpect(model().attributeExists("project", "branches", "defaultBranch"))
            }
        }
    }
})
