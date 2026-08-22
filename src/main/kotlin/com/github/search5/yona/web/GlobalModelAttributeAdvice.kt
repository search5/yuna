package com.github.search5.yona.web

import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.support.MarkdownService
import com.github.search5.yona.domain.support.YonaUpdateService
import com.github.search5.yona.config.TemplateHelper
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

@ControllerAdvice
class GlobalModelAttributeAdvice(
    private val userRepository: UserRepository,
    private val markdownService: MarkdownService,
    private val templateHelper: TemplateHelper,
    private val yonaUpdateService: YonaUpdateService,
    private val issueRepository: IssueRepository,
    // yona application.conf의 "application.sendYonaUsage"(Application.SEND_YONA_USAGE) 대응.
    @Value("\${yuna.analytics.send-usage:false}") private val sendYonaUsage: Boolean,
    // yona controllers/Application.java:35 HIDE_PROJECT_LISTING 대응 (P0-23). 기존 컨트롤러들과 동일 키 재사용.
    @Value("\${yuna.application.hide-project-listing:false}") private val hideProjectListing: Boolean
) {

    @ModelAttribute("currentUser")
    fun currentUser(): User? {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication == null || !authentication.isAuthenticated || authentication.name == "anonymousUser") {
            return null
        }
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    @ModelAttribute("markdownService")
    fun markdownService(): MarkdownService {
        return markdownService
    }

    @ModelAttribute("templateHelper")
    fun templateHelper(): TemplateHelper {
        return templateHelper
    }

    @ModelAttribute("yonaUpdateService")
    fun yonaUpdateService(): YonaUpdateService {
        return yonaUpdateService
    }

    @ModelAttribute("currentRequestPath")
    fun currentRequestPath(request: HttpServletRequest): String {
        return request.requestURI
    }

    @ModelAttribute("sendYonaUsage")
    fun sendYonaUsage(): Boolean {
        return sendYonaUsage
    }

    @ModelAttribute("hideProjectListing")
    fun hideProjectListing(): Boolean {
        return hideProjectListing
    }

    // yona Issue.countOpenIssuesByUser(User) 대응.
    @ModelAttribute("myOpenIssueCount")
    fun myOpenIssueCount(): Long {
        val user = currentUser() ?: return 0
        return issueRepository.countByAssigneeAndState(user.id!!, State.OPEN)
    }
}
