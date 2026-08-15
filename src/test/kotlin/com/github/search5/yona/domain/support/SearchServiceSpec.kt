package com.github.search5.yona.domain.support

import com.github.search5.yona.domain.enumeration.SearchType
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.milestone.MilestoneRepository
import com.github.search5.yona.domain.issue.IssueCommentRepository
import com.github.search5.yona.domain.board.PostingCommentRepository
import com.github.search5.yona.domain.pullrequest.ReviewCommentRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

class SearchServiceSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val milestoneRepository = mockk<MilestoneRepository>()
    val issueCommentRepository = mockk<IssueCommentRepository>()
    val postingCommentRepository = mockk<PostingCommentRepository>()
    val reviewCommentRepository = mockk<ReviewCommentRepository>()

    val searchService = SearchServiceImpl(
        userRepository,
        projectRepository,
        issueRepository,
        postingRepository,
        milestoneRepository,
        issueCommentRepository,
        postingCommentRepository,
        reviewCommentRepository
    )

    describe("SearchService 비즈니스 로직 테스트") {
        val loginUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val pageable = PageRequest.of(0, 20)

        it("전역 검색 시 사용자가 접근 가능한 프로젝트의 이슈들을 검색해야 한다") {
            every { projectRepository.findAllowedProjectIdsForUser(10L) } returns listOf(1L, 2L)
            
            // counts mock
            every { userRepository.countSearchUsers("%test%") } returns 0
            every { projectRepository.countSearchProjects(listOf(1L, 2L), "%test%") } returns 0
            every { issueRepository.countSearchIssues(listOf(1L, 2L), "%test%") } returns 1
            every { postingRepository.countSearchPostings(listOf(1L, 2L), "%test%") } returns 0
            every { milestoneRepository.countSearchMilestones(listOf(1L, 2L), "%test%") } returns 0
            every { issueCommentRepository.countSearchIssueComments(listOf(1L, 2L), "%test%") } returns 0
            every { postingCommentRepository.countSearchPostingComments(listOf(1L, 2L), "%test%") } returns 0
            every { reviewCommentRepository.countSearchReviewComments(listOf(1L, 2L), "%test%") } returns 0

            // search result mock
            val expectedIssues: Page<com.github.search5.yona.domain.issue.Issue> = PageImpl(emptyList())
            every { issueRepository.searchIssues(listOf(1L, 2L), "%test%", pageable) } returns expectedIssues

            val result = searchService.searchInAll("test", SearchType.AUTO, loginUser, pageable)

            result.issuesCount shouldBe 1
            result.searchType shouldBe SearchType.ISSUE
        }

        it("단일 프로젝트 검색 시 해당 프로젝트 내부의 게시글을 검색해야 한다") {
            val project = Project(id = 1L, name = "TestProj", owner = "owner")

            // counts mock
            every { userRepository.countSearchUsers("%board%") } returns 0
            every { issueRepository.countSearchIssuesInProject(project, "%board%") } returns 0
            every { postingRepository.countSearchPostingsInProject(project, "%board%") } returns 2
            every { milestoneRepository.countSearchMilestonesInProject(project, "%board%") } returns 0
            every { issueCommentRepository.countSearchIssueCommentsInProject(project, "%board%") } returns 0
            every { postingCommentRepository.countSearchPostingCommentsInProject(project, "%board%") } returns 0
            every { reviewCommentRepository.countSearchReviewCommentsInProject(project, "%board%") } returns 0

            // search result mock
            val expectedPosts: Page<com.github.search5.yona.domain.board.Posting> = PageImpl(emptyList())
            every { postingRepository.searchPostingsInProject(project, "%board%", pageable) } returns expectedPosts

            val result = searchService.searchInAProject("board", SearchType.POST, loginUser, project, pageable)

            result.postsCount shouldBe 2
            result.searchType shouldBe SearchType.POST
        }
    }
})
