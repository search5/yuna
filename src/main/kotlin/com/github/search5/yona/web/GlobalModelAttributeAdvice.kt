package com.github.search5.yona.web

import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.support.MarkdownService
import com.github.search5.yona.config.TemplateHelper
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

@ControllerAdvice
class GlobalModelAttributeAdvice(
    private val userRepository: UserRepository,
    private val markdownService: MarkdownService,
    private val templateHelper: TemplateHelper
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
}
