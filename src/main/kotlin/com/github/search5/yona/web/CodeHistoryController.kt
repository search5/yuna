package com.github.search5.yona.web

import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.vcs.RepositoryService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/vcs/{owner}/{projectName}")
class CodeHistoryController(
    private val projectRepository: ProjectRepository,
    private val repositoryService: RepositoryService
) {

    @GetMapping("/history")
    fun getHistory(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int,
        @RequestParam(required = false, defaultValue = "HEAD") branch: String,
        @RequestParam(required = false) path: String?
    ): ResponseEntity<List<CommitResponse>> {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val repository = repositoryService.getRepository(project)
        val history = repository.getHistory(page, size, branch, path)

        val responseList = history.map { commit ->
            CommitResponse(
                id = commit.getId(),
                shortId = commit.getShortId(),
                message = commit.getMessage(),
                shortMessage = commit.getShortMessage(),
                authorName = commit.getAuthorName(),
                authorEmail = commit.getAuthorEmail(),
                authorDate = commit.getAuthorDate()?.time ?: 0L,
                committerName = commit.getCommitterName(),
                committerEmail = commit.getCommitterEmail(),
                committerDate = commit.getCommitterDate()?.time ?: 0L
            )
        }

        return ResponseEntity.ok(responseList)
    }

    @GetMapping("/commit/{commitId}")
    fun getCommit(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable commitId: String
    ): ResponseEntity<CommitResponse> {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val repository = repositoryService.getRepository(project)
        val commit = repository.getCommit(commitId)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val response = CommitResponse(
            id = commit.getId(),
            shortId = commit.getShortId(),
            message = commit.getMessage(),
            shortMessage = commit.getShortMessage(),
            authorName = commit.getAuthorName(),
            authorEmail = commit.getAuthorEmail(),
            authorDate = commit.getAuthorDate()?.time ?: 0L,
            committerName = commit.getCommitterName(),
            committerEmail = commit.getCommitterEmail(),
            committerDate = commit.getCommitterDate()?.time ?: 0L
        )

        return ResponseEntity.ok(response)
    }
}

data class CommitResponse(
    val id: String,
    val shortId: String,
    val message: String?,
    val shortMessage: String,
    val authorName: String?,
    val authorEmail: String?,
    val authorDate: Long,
    val committerName: String?,
    val committerEmail: String?,
    val committerDate: Long
)
