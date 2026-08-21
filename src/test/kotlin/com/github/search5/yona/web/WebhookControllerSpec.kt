package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.enumeration.Operation
import com.github.search5.yona.domain.enumeration.WebhookType
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.user.User
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

// yona ProjectApp.java:1268,1283,1313 @IsAllowed(Operation.UPDATE)(resourceType 기본값 PROJECT)
// 대응(P1-87) — 세 엔드포인트 전부 매니저 또는 조직관리자만 허용됨을 검증한다.
class WebhookControllerSpec : DescribeSpec({
    val webhookService = mockk<WebhookService>()
    val projectRepository = mockk<ProjectRepository>()
    val userRepository = mockk<UserRepository>()
    val accessControl = mockk<AccessControl>()

    val webhookController = WebhookController(webhookService, projectRepository, userRepository, accessControl)
    val mockMvc = MockMvcBuilders.standaloneSetup(webhookController).build()

    beforeTest {
        io.mockk.clearMocks(webhookService, projectRepository, userRepository, accessControl)
    }

    describe("WebhookController API 단위 테스트") {
        val userAuth = UsernamePasswordAuthenticationToken("owner", "password")
        val project = Project(id = 1L, owner = "owner", name = "test-project")
        val managerUser = User(id = 100L, loginId = "owner", name = "owner")
        val webhook = Webhook(
            id = 10L,
            project = project,
            payloadUrl = "http://localhost:8080/hook",
            secret = "secret",
            gitPush = false,
            webhookType = WebhookType.SIMPLE
        )

        beforeTest {
            every { userRepository.findByLoginId("owner") } returns Optional.of(managerUser)
            every { accessControl.isAllowed(managerUser, project, Operation.UPDATE) } returns true
        }

        describe("GET /projects/{owner}/{projectName}/webhooks") {
            it("웹훅 설정 페이지 뷰를 정상 반환한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
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
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
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

            // yona Webhook.java:74-81 @Required/@Size(payloadUrl<=2000, secret<=250) 대응 (P2-28).
            // yona는 Play 폼 바인딩 단계에서 이 검증을 통과 못하면 DB에 닿기도 전에 400을 반환하는데,
            // yuna는 이 사전 검증이 없어 그대로 DB에 넣으려다 컬럼 길이 제약 위반(500)이 노출될 수 있었다.
            it("payloadUrl이 비어있으면 400 Bad Request를 반환하고 저장을 시도하지 않는다 (P2-28)") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)

                mockMvc.perform(
                    post("/projects/owner/test-project/webhooks")
                        .param("payloadUrl", "")
                        .param("secret", "secret")
                        .param("gitPush", "false")
                        .param("webhookType", "SIMPLE")
                        .principal(userAuth)
                )
                    .andExpect(status().isBadRequest)

                verify(exactly = 0) { webhookService.createWebhook(any(), any(), any(), any(), any()) }
            }

            it("payloadUrl이 2000자를 넘으면 400 Bad Request를 반환하고 저장을 시도하지 않는다 (P2-28)") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                val tooLongUrl = "http://localhost:8080/" + "a".repeat(2000)

                mockMvc.perform(
                    post("/projects/owner/test-project/webhooks")
                        .param("payloadUrl", tooLongUrl)
                        .param("secret", "secret")
                        .param("gitPush", "false")
                        .param("webhookType", "SIMPLE")
                        .principal(userAuth)
                )
                    .andExpect(status().isBadRequest)

                verify(exactly = 0) { webhookService.createWebhook(any(), any(), any(), any(), any()) }
            }

            it("secret이 250자를 넘으면 400 Bad Request를 반환하고 저장을 시도하지 않는다 (P2-28)") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                val tooLongSecret = "s".repeat(251)

                mockMvc.perform(
                    post("/projects/owner/test-project/webhooks")
                        .param("payloadUrl", "http://localhost:8080/hook")
                        .param("secret", tooLongSecret)
                        .param("gitPush", "false")
                        .param("webhookType", "SIMPLE")
                        .principal(userAuth)
                )
                    .andExpect(status().isBadRequest)

                verify(exactly = 0) { webhookService.createWebhook(any(), any(), any(), any(), any()) }
            }
        }

        describe("DELETE /projects/{owner}/{projectName}/webhooks/{id}") {
            it("웹훅 삭제 비즈니스를 호출하고 200 OK를 반환한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                every { webhookService.deleteWebhook(10L) } returns Unit

                mockMvc.perform(
                    delete("/projects/owner/test-project/webhooks/10")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)

                verify(exactly = 1) { webhookService.deleteWebhook(10L) }
            }
        }

        describe("권한 검사 (P1-87)") {
            it("비로그인 사용자는 웹훅 목록 조회가 403으로 거부된다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                every { accessControl.isAllowed(null, project, Operation.UPDATE) } returns false

                mockMvc.perform(get("/projects/owner/test-project/webhooks"))
                    .andExpect(status().isForbidden)
            }

            it("프로젝트 매니저가 아닌 로그인 사용자는 웹훅 생성이 403으로 거부된다") {
                val strangerAuth = UsernamePasswordAuthenticationToken("stranger", "password")
                val stranger = User(id = 200L, loginId = "stranger", name = "stranger")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                every { userRepository.findByLoginId("stranger") } returns Optional.of(stranger)
                every { accessControl.isAllowed(stranger, project, Operation.UPDATE) } returns false

                mockMvc.perform(
                    post("/projects/owner/test-project/webhooks")
                        .param("payloadUrl", "http://localhost:8080/hook")
                        .param("webhookType", "SIMPLE")
                        .principal(strangerAuth)
                )
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { webhookService.createWebhook(any(), any(), any(), any(), any()) }
            }

            it("프로젝트 매니저가 아닌 로그인 사용자는 웹훅 삭제가 403으로 거부된다") {
                val strangerAuth = UsernamePasswordAuthenticationToken("stranger", "password")
                val stranger = User(id = 200L, loginId = "stranger", name = "stranger")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "test-project") } returns Optional.of(project)
                every { userRepository.findByLoginId("stranger") } returns Optional.of(stranger)
                every { accessControl.isAllowed(stranger, project, Operation.UPDATE) } returns false

                mockMvc.perform(
                    delete("/projects/owner/test-project/webhooks/10")
                        .principal(strangerAuth)
                )
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { webhookService.deleteWebhook(any()) }
            }
        }
    }
})
