with open("src/test/kotlin/com/github/search5/yona/domain/event/PullRequestMergeEventListenerSpec.kt", "r") as f:
    content = f.read()

content = content.replace(
    "every { pullRequestCommitRepository.findByPullRequest(pr) } returns listOf(commit1)",
    "every { pullRequestCommitRepository.findByPullRequest(pr) } returns listOf(commit1)\n            every { issueService.changeState(any(), any(), any()) } returns Unit"
)

with open("src/test/kotlin/com/github/search5/yona/domain/event/PullRequestMergeEventListenerSpec.kt", "w") as f:
    f.write(content)
