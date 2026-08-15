package com.github.search5.yona.config

import com.github.search5.yona.domain.attachment.AttachmentRepository
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.watch.WatchRepository
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueLabel
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.project.Project
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Component("templateHelper")
class TemplateHelper(
    private val messageSource: MessageSource,
    private val watchRepository: WatchRepository,
    private val attachmentRepository: AttachmentRepository,
    private val issueRepository: IssueRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val postingRepository: PostingRepository
) {

    fun agoOrDateString(instant: Instant?): String {
        if (instant == null) return ""
        val now = Instant.now()
        val duration = Duration.between(instant, now)
        val days = duration.toDays()

        val zone = ZoneId.systemDefault()
        val dateYear = instant.atZone(zone).year
        val thisYear = now.atZone(zone).year

        return if (days < 8) {
            agoString(duration)
        } else if (dateYear == thisYear) {
            val formatter = DateTimeFormatter.ofPattern("MM-dd").withZone(zone)
            formatter.format(instant)
        } else {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(zone)
            formatter.format(instant)
        }
    }

    fun getDateString(instant: Instant?): String {
        if (instant == null) return ""
        val zone = ZoneId.systemDefault()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd h:mm:ss a").withZone(zone)
        return formatter.format(instant)
    }

    fun agoString(duration: Duration): String {
        val sec = duration.seconds
        val locale = LocaleContextHolder.getLocale()

        return when {
            sec >= 86400 -> {
                val days = duration.toDays()
                plural("common.time.day", days, locale)
            }
            sec >= 3600 -> {
                val hours = duration.toHours()
                plural("common.time.hour", hours, locale)
            }
            sec >= 60 -> {
                val minutes = duration.toMinutes()
                plural("common.time.minute", minutes, locale)
            }
            sec > 0 -> {
                val seconds = sec
                plural("common.time.second", seconds, locale)
            }
            else -> {
                messageSource.getMessage("common.time.just", null, locale)
            }
        }
    }

    fun getWatchingCount(projectId: Long?): Long {
        if (projectId == null) return 0L
        return watchRepository.countByResourceTypeAndResourceId(ResourceType.PROJECT, projectId.toString())
    }

    fun hasProjectLogo(projectId: Long?): Boolean {
        if (projectId == null) return false
        val attachments = attachmentRepository.findByContainerTypeAndContainerId(
            ResourceType.PROJECT,
            projectId.toString()
        )
        return attachments.isNotEmpty()
    }

    private fun plural(key: String, count: Long, locale: Locale): String {
        val resolvedKey = if (count != 1L) {
            "${key}s"
        } else {
            key
        }
        return messageSource.getMessage(resolvedKey, arrayOf(count.toString()), locale)
    }

    fun showHeaderWordsInBracketsIfExist(title: String?): String {
        if (title.isNullOrEmpty()) return ""
        val regex = "^\\[.*?\\]".toRegex()
        val match = regex.find(title)
        return match?.value ?: ""
    }

    fun removeHeaderWords(title: String?): String {
        if (title.isNullOrEmpty()) return ""
        val regex = "^\\[.*?\\]\\s*".toRegex()
        return title.replace(regex, "")
    }

    fun hasChildIssue(issue: Issue): Boolean {
        val issueId = issue.id ?: return false
        return issueRepository.countByParentId(issueId) > 0
    }

    fun countByParentIssueIdAndState(parentId: Long, state: com.github.search5.yona.domain.enumeration.State): Long {
        return issueRepository.countByParentIdAndState(parentId, state)
    }

    fun getPercent(numerator: Double, denominator: Double): Double {
        if (denominator == 0.0) return 0.0
        return (numerator / denominator) * 100.0
    }

    fun getPercentFormatted(numerator: Long, denominator: Long): String {
        val pct = getPercent(numerator.toDouble(), (numerator + denominator).toDouble())
        return String.format(Locale.US, "%.0f", pct)
    }

    fun isOverDueDate(issue: Issue): Boolean {
        val due = issue.dueDate ?: return false
        return issue.state == com.github.search5.yona.domain.enumeration.State.OPEN && Instant.now().isAfter(due)
    }

    fun until(issue: Issue): String {
        val due = issue.dueDate ?: return ""
        val zone = ZoneId.systemDefault()
        val nowDate = Instant.now().atZone(zone).toLocalDate()
        val dueDate = due.atZone(zone).toLocalDate()

        val locale = LocaleContextHolder.getLocale()

        if (nowDate.isEqual(dueDate)) {
            return messageSource.getMessage("common.time.today", null, locale)
        }

        val days = java.time.temporal.ChronoUnit.DAYS.between(nowDate, dueDate)
        return if (days < 0) {
            messageSource.getMessage("common.time.default.day", arrayOf((-days).toString()), locale)
        } else {
            messageSource.getMessage("common.time.default.day", arrayOf(days.toString()), locale)
        }
    }

    fun getDueDateString(instant: Instant?): String {
        if (instant == null) return ""
        val zone = ZoneId.systemDefault()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(zone)
        return formatter.format(instant)
    }

    fun getIssueLabelsString(labels: Set<IssueLabel>?): String {
        if (labels == null || labels.isEmpty()) return ""
        return labels.sortedWith(compareBy({ it.category.name }, { it.name }))
            .joinToString("|") { label ->
                val categoryName = label.category.name
                val id = label.id ?: ""
                val name = label.name
                val categoryId = label.category.id ?: ""
                val isExclusive = label.category.isExclusive
                "$categoryName,$id,$name,$categoryId,$isExclusive"
            }
    }

    fun countIssues(project: Project): Long {
        return issueRepository.countByProjectAndState(project, com.github.search5.yona.domain.enumeration.State.OPEN)
    }

    fun countPullRequests(project: Project): Long {
        return pullRequestRepository.countByToProjectAndState(project, com.github.search5.yona.domain.enumeration.State.OPEN)
    }

    fun countBoardPosts(project: Project): Long {
        return postingRepository.countByProject(project)
    }
}
