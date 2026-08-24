import sys

with open("src/test/kotlin/com/github/search5/yona/domain/pullrequest/PullRequestServiceSpec.kt", "r") as f:
    lines = f.readlines()

last_idx = -1
for i, line in enumerate(lines):
    if line.strip() == "})":
        last_idx = i

if last_idx != -1:
    new_test = """
        describe("Coverage addition for PullRequestServiceImpl - suggestTitleAndBody") {
            it("should handle null message via reflection") {
                val method = com.github.search5.yona.domain.pullrequest.PullRequestServiceImpl::class.java.getDeclaredMethod("suggestTitleAndBody", List::class.java)
                method.isAccessible = true
                
                val mockCommit = io.mockk.mockk<com.github.search5.yona.domain.vcs.GitCommit>()
                io.mockk.every { mockCommit.getMessage() } returns null
                
                // single commit
                val singleResult = method.invoke(pullRequestService, listOf(mockCommit)) as Pair<String?, String?>
                singleResult.first shouldBe ""
                
                // multiple commits
                val multiResult = method.invoke(pullRequestService, listOf(mockCommit, mockCommit)) as Pair<String?, String?>
                multiResult.second shouldBe "\\n"
            }
        }
    """
    lines.insert(last_idx, new_test + "\n")
    with open("src/test/kotlin/com/github/search5/yona/domain/pullrequest/PullRequestServiceSpec.kt", "w") as f:
        f.writelines(lines)
