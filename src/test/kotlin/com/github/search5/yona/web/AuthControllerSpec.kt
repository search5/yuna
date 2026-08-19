package com.github.search5.yona.web

import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserService
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class AuthControllerSpec : DescribeSpec({
    val userService = mockk<UserService>()
    val authController = AuthController(userService, "")
    val viewResolver = org.springframework.web.servlet.view.InternalResourceViewResolver().apply {
        setPrefix("/templates/")
        setSuffix(".html")
    }
    val mockMvc = MockMvcBuilders.standaloneSetup(authController)
        .setViewResolvers(viewResolver)
        .build()

    beforeTest {
        io.mockk.clearMocks(userService)
    }

    describe("AuthController") {
        describe("GET /login") {
            it("로그인 폼 페이지로 리다이렉트되어야 한다") {
                mockMvc.perform(get("/login"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/users/loginform"))
            }

            it("에러가 있을 경우 에러 파라미터를 담아 리다이렉트되어야 한다") {
                mockMvc.perform(get("/login").param("error", "true"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/users/loginform?error=true"))
            }
        }

        describe("GET /users/loginform") {
            it("로그인 페이지가 정상 반환되어야 한다") {
                mockMvc.perform(get("/users/loginform"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("login"))
            }

            it("에러가 있을 경우 에러 메시지가 모델에 적재되어야 한다") {
                mockMvc.perform(get("/users/loginform").param("error", "true"))
                    .andExpect(status().isOk)
                    .andExpect(model().attributeExists("loginError"))
                    .andExpect(view().name("login"))
            }
        }

        describe("GET /signup") {
            it("회원가입 페이지가 정상 반환되어야 한다") {
                mockMvc.perform(get("/signup"))
                    .andExpect(status().isOk)
                    .andExpect(model().attributeExists("user"))
                    .andExpect(view().name("signup"))
            }
        }

        describe("POST /signup") {
            it("회원가입 요청 시 정상 가입 후 로그인 페이지로 리다이렉트되어야 한다") {
                // Given
                every { userService.isLoginIdExist("gildong") } returns false
                val user = User(id = 1L, loginId = "gildong", name = "홍길동", email = "gildong@example.com")
                every { userService.createUser(any()) } returns user

                // When & Then
                mockMvc.perform(
                    post("/signup")
                        .param("loginId", "gildong")
                        .param("name", "홍길동")
                        .param("email", "gildong@example.com")
                        .param("password", "pass123")
                        .param("retypedPassword", "pass123")
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/users/loginform?signupSuccess"))

                verify(exactly = 1) { userService.createUser(any()) }
            }

            it("비밀번호 재입력이 일치하지 않으면 회원가입 폼이 유지되어야 한다") {
                // Given
                every { userService.isLoginIdExist("gildong") } returns false

                // When & Then
                mockMvc.perform(
                    post("/signup")
                        .param("loginId", "gildong")
                        .param("name", "홍길동")
                        .param("email", "gildong@example.com")
                        .param("password", "pass123")
                        .param("retypedPassword", "differentPass")
                )
                    .andExpect(status().isOk)
                    .andExpect(model().attributeExists("passwordError"))
                    .andExpect(view().name("signup"))

                verify(exactly = 0) { userService.createUser(any()) }
            }

            it("허용된 이메일 도메인 설정이 있고 그 목록에 없는 도메인이면 가입이 거부되어야 한다") {
                val restrictedController = AuthController(userService, "allowed.com")
                val restrictedViewResolver = org.springframework.web.servlet.view.InternalResourceViewResolver().apply {
                    setPrefix("/templates/")
                    setSuffix(".html")
                }
                val restrictedMockMvc = MockMvcBuilders.standaloneSetup(restrictedController)
                    .setViewResolvers(restrictedViewResolver)
                    .build()
                every { userService.isLoginIdExist("gildong") } returns false

                restrictedMockMvc.perform(
                    post("/signup")
                        .param("loginId", "gildong")
                        .param("name", "홍길동")
                        .param("email", "gildong@notallowed.com")
                        .param("password", "pass123")
                        .param("retypedPassword", "pass123")
                )
                    .andExpect(status().isOk)
                    .andExpect(model().attributeExists("emailDomainError"))
                    .andExpect(view().name("signup"))

                verify(exactly = 0) { userService.createUser(any()) }
            }
        }
    }
})
