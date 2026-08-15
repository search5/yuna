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
import org.eclipse.jgit.api.errors.InvalidRemoteException
import org.eclipse.jgit.api.errors.TransportException
import org.springframework.context.MessageSource
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.io.File
import java.util.*

class ImportViewControllerSpec : DescribeSpec({
    val projectService = mockk<ProjectService>()
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val organizationRepository = mockk<OrganizationRepository>()
    val gitService = mockk<GitService>()
    val messageSource = mockk<MessageSource>(relaxed = true)

    val controller = ImportViewController(
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

    describe("ImportViewController 테스트") {
        val loginUser = User(id = 1L, loginId = "testuser", name = "Test User")
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        describe("GET /new/import") {
            it("로그인하지 않은 경우 로그인 폼으로 리다이렉트되어야 한다") {
                every { userRepository.findByLoginId(any()) } returns Optional.empty()

                mockMvc.perform(get("/new/import"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/users/loginform"))
            }

            it("로그인한 사용자 정보와 소속 조직 목록을 조회하여 가져오기 화면을 보여주어야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()

                mockMvc.perform(get("/new/import").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/importing"))
                    .andExpect(model().attributeExists("importForm"))
                    .andExpect(model().attributeExists("currentUser"))
                    .andExpect(model().attributeExists("organizations"))
            }
        }

        describe("POST /new/import") {
            val validForm = ImportForm().apply {
                url = "https://github.com/naver/yona.git"
                owner = "testuser"
                name = "yona-imported"
            }

            it("성공적으로 Git 저장소를 복제하고 새 프로젝트를 생성해야 한다") {
                val mockRepoPath = File("/tmp/yuna/git/testuser/yona-imported.git")
                val savedProject = Project(id = 100L, name = "yona-imported", owner = "testuser")

                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("https://github.com/naver/yona.git", "testuser", "yona-imported", null, null) } returns mockRepoPath
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns mockRepoPath
                every { projectService.createProject(any(), loginUser) } returns savedProject

                mockMvc.perform(
                    post("/new/import")
                        .principal(userAuth)
                        .param("url", validForm.url)
                        .param("owner", validForm.owner)
                        .param("name", validForm.name)
                        .param("overview", validForm.overview)
                        .param("projectScope", validForm.projectScope.name)
                        .param("vcs", validForm.vcs)
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/testuser/yona-imported"))

                verify(exactly = 1) { projectService.createProject(any(), loginUser) }
            }

            it("잘못된 Git 리포지토리 URL인 경우 wrong.url 에러와 함께 400 Bad Request를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("https://github.com/invalid/repo.git", "testuser", "yona-imported", null, null) } throws InvalidRemoteException("Invalid remote")
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yuna/git/testuser/yona-imported.git")

                mockMvc.perform(
                    post("/new/import")
                        .principal(userAuth)
                        .param("url", "https://github.com/invalid/repo.git")
                        .param("owner", "testuser")
                        .param("name", "yona-imported")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(view().name("project/importing"))
                    .andExpect(model().hasErrors())
                    .andExpect(model().attributeHasFieldErrorCode("importForm", "url", "project.import.error.wrong.url"))
            }

            it("인증 정보가 없고 unauthorized 에러가 발생한 경우 required 및 unauthorized 에러를 필드에 추가해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("https://github.com/naver/yona.git", "testuser", "yona-imported", null, null) } throws TransportException("not authorized")
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yuna/git/testuser/yona-imported.git")

                mockMvc.perform(
                    post("/new/import")
                        .principal(userAuth)
                        .param("url", "https://github.com/naver/yona.git")
                        .param("owner", "testuser")
                        .param("name", "yona-imported")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(view().name("project/importing"))
                    .andExpect(model().hasErrors())
                    .andExpect(model().attributeHasFieldErrorCode("importForm", "url", "project.import.error.transport.unauthorized"))
                    .andExpect(model().attributeHasFieldErrorCode("importForm", "repoAuth", "required"))
            }

            it("인증 정보를 보냈으나 인증 실패한 경우 authId 필드에 failedToAuth 에러를 추가해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "yona-imported") } returns Optional.empty()
                every { gitService.cloneRepository("https://github.com/naver/yona.git", "testuser", "yona-imported", "wrongId", "wrongPw") } throws TransportException("not authorized")
                every { gitService.getRepositoryPath("testuser", "yona-imported") } returns File("/tmp/yuna/git/testuser/yona-imported.git")

                mockMvc.perform(
                    post("/new/import")
                        .principal(userAuth)
                        .param("url", "https://github.com/naver/yona.git")
                        .param("owner", "testuser")
                        .param("name", "yona-imported")
                        .param("authId", "wrongId")
                        .param("authPw", "wrongPw")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(view().name("project/importing"))
                    .andExpect(model().hasErrors())
                    .andExpect(model().attributeHasFieldErrorCode("importForm", "authId", "project.import.error.transport.failedToAuth"))
            }
        }
    }
})
