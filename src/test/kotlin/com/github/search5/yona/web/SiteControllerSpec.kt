package com.github.search5.yona.web

import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.board.Posting
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectService
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.project.ProjectUser
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.user.UserState
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.mail.MailService
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.model
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

import tools.jackson.databind.ObjectMapper
import com.github.search5.yona.domain.support.DiagnosticService
import org.springframework.core.env.Environment
import com.github.search5.yona.domain.support.YonaUpdateService

class SiteControllerSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val projectService = mockk<ProjectService>()
    val mailService = mockk<MailService>()
    val diagnosticService = mockk<DiagnosticService>()
    val yonaUpdateService = mockk<YonaUpdateService>()
    val environment = mockk<Environment>()
    val objectMapper = ObjectMapper()

    val siteController = SiteController(
        userRepository,
        projectRepository,
        projectUserRepository,
        issueRepository,
        postingRepository,
        projectService,
        mailService,
        diagnosticService,
        objectMapper,
        yonaUpdateService,
        environment
    )

    val mockMvc = MockMvcBuilders.standaloneSetup(siteController).build()

    beforeTest {
        io.mockk.clearMocks(
            userRepository,
            projectRepository,
            projectUserRepository,
            issueRepository,
            postingRepository,
            projectService,
            mailService,
            diagnosticService,
            yonaUpdateService,
            environment
        )
    }

    describe("SiteController 관리 기능 명세") {
        var adminUser = User(id = 1L, loginId = "admin", name = "어드민", email = "admin@example.com", state = UserState.SITE_ADMIN)
        var normalUser = User(id = 2L, loginId = "gildong", name = "홍길동", email = "gildong@example.com", state = UserState.ACTIVE)
        val adminAuth = UsernamePasswordAuthenticationToken("admin", "pass")
        val normalAuth = UsernamePasswordAuthenticationToken("gildong", "pass")

        beforeEach {
            adminUser = User(id = 1L, loginId = "admin", name = "어드민", email = "admin@example.com", state = UserState.SITE_ADMIN)
            normalUser = User(id = 2L, loginId = "gildong", name = "홍길동", email = "gildong@example.com", state = UserState.ACTIVE)
        }

        describe("GET /site/userList") {
            it("로그인한 주체가 사이트 관리자인 경우 200 OK와 사용자 관리 뷰를 리턴해야 한다") {
                // Given
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { userRepository.findUsersForAdmin(UserState.ACTIVE, any(), any()) } returns PageImpl(listOf(normalUser))
                every { userRepository.countUsersForAdmin(UserState.SITE_ADMIN, any()) } returns 1

                // When & Then
                mockMvc.perform(
                    get("/site/userList")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("site/userList"))
                    .andExpect(model().attributeExists("users"))
                    .andExpect(model().attribute("adminCount", 1))
            }

            it("로그인한 주체가 관리자가 아니면 403 Forbidden 뷰로 리다이렉트되어야 한다") {
                // Given
                every { userRepository.findByLoginId("gildong") } returns Optional.of(normalUser)

                // When & Then
                mockMvc.perform(
                    get("/site/userList")
                        .principal(normalAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/403"))
            }
        }

        describe("POST /site/toggleAccountLock") {
            it("정상적으로 대상 사용자의 계정 잠금 여부를 반전해야 한다") {
                // Given
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { userRepository.findByLoginId("gildong") } returns Optional.of(normalUser)
                every { userRepository.save(any<User>()) } returns normalUser

                // When & Then
                mockMvc.perform(
                    post("/site/toggleAccountLock")
                        .param("loginId", "gildong")
                        .principal(adminAuth)
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(view().name("redirect:/sites/userList"))

                verify { userRepository.save(match { it.state == UserState.LOCKED }) }
            }
        }

        describe("POST /site/toggleGuestMode") {
            it("정상적으로 게스트 사용자 모드를 토글해야 한다") {
                // Given
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { userRepository.findByLoginId("gildong") } returns Optional.of(normalUser)
                every { userRepository.save(any<User>()) } returns normalUser

                // When & Then
                mockMvc.perform(
                    post("/site/toggleGuestMode")
                        .param("loginId", "gildong")
                        .principal(adminAuth)
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(view().name("redirect:/sites/userList"))

                verify { userRepository.save(match { it.isGuest }) }
            }
        }

        describe("POST /site/users/{loginId}/reset-password") {
            it("임시 비밀번호를 무작위 생성하여 암호화 저장 후 JSON 응답을 주어야 한다") {
                // Given
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { userRepository.findByLoginId("gildong") } returns Optional.of(normalUser)
                every { userRepository.save(any<User>()) } returns normalUser

                // When & Then
                mockMvc.perform(
                    post("/site/users/gildong/reset-password")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.isSuccess").value(true))
                    .andExpect(jsonPath("$.newPassword").exists())

                verify { userRepository.save(match { it.password != null }) }
            }
        }

        describe("DELETE /site/user/delete/{userId}") {
            it("프로젝트 내 유일한 매니저가 아니라면 탈퇴 처리를 수행해야 한다") {
                // Given
                val testRole = Role(id = RoleType.MANAGER.roleType, name = "MANAGER")
                val testProject = Project(id = 100L, name = "test-project", owner = "gildong")
                val member1 = ProjectUser(id = 1L, user = normalUser, project = testProject, role = testRole)
                val anotherManager = User(id = 3L, loginId = "another", name = "다른이")
                val member2 = ProjectUser(id = 2L, user = anotherManager, project = testProject, role = testRole)

                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { userRepository.findById(2L) } returns Optional.of(normalUser)
                every { projectUserRepository.findByUserId(2L) } returns listOf(member1)
                every { projectUserRepository.findByProjectId(100L) } returns listOf(member1, member2)
                every { projectUserRepository.deleteAll(any<List<ProjectUser>>()) } returns Unit
                every { userRepository.save(any<User>()) } returns normalUser

                // When & Then
                mockMvc.perform(
                    delete("/site/user/delete/2")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.isSuccess").value(true))

                verify { userRepository.save(match { it.state == UserState.DELETED }) }
            }

            it("프로젝트 내 유일한 매니저인 경우 탈퇴 처리를 반려해야 한다") {
                // Given
                val testRole = Role(id = RoleType.MANAGER.roleType, name = "MANAGER")
                val testProject = Project(id = 100L, name = "test-project", owner = "gildong")
                val member1 = ProjectUser(id = 1L, user = normalUser, project = testProject, role = testRole)

                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { userRepository.findById(2L) } returns Optional.of(normalUser)
                every { projectUserRepository.findByUserId(2L) } returns listOf(member1)
                every { projectUserRepository.findByProjectId(100L) } returns listOf(member1)

                // When & Then
                mockMvc.perform(
                    delete("/site/user/delete/2")
                        .principal(adminAuth)
                )
                    .andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.reason").value("ONLY_MANAGER"))
            }
        }

        describe("GET /site/projectList") {
            it("프로젝트 목록을 200 OK와 함께 반환해야 한다") {
                // Given
                val testProject = Project(id = 100L, name = "test-project", owner = "gildong")
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { projectRepository.findProjectsForAdmin(any(), any()) } returns PageImpl(listOf(testProject))

                // When & Then
                mockMvc.perform(
                    get("/site/projectList")
                        .param("projectName", "test")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("site/projectList"))
                    .andExpect(model().attributeExists("projects"))
            }
        }

        describe("DELETE /site/project/delete/{projectId}") {
            it("프로젝트를 강제 소거하고 리다이렉트해야 한다") {
                // Given
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { projectService.deleteProject(100L) } returns Unit

                // When & Then
                mockMvc.perform(
                    delete("/site/project/delete/100")
                        .principal(adminAuth)
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(view().name("redirect:/sites/projectList"))

                verify { projectService.deleteProject(100L) }
            }
        }

        describe("GET /site/issueList") {
            it("전체 미해결 이슈 목록을 페이징하여 정상 반환해야 한다") {
                // Given
                val testProject = Project(id = 100L, name = "test", owner = "gildong")
                val testIssue = Issue(id = 10L, title = "버그", body = "버그수정", project = testProject)
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { issueRepository.findByState(State.OPEN, any()) } returns PageImpl(listOf(testIssue))

                // When & Then
                mockMvc.perform(
                    get("/site/issueList")
                        .param("state", "open")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("site/issueList"))
                    .andExpect(model().attributeExists("issues"))
            }
        }

        describe("GET /site/postList") {
            it("전체 자유게시판 글 목록을 페이징하여 정상 반환해야 한다") {
                // Given
                val testProject = Project(id = 100L, name = "test", owner = "gildong")
                val testPost = Posting(id = 20L, title = "공지", body = "공지내용", project = testProject)
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { postingRepository.findAll(any<Pageable>()) } returns PageImpl(listOf(testPost))

                // When & Then
                mockMvc.perform(
                    get("/site/postList")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("site/postList"))
                    .andExpect(model().attributeExists("posts"))
            }
        }

        describe("GET /site/mail") {
            it("메일 작성 폼 뷰를 리턴해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { environment.getProperty("spring.mail.host") } returns "smtp.gmail.com"
                every { environment.getProperty("spring.mail.username") } returns "user@gmail.com"
                every { environment.getProperty("spring.mail.password") } returns "password"
                every { environment.getProperty("spring.mail.properties.mail.smtp.from") } returns "user@gmail.com"

                mockMvc.perform(
                    get("/site/mail")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("site/mail"))
                    .andExpect(model().attributeExists("sender"))
            }
        }

        describe("POST /site/mail") {
            it("메일을 정상적으로 발송하고 메일 결과와 함께 리다이렉트가 아닌 메일 뷰를 렌더링해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { environment.getProperty("spring.mail.host") } returns "smtp.gmail.com"
                every { environment.getProperty("spring.mail.username") } returns "user@gmail.com"
                every { environment.getProperty("spring.mail.password") } returns "password"
                every { mailService.sendHtmlMail("admin@example.com", "target@example.com", "target@example.com", "제목", "내용") } returns Unit

                mockMvc.perform(
                    post("/site/mail")
                        .param("to", "target@example.com")
                        .param("from", "admin@example.com")
                        .param("subject", "제목")
                        .param("body", "내용")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("site/mail"))
                    .andExpect(model().attribute("sended", true))
            }
        }

        describe("GET /site/massmail") {
            it("대량 메일 작성 폼 뷰를 리턴해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)

                mockMvc.perform(
                    get("/site/massmail")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("site/massMail"))
            }
        }

        describe("POST /site/mailList") {
            it("all=true일 때 전체 사용자의 이메일 목록을 JSON으로 리턴해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { userRepository.findAll() } returns listOf(adminUser, normalUser)

                mockMvc.perform(
                    post("/site/mailList")
                        .param("all", "true")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0]").value("admin@example.com"))
                    .andExpect(jsonPath("$[1]").value("gildong@example.com"))
            }

            it("특정 프로젝트들이 선택되었을 때 해당 프로젝트 멤버들의 이메일 목록을 JSON으로 리턴해야 한다") {
                val testProject = Project(id = 100L, name = "test-proj", owner = "owner")
                val testRole = com.github.search5.yona.domain.role.Role(id = RoleType.MEMBER.roleType)
                val projectUser = ProjectUser(id = 10L, user = normalUser, project = testProject, role = testRole)

                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { projectRepository.findByOwnerAndName("owner", "test-proj") } returns Optional.of(testProject)
                every { projectUserRepository.findByProjectId(100L) } returns listOf(projectUser)

                mockMvc.perform(
                    post("/site/mailList")
                        .param("projects", "owner/test-proj")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0]").value("gildong@example.com"))
            }
        }

        describe("GET /site/data") {
            it("로그인한 주체가 관리자일 때 데이터 백업 화면 뷰를 리턴해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)

                mockMvc.perform(
                    get("/site/data")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("site/data"))
            }
        }

        describe("GET /site/export") {
            it("로그인한 주체가 관리자일 때 JSON 백업 데이터를 파일로 내려주어야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { userRepository.findAll() } returns listOf(adminUser, normalUser)
                every { projectRepository.findAll() } returns emptyList()

                mockMvc.perform(
                    get("/site/export")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(content().contentType("application/json"))
            }
        }

        describe("GET /site/noAvatarUsers") {
            it("아바타가 설정되지 않은 회원들의 리스트를 JSON 형태로 반환해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { userRepository.findAll() } returns listOf(adminUser, normalUser)

                mockMvc.perform(
                    get("/site/noAvatarUsers")
                        .principal(adminAuth)
                )
                    .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.users[0].loginId").value("gildong"))
            }
        }

        describe("GET /site/diagnostic") {
            it("시스템 자가진단 분석 뷰를 반환해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                every { diagnosticService.checkAll() } returns listOf("Test Database Warning")

                mockMvc.perform(
                    get("/site/diagnostic")
                        .principal(adminAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("site/diagnostic"))
                    .andExpect(model().attributeExists("errors"))
            }
        }
    }
})

