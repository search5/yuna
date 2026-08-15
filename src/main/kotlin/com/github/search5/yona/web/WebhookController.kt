package com.github.search5.yona.web

import com.github.search5.yona.domain.enumeration.WebhookType
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.webhook.WebhookService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*

@Controller
class WebhookController(
    private val webhookService: WebhookService,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository
) {

    @GetMapping("/projects/{owner}/{projectName}/webhooks")
    fun webhooks(
        @PathVariable("owner") owner: String,
        @PathVariable("projectName") projectName: String,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found")

        val webhooks = webhookService.findByProject(project.id ?: 0L)
        model.addAttribute("project", project)
        model.addAttribute("webhooks", webhooks)
        return "project/setting_webhook"
    }

    @PostMapping("/projects/{owner}/{projectName}/webhooks")
    fun newWebhook(
        @PathVariable("owner") owner: String,
        @PathVariable("projectName") projectName: String,
        @RequestParam("payloadUrl") payloadUrl: String,
        @RequestParam(value = "secret", required = false) secret: String?,
        @RequestParam(value = "gitPush", defaultValue = "false") gitPush: Boolean,
        @RequestParam("webhookType") webhookTypeStr: String
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found")

        val webhookType = try {
            WebhookType.valueOf(webhookTypeStr)
        } catch (e: Exception) {
            WebhookType.SIMPLE
        }

        webhookService.createWebhook(
            project = project,
            payloadUrl = payloadUrl,
            secret = secret,
            gitPush = gitPush,
            webhookType = webhookType
        )

        return "redirect:/projects/$owner/$projectName/webhooks"
    }

    @DeleteMapping("/projects/{owner}/{projectName}/webhooks/{id}")
    @ResponseBody
    fun deleteWebhook(
        @PathVariable("owner") owner: String,
        @PathVariable("projectName") projectName: String,
        @PathVariable("id") id: Long
    ): ResponseEntity<Unit> {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        webhookService.deleteWebhook(id)
        return ResponseEntity.ok().build()
    }
}

// Spring Boot HTTP 예외 래핑 헬퍼
@org.springframework.web.bind.annotation.ResponseStatus(value = HttpStatus.NOT_FOUND)
class ResponseStatusException(status: HttpStatus, reason: String) : RuntimeException(reason)
