import sys

with open("src/test/kotlin/com/github/search5/yona/domain/pullrequest/PullRequestServiceSpec.kt", "r") as f:
    lines = f.readlines()

last_idx = -1
for i, line in enumerate(lines):
    if line.strip() == "})":
        last_idx = i

if last_idx != -1:
    new_test = """
        describe("Coverage addition for PullRequestServiceImpl - getMergedTreeIfReusable") {
            it("should handle exception in getMergedTreeIfReusable") {
                val method = com.github.search5.yona.domain.pullrequest.PullRequestServiceImpl::class.java.getDeclaredMethod("getMergedTreeIfReusable", org.eclipse.jgit.lib.Repository::class.java, org.eclipse.jgit.lib.ObjectId::class.java, org.eclipse.jgit.lib.ObjectId::class.java, com.github.search5.yona.domain.pullrequest.PullRequest::class.java)
                method.isAccessible = true
                
                val mockRepo = io.mockk.mockk<org.eclipse.jgit.lib.Repository>()
                io.mockk.every { mockRepo.findRef(any()) } throws RuntimeException("Test exception")
                
                val pr = com.github.search5.yona.domain.pullrequest.PullRequest(
                    title = "title", body = "body", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = user, state = com.github.search5.yona.domain.enumeration.State.OPEN, number = 1L
                ).apply { id = 1L }
                
                val zero = org.eclipse.jgit.lib.ObjectId.zeroId()
                val result = method.invoke(pullRequestService, mockRepo, zero, zero, pr)
                result shouldBe null
            }
            
            it("should handle null ref in getMergedTreeIfReusable") {
                val method = com.github.search5.yona.domain.pullrequest.PullRequestServiceImpl::class.java.getDeclaredMethod("getMergedTreeIfReusable", org.eclipse.jgit.lib.Repository::class.java, org.eclipse.jgit.lib.ObjectId::class.java, org.eclipse.jgit.lib.ObjectId::class.java, com.github.search5.yona.domain.pullrequest.PullRequest::class.java)
                method.isAccessible = true
                
                val mockRepo = io.mockk.mockk<org.eclipse.jgit.lib.Repository>()
                io.mockk.every { mockRepo.findRef(any()) } returns null
                
                val pr = com.github.search5.yona.domain.pullrequest.PullRequest(
                    title = "title", body = "body", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = user, state = com.github.search5.yona.domain.enumeration.State.OPEN, number = 1L
                ).apply { id = 1L }
                
                val zero = org.eclipse.jgit.lib.ObjectId.zeroId()
                val result = method.invoke(pullRequestService, mockRepo, zero, zero, pr)
                result shouldBe null
            }
        }
    """
    lines.insert(last_idx, new_test + "\n")
    with open("src/test/kotlin/com/github/search5/yona/domain/pullrequest/PullRequestServiceSpec.kt", "w") as f:
        f.writelines(lines)
