package com.github.search5.yona.web

import com.github.search5.yona.domain.organization.Organization
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.project.*
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.*
import org.eclipse.jgit.api.errors.TransportException
import org.springframework.context.MessageSource
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.io.File
import java.util.*

class ImportApiControllerSpec : DescribeSpec({
    val projectService = mockk<ProjectService>()
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val organizationRepository = mockk<OrganizationRepository>()
    val gitService = mockk<GitService>()
    val messageSource = mockk<MessageSource>(relaxed = true)

    val controller = ImportApiController(
        projectService,
        projectRepository,
        projectUserRepository,
        userRepository,
        organizationUserRepository,
        organizationRepository,
        gitService,
        messageSource
    )

    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    beforeTest {
        clearMocks(
            projectService,
            projectRepository,
            projectUserRepository,
            userRepository,
            organizationUserRepository,
            organizationRepository,
            gitService,
            messageSource
        )
    }

    describe("ImportApiController 테스트") {
        val loginUser = User(id = 1L, loginId = "testuser", name = "Test User")
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        describe("POST /api/new/import") {
            it("성공 시 200 OK와 함께 생성된 프로젝트 정보를 반환해야 한다") {
                val mockRepoPath = File("/tmp/yuna/git/testuser/yona-imported.git")
                val savedProject = Project(id = 100L, name = "yona-imported", owner = "testuser", overview = "프로젝트 설명")

                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("https://github.com/naver/yona.git", "testuser", "yona-imported", null, null) } returns mockRepoPath
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns mockRepoPath
                every { projectService.createProject(any(), loginUser) } returns savedProject

                val requestJson = """
                    {
                        "url": "https://github.com/naver/yona.git",
                        "owner": "testuser",
                        "name": "yona-imported",
                        "overview": "프로젝트 설명",
                        "projectScope": "PUBLIC"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/api/new/import")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.id").value(100L))
                    .andExpect(jsonPath("$.name").value("yona-imported"))
                    .andExpect(jsonPath("$.owner").value("testuser"))
            }

            it("인증 정보가 없고 unauthorized 에러가 발생한 경우 다국어 처리된 인증 에러 메시지와 400 Bad Request를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("https://github.com/naver/yona.git", "testuser", "yona-imported", null, null) } throws TransportException("not authorized")
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yuna/git/testuser/yona-imported.git")
                every { messageSource.getMessage("project.import.error.transport.unauthorized", null, any()) } returns "인증 권한이 필요합니다."

                val requestJson = """
                    {
                        "url": "https://github.com/naver/yona.git",
                        "owner": "testuser",
                        "name": "yona-imported"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/api/new/import")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("인증 권한이 필요합니다."))
            }

            it("인증 정보가 없을 경우 401 Unauthorized를 반환해야 한다") {
                val requestJson = """{"url": "a", "owner": "a", "name": "a"}"""
                mockMvc.perform(post("/api/new/import").contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isUnauthorized)
            }

            it("게스트 유저일 경우 403 Forbidden을 반환해야 한다") {
                val guestUser = User(id = 2L, loginId = "guest", name = "Guest", isGuest = true)
                every { userRepository.findByLoginId("guest") } returns Optional.of(guestUser)
                val requestJson = """{"url": "a", "owner": "a", "name": "a"}"""
                mockMvc.perform(post("/api/new/import").principal(UsernamePasswordAuthenticationToken("guest", "")).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.error").value("Guest users cannot create projects."))
            }

            it("owner가 유저도 아니고 조직도 아닐 경우 400 Bad Request를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { userRepository.findByLoginId("invalid_owner") } returns Optional.empty()
                every { organizationRepository.findByName("invalid_owner") } returns Optional.empty()
                val requestJson = """{"url": "a", "owner": "invalid_owner", "name": "a"}"""
                mockMvc.perform(post("/api/new/import").principal(userAuth).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Invalid owner"))
            }

            it("owner가 다른 유저일 경우 400 Bad Request를 반환해야 한다") {
                val otherUser = User(id = 3L, loginId = "other", name = "Other")
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { userRepository.findByLoginId("other") } returns Optional.of(otherUser)
                every { organizationRepository.findByName("other") } returns Optional.empty()
                val requestJson = """{"url": "a", "owner": "other", "name": "a"}"""
                mockMvc.perform(post("/api/new/import").principal(userAuth).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Invalid owner"))
            }

            it("owner가 조직인데 어드민 권한이 없을 경우 403 Forbidden을 반환해야 한다") {
                val org = Organization(id = 10L, name = "myorg")
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { userRepository.findByLoginId("myorg") } returns Optional.empty()
                every { organizationRepository.findByName("myorg") } returns Optional.of(org)
                every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 1L) } returns Optional.empty()
                val requestJson = """{"url": "a", "owner": "myorg", "name": "a"}"""
                mockMvc.perform(post("/api/new/import").principal(userAuth).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.error").value("No permission for this organization"))
            }

            it("프로젝트 이름이 이미 존재할 경우 400 Bad Request를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.of(Project())
                val requestJson = """{"url": "a", "owner": "testuser", "name": "yona-imported"}"""
                mockMvc.perform(post("/api/new/import").principal(userAuth).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Project name already exists"))
            }

            it("URL이 비어있을 경우 400 Bad Request를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                val requestJson = """{"url": "  ", "owner": "testuser", "name": "yona-imported"}"""
                mockMvc.perform(post("/api/new/import").principal(userAuth).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("URL cannot be empty"))
            }

            it("InvalidRemoteException 발생 시 400 Bad Request를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("http://invalid", "testuser", "yona-imported", null, null) } throws org.eclipse.jgit.api.errors.InvalidRemoteException("invalid")
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yuna/git/testuser/yona-imported.git")
                every { messageSource.getMessage("project.import.error.wrong.url", null, any()) } returns "잘못된 URL"
                val requestJson = """{"url": "http://invalid", "owner": "testuser", "name": "yona-imported"}"""
                mockMvc.perform(post("/api/new/import").principal(userAuth).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("잘못된 URL"))
            }

            it("JGitInternalException 발생 시 400 Bad Request를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("http://invalid", "testuser", "yona-imported", null, null) } throws org.eclipse.jgit.api.errors.JGitInternalException("internal")
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yuna/git/testuser/yona-imported.git")
                every { messageSource.getMessage("project.import.error.wrong.url", null, any()) } returns "잘못된 URL"
                val requestJson = """{"url": "http://invalid", "owner": "testuser", "name": "yona-imported"}"""
                mockMvc.perform(post("/api/new/import").principal(userAuth).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("잘못된 URL"))
            }

            it("TransportException with credentials not authorized") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("http://auth", "testuser", "yona-imported", "id", "pw") } throws TransportException("not authorized")
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yuna/git/testuser/yona-imported.git")
                every { messageSource.getMessage("project.import.error.transport.failedToAuth", null, any()) } returns "인증 실패"
                val requestJson = """{"url": "http://auth", "owner": "testuser", "name": "yona-imported", "authId": "id", "authPw": "pw"}"""
                mockMvc.perform(post("/api/new/import").principal(userAuth).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("인증 실패"))
            }

            it("TransportException with service not permitted") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("http://forbidden", "testuser", "yona-imported", null, null) } throws TransportException(java.text.MessageFormat.format(org.eclipse.jgit.internal.JGitText.get().serviceNotPermitted, ""))
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yuna/git/testuser/yona-imported.git")
                every { messageSource.getMessage("project.import.error.transport.forbidden", null, any()) } returns "접근 금지"
                val requestJson = """{"url": "http://forbidden", "owner": "testuser", "name": "yona-imported"}"""
                mockMvc.perform(post("/api/new/import").principal(userAuth).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("접근 금지"))
            }

            it("TransportException other") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("http://other", "testuser", "yona-imported", null, null) } throws TransportException("some 404 error")
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yuna/git/testuser/yona-imported.git")
                every { messageSource.getMessage("project.import.error.transport", arrayOf("404"), any()) } returns "전송 에러"
                val requestJson = """{"url": "http://other", "owner": "testuser", "name": "yona-imported"}"""
                mockMvc.perform(post("/api/new/import").principal(userAuth).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("전송 에러"))
            }

            it("일반 Exception 발생 시 400 Bad Request를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("http://error", "testuser", "yona-imported", null, null) } throws RuntimeException("Unknown error")
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yuna/git/testuser/yona-imported.git")
                val requestJson = """{"url": "http://error", "owner": "testuser", "name": "yona-imported"}"""
                mockMvc.perform(post("/api/new/import").principal(userAuth).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Unknown error"))
            }

            it("조직으로 임포트 성공 시 200 OK와 함께 생성된 프로젝트 정보를 반환해야 한다") {
                val org = Organization(id = 10L, name = "myorg")
                val mockOrgUser = mockk<com.github.search5.yona.domain.organization.OrganizationUser>()
                every { mockOrgUser.role.id } returns RoleType.ORG_ADMIN.roleType

                val mockRepoPath = File("/tmp/yuna/git/myorg/yona-imported.git")
                val savedProject = Project(id = 100L, name = "yona-imported", owner = "myorg", overview = "프로젝트 설명")
                
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { userRepository.findByLoginId("myorg") } returns Optional.empty()
                every { organizationRepository.findByName("myorg") } returns Optional.of(org)
                every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 1L) } returns Optional.of(mockOrgUser)
                every { projectRepository.findByOwnerAndName("myorg", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("https://github.com/naver/yona.git", "myorg", "yona-imported", null, null) } returns mockRepoPath
                every { gitService.getRepositoryPath("myorg", "yona-imported") } returns mockRepoPath
                every { projectService.createProject(any(), loginUser) } returns savedProject

                val requestJson = """
                    {
                        "url": "https://github.com/naver/yona.git",
                        "owner": "myorg",
                        "name": "yona-imported",
                        "overview": "프로젝트 설명",
                        "projectScope": "PUBLIC"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/api/new/import")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.id").value(100L))
            }
        }
    }
})
