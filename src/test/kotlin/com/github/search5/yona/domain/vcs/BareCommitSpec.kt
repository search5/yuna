package com.github.search5.yona.domain.vcs

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.treewalk.TreeWalk
import java.io.File
import java.nio.file.Files

private fun seedInitialCommit(bareDir: File, branch: String, filePath: String, content: String) {
    val tempWorkingDir = Files.createTempDirectory("yuna-barecommit-seed").toFile()
    val git = Git.init().setDirectory(tempWorkingDir).call()
    try {
        val file = File(tempWorkingDir, filePath)
        file.parentFile.mkdirs()
        file.writeText(content)

        git.add().addFilepattern(filePath).call()
        git.commit().setSign(false).setAuthor("seed", "seed@yona.io").setMessage("seed").call()

        val config = git.repository.config
        config.setString("remote", "origin", "url", bareDir.absolutePath)
        config.save()

        git.push()
            .setRemote("origin")
            .setRefSpecs(RefSpec("HEAD:refs/heads/$branch"))
            .call()
    } finally {
        git.close()
    }
}

// yona GitUtil.commitTextFile()가 위임하는 BareCommit.commitTextFile(branchName, path, text, message)
// (BareCommit.java:249-286, "Bare commit" 오버로드) 대응 (P1-135). yona는 이 오버로드로
// 1) 지정 브랜치(refs/heads/<branch>)에만 커밋하고, 2) DirCache+TreeWalk 재귀 순회로
// 하위 경로(nested path) 파일도 기존 트리를 보존하며 반영한다.
class BareCommitSpec : DescribeSpec({
    describe("BareCommit.commitTextFile(branchName, path, text, message)") {
        it("지정한 브랜치에만 커밋을 반영하고 다른 브랜치(master)는 건드리지 않는다") {
            val gitBaseDir = Files.createTempDirectory("yuna-barecommit-test").toFile()
            val bareDir = File(gitBaseDir, "tester/repo.git")
            Git.init().setDirectory(bareDir).setBare(true).call().close()

            seedInitialCommit(bareDir, "develop", "README.md", "root file")

            val project = Project(id = 1L, owner = "tester", name = "repo")
            val user = User(id = 1L, loginId = "tester", name = "테스터", email = "tester@yona.io")

            val bare = BareCommit(project, user, gitBaseDir.absolutePath)
            bare.setRefName(Constants.R_HEADS + "develop")
            val commitId = bare.commitTextFile("develop", "src/main/Foo.kt", "package foo", "add nested file")

            commitId shouldNotBe null

            val repository = FileRepositoryBuilder().setGitDir(bareDir).build()
            try {
                repository.resolve("refs/heads/develop") shouldBe commitId
                repository.findRef("refs/heads/master") shouldBe null

                val revWalk = RevWalk(repository)
                val commit = revWalk.parseCommit(commitId)
                val treeWalk = TreeWalk(repository)
                treeWalk.addTree(commit.tree)
                treeWalk.isRecursive = true

                val filesInTree = mutableMapOf<String, String>()
                while (treeWalk.next()) {
                    val loader = repository.open(treeWalk.getObjectId(0))
                    filesInTree[treeWalk.pathString] = String(loader.bytes, Charsets.UTF_8)
                }
                treeWalk.close()
                revWalk.close()

                filesInTree["src/main/Foo.kt"] shouldBe "package foo"
                filesInTree["README.md"] shouldBe "root file"
            } finally {
                repository.close()
            }
        }
    }
})
