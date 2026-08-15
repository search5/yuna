package com.github.search5.yona.domain.vcs

import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.user.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File

@Service
class RepositoryService(
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    @Value("\${yuna.git.base-dir:/tmp/yuna/git}")
    private val gitBaseDir: String,
    @Value("\${yuna.svn.base-dir:/tmp/yuna/svn}")
    private val svnBaseDir: String
) {
    private val objectMapper = ObjectMapper()

    fun getRepository(project: Project): PlayRepository {
        val vcsType = project.vcs?.uppercase() ?: "GIT"
        return if (vcsType == "SUBVERSION" || vcsType == "SVN") {
            SvnRepository(
                ownerName = project.owner ?: "",
                projectName = project.name,
                baseDir = svnBaseDir
            ) { loginId ->
                userRepository.findByLoginId(loginId).orElse(null)
            }
        } else {
            GitRepository(
                ownerName = project.owner ?: "",
                projectName = project.name,
                baseDir = gitBaseDir
            ) { _, email ->
                if (email != null) {
                    userRepository.findByEmail(email).orElse(null)
                } else {
                    null
                }
            }
        }
    }

    fun getFileAsRaw(userName: String, projectName: String, revision: String, path: String): ByteArray? {
        val project = projectRepository.findByOwnerAndName(userName, projectName).orElse(null) ?: return null
        return getRepository(project).getRawFile(revision, path)
    }

    fun getMetaDataFromAncestorDirectories(
        repository: PlayRepository,
        branch: String,
        path: String
    ): List<ObjectNode>? {
        val recursiveData = ArrayList<ObjectNode>()
        var partialPath = ""
        val pathArray = path.split("/").filter { it.isNotEmpty() }
        val pathLength = pathArray.size

        val metaData = repository.getMetaDataFromPath(branch, "") ?: return null
        metaData.put("path", "")
        recursiveData.add(metaData)

        for (i in 0 until pathLength) {
            partialPath = if (partialPath.isEmpty()) pathArray[i] else "$partialPath/${pathArray[i]}"
            if (!repository.isIntermediateFolder(partialPath)) {
                val subMeta = repository.getMetaDataFromPath(branch, partialPath) ?: return null
                subMeta.put("path", partialPath)
                recursiveData.add(subMeta)
            }
        }
        return recursiveData
    }
}
