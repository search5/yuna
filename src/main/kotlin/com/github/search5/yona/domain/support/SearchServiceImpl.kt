package com.github.search5.yona.domain.support

import com.github.search5.yona.domain.enumeration.SearchType
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.organization.Organization
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.issue.IssueCommentRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.board.PostingCommentRepository
import com.github.search5.yona.domain.milestone.MilestoneRepository
import com.github.search5.yona.domain.pullrequest.ReviewCommentRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class SearchServiceImpl(
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val issueRepository: IssueRepository,
    private val postingRepository: PostingRepository,
    private val milestoneRepository: MilestoneRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val postingCommentRepository: PostingCommentRepository,
    private val reviewCommentRepository: ReviewCommentRepository
) : SearchService {

    private fun getAllowedProjectIds(user: User?): List<Long> {
        return if (user == null || user.id == null) {
            projectRepository.findPublicProjectIds()
        } else {
            projectRepository.findAllowedProjectIdsForUser(user.id!!)
        }
    }

    override fun searchInAll(keyword: String, searchType: SearchType, user: User?, pageable: Pageable): SearchResult {
        val allowedProjectIds = getAllowedProjectIds(user)
        if (allowedProjectIds.isEmpty()) {
            return SearchResult(keyword = keyword, searchType = searchType)
        }

        val processedKeyword = "%${keyword.lowercase()}%"
        val result = getSearchResultCounts(keyword, allowedProjectIds, user?.id)
        result.searchType = searchType
        result.updateSearchType()

        when (result.searchType) {
            SearchType.ISSUE -> result.issues = issueRepository.searchIssues(allowedProjectIds, processedKeyword, user?.id, pageable)
            SearchType.USER -> result.users = userRepository.searchUsers(processedKeyword, pageable)
            SearchType.PROJECT -> result.projects = projectRepository.searchProjects(allowedProjectIds, processedKeyword, pageable)
            SearchType.POST -> result.posts = postingRepository.searchPostings(allowedProjectIds, processedKeyword, pageable)
            SearchType.MILESTONE -> result.milestones = milestoneRepository.searchMilestones(allowedProjectIds, processedKeyword, pageable)
            SearchType.ISSUE_COMMENT -> result.issueComments = issueCommentRepository.searchIssueComments(allowedProjectIds, processedKeyword, pageable)
            SearchType.POST_COMMENT -> result.postComments = postingCommentRepository.searchPostingComments(allowedProjectIds, processedKeyword, pageable)
            SearchType.REVIEW -> result.reviews = reviewCommentRepository.searchReviewComments(allowedProjectIds, processedKeyword, pageable)
            else -> {}
        }

        return result
    }

    override fun searchInAProject(keyword: String, searchType: SearchType, user: User?, project: Project, pageable: Pageable): SearchResult {
        // 프로젝트 단일 검색 카운트 및 조회
        val result = SearchResult(keyword = keyword, searchType = searchType)
        val processedKeyword = "%${keyword.lowercase()}%"
        
        result.usersCount = userRepository.countSearchUsers(processedKeyword) // 유저는 전역 검색
        result.issuesCount = issueRepository.countSearchIssuesInProject(project, processedKeyword)
        result.postsCount = postingRepository.countSearchPostingsInProject(project, processedKeyword)
        result.milestonesCount = milestoneRepository.countSearchMilestonesInProject(project, processedKeyword)
        result.issueCommentsCount = issueCommentRepository.countSearchIssueCommentsInProject(project, processedKeyword)
        result.postCommentsCount = postingCommentRepository.countSearchPostingCommentsInProject(project, processedKeyword)
        result.reviewsCount = reviewCommentRepository.countSearchReviewCommentsInProject(project, processedKeyword)
        
        result.updateSearchType()

        when (result.searchType) {
            SearchType.ISSUE -> result.issues = issueRepository.searchIssuesInProject(project, processedKeyword, pageable)
            SearchType.USER -> result.users = userRepository.searchUsers(processedKeyword, pageable)
            SearchType.POST -> result.posts = postingRepository.searchPostingsInProject(project, processedKeyword, pageable)
            SearchType.MILESTONE -> result.milestones = milestoneRepository.searchMilestonesInProject(project, processedKeyword, pageable)
            SearchType.ISSUE_COMMENT -> result.issueComments = issueCommentRepository.searchIssueCommentsInProject(project, processedKeyword, pageable)
            SearchType.POST_COMMENT -> result.postComments = postingCommentRepository.searchPostingCommentsInProject(project, processedKeyword, pageable)
            SearchType.REVIEW -> result.reviews = reviewCommentRepository.searchReviewCommentsInProject(project, processedKeyword, pageable)
            else -> {}
        }

        return result
    }

    override fun searchInAGroup(keyword: String, searchType: SearchType, user: User?, organization: Organization, pageable: Pageable): SearchResult {
        val allowedProjectIds = getAllowedProjectIds(user)
        val groupProjectIds = projectRepository.findAllById(allowedProjectIds)
            .filter { it.organization?.id == organization.id }
            .map { it.id!! }

        if (groupProjectIds.isEmpty()) {
            return SearchResult(keyword = keyword, searchType = searchType)
        }

        val processedKeyword = "%${keyword.lowercase()}%"
        val result = getSearchResultCounts(keyword, groupProjectIds, user?.id)
        result.searchType = searchType
        result.updateSearchType()

        when (result.searchType) {
            SearchType.ISSUE -> result.issues = issueRepository.searchIssues(groupProjectIds, processedKeyword, user?.id, pageable)
            SearchType.USER -> result.users = userRepository.searchUsers(processedKeyword, pageable)
            SearchType.PROJECT -> result.projects = projectRepository.searchProjects(groupProjectIds, processedKeyword, pageable)
            SearchType.POST -> result.posts = postingRepository.searchPostings(groupProjectIds, processedKeyword, pageable)
            SearchType.MILESTONE -> result.milestones = milestoneRepository.searchMilestones(groupProjectIds, processedKeyword, pageable)
            SearchType.ISSUE_COMMENT -> result.issueComments = issueCommentRepository.searchIssueComments(groupProjectIds, processedKeyword, pageable)
            SearchType.POST_COMMENT -> result.postComments = postingCommentRepository.searchPostingComments(groupProjectIds, processedKeyword, pageable)
            SearchType.REVIEW -> result.reviews = reviewCommentRepository.searchReviewComments(groupProjectIds, processedKeyword, pageable)
            else -> {}
        }

        return result
    }

    private fun getSearchResultCounts(keyword: String, projectIds: List<Long>, userId: Long?): SearchResult {
        val processedKeyword = "%${keyword.lowercase()}%"
        return SearchResult(
            keyword = keyword,
            usersCount = userRepository.countSearchUsers(processedKeyword),
            projectsCount = projectRepository.countSearchProjects(projectIds, processedKeyword),
            issuesCount = issueRepository.countSearchIssues(projectIds, processedKeyword, userId),
            postsCount = postingRepository.countSearchPostings(projectIds, processedKeyword),
            milestonesCount = milestoneRepository.countSearchMilestones(projectIds, processedKeyword),
            issueCommentsCount = issueCommentRepository.countSearchIssueComments(projectIds, processedKeyword),
            postCommentsCount = postingCommentRepository.countSearchPostingComments(projectIds, processedKeyword),
            reviewsCount = reviewCommentRepository.countSearchReviewComments(projectIds, processedKeyword)
        )
    }
}
