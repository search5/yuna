import sys

with open("src/test/kotlin/com/github/search5/yona/domain/vcs/BareCommitSpec.kt", "r") as f:
    lines = f.readlines()

# Find the last "})"
last_idx = -1
for i, line in enumerate(lines):
    if line.strip() == "})":
        last_idx = i

if last_idx != -1:
    new_test = """
        describe("Coverage addition for BareCommit") {
            it("should handle null parentFile in commitTextFile (4-args)") {
                val gitBaseDir = java.nio.file.Files.createTempDirectory("yuna-barecommit-cov").toFile()
                val bareDir = java.io.File(gitBaseDir, "tester/repo.git")
                org.eclipse.jgit.api.Git.init().setDirectory(bareDir).setBare(true).call().close()
                
                val project = com.github.search5.yona.domain.project.Project(id = 1L, owner = "tester", name = "repo")
                val user = com.github.search5.yona.domain.user.User(id = 1L, loginId = "tester", name = "tester", email = "tester@yona.io")
                val bare = com.github.search5.yona.domain.vcs.BareCommit(project, user, gitBaseDir.absolutePath)
                
                // "root.txt" has no parent directory, so file.parentFile is null.
                val commitId = bare.commitTextFile("develop", "root.txt", "content", "msg")
                io.kotest.matchers.shouldNotBe.shouldNotBe(commitId, null)
            }
            
            it("should handle unreachable branches using reflection") {
                val gitBaseDir = java.nio.file.Files.createTempDirectory("yuna-barecommit-cov2").toFile()
                val bareDir = java.io.File(gitBaseDir, "tester/repo.git")
                org.eclipse.jgit.api.Git.init().setDirectory(bareDir).setBare(true).call().close()
                
                val project = com.github.search5.yona.domain.project.Project(id = 1L, owner = "tester", name = "repo")
                val user = com.github.search5.yona.domain.user.User(id = 1L, loginId = "tester", name = "tester", email = "tester@yona.io")
                val bare = com.github.search5.yona.domain.vcs.BareCommit(project, user, gitBaseDir.absolutePath)
                
                // Access private field headObjectId and set to null to cover branch in createTreeWith
                val field = com.github.search5.yona.domain.vcs.BareCommit::class.java.getDeclaredField("headObjectId")
                field.isAccessible = true
                field.set(bare, null)
                
                val inserterMethod = com.github.search5.yona.domain.vcs.BareCommit::class.java.getDeclaredMethod("createTreeWith", org.eclipse.jgit.lib.ObjectInserter::class.java, String::class.java, org.eclipse.jgit.lib.ObjectId::class.java)
                inserterMethod.isAccessible = true
                
                val git = com.github.search5.yona.domain.vcs.PlayRepository.gitRepository(project, gitBaseDir.absolutePath)
                git.repository.newObjectInserter().use { inserter ->
                    val zeroBlob = org.eclipse.jgit.lib.ObjectId.zeroId()
                    val treeId = inserterMethod.invoke(bare, inserter, "test.txt", zeroBlob) as org.eclipse.jgit.lib.ObjectId
                    io.kotest.matchers.shouldNotBe.shouldNotBe(treeId, null)
                }
                git.close()
            }
        }
    """
    lines.insert(last_idx, new_test + "\n")
    with open("src/test/kotlin/com/github/search5/yona/domain/vcs/BareCommitSpec.kt", "w") as f:
        f.writelines(lines)
