package com.github.search5.yona.domain.event

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.transport.ReceiveCommand
import java.io.File
import java.nio.file.Files

fun main() {
    val tempDir = Files.createTempDirectory("gitrepo").toFile()
    val git = Git.init().setDirectory(tempDir).call()
    File(tempDir, "file.txt").writeText("hello")
    git.add().addFilepattern("file.txt").call()
    val revCommit = git.commit().setMessage("initial").call()
    println(revCommit.id)
}
