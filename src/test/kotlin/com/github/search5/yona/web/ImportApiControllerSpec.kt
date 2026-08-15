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
        }
    }
})
