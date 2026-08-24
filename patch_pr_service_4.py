import sys

with open("src/test/kotlin/com/github/search5/yona/domain/pullrequest/PullRequestServiceSpec.kt", "r") as f:
    lines = f.readlines()

last_idx = -1
for i, line in enumerate(lines):
    if line.strip() == "})":
        last_idx = i

if last_idx != -1:
    new_test = """
        describe("Coverage addition for PullRequestServiceImpl - deleteFromBranch exceptions") {
            it("should throw if not merged in deleteFromBranch") {
                val pr = com.github.search5.yona.domain.pullrequest.PullRequest(
                    title = "title", body = "body", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = user, state = com.github.search5.yona.domain.enumeration.State.OPEN, number = 1L
                ).apply { id = 1L }
                io.mockk.every { pullRequestRepository.findById(1L) } returns java.util.Optional.of(pr)
                
                io.kotest.assertions.throwables.shouldThrow<com.github.search5.yona.domain.vcs.InvalidBranchOperationException> {
                    pullRequestService.deleteFromBranch(1L)
                }
            }
            
            it("should throw if branch not found in deleteFromBranch") {
                val pr = com.github.search5.yona.domain.pullrequest.PullRequest(
                    title = "title", body = "body", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = user, state = com.github.search5.yona.domain.enumeration.State.MERGED, number = 1L
                ).apply { id = 1L }
                io.mockk.every { pullRequestRepository.findById(1L) } returns java.util.Optional.of(pr)
                
                val playRepoMock = io.mockk.mockk<com.github.search5.yona.domain.vcs.PlayRepository>()
                io.mockk.every { repositoryService.getRepository(project) } returns playRepoMock
                io.mockk.every { playRepoMock.getBranches() } returns emptyList()
                
                io.kotest.assertions.throwables.shouldThrow<com.github.search5.yona.domain.vcs.InvalidBranchOperationException> {
                    pullRequestService.deleteFromBranch(1L)
                }
            }
        }
    """
    lines.insert(last_idx, new_test + "\n")
    with open("src/test/kotlin/com/github/search5/yona/domain/pullrequest/PullRequestServiceSpec.kt", "w") as f:
        f.writelines(lines)
