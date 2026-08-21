package com.github.search5.yona.web

import com.github.search5.yona.config.YonaAuthenticationProvider
import com.github.search5.yona.domain.issue.RecentIssue
import com.github.search5.yona.domain.issue.RecentIssueService
import com.github.search5.yona.domain.user.Email
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.user.UserService
import com.github.search5.yona.domain.user.UserSetting
import com.github.search5.yona.domain.user.UserSettingRepository
import com.github.search5.yona.domain.user.UserState
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
import io.mockk.clearMocks
import io.mockk.slot
import org.springframework.security.authentication.BadCredentialsException
import org.hamcrest.Matchers

class UserControllerSpec : DescribeSpec({
    val userService = mockk<UserService>()
    val userRepository = mockk<UserRepository>()
    val recentIssueService = mockk<RecentIssueService>()
    val userSettingRepository = mockk<UserSettingRepository>()
    val yonaAuthenticationProvider = mockk<YonaAuthenticationProvider>()
    val userController = UserController(
        userService, userRepository, recentIssueService, userSettingRepository,
        yonaAuthenticationProvider, allowedEmailDomains = "", requireAdminConfirm = false
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(userController).build()

    beforeTest {
        clearMocks(userService, userRepository, recentIssueService, userSettingRepository, yonaAuthenticationProvider)
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
                    .andExpect(content().string(Matchers.containsString("이메일 인증이 완료되었습니다.")))
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
                    .andExpect(content().string(Matchers.containsString("회원가입 계정 인증이 완료되었습니다.")))
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

                val capturedUser = slot<User>()
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

        describe("GET /api/users/me/recent-issues (P1-41)") {
            it("로그인한 사용자의 최근 방문 이슈/게시글 목록을 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { recentIssueService.getRecentIssues(testUser) } returns listOf(
                    RecentIssue(id = 1L, userId = 1L, issueId = 5L, title = "최근 본 이슈", url = "/owner/proj/issue/1")
                )

                mockMvc.perform(
                    get("/api/users/me/recent-issues")
                        .principal(auth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].title").value("최근 본 이슈"))
                    .andExpect(jsonPath("$[0].url").value("/owner/proj/issue/1"))
            }

            it("로그인하지 않았다면 401을 반환해야 한다") {
                mockMvc.perform(get("/api/users/me/recent-issues"))
                    .andExpect(status().isUnauthorized)
            }
        }

        // yona UserApp.java:1372-1380 setDefaultLoginPage() 대응 (P2-11)
        describe("POST /user/setDefaultLoginPage") {
            it("설정이 없던 사용자면 새로 만들어 기본 페이지를 저장해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userSettingRepository.findByUserId(1L) } returns Optional.empty()
                every { userSettingRepository.save(any()) } answers { firstArg() }

                mockMvc.perform(
                    post("/user/setDefaultLoginPage")
                        .param("path", "notifications")
                        .principal(auth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.defaultLoginPage").value("notifications"))

                verify(exactly = 1) { userSettingRepository.save(match { it.loginDefaultPage == "notifications" && it.user == testUser }) }
            }

            it("이미 설정이 있던 사용자면 기존 설정을 갱신해야 한다") {
                val existing = UserSetting(id = 100L, user = testUser, loginDefaultPage = "issues")
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userSettingRepository.findByUserId(1L) } returns Optional.of(existing)
                every { userSettingRepository.save(any()) } answers { firstArg() }

                mockMvc.perform(
                    post("/user/setDefaultLoginPage")
                        .param("path", "pull-requests")
                        .principal(auth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.defaultLoginPage").value("pull-requests"))

                verify(exactly = 1) { userSettingRepository.save(match { it.id == 100L && it.loginDefaultPage == "pull-requests" }) }
            }
        }

        // yona UserApi.java:218-241 newUser() 대응 (P1-118).
        describe("POST /api/users") {
            val siteManager = User(id = 2L, loginId = "admin", name = "관리자", email = "admin@example.com", state = UserState.SITE_ADMIN)
            val adminAuth = UsernamePasswordAuthenticationToken("admin", "password")

            it("사이트관리자가 아니면 400 Bad Request를 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)

                mockMvc.perform(
                    post("/api/users")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"users": [{"loginId": "newbie", "name": "새사람", "email": "newbie@example.com"}]}""")
                )
                    .andExpect(status().isBadRequest)
            }

            it("사이트관리자면 신규 사용자를 생성하고 201과 결과 배열을 반환해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
                every { userRepository.findByEmail("newbie@example.com") } returns Optional.empty()
                every { userService.createUser(any()) } answers {
                    (firstArg() as User).apply { id = 99L }
                }

                mockMvc.perform(
                    post("/api/users")
                        .principal(adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"users": [{"loginId": "newbie", "name": "새사람", "email": "newbie@example.com"}]}""")
                )
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$[0].status").value(201))
                    .andExpect(jsonPath("$[0].user.loginId").value("newbie"))

                verify(exactly = 1) { userService.createUser(match { it.loginId == "newbie" && it.email == "newbie@example.com" }) }
            }

            it("이미 존재하는 이메일이면 결과 배열의 해당 항목에 409를 담아야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
                every { userRepository.findByEmail("dup@example.com") } returns Optional.of(testUser)

                mockMvc.perform(
                    post("/api/users")
                        .principal(adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"users": [{"loginId": "dup", "name": "중복", "email": "dup@example.com"}]}""")
                )
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$[0].status").value(409))

                verify(exactly = 0) { userService.createUser(any()) }
            }
        }

        // yona UserApi.java:244-265 newToken() 대응 (P1-118).
        describe("POST /api/users/token") {
            it("존재하지 않는 아이디/이메일이면 401과 No valid user by id를 반환해야 한다") {
                every { userRepository.findByLoginId("nobody") } returns Optional.empty()
                every { userRepository.findByEmail("nobody") } returns Optional.empty()

                mockMvc.perform(
                    post("/api/users/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"id": "nobody", "password": "pw"}""")
                )
                    .andExpect(status().isUnauthorized)
                    .andExpect(jsonPath("$.message").value("No valid user by id"))
            }

            it("잠긴 계정이면 401과 No valid user by id를 반환해야 한다") {
                val locked = User(id = 3L, loginId = "locked", name = "잠김", email = "locked@example.com", state = UserState.LOCKED)
                every { userRepository.findByLoginId("locked") } returns Optional.of(locked)

                mockMvc.perform(
                    post("/api/users/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"id": "locked", "password": "pw"}""")
                )
                    .andExpect(status().isUnauthorized)
                    .andExpect(jsonPath("$.message").value("No valid user by id"))
            }

            it("비밀번호가 틀리면 401과 No user by id and password를 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every {
                    yonaAuthenticationProvider.authenticate(match<UsernamePasswordAuthenticationToken> { it.name == "gildong" })
                } throws BadCredentialsException("비밀번호가 일치하지 않습니다.")

                mockMvc.perform(
                    post("/api/users/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"id": "gildong", "password": "wrong"}""")
                )
                    .andExpect(status().isUnauthorized)
                    .andExpect(jsonPath("$.message").value("No user by id and password"))
            }

            it("아이디/비밀번호가 맞으면 새 토큰을 발급하고 저장해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every {
                    yonaAuthenticationProvider.authenticate(match<UsernamePasswordAuthenticationToken> { it.name == "gildong" })
                } returns UsernamePasswordAuthenticationToken("gildong", "correct")
                every { userRepository.save(any()) } answers { firstArg() }

                mockMvc.perform(
                    post("/api/users/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"id": "gildong", "password": "correct"}""")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.access_token").isNotEmpty)

                verify(exactly = 1) { userRepository.save(match { it.token != null }) }
            }
        }

        // yona UserApi.java:320-339 users() 대응 (P1-118).
        describe("GET /api/admin/users") {
            val siteManager = User(id = 2L, loginId = "admin", name = "관리자", email = "admin@example.com", state = UserState.SITE_ADMIN)
            val adminAuth = UsernamePasswordAuthenticationToken("admin", "password")

            it("사이트관리자가 아니면 403을 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)

                mockMvc.perform(get("/api/admin/users").principal(auth))
                    .andExpect(status().isForbidden)
            }

            it("사이트관리자면 ACTIVE 사용자 목록을 반환해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
                every { userRepository.findByState(UserState.ACTIVE) } returns listOf(testUser)

                mockMvc.perform(get("/api/admin/users").principal(adminAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].login_id").value("gildong"))
            }
        }

        // yona UserApi.java:341-379 updateUserState() 대응 (P1-118).
        describe("PATCH /api/admin/users/{loginId}") {
            val siteManager = User(id = 2L, loginId = "admin", name = "관리자", email = "admin@example.com", state = UserState.SITE_ADMIN)
            val adminAuth = UsernamePasswordAuthenticationToken("admin", "password")

            it("사이트관리자가 아니면 403을 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)

                mockMvc.perform(
                    patch("/api/admin/users/gildong").principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"state": "LOCKED"}""")
                )
                    .andExpect(status().isForbidden)
            }

            it("SITE_ADMIN 상태로 변경 요청하면 403을 반환해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)

                mockMvc.perform(
                    patch("/api/admin/users/gildong").principal(adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"state": "SITE_ADMIN"}""")
                )
                    .andExpect(status().isForbidden)
            }

            it("정상 상태값이면 사용자 상태를 변경하고 200을 반환해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userRepository.save(any()) } answers { firstArg() }

                mockMvc.perform(
                    patch("/api/admin/users/gildong").principal(adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"state": "LOCKED"}""")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.state").value("LOCKED"))

                verify(exactly = 1) { userRepository.save(match { it.state == UserState.LOCKED }) }
            }
        }
    }
})
