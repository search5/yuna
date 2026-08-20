package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.support.ReviewSearchCondition
import com.github.search5.yona.domain.support.ReviewThreadService
import com.github.search5.yona.domain.user.User
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
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

@Controller
class ReviewThreadController(
    private val projectRepository: ProjectRepository,
    private val reviewThreadService: ReviewThreadService,
    private val userRepository: UserRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val accessControl: AccessControl
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

        val isCodeAccessible = checkCodeAccessibility(project, currentUser)
        if (!isCodeAccessible) {
            return "error/403"
        }

        if (format == "xls") {
            val threads = reviewThreadService.getReviewThreads(project, condition)
            val fileBytes = excelFrom(threads)
            val filename = "${project.name}_reviews_${java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(java.time.LocalDateTime.now())}.xls"

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

    private fun excelFrom(commentThreads: List<com.github.search5.yona.domain.pullrequest.CommentThread>): ByteArray {
        val bos = ByteArrayOutputStream()
        val workbook = jxl.Workbook.createWorkbook(bos)
        val todayStr = java.time.Instant.now().toEpochMilli().toString()
        val sheet = workbook.createSheet(todayStr, 0)

        val headerCellFormat = getHeaderCellFormat()
        val bodyCellFormat = getBodyCellFormat()
        val dateCellFormat = getDateCellFormat()

        val titles = arrayOf("No", "COMMIT ID", "REVIEW ID", "REVIEW TITLE", "Thread Author", "Response Text", "Response", "REVIEW STATE", "is PullRequest?", "Date")

        for (i in titles.indices) {
            sheet.addCell(jxl.write.Label(i, 0, titles[i], headerCellFormat))
            sheet.setColumnView(i, 20)
        }

        var rowNumber = 0
        for (idx in commentThreads.indices) {
            val commentThread = commentThreads[idx]
            val commitId = commentThread.commitId ?: ""
            val threadFirstComment = commentThread.getFirstReviewComment().contents
            for (j in commentThread.reviewComments.indices) {
                val comment = commentThread.reviewComments[j]
                var columnPos = 0
                val responseComment = if (threadFirstComment == comment.contents) "" else comment.contents
                
                sheet.addCell(jxl.write.Label(columnPos++, rowNumber + 1, (rowNumber + 1).toString(), bodyCellFormat))
                sheet.addCell(jxl.write.Label(columnPos++, rowNumber + 1, if (commitId.length >= 7) commitId.substring(0, 7) else commitId, bodyCellFormat))
                sheet.addCell(jxl.write.Label(columnPos++, rowNumber + 1, commentThread.id.toString(), bodyCellFormat))
                sheet.addCell(jxl.write.Label(columnPos++, rowNumber + 1, if (responseComment.isEmpty()) threadFirstComment else "", bodyCellFormat))
                sheet.addCell(jxl.write.Label(columnPos++, rowNumber + 1, if (responseComment.isEmpty()) (commentThread.author?.name ?: "") else "", bodyCellFormat))
                sheet.addCell(jxl.write.Label(columnPos++, rowNumber + 1, responseComment, bodyCellFormat))
                sheet.addCell(jxl.write.Label(columnPos++, rowNumber + 1, if (responseComment.isNotEmpty()) (comment.author?.name ?: "") else "", bodyCellFormat))
                sheet.addCell(jxl.write.Label(columnPos++, rowNumber + 1, commentThread.state.toString(), bodyCellFormat))
                sheet.addCell(jxl.write.Label(columnPos++, rowNumber + 1, commentThread.isOnPullRequest().toString(), bodyCellFormat))
                sheet.addCell(jxl.write.DateTime(columnPos++, rowNumber + 1, java.util.Date.from(comment.createdDate), dateCellFormat))
                rowNumber++
            }
        }
        workbook.write()
        try {
            workbook.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return bos.toByteArray()
    }

    private fun getHeaderCellFormat(): jxl.write.WritableCellFormat {
        val headerFont = jxl.write.WritableFont(jxl.write.WritableFont.ARIAL, 14, jxl.write.WritableFont.BOLD, false, jxl.format.UnderlineStyle.NO_UNDERLINE, jxl.format.Colour.BLACK, jxl.format.ScriptStyle.NORMAL_SCRIPT)
        val headerCell = jxl.write.WritableCellFormat(headerFont)
        headerCell.setBorder(jxl.format.Border.ALL, jxl.format.BorderLineStyle.DOUBLE)
        headerCell.alignment = jxl.format.Alignment.CENTRE
        return headerCell
    }

    private fun getBodyCellFormat(): jxl.write.WritableCellFormat {
        val baseFont = jxl.write.WritableFont(jxl.write.WritableFont.ARIAL, 12, jxl.write.WritableFont.NO_BOLD, false, jxl.format.UnderlineStyle.NO_UNDERLINE, jxl.format.Colour.BLACK, jxl.format.ScriptStyle.NORMAL_SCRIPT)
        val cellFormat = jxl.write.WritableCellFormat(baseFont)
        cellFormat.setBorder(jxl.format.Border.ALL, jxl.format.BorderLineStyle.THIN)
        cellFormat.wrap = true
        cellFormat.verticalAlignment = jxl.format.VerticalAlignment.TOP
        return cellFormat
    }

    private fun getDateCellFormat(): jxl.write.WritableCellFormat {
        val baseFont = jxl.write.WritableFont(jxl.write.WritableFont.ARIAL, 12, jxl.write.WritableFont.NO_BOLD, false, jxl.format.UnderlineStyle.NO_UNDERLINE, jxl.format.Colour.BLACK, jxl.format.ScriptStyle.NORMAL_SCRIPT)
        val valueFormatDate = jxl.write.DateFormat("yyyy-MM-dd HH:mm")
        val cellFormat = jxl.write.WritableCellFormat(valueFormatDate)
        cellFormat.setFont(baseFont)
        cellFormat.setShrinkToFit(true)
        cellFormat.setBorder(jxl.format.Border.ALL, jxl.format.BorderLineStyle.THIN)
        cellFormat.alignment = jxl.format.Alignment.CENTRE
        cellFormat.verticalAlignment = jxl.format.VerticalAlignment.TOP
        return cellFormat
    }

    private fun checkCodeAccessibility(project: Project, user: User?): Boolean {
        if (project.projectScope != ProjectScope.PUBLIC) {
            if (user == null) return false
            return projectUserRepository.existsByProjectIdAndUserId(project.id!!, user.id!!) ||
                accessControl.isAllowedIfGroupMember(project, user)
        }
        if (project.isCodeAccessibleMemberOnly == true) {
            if (user == null) return false
            return projectUserRepository.existsByProjectIdAndUserId(project.id!!, user.id!!) ||
                accessControl.isAllowedIfGroupMember(project, user)
        }
        return true
    }
}
