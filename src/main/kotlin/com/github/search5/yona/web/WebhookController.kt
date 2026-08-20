package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.enumeration.Operation
import com.github.search5.yona.domain.enumeration.WebhookType
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.webhook.WebhookService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

// yona ProjectApp.java:1268,1283,1313 webhooks()/newWebhook()/deleteWebhook() 셋 다 걸려 있던
// `@IsAllowed(Operation.UPDATE)`(resourceType 기본값 PROJECT) 대응 (P1-87). yuna는 이 세 엔드포인트에
// 로그인 체크 자체가 없어 미인증 사용자가 임의 프로젝트의 웹훅(secret 포함)을 조회/생성/삭제할 수 있던
// 취약점이었다. resourceType 기본값 PROJECT는 `Resource.getResourceObject()`가 project 자신을
// `GlobalResource`로 반환해 `isGlobalResourceAllowed()`의 PROJECT 케이스(매니저 또는 조직관리자만)를
// 타는 것이지, `isProjectResourceAllowed()`의 일반 멤버 규칙이 아니다 — Serena LSP로 `IsAllowedAction`/
// `IsAllowed`/`Resource.getResourceObject()`를 직접 대조해 확인(백로그의 이전 추정 "isMemberOf만 있으면
// 허용"은 부정확했음).
@Controller
class WebhookController(
    private val webhookService: WebhookService,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val accessControl: AccessControl
) {

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    private fun checkWebhookPermission(project: Project, user: User?): Boolean {
        return accessControl.isAllowed(user, project, Operation.UPDATE)
    }

    @GetMapping("/projects/{owner}/{projectName}/webhooks")
    fun webhooks(
        @PathVariable("owner") owner: String,
        @PathVariable("projectName") projectName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found")

        val user = getLoginUser(authentication)
        if (!checkWebhookPermission(project, user)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden")
        }

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
        @RequestParam("webhookType") webhookTypeStr: String,
        authentication: Authentication?
    ): String {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found")

        val user = getLoginUser(authentication)
        if (!checkWebhookPermission(project, user)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden")
        }

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
        @PathVariable("id") id: Long,
        authentication: Authentication?
    ): ResponseEntity<Unit> {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val user = getLoginUser(authentication)
        if (!checkWebhookPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        webhookService.deleteWebhook(id)
        return ResponseEntity.ok().build()
    }
}
