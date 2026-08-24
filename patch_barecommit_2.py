import sys

with open("src/test/kotlin/com/github/search5/yona/domain/vcs/BareCommitSpec.kt", "r") as f:
    lines = f.readlines()

last_idx = -1
for i, line in enumerate(lines):
    if line.strip() == "})":
        last_idx = i

if last_idx != -1:
    new_test = """
        describe("Coverage addition for BareCommit - Constructor nulls") {
            it("should handle null name and email in constructor") {
                val gitBaseDir = java.nio.file.Files.createTempDirectory("yuna-barecommit-cov3").toFile()
                val bareDir = java.io.File(gitBaseDir, "tester/repo.git")
                org.eclipse.jgit.api.Git.init().setDirectory(bareDir).setBare(true).call().close()
                
                val project = com.github.search5.yona.domain.project.Project(id = 1L, owner = "tester", name = "repo")
                
                // Create User via Unsafe or just use MockK if possible? No, MockK doesn't like returning null for non-null types.
                // We can use Java reflection to set the field to null directly.
                val user = com.github.search5.yona.domain.user.User(id = 1L, loginId = "tester", name = "tester", email = "tester@yona.io")
                val nameField = com.github.search5.yona.domain.user.User::class.java.getDeclaredField("name")
                nameField.isAccessible = true
                nameField.set(user, null)
                
                val emailField = com.github.search5.yona.domain.user.User::class.java.getDeclaredField("email")
                emailField.isAccessible = true
                emailField.set(user, null)
                
                val bare = com.github.search5.yona.domain.vcs.BareCommit(project, user, gitBaseDir.absolutePath)
                bare shouldNotBe null
            }
        }
    """
    lines.insert(last_idx, new_test + "\n")
    with open("src/test/kotlin/com/github/search5/yona/domain/vcs/BareCommitSpec.kt", "w") as f:
        f.writelines(lines)
