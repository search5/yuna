import sys

with open("src/test/kotlin/com/github/search5/yona/domain/pullrequest/PullRequestServiceSpec.kt", "r") as f:
    lines = f.readlines()

last_idx = -1
for i, line in enumerate(lines):
    if line.strip() == "})":
        last_idx = i

if last_idx != -1:
    new_test = """
        describe("Coverage addition for PullRequestServiceImpl") {
            it("addReviewer should return early if reviewer already exists") {
                val pr = com.github.search5.yona.domain.pullrequest.PullRequest(
                    title = "title", body = "body", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = user, state = com.github.search5.yona.domain.enumeration.State.OPEN, number = 1L
                ).apply { id = 1L }
                pr.reviewers.add(user)
                
                io.mockk.every { pullRequestRepository.findById(1L) } returns java.util.Optional.of(pr)
                val result = pullRequestService.addReviewer(1L, user)
                result.reviewers.size shouldBe 1
            }
            
            it("removeReviewer should return early if reviewer does not exist") {
                val pr = com.github.search5.yona.domain.pullrequest.PullRequest(
                    title = "title", body = "body", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = user, state = com.github.search5.yona.domain.enumeration.State.OPEN, number = 1L
                ).apply { id = 1L }
                
                io.mockk.every { pullRequestRepository.findById(1L) } returns java.util.Optional.of(pr)
                val result = pullRequestService.removeReviewer(1L, user)
                result.reviewers.size shouldBe 0
            }
            
            it("updatePullRequest should update only specified fields") {
                val pr = com.github.search5.yona.domain.pullrequest.PullRequest(
                    title = "title", body = "body", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = user, state = com.github.search5.yona.domain.enumeration.State.OPEN, number = 1L
                ).apply { id = 1L }
                
                io.mockk.every { pullRequestRepository.findById(1L) } returns java.util.Optional.of(pr)
                val result = pullRequestService.updatePullRequest(1L, null, null, null, null)
                result.title shouldBe "title"
            }
        }
    """
    lines.insert(last_idx, new_test + "\n")
    with open("src/test/kotlin/com/github/search5/yona/domain/pullrequest/PullRequestServiceSpec.kt", "w") as f:
        f.writelines(lines)
