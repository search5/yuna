with open("src/test/kotlin/com/github/search5/yona/domain/event/PullRequestMergeEventListenerSpec.kt", "r") as f:
    content = f.read()

imports = "import com.github.search5.yona.domain.pullrequest.PullRequestCommit\n"
if "import com.github.search5.yona.domain.pullrequest.PullRequestCommit\n" not in content:
    content = content.replace("import com.github.search5.yona.domain.pullrequest.PullRequest\n", imports + "import com.github.search5.yona.domain.pullrequest.PullRequest\n")

new_tests = """
    describe("PullRequestMergeEventListener.closeReferredIssues") {
        it("should close issues found in PR title, body, and commit messages") {
            val pr = pr(300L, conflict = false)
            pr.title = "fix #1"
            pr.body = "close #2"
            
            val commit1 = mockk<PullRequestCommit>()
            every { commit1.commitMessage } returns "resolve #3"
            every { pullRequestCommitRepository.findByPullRequest(pr) } returns listOf(commit1)
            
            val issue1 = mockk<com.github.search5.yona.domain.issue.Issue>(relaxed = true)
            every { issue1.id } returns 101L
            every { issue1.number } returns 1L
            every { issue1.state } returns State.OPEN
            
            val issue2 = mockk<com.github.search5.yona.domain.issue.Issue>(relaxed = true)
            every { issue2.id } returns 102L
            every { issue2.number } returns 2L
            every { issue2.state } returns State.OPEN
            
            val issue3 = mockk<com.github.search5.yona.domain.issue.Issue>(relaxed = true)
            every { issue3.id } returns 103L
            every { issue3.number } returns 3L
            every { issue3.state } returns State.CLOSED
            
            every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue1
            every { issueRepository.findByProjectAndNumber(project, 2L) } returns issue2
            every { issueRepository.findByProjectAndNumber(project, 3L) } returns issue3
            every { issueRepository.findByProjectAndNumber(project, 4L) } returns null
            
            listener.closeReferredIssues(pr, "pusher")
            
            verify(exactly = 1) { issueService.changeState(101L, State.CLOSED, "pusher") }
            verify(exactly = 1) { issueService.changeState(102L, State.CLOSED, "pusher") }
            verify(exactly = 0) { issueService.changeState(103L, any(), any()) }
        }
        
        it("should return early if no issue numbers are found") {
            val pr = pr(301L, conflict = false)
            pr.title = "just a PR"
            pr.body = null
            every { pullRequestCommitRepository.findByPullRequest(pr) } returns emptyList()
            
            listener.closeReferredIssues(pr, "pusher")
            
            verify(exactly = 0) { issueRepository.findByProjectAndNumber(any(), any()) }
        }
    }
"""

null_tests = """
        it("should return if PR not found") {
            every { pullRequestRepository.findById(999L) } returns java.util.Optional.empty()
            listener.handlePullRequestMergeEvent(PullRequestMergeEvent(pullRequestId = 999L, sender = sender, isNewPullRequest = false))
            verify(exactly = 0) { pullRequestRepository.save(any()) }
        }
        
        it("should handle exceptions when closing issues") {
            val mergedPr = pr(201L, conflict = false)
            every { pullRequestRepository.findById(201L) } returns java.util.Optional.of(mergedPr)
            every { pullRequestRepository.save(any()) } answers { firstArg() }
            every { pullRequestEventRepository.save(any()) } answers { firstArg() }
            every { pullRequestCommitRepository.findByPullRequest(mergedPr) } throws RuntimeException("DB Error")
            
            listener.handlePullRequestMergeEvent(PullRequestMergeEvent(pullRequestId = 201L, sender = sender, isNewPullRequest = false))
            
            mergedPr.state shouldBe State.MERGED
        }
"""

related_null = """
        it("should ignore PRs without ID") {
            val noIdPr = pr(100L, conflict = false)
            noIdPr.id = null
            every { pullRequestRepository.findRelatedPullRequests(project, "feature") } returns listOf(noIdPr)
            
            listener.handleRelatedPullRequestMergeEvent(RelatedPullRequestMergeEvent(project, "feature", sender))
            
            verify(exactly = 0) { pullRequestRepository.save(any()) }
        }
        
        it("should handle when contributor is the same as sender for notification") {
            val relatedPr = pr(105L, conflict = false)
            relatedPr.contributor = sender
            every { pullRequestRepository.findRelatedPullRequests(project, "feature") } returns listOf(relatedPr)
            every { pullRequestRepository.save(any()) } answers { firstArg() }
            every { pullRequestService.processMergeCheck(105L, sender, false) } answers {
                relatedPr.isConflict = true
                PullRequestMergeResult(pullRequest = relatedPr)
            }
            every { pullRequestRepository.findById(105L) } returns java.util.Optional.of(relatedPr)
            
            listener.handleRelatedPullRequestMergeEvent(RelatedPullRequestMergeEvent(project, "feature", sender))
            
            verify(exactly = 1) { eventPublisher.publishEvent(any<NotificationEvent>()) }
        }
"""

content = content.replace("describe(\"PullRequestMergeEventListener.handlePullRequestMergeEvent\") {", "describe(\"PullRequestMergeEventListener.handlePullRequestMergeEvent\") {\n" + null_tests)
content = content.replace("describe(\"PullRequestMergeEventListener.handleRelatedPullRequestMergeEvent\") {", new_tests + "\n    describe(\"PullRequestMergeEventListener.handleRelatedPullRequestMergeEvent\") {\n" + related_null)

with open("src/test/kotlin/com/github/search5/yona/domain/event/PullRequestMergeEventListenerSpec.kt", "w") as f:
    f.write(content)
