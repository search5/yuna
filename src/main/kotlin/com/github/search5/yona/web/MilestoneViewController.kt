package com.github.search5.yona.web

import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.milestone.Milestone
import com.github.search5.yona.domain.milestone.MilestoneRepository
import com.github.search5.yona.domain.milestone.MilestoneService
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.attachment.AttachmentRepository
import com.github.search5.yona.domain.support.MarkdownService
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Controller
class MilestoneViewController(
    private val projectRepository: ProjectRepository,
    private val milestoneService: MilestoneService,
    private val milestoneRepository: MilestoneRepository,
    private val issueRepository: IssueRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val attachmentRepository: AttachmentRepository,
    private val markdownService: MarkdownService
) {

    data class MilestoneViewDto(
        val milestone: Milestone,
        val openIssuesCount: Int,
        val closedIssuesCount: Int,
        val completionRate: Int,
        val openIssues: List<Issue>,
        val closedIssues: List<Issue>,
        val isOverdue: Boolean,
        val daysBetween: Long?
    )

    private fun toViewDto(milestone: Milestone): MilestoneViewDto {
        val allIssues = issueRepository.findByMilestone(milestone)
        val openIssues = allIssues.filter { it.state == State.OPEN }
        val closedIssues = allIssues.filter { it.state == State.CLOSED }
        
        val total = openIssues.size + closedIssues.size
        val completionRate = if (total > 0) {
            (closedIssues.size * 100) / total
        } else {
            0
        }
        
        val isOverdue = milestone.dueDate?.let { it.isBefore(Instant.now()) } ?: false
        val daysBetween = milestone.dueDate?.let { dueDate ->
            val nowLocalDate = LocalDate.now(ZoneId.systemDefault())
            val dueLocalDate = LocalDate.ofInstant(dueDate, ZoneId.systemDefault())
            java.time.temporal.ChronoUnit.DAYS.between(nowLocalDate, dueLocalDate)
        }
        
        return MilestoneViewDto(
            milestone = milestone,
            openIssuesCount = openIssues.size,
            closedIssuesCount = closedIssues.size,
            completionRate = completionRate,
            openIssues = openIssues,
            closedIssues = closedIssues,
            isOverdue = isOverdue,
            daysBetween = daysBetween
        )
    }

    @GetMapping("/{owner}/{projectName}/milestones")
    fun listMilestones(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false, defaultValue = "open") state: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (project.projectScope != ProjectScope.PUBLIC) {
            if (loginUser == null || !projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!)) {
                return "error/403"
            }
        }

        // 파라미터 값에 따라 이넘 조회
        val stateEnum = when (state.lowercase()) {
            "closed" -> State.CLOSED
            "all" -> State.ALL
            else -> State.OPEN
        }

        val milestones = milestoneService.getMilestones(project.id!!, stateEnum)
        val milestoneDtos = milestones.map { toViewDto(it) }

        model.addAttribute("project", project)
        model.addAttribute("milestones", milestoneDtos)
        model.addAttribute("state", state)
        model.addAttribute("currentUser", loginUser)

        return "milestone/list"
    }

    @GetMapping("/{owner}/{projectName}/milestone/{id}")
    fun viewMilestone(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable id: Long,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (project.projectScope != ProjectScope.PUBLIC) {
            if (loginUser == null || !projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!)) {
                return "error/403"
            }
        }

        val milestone = milestoneService.getMilestone(id) ?: return "error/404"
        if (milestone.project.id != project.id) {
            return "error/404"
        }

        val dto = toViewDto(milestone)

        val attachments = attachmentRepository.findByContainerTypeAndContainerId(ResourceType.MILESTONE, id.toString())
        val attachmentsJson = attachments.joinToString(prefix = "{\"attachments\":[", postfix = "]}", separator = ",") { attach ->
            val attachId = attach.id?.toString() ?: ""
            val mimeType = attach.mimeType ?: ""
            val name = attach.name.replace("\"", "\\\"").replace("\n", "\\n")
            val url = "/files/${attach.id}"
            val size = attach.size?.toString() ?: "0"
            """{"id":"$attachId","mimeType":"$mimeType","name":"$name","url":"$url","size":$size}"""
        }

        val contentsHtml = milestone.contents?.let { markdownService.render(it, true, project) } ?: ""

        model.addAttribute("project", project)
        model.addAttribute("milestoneDto", dto)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("attachmentsJson", attachmentsJson)
        model.addAttribute("contentsHtml", contentsHtml)

        return "milestone/view"
    }

    @GetMapping("/{owner}/{projectName}/milestone/new", "/{owner}/{projectName}/newMilestoneForm", "/{owner}/{projectName}/newmilestoneform")
    fun createMilestoneForm(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (loginUser == null || !projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!)) {
            return "error/403"
        }

        model.addAttribute("project", project)
        model.addAttribute("currentUser", loginUser)

        return "milestone/create"
    }

    @GetMapping("/{owner}/{projectName}/milestone/{id}/editform")
    fun editMilestoneForm(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable id: Long,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (loginUser == null || !projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!)) {
            return "error/403"
        }

        val milestone = milestoneService.getMilestone(id) ?: return "error/404"
        if (milestone.project.id != project.id) {
            return "error/404"
        }

        val attachments = attachmentRepository.findByContainerTypeAndContainerId(ResourceType.MILESTONE, id.toString())
        val attachmentsJson = attachments.joinToString(prefix = "{\"attachments\":[", postfix = "]}", separator = ",") { attach ->
            val attachId = attach.id?.toString() ?: ""
            val mimeType = attach.mimeType ?: ""
            val name = attach.name.replace("\"", "\\\"").replace("\n", "\\n")
            val url = "/files/${attach.id}"
            val size = attach.size?.toString() ?: "0"
            """{"id":"$attachId","mimeType":"$mimeType","name":"$name","url":"$url","size":$size}"""
        }

        model.addAttribute("project", project)
        model.addAttribute("milestone", milestone)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("attachmentsJson", attachmentsJson)

        return "milestone/edit"
    }

    @PostMapping("/{owner}/{projectName}/milestones")
    fun createMilestone(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam title: String,
        @RequestParam(required = false) contents: String?,
        @RequestParam(required = false) dueDate: String?,
        @RequestParam(required = false, defaultValue = "OPEN") state: State,
        @RequestParam(required = false) temporaryUploadFiles: String?,
        authentication: Authentication?,
        redirectAttributes: RedirectAttributes,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        if (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!)) {
            return "error/403"
        }

        // 1. 중복 제목 검증
        if (milestoneRepository.findByProjectAndTitle(project, title) != null) {
            model.addAttribute("project", project)
            model.addAttribute("currentUser", loginUser)
            model.addAttribute("titleError", "milestone.title.duplicated")
            model.addAttribute("title", title)
            model.addAttribute("contents", contents)
            model.addAttribute("dueDate", dueDate)
            return "milestone/create"
        }

        // 2. DueDate 날짜 끝 시간(23:59:59.999) 보정
        val parsedDueDate = if (!dueDate.isNullOrBlank()) {
            try {
                val localDate = LocalDate.parse(dueDate)
                localDate.atTime(23, 59, 59, 999000000).atZone(ZoneId.systemDefault()).toInstant()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        val milestone = Milestone(
            title = title,
            contents = contents ?: "",
            dueDate = parsedDueDate,
            state = state,
            project = project
        )

        val savedMilestone = milestoneService.createMilestone(project.id!!, milestone)

        if (!temporaryUploadFiles.isNullOrBlank()) {
            val fileIds = temporaryUploadFiles.split(",").mapNotNull { it.trim().toLongOrNull() }
            fileIds.forEach { fileId ->
                attachmentRepository.findById(fileId).ifPresent { attachment ->
                    attachment.containerType = ResourceType.MILESTONE
                    attachment.containerId = savedMilestone.id.toString()
                    attachmentRepository.save(attachment)
                }
            }
        }

        return "redirect:/$owner/$projectName/milestone/${savedMilestone.id}"
    }

    @PostMapping("/{owner}/{projectName}/milestone/{id}/edit")
    fun editMilestone(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable id: Long,
        @RequestParam title: String,
        @RequestParam(required = false) contents: String?,
        @RequestParam(required = false) dueDate: String?,
        @RequestParam(required = false, defaultValue = "OPEN") state: State,
        @RequestParam(required = false) temporaryUploadFiles: String?,
        authentication: Authentication?,
        redirectAttributes: RedirectAttributes,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"

        if (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!)) {
            return "error/403"
        }

        // 2. DueDate 날짜 끝 시간(23:59:59.999) 보정
        val parsedDueDate = if (!dueDate.isNullOrBlank()) {
            try {
                val localDate = LocalDate.parse(dueDate)
                localDate.atTime(23, 59, 59, 999000000).atZone(ZoneId.systemDefault()).toInstant()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        // 1. 중복 제목 검증
        val original = milestoneService.getMilestone(id) ?: return "error/404"
        if (original.title != title && milestoneRepository.findByProjectAndTitle(project, title) != null) {
            model.addAttribute("project", project)
            model.addAttribute("currentUser", loginUser)
            model.addAttribute("titleError", "milestone.title.duplicated")
            val dummyMilestone = Milestone(
                id = id,
                title = title,
                contents = contents,
                dueDate = parsedDueDate,
                state = state,
                project = project
            )
            model.addAttribute("milestone", dummyMilestone)
            
            // 기존 첨부파일 목록 전달
            val attachments = attachmentRepository.findByContainerTypeAndContainerId(ResourceType.MILESTONE, id.toString())
            val attachmentsJson = attachments.joinToString(prefix = "{\"attachments\":[", postfix = "]}", separator = ",") { attach ->
                val attachId = attach.id?.toString() ?: ""
                val mimeType = attach.mimeType ?: ""
                val name = attach.name.replace("\"", "\\\"").replace("\n", "\\n")
                val url = "/files/${attach.id}"
                val size = attach.size?.toString() ?: "0"
                """{"id":"$attachId","mimeType":"$mimeType","name":"$name","url":"$url","size":$size}"""
            }
            model.addAttribute("attachmentsJson", attachmentsJson)
            return "milestone/edit"
        }

        val updated = milestoneService.updateMilestone(
            milestoneId = id,
            title = title,
            contents = contents ?: "",
            dueDate = parsedDueDate,
            state = state
        )

        if (!temporaryUploadFiles.isNullOrBlank()) {
            val fileIds = temporaryUploadFiles.split(",").mapNotNull { it.trim().toLongOrNull() }
            fileIds.forEach { fileId ->
                attachmentRepository.findById(fileId).ifPresent { attachment ->
                    attachment.containerType = ResourceType.MILESTONE
                    attachment.containerId = updated.id.toString()
                    attachmentRepository.save(attachment)
                }
            }
        }

        return "redirect:/$owner/$projectName/milestone/$id"
    }

    @PostMapping("/{owner}/{projectName}/milestone/{id}/open")
    fun openMilestone(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable id: Long,
        authentication: Authentication?
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"
        if (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!)) {
            return "error/403"
        }
        val milestone = milestoneService.getMilestone(id) ?: return "error/404"
        if (milestone.project.id != project.id) {
            return "error/404"
        }
        
        milestoneService.updateMilestone(
            milestoneId = id,
            title = milestone.title,
            contents = milestone.contents,
            dueDate = milestone.dueDate,
            state = State.OPEN
        )
        return "redirect:/$owner/$projectName/milestone/$id"
    }

    @PostMapping("/{owner}/{projectName}/milestone/{id}/close")
    fun closeMilestone(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable id: Long,
        authentication: Authentication?
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return "error/403"
        if (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!)) {
            return "error/403"
        }
        val milestone = milestoneService.getMilestone(id) ?: return "error/404"
        if (milestone.project.id != project.id) {
            return "error/404"
        }
        
        milestoneService.updateMilestone(
            milestoneId = id,
            title = milestone.title,
            contents = milestone.contents,
            dueDate = milestone.dueDate,
            state = State.CLOSED
        )
        return "redirect:/$owner/$projectName/milestone/$id"
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{owner}/{projectName}/milestone/{id}")
    @org.springframework.web.bind.annotation.ResponseBody
    fun deleteMilestone(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable id: Long,
        authentication: Authentication?
    ): org.springframework.http.ResponseEntity<Void> {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return org.springframework.http.ResponseEntity.notFound().build()
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build()
        if (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!)) {
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build()
        }
        val milestone = milestoneService.getMilestone(id)
            ?: return org.springframework.http.ResponseEntity.notFound().build()
        if (milestone.project.id != project.id) {
            return org.springframework.http.ResponseEntity.notFound().build()
        }
        
        milestoneService.deleteMilestone(id)
        
        return org.springframework.http.ResponseEntity.noContent()
            .header("Location", "/$owner/$projectName/milestones")
            .build()
    }
}
