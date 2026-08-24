import sys

with open("src/test/kotlin/com/github/search5/yona/domain/pullrequest/PullRequestServiceSpec.kt", "r") as f:
    lines = f.readlines()

last_idx = -1
for i, line in enumerate(lines):
    if line.strip() == "})":
        last_idx = i

if last_idx != -1:
    new_test = """
        describe("Coverage addition for PullRequestServiceImpl - makeMergeCommitMessage") {
            it("should handle fromProject != toProject and non-master branch") {
                val method = com.github.search5.yona.domain.pullrequest.PullRequestServiceImpl::class.java.getDeclaredMethod("makeMergeCommitMessage", com.github.search5.yona.domain.pullrequest.PullRequest::class.java, List::class.java)
                method.isAccessible = true
                
                val fromProject = com.github.search5.yona.domain.project.Project(id = 2L, name = "from", owner = "owner2")
                val toProject = com.github.search5.yona.domain.project.Project(id = 1L, name = "to", owner = "owner")
                val pr = com.github.search5.yona.domain.pullrequest.PullRequest(
                    title = "title", body = "body", toProject = toProject, fromProject = fromProject,
                    toBranch = "develop", fromBranch = "feature", contributor = user, state = com.github.search5.yona.domain.enumeration.State.OPEN, number = 1L
                ).apply { id = 1L }
                
                val result = method.invoke(pullRequestService, pr, emptyList<Any>()) as String
                result shouldContain "of owner2/from"
                result shouldContain "into 'develop'"
            }
        }
    """
    lines.insert(last_idx, new_test + "\n")
    with open("src/test/kotlin/com/github/search5/yona/domain/pullrequest/PullRequestServiceSpec.kt", "w") as f:
        f.writelines(lines)
