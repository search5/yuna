package com.github.search5.yona.web

import com.github.search5.yona.domain.user.Email
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.user.UserService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

class UserControllerSpec : DescribeSpec({
    val userService = mockk<UserService>()
    val userRepository = mockk<UserRepository>()
    val userController = UserController(userService, userRepository)
    val mockMvc = MockMvcBuilders.standaloneSetup(userController).build()

    beforeTest {
        io.mockk.clearMocks(userService, userRepository)
    }

    describe("UserController 웹 API 테스트") {
        val testUser = User(id = 1L, loginId = "gildong", name = "홍길동", email = "gildong@example.com")
        val auth = UsernamePasswordAuthenticationToken("gildong", "password")

        describe("GET /api/users") {
            it("사용자 검색어에 일치하는 활성 유저 목록을 반환해야 한다") {
                // Given
                every { userRepository.findAll() } returns listOf(testUser)

                // When & Then
                mockMvc.perform(
                    get("/api/users")
                        .param("query", "gildong")
                        .header("referer", "http://localhost/members")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].loginId").value("gildong"))
                    .andExpect(jsonPath("$[0].info").exists())
            }
        }

        describe("POST /api/users/emails") {
            it("새로운 보조 이메일을 정상적으로 추가해야 한다") {
                // Given
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                val newEmail = Email(id = 10L, user = testUser, email = "gildong-sub@example.com")
                every { userService.addEmail(1L, "gildong-sub@example.com") } returns newEmail

                // When & Then
                mockMvc.perform(
                    post("/api/users/emails")
                        .param("email", "gildong-sub@example.com")
                        .principal(auth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.emailId").value(10))
            }
        }

        describe("DELETE /api/users/emails/{emailId}") {
            it("보조 이메일을 정상적으로 삭제해야 한다") {
                // Given
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userService.deleteEmail(1L, 10L) } returns Unit

                // When & Then
                mockMvc.perform(
                    delete("/api/users/emails/10")
                        .principal(auth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))
            }
        }

        describe("GET /user/emails/{emailId}/confirm") {
            it("토큰이 일치하면 보조 이메일 인증 완료 메시지를 표시해야 한다") {
                // Given
                every { userService.confirmEmail(10L, "test-token-50") } returns true

                // When & Then
                mockMvc.perform(
                    get("/user/emails/10/confirm")
                        .param("token", "test-token-50")
                )
                    .andExpect(status().isOk)
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("이메일 인증이 완료되었습니다.")))
            }
        }

        describe("POST /api/users/emails/{emailId}/set-main") {
            it("서브 이메일을 기본 이메일로 격상하고 성공 상태를 리턴해야 한다") {
                // Given
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userService.setAsMainEmail(1L, 10L) } returns Unit

                // When & Then
                mockMvc.perform(
                    post("/api/users/emails/10/set-main")
                        .principal(auth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))
            }
        }

        describe("GET /user/verify") {
            it("회원가입 계정 인증코드가 맞으면 가입완료 화면을 표시해야 한다") {
                // Given
                every { userService.verifyUser("gildong", "verification-code") } returns true

                // When & Then
                mockMvc.perform(
                    get("/user/verify")
                        .param("loginId", "gildong")
                        .param("code", "verification-code")
                )
                    .andExpect(status().isOk)
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("회원가입 계정 인증이 완료되었습니다.")))
            }
        }

        describe("POST /api/users/profile/update") {
            it("프로필 정보를 정상 수정해야 한다") {
                // Given
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userRepository.findById(1L) } returns Optional.of(testUser)
                every { userService.isEmailExist("new-mail@example.com") } returns false
                every { userRepository.save(any()) } returns testUser

                // When & Then
                mockMvc.perform(
                    post("/api/users/profile/update")
                        .param("name", "신길동")
                        .param("email", "new-mail@example.com")
                        .principal(auth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))
            }

            it("[Test-18-1-2] 프로필 수정 시 이름에 HTML 스크립트가 유입되면 htmlEscape 처리되어 저장되어야 한다") {
                // Given
                val dirtyName = "<script>alert('XSS')</script>길동"
                val expectedCleanName = "&lt;script&gt;alert(&#39;XSS&#39;)&lt;/script&gt;길동"
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userRepository.findById(1L) } returns Optional.of(testUser)
                every { userService.isEmailExist("new-mail@example.com") } returns false

                val capturedUser = io.mockk.slot<User>()
                every { userRepository.save(capture(capturedUser)) } answers { capturedUser.captured }

                // When & Then
                mockMvc.perform(
                    post("/api/users/profile/update")
                        .param("name", dirtyName)
                        .param("email", "new-mail@example.com")
                        .principal(auth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))

                capturedUser.captured.name shouldBe expectedCleanName
            }
        }

        describe("POST /api/users/token/reset") {
            it("사용자의 API 접근 토큰을 재생성하여 반환해야 한다") {
                // Given
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userRepository.findById(1L) } returns Optional.of(testUser)
                every { userRepository.save(any()) } returns testUser

                // When & Then
                mockMvc.perform(
                    post("/api/users/token/reset")
                        .principal(auth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.token").exists())
            }
        }
    }
})
