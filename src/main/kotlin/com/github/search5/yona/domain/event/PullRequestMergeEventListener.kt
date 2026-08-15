package com.github.search5.yona.domain.event

import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.domain.pullrequest.PullRequestCommitRepository
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.issue.IssueService
import com.github.search5.yona.domain.enumeration.State
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional

@Component
class PullRequestMergeEventListener(
    private val pullRequestRepository: PullRequestRepository,
    private val pullRequestCommitRepository: PullRequestCommitRepository,
    private val issueRepository: IssueRepository,
    private val issueService: IssueService
) {
    private val logger = LoggerFactory.getLogger(PullRequestMergeEventListener::class.java)

    // 이슈 자동 닫기 정규식 패턴 (대소문자 구분 없이 close(s/d), fix(es/ed), resolve(s/d) #숫자)
    private val closePattern = "(?i)(?:close[s|d]?|fix[e[s|d]]?|resolve[s|d]?)\\s+#(\\d+)".toRegex()

    @Async("taskExecutor")
    @EventListener
    @Transactional
    fun handlePullRequestMergeEvent(event: PullRequestMergeEvent) {
        logger.info("Handling PullRequestMergeEvent asynchronously for PR ID: ${event.pullRequestId} by user: ${event.sender.loginId}")
        
        val pullRequestOptional = pullRequestRepository.findById(event.pullRequestId)
        if (!pullRequestOptional.isPresent) {
            logger.warn("PullRequest with ID ${event.pullRequestId} not found")
            return
        }

        val pullRequest = pullRequestOptional.get()
        pullRequest.isMerging = true
        pullRequest.state = State.MERGED
        pullRequestRepository.save(pullRequest)

        logger.info("[PR MERGE] Successfully updated merge state for PR ID: ${event.pullRequestId}")

        try {
            closeReferredIssues(pullRequest, event.sender.loginId)
        } catch (e: Exception) {
            logger.error("Failed to automatically close issues for PR ID: ${event.pullRequestId}", e)
        }
    }

    fun closeReferredIssues(pullRequest: com.github.search5.yona.domain.pullrequest.PullRequest, senderLoginId: String) {
        val project = pullRequest.toProject
        val textsToSearch = mutableListOf<String>()

        textsToSearch.add(pullRequest.title)
        pullRequest.body?.let { textsToSearch.add(it) }

        val commits = pullRequestCommitRepository.findByPullRequest(pullRequest)
        for (commit in commits) {
            textsToSearch.add(commit.commitMessage)
        }

        val issueNumbers = mutableSetOf<Long>()
        for (text in textsToSearch) {
            closePattern.findAll(text).forEach { matchResult ->
                val numberStr = matchResult.groups[1]?.value
                if (numberStr != null) {
                    numberStr.toLongOrNull()?.let { issueNumbers.add(it) }
                }
            }
        }

        if (issueNumbers.isEmpty()) {
            return
        }

        logger.info("[PR MERGE] Found referred issue numbers to close: $issueNumbers for PR ID: ${pullRequest.id}")

        for (number in issueNumbers) {
            val issue = issueRepository.findByProjectAndNumber(project, number)
            if (issue != null) {
                if (issue.state != State.CLOSED) {
                    logger.info("[PR MERGE] Automatically closing issue #${issue.number} (ID: ${issue.id})")
                    issueService.changeState(issue.id!!, State.CLOSED, senderLoginId)
                } else {
                    logger.info("[PR MERGE] Issue #${issue.number} is already CLOSED")
                }
            } else {
                logger.warn("[PR MERGE] Issue #${number} not found in project: ${project.name}")
            }
        }
    }

    @Async("taskExecutor")
    @EventListener
    @Transactional
    fun handleRelatedPullRequestMergeEvent(event: RelatedPullRequestMergeEvent) {
        logger.info("Handling RelatedPullRequestMergeEvent asynchronously for project: ${event.project.name}, branch: ${event.branch}")
        
        val relatedPullRequests = pullRequestRepository.findRelatedPullRequests(event.project, event.branch)
        for (pullRequest in relatedPullRequests) {
            pullRequest.isMerging = true
            pullRequestRepository.save(pullRequest)
        }
        
        logger.info("[PR MERGE] Successfully updated ${relatedPullRequests.size} related PRs status for project: ${event.project.name}, branch: ${event.branch}")
    }
}
