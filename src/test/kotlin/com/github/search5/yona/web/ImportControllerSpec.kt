package com.github.search5.yona.web

import com.github.search5.yona.domain.organization.Organization
import com.github.search5.yona.domain.organization.OrganizationUser
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.project.GitService
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectService
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.eclipse.jgit.api.errors.TransportException
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.io.File
import java.util.Optional

class ImportControllerSpec : DescribeSpec({
    val projectService = mockk<ProjectService>()
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val gitService = mockk<GitService>()

    // ImportController가 아직 존재하지 않으므로, 이 시점의 컴파일 실패 자체가 TDD Red 단계입니다.
    val importController = ImportController(
        projectService,
        projectRepository,
        projectUserRepository,
        userRepository,
        organizationUserRepository,
        gitService
    )

    val mockMvc = MockMvcBuilders.standaloneSetup(importController).build()

    beforeTest {
        io.mockk.clearMocks(
            projectService,
            projectRepository,
            projectUserRepository,
            userRepository,
            organizationUserRepository,
            gitService
        )
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
        every { organizationUserRepository.findByUserIdAndRoleId(10L, any()) } returns emptyList()
    }

    describe("ImportController 웹 테스트") {
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        describe("GET /_import") {
            it("로그인된 유저는 임포팅 폼을 조회할 수 있어야 하고, 조직 목록이 바인딩되어야 한다") {
                mockMvc.perform(
                    get("/_import")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/importing"))
                    .andExpect(model().attributeExists("currentUser"))
                    .andExpect(model().attributeExists("orgUserList"))
            }
        }

        describe("POST /_import") {
            it("정상적인 파라미터를 전송하면 클론 및 프로젝트 생성이 성공하고 리다이렉트되어야 한다") {
                val project = Project(id = 1L, name = "NewProject", owner = "testuser")
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findByOwnerAndName("testuser", "NewProject") } returns Optional.empty()
                every { gitService.cloneRepository("https://github.com/test/repo.git", "testuser", "NewProject", any(), any()) } returns File("/tmp/yuna/git/testuser/NewProject.git")
                every { projectService.createProject(any(), any()) } returns project
                every { projectUserRepository.save(any()) } returns mockk()

                mockMvc.perform(
                    post("/_import")
                        .principal(userAuth)
                        .param("url", "https://github.com/test/repo.git")
                        .param("owner", "testuser")
                        .param("name", "NewProject")
                        .param("overview", "설명")
                        .param("projectScope", "PUBLIC")
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/testuser/NewProject"))

                verify { gitService.cloneRepository("https://github.com/test/repo.git", "testuser", "NewProject", null, null) }
                verify { projectService.createProject(any(), any()) }
            }

            it("필수 파라미터인 url이 누락되면 400 badRequest를 반환하고 폼 화면을 다시 보여주어야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { organizationUserRepository.findByUserIdAndRoleId(10L, RoleType.ORG_ADMIN.roleType) } returns emptyList()

                mockMvc.perform(
                    post("/_import")
                        .principal(userAuth)
                        .param("owner", "testuser")
                        .param("name", "NewProject")
                        .param("url", "")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(view().name("project/importing"))
            }
        }
    }
})
