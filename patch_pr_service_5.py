import sys

with open("src/test/kotlin/com/github/search5/yona/domain/pullrequest/PullRequestServiceSpec.kt", "r") as f:
    lines = f.readlines()

last_idx = -1
for i, line in enumerate(lines):
    if line.strip() == "})":
        last_idx = i

if last_idx != -1:
    new_test = """
        describe("Coverage addition for PullRequestServiceImpl - previewMerge exceptions") {
            it("should throw if toBranch not found in previewMerge") {
                io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
                    pullRequestService.previewMerge(project, project, "refs/heads/feature", "refs/heads/nonexistent")
                }
            }
            it("should throw if fromBranch not found in previewMerge") {
                io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
                    pullRequestService.previewMerge(project, project, "refs/heads/nonexistent", "refs/heads/master")
                }
            }
        }
    """
    lines.insert(last_idx, new_test + "\n")
    with open("src/test/kotlin/com/github/search5/yona/domain/pullrequest/PullRequestServiceSpec.kt", "w") as f:
        f.writelines(lines)
