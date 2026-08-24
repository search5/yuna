with open("src/test/kotlin/com/github/search5/yona/domain/event/PullRequestMergeEventListenerSpec.kt", "r") as f:
    content = f.read()

content = content.replace(
    "every { issueService.changeState(any(), any(), any()) } returns Unit",
    "every { issueService.changeState(any(), any(), any()) } returns mockk(relaxed = true)"
)

with open("src/test/kotlin/com/github/search5/yona/domain/event/PullRequestMergeEventListenerSpec.kt", "w") as f:
    f.write(content)
