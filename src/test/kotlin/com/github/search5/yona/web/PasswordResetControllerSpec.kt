package com.github.search5.yona.web

import com.github.search5.yona.domain.mail.MailService
import com.github.search5.yona.domain.user.PasswordResetService
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.model
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

class PasswordResetControllerSpec : DescribeSpec({
    val passwordResetService = mockk<PasswordResetService>()
    val userRepository = mockk<UserRepository>()
    val mailService = mockk<MailService>()
    val passwordResetController = PasswordResetController(passwordResetService, userRepository, mailService)
    val mockMvc = MockMvcBuilders.standaloneSetup(passwordResetController).build()

    beforeTest {
        io.mockk.clearMocks(passwordResetService, userRepository, mailService)
    }

    describe("PasswordResetController 웹 API 테스트") {
        val testUser = User(id = 1L, loginId = "gildong", name = "홍길동", email = "gildong@example.com")

        describe("POST /lostPassword") {
            it("아이디와 이메일이 일치하면 비밀번호 재설정 링크 메일을 전송해야 한다") {
                // Given
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { passwordResetService.generateResetHash("gildong") } returns "reset-hash-token"
                every { passwordResetService.addHashToResetTable("gildong", "reset-hash-token") } returns Unit
                every { mailService.sendHtmlMail(any(), any(), any(), any()) } returns Unit

                // When & Then
                mockMvc.perform(
                    post("/lostPassword")
                        .param("loginId", "gildong")
                        .param("emailAddress", "gildong@example.com")
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("user/lostPassword"))
                    .andExpect(model().attributeExists("successMessage"))

                verify(exactly = 1) { mailService.sendHtmlMail("gildong@example.com", "홍길동", any(), any()) }
            }

            it("아이디와 이메일 정보가 다르면 뷰와 에러메시지를 반환해야 한다") {
                // Given
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)

                // When & Then
                mockMvc.perform(
                    post("/lostPassword")
                        .param("loginId", "gildong")
                        .param("emailAddress", "wrong@example.com")
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("user/lostPassword"))
                    .andExpect(model().attributeExists("errorMessage"))
            }
        }

        describe("GET /user/reset-password") {
            it("유효한 토큰이면 비밀번호 변경을 요청할 수 있는 입력 폼을 제공해야 한다") {
                // Given
                every { passwordResetService.isValidResetHash("reset-hash-token") } returns true

                // When & Then
                mockMvc.perform(
                    get("/user/reset-password")
                        .param("hash", "reset-hash-token")
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("user/resetPassword"))
                    .andExpect(model().attribute("hash", "reset-hash-token"))
            }

            it("만료되거나 잘못된 토큰이면 뷰와 에러메시지를 반환해야 한다") {
                // Given
                every { passwordResetService.isValidResetHash("invalid-hash") } returns false

                // When & Then
                mockMvc.perform(
                    get("/user/reset-password")
                        .param("hash", "invalid-hash")
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("user/resetPassword"))
                    .andExpect(model().attributeExists("errorMessage"))
            }
        }

        describe("POST /user/reset-password") {
            it("토큰 검증 성공 후 새로운 비밀번호로 정상 재설정하고 로그인 화면으로 리다이렉트해야 한다") {
                // Given
                every { passwordResetService.resetPassword("reset-hash-token", "newPass123") } returns true

                // When & Then
                mockMvc.perform(
                    post("/user/reset-password")
                        .param("hashString", "reset-hash-token")
                        .param("password", "newPass123")
                        .param("retypedPassword", "newPass123")
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/users/loginform"))
            }
        }
    }
})
