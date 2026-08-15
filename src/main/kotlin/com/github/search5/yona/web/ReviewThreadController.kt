package com.github.search5.yona.web

import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.support.ReviewSearchCondition
import com.github.search5.yona.domain.support.ReviewThreadService
import com.github.search5.yona.domain.user.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import java.nio.charset.StandardCharsets

@Controller
class ReviewThreadController(
    private val projectRepository: ProjectRepository,
    private val reviewThreadService: ReviewThreadService,
    private val userRepository: UserRepository
) {

    @GetMapping("/{owner}/{projectName}/reviews")
    fun reviewThreads(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        condition: ReviewSearchCondition,
        @RequestParam(value = "format", required = false) format: String?,
        authentication: Authentication?,
        model: Model
    ): Any {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return "error/404"

        val currentUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }

        if (format == "xls") {
            val threads = reviewThreadService.getReviewThreads(project, condition)
            val csvBuilder = StringBuilder()
            csvBuilder.append('\ufeff')
            csvBuilder.append("No,COMMIT ID,REVIEW ID,REVIEW TITLE,Thread Author,REVIEW STATE,Date\n")
            threads.forEachIndexed { index, thread ->
                val commitId = thread.commitId ?: ""
                val firstComment = if (thread.reviewComments.isNotEmpty()) thread.getFirstReviewComment().contents else ""
                val authorName = thread.author?.name ?: ""
                val state = thread.state.name
                val createdDate = thread.createdDate.toString()

                csvBuilder.append("${index + 1},$commitId,${thread.id},\"${firstComment.replace("\"", "\"\"")}\",\"$authorName\",$state,$createdDate\n")
            }

            val fileBytes = csvBuilder.toString().toByteArray(StandardCharsets.UTF_8)
            val filename = "${project.name}_reviews.csv"

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(fileBytes)
        }

        val pageable = PageRequest.of(condition.pageNum - 1, 15)
        val commentThreads = reviewThreadService.getReviewThreads(project, condition, pageable)

        val countEveryone = reviewThreadService.countReviewThreads(project, condition.clone().setAuthorId(null).setParticipantId(null))
        val countParticipant = currentUser?.let {
            reviewThreadService.countReviewThreads(project, condition.clone().setAuthorId(null).setParticipantId(it.id))
        } ?: 0L
        val countAuthor = currentUser?.let {
            reviewThreadService.countReviewThreads(project, condition.clone().setParticipantId(null).setAuthorId(it.id))
        } ?: 0L

        val countOpen = reviewThreadService.countReviewThreads(project, condition.clone().setState("OPEN"))
        val countClosed = reviewThreadService.countReviewThreads(project, condition.clone().setState("CLOSED"))

        model.addAttribute("project", project)
        model.addAttribute("commentThreads", commentThreads)
        model.addAttribute("param", condition)
        model.addAttribute("currentUser", currentUser)
        model.addAttribute("countEveryone", countEveryone)
        model.addAttribute("countParticipant", countParticipant)
        model.addAttribute("countAuthor", countAuthor)
        model.addAttribute("countOpen", countOpen)
        model.addAttribute("countClosed", countClosed)

        return "reviewthread/list"
    }
}
