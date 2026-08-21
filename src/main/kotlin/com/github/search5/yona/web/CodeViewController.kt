package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.enumeration.Operation
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.pullrequest.CommentThreadRepository
import com.github.search5.yona.domain.pullrequest.CommitCommentRepository
import com.github.search5.yona.domain.support.MarkdownService
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.vcs.RepositoryService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import java.net.URLEncoder
import java.nio.file.Files
import org.springframework.http.HttpHeaders
import org.springframework.http.CacheControl
import java.util.concurrent.TimeUnit

@Controller
class CodeViewController(
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val repositoryService: RepositoryService,
    private val commentThreadRepository: CommentThreadRepository,
    private val commitCommentRepository: CommitCommentRepository,
    private val accessControl: AccessControl,
    private val markdownService: MarkdownService
) {

    @GetMapping("/{owner}/{projectName}/code")
    fun codeBrowser(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (project.isCodeAccessibleMemberOnly == true) {
            if (loginUser == null || (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser))) {
                return "error/403"
            }
        } else if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
            return "error/403"
        }

        val repository = repositoryService.getRepository(project)
        if (repository.isEmpty()) {
            model.addAttribute("project", project)
            return if (project.vcs == "SUBVERSION") "code/nohead_svn" else "code/nohead"
        }

        val headBranch = repository.getHeadBranch()
        val defaultBranch = headBranch?.shortName ?: "master"
        val encodedBranch = URLEncoder.encode(defaultBranch, "UTF-8")

        return "redirect:/$owner/$projectName/code/$encodedBranch"
    }

    @GetMapping("/{owner}/{projectName}/code/{branch}")
    fun codeBrowserWithBranchRoot(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable branch: String,
        authentication: Authentication?,
        model: Model
    ): String {
        return codeBrowserWithBranch(owner, projectName, branch, "", authentication, model)
    }

    @GetMapping("/{owner}/{projectName}/code/{branch}/{*path}")
    fun codeBrowserWithBranch(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable branch: String,
        @PathVariable path: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (project.isCodeAccessibleMemberOnly == true) {
            if (loginUser == null || (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser))) {
                return "error/403"
            }
        } else if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
            return "error/403"
        }

        val decodedBranch = java.net.URLDecoder.decode(branch, "UTF-8")
        val normalizedPath = path.removePrefix("/")

        val repository = repositoryService.getRepository(project)
        val branches = repository.getRefNames()
        val recursiveData = repositoryService.getMetaDataFromAncestorDirectories(repository, decodedBranch, normalizedPath)
            ?: return "error/404"

        model.addAttribute("project", project)
        model.addAttribute("branches", branches)
        model.addAttribute("recursiveData", recursiveData)
        model.addAttribute("branch", decodedBranch)
        model.addAttribute("path", normalizedPath)
        model.addAttribute("currentUser", loginUser)

        // yona views/code/partial_view_file.scala.html:109-114 "if(isMarkdownExtension(path))" 대응
        // (P1-139) — 코드브라우저에서 .md류 파일은 원문 대신 렌더링된 HTML로 보여준다.
        val lastEntry = recursiveData.lastOrNull()
        if (lastEntry?.get("type")?.asText() == "file" && isMarkdownExtension(normalizedPath)) {
            val data = lastEntry.get("data")?.asText()
            if (data != null) {
                model.addAttribute("markdownHtml", markdownService.renderFileInCodeBrowser(data, project))
            }
        }

        return "code/view"
    }

    // yona utils/TemplateHelper.scala:594-600 isMarkdownExtension() 대응 (P1-139).
    private fun isMarkdownExtension(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in setOf("markdown", "mdown", "mkdn", "mkd", "md", "mdwn")
    }

    @GetMapping("/{owner}/{projectName}/rawcode/{rev}/{*path}")
    fun showRawFile(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable rev: String,
        @PathVariable path: String,
        authentication: Authentication?
    ): ResponseEntity<ByteArray> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (project.isCodeAccessibleMemberOnly == true) {
            if (loginUser == null || (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
            }
        } else if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val decodedRev = java.net.URLDecoder.decode(rev, "UTF-8")
        val normalizedPath = path.removePrefix("/")

        val rawData = repositoryService.getFileAsRaw(owner, projectName, decodedRev, normalizedPath)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val headers = HttpHeaders()
        
        // MIME Type 감지
        val mimeType = try {
            val fileTmp = Files.createTempFile("yuna-view-mime", null)
            Files.write(fileTmp, rawData)
            val detected = Files.probeContentType(fileTmp)
            Files.delete(fileTmp)
            detected ?: "text/plain"
        } catch (e: Exception) {
            "text/plain"
        }

        headers.contentType = MediaType.parseMediaType(mimeType)
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"${normalizedPath.substringAfterLast('/')}\"")

        return ResponseEntity(rawData, headers, HttpStatus.OK)
    }

    @GetMapping("/{owner}/{projectName}/image/{rev}/{*path}")
    fun showImageFile(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable rev: String,
        @PathVariable path: String,
        authentication: Authentication?
    ): ResponseEntity<ByteArray> {
        return showRawFile(owner, projectName, rev, path, authentication)
    }

    @GetMapping("/{owner}/{projectName}/files/{rev}/{*path}")
    fun openFile(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable rev: String,
        @PathVariable path: String,
        authentication: Authentication?
    ): ResponseEntity<ByteArray> {
        return showRawFile(owner, projectName, rev, path, authentication)
    }

    @GetMapping("/{owner}/{projectName}/commits")
    fun historyUntilHead(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        authentication: Authentication?,
        model: Model
    ): String {
        return history(owner, projectName, "HEAD", null, page, authentication, model)
    }

    @GetMapping(value = ["/{owner}/{projectName}/commits/{branch}", "/{owner}/{projectName}/commits/{branch}/{*path}"])
    fun history(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable branch: String,
        @PathVariable(required = false) path: String?,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (project.isCodeAccessibleMemberOnly == true) {
            if (loginUser == null || (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser))) {
                return "error/403"
            }
        } else if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
            return "error/403"
        }

        val decodedBranch = java.net.URLDecoder.decode(branch, "UTF-8")
        val decodedPath = path?.let { java.net.URLDecoder.decode(it, "UTF-8") }?.removePrefix("/")

        val repository = repositoryService.getRepository(project)
        val branches = repository.getRefNames()

        // yona CodeHistoryApp.history()의 "catch (NoHeadException e) { return notFound(nohead.render(project)); }"
        // 대응 (P1-136) — 커밋이 하나도 없는 빈 저장소에서 히스토리를 조회하면 JGit이 NoHeadException을
        // 던진다. 잡지 않으면 500으로 전파되므로, 코드브라우저 루트(codeBrowserRoot)와 동일한
        // code/nohead(_svn) 뷰로 안내한다.
        val commits = try {
            repository.getHistory(page, 25, decodedBranch, decodedPath)
        } catch (e: org.eclipse.jgit.api.errors.NoHeadException) {
            model.addAttribute("project", project)
            return if (project.vcs == "SUBVERSION") "code/nohead_svn" else "code/nohead"
        }

        model.addAttribute("project", project)
        model.addAttribute("branches", branches)
        model.addAttribute("branch", decodedBranch)
        model.addAttribute("path", decodedPath ?: "")
        model.addAttribute("commits", commits)
        model.addAttribute("page", page)
        model.addAttribute("currentUser", loginUser)

        return "code/history"
    }

    @GetMapping("/{owner}/{projectName}/commit/{commitId}")
    fun showCommit(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable commitId: String,
        @RequestParam(required = false, defaultValue = "HEAD") branch: String,
        @RequestParam(required = false, defaultValue = "") path: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (project.isCodeAccessibleMemberOnly == true) {
            if (loginUser == null || (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser))) {
                return "error/403"
            }
        } else if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
            return "error/403"
        }

        val repository = repositoryService.getRepository(project)
        val commit = try {
            repository.getCommit(commitId)
        } catch (e: Exception) {
            null
        } ?: return "error/404"

        val parentCommit = try {
            repository.getParentCommitOf(commitId)
        } catch (e: Exception) {
            null
        }

        model.addAttribute("project", project)
        model.addAttribute("commit", commit)
        model.addAttribute("parentCommit", parentCommit)
        model.addAttribute("selectedBranch", branch)
        model.addAttribute("path", path)
        model.addAttribute("currentUser", loginUser)

        val isSvn = project.vcs?.uppercase() == "SUBVERSION" || project.vcs?.uppercase() == "SVN"
        val commentThreads = if (isSvn) {
            emptyList()
        } else {
            commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(project, commitId)
        }
        val comments = if (isSvn) {
            commitCommentRepository.findByProjectAndCommitIdOrderByCreatedDateAsc(project, commitId)
        } else {
            emptyList()
        }

        model.addAttribute("commentThreads", commentThreads)
        model.addAttribute("commitB", commit)

        return if (isSvn) {
            val patch = try {
                repository.getPatch(commitId)
            } catch (e: Exception) {
                ""
            }
            model.addAttribute("patch", patch)
            model.addAttribute("comments", comments)
            "code/svnDiff"
        } else {
            val fileDiffs = try {
                repository.getDiff(commitId)
            } catch (e: Exception) {
                null
            }
            if (fileDiffs == null) {
                return "error/404"
            }
            model.addAttribute("fileDiffs", fileDiffs)
            model.addAttribute("comments", comments)
            "code/diff"
        }
    }
}

