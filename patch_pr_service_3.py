import sys

with open("src/test/kotlin/com/github/search5/yona/domain/pullrequest/PullRequestServiceSpec.kt", "r") as f:
    lines = f.readlines()

last_idx = -1
for i, line in enumerate(lines):
    if line.strip() == "})":
        last_idx = i

if last_idx != -1:
    new_test = """
        describe("Coverage addition for PullRequestServiceImpl - getDiff non-git") {
            it("should handle non-GitRepository in getDiff") {
                val pr = com.github.search5.yona.domain.pullrequest.PullRequest(
                    title = "title", body = "body", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = user, state = com.github.search5.yona.domain.enumeration.State.OPEN, number = 1L
                ).apply { id = 1L }
                
                // create a mock that is not GitRepository
                val playRepoMock = io.mockk.mockk<com.github.search5.yona.domain.vcs.PlayRepository>()
                io.mockk.every { repositoryService.getRepository(project) } returns playRepoMock
                io.mockk.every { playRepoMock.getDiff(any<String>(), any<String>()) } returns emptyList<Any>()
                
                val result = pullRequestService.getDiff(pr)
                result.size shouldBe 0
            }
        }
    """
    lines.insert(last_idx, new_test + "\n")
    with open("src/test/kotlin/com/github/search5/yona/domain/pullrequest/PullRequestServiceSpec.kt", "w") as f:
        f.writelines(lines)
