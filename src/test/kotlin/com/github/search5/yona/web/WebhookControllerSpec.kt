package com.github.search5.yona.web

import com.github.search5.yona.domain.enumeration.WebhookType
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.webhook.Webhook
import com.github.search5.yona.domain.webhook.WebhookService
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

class WebhookControllerSpec : DescribeSpec({
    val webhookService = mockk<WebhookService>()
    val projectRepository = mockk<ProjectRepository>()
    val userRepository = mockk<UserRepository>()

    val webhookController = WebhookController(webhookService, projectRepository, userRepository)
    val mockMvc = MockMvcBuilders.standaloneSetup(webhookController).build()

    beforeTest {
        io.mockk.clearMocks(webhookService, projectRepository, userRepository)
    }

    describe("WebhookController API 단위 테스트") {
        val userAuth = UsernamePasswordAuthenticationToken("owner", "password")
        val project = Project(id = 1L, owner = "owner", name = "test-project")
        val webhook = Webhook(
            id = 10L,
            project = project,
            payloadUrl = "http://localhost:8080/hook",
            secret = "secret",
            gitPush = false,
            webhookType = WebhookType.SIMPLE
        )

        describe("GET /projects/{owner}/{projectName}/webhooks") {
            it("웹훅 설정 페이지 뷰를 정상 반환한다") {
                every { projectRepository.findByOwnerAndName("owner", "test-project") } returns Optional.of(project)
                every { webhookService.findByProject(1L) } returns listOf(webhook)

                mockMvc.perform(
                    get("/projects/owner/test-project/webhooks")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/setting_webhook"))
                    .andExpect(model().attributeExists("webhooks"))
                    .andExpect(model().attributeExists("project"))
            }
        }

        describe("POST /projects/{owner}/{projectName}/webhooks") {
            it("유효한 웹훅 폼 데이터를 받아 웹훅을 등록하고 웹훅 목록으로 리다이렉트한다") {
                every { projectRepository.findByOwnerAndName("owner", "test-project") } returns Optional.of(project)
                every {
                    webhookService.createWebhook(
                        project,
                        "http://localhost:8080/hook",
                        "secret",
                        false,
                        WebhookType.SIMPLE
                    )
                } returns webhook

                mockMvc.perform(
                    post("/projects/owner/test-project/webhooks")
                        .param("payloadUrl", "http://localhost:8080/hook")
                        .param("secret", "secret")
                        .param("gitPush", "false")
                        .param("webhookType", "SIMPLE")
                        .principal(userAuth)
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/projects/owner/test-project/webhooks"))

                verify(exactly = 1) {
                    webhookService.createWebhook(
                        project,
                        "http://localhost:8080/hook",
                        "secret",
                        false,
                        WebhookType.SIMPLE
                    )
                }
            }
        }

        describe("DELETE /projects/{owner}/{projectName}/webhooks/{id}") {
            it("웹훅 삭제 비즈니스를 호출하고 200 OK를 반환한다") {
                every { projectRepository.findByOwnerAndName("owner", "test-project") } returns Optional.of(project)
                every { webhookService.deleteWebhook(10L) } returns Unit

                mockMvc.perform(
                    delete("/projects/owner/test-project/webhooks/10")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)

                verify(exactly = 1) { webhookService.deleteWebhook(10L) }
            }
        }
    }
})
