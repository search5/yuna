package com.github.search5.yona.domain.vcs

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.user.User
import org.eclipse.jgit.lib.*
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import java.io.File
import java.io.IOException

class BareCommit(project: Project, user: User, gitBaseDir: String) {
    private val repository: Repository
    private val personIdent: PersonIdent
    private var commitMessage: String? = null
    private var file: File? = null
    private var refName: String = Constants.R_HEADS + "master" // refs/heads/master
    private var headObjectId: ObjectId? = null

    init {
        val gitDir = File(File(gitBaseDir), "${project.owner}/${project.name}.git")
        this.repository = FileRepositoryBuilder().setGitDir(gitDir).build()
        this.personIdent = PersonIdent(user.name ?: user.loginId, user.email ?: "")
    }

    @Throws(IOException::class)
    fun commitTextFile(fileNameWithPath: String, contents: String, message: String): ObjectId? {
        this.file = File(fileNameWithPath)
        this.commitMessage = message
        
        var commitId: ObjectId? = null
        val inserter = repository.newObjectInserter()
        try {
            val ref = repository.findRef(refName)
            this.headObjectId = ref?.objectId ?: ObjectId.zeroId()
            
            val blobId = inserter.insert(Constants.OBJ_BLOB, contents.toByteArray(Charsets.UTF_8))
            val treeId = createTreeWith(inserter, file!!.name, blobId)
            
            val commitBuilder = CommitBuilder().apply {
                setTreeId(treeId)
                if (headObjectId != ObjectId.zeroId()) {
                    setParentId(headObjectId)
                }
                author = personIdent
                committer = personIdent
                setMessage(commitMessage)
            }
            
            commitId = inserter.insert(commitBuilder)
            inserter.flush()
            
            val ru = repository.updateRef(refName)
            ru.isForceUpdate = false
            ru.setRefLogIdent(personIdent)
            ru.setNewObjectId(commitId)
            if (ref?.objectId != null) {
                ru.setExpectedOldObjectId(headObjectId)
            }
            ru.setRefLogMessage(commitMessage, true)
            ru.update()
        } finally {
            inserter.close()
            repository.close()
        }
        return commitId
    }

    private fun createTreeWith(inserter: ObjectInserter, fileName: String, fileObjectId: ObjectId): ObjectId {
        return if (headObjectId == ObjectId.zeroId() || headObjectId == null) {
            val formatter = TreeFormatter().apply {
                append(fileName, FileMode.REGULAR_FILE, fileObjectId)
            }
            inserter.insert(formatter)
        } else {
            val formatter = TreeFormatter()
            val revWalk = RevWalk(repository)
            val commit = revWalk.parseCommit(headObjectId)
            val treeParser = CanonicalTreeParser(byteArrayOf(), revWalk.objectReader, commit.tree.id)
            
            var isInserted = false
            while (!treeParser.eof()) {
                val entryName = String(treeParser.entryPathBuffer, 0, treeParser.entryPathLength, Charsets.UTF_8)
                var nameForComparison = entryName
                if (treeParser.entryFileMode == FileMode.TREE) {
                    nameForComparison = "$entryName/"
                }
                
                if (nameForComparison.compareTo(fileName) == 0 && !isInserted) {
                    formatter.append(fileName, FileMode.REGULAR_FILE, fileObjectId)
                    isInserted = true
                } else if (nameForComparison.compareTo(fileName) > 0 && !isInserted) {
                    formatter.append(fileName, FileMode.REGULAR_FILE, fileObjectId)
                    formatter.append(entryName.toByteArray(Charsets.UTF_8), treeParser.entryFileMode, treeParser.entryObjectId)
                    isInserted = true
                } else {
                    formatter.append(entryName.toByteArray(Charsets.UTF_8), treeParser.entryFileMode, treeParser.entryObjectId)
                }
                treeParser.next()
            }
            if (!isInserted) {
                formatter.append(fileName, FileMode.REGULAR_FILE, fileObjectId)
            }
            inserter.insert(formatter)
        }
    }
}
