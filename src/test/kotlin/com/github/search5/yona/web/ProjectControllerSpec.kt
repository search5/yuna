package com.github.search5.yona.web

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectService
import com.github.search5.yona.domain.project.ProjectUser
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

class ProjectControllerSpec : DescribeSpec({
    val projectService = mockk<ProjectService>()
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()

    val projectController = ProjectController(
        projectService,
        projectRepository,
        projectUserRepository,
        userRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(projectController).build()

    beforeTest {
        io.mockk.clearMocks(projectService, projectRepository, projectUserRepository, userRepository)
    }

    describe("ProjectController 웹 API 테스트") {
        val project = Project(id = 1L, name = "TestProject", owner = "owner", projectScope = ProjectScope.PRIVATE)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val managerRole = Role(id = RoleType.MANAGER.roleType)
        val projectUser = ProjectUser(id = 100L, user = user, project = project, role = managerRole)

        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        describe("PUT /api/projects/{projectId}") {
            it("MANAGER 권한이 있다면 200 OK를 반환하고 설정을 갱신해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every { projectService.updateProject(1L, any()) } returns project

                val jsonContent = """
                    {
                        "overview": "새로운 설명",
                        "projectScope": "PUBLIC",
                        "isCodeAccessibleMemberOnly": false,
                        "isUsingReviewerCount": false,
                        "defaultReviewerCount": 2,
                        "defaultBranch": "refs/heads/master",
                        "isCodeEnabled": true,
                        "isIssueEnabled": true,
                        "isPullRequestEnabled": true,
                        "isReviewEnabled": true,
                        "isMilestoneEnabled": true,
                        "isBoardEnabled": true
                    }
                """.trimIndent()

                mockMvc.perform(
                    put("/api/projects/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)

                verify { projectService.updateProject(1L, any()) }
            }

            it("MANAGER 권한이 없다면 403 Forbidden을 반환해야 한다") {
                val memberRole = Role(id = RoleType.MEMBER.roleType)
                val memberProjectUser = ProjectUser(id = 100L, user = user, project = project, role = memberRole)

                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(memberProjectUser)

                val jsonContent = """
                    {
                        "overview": "새로운 설명",
                        "projectScope": "PUBLIC"
                    }
                """.trimIndent()

                mockMvc.perform(
                    put("/api/projects/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isForbidden)
            }
        }

        describe("DELETE /api/projects/{projectId}") {
            it("소유자(owner) 본인이라면 200 OK를 반환하고 프로젝트를 제거해야 한다") {
                val ownerUser = User(id = 20L, loginId = "owner", name = "소유자")
                val ownerAuth = UsernamePasswordAuthenticationToken("owner", "password")

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("owner") } returns Optional.of(ownerUser)
                every { projectService.deleteProject(1L) } returns Unit

                mockMvc.perform(
                    delete("/api/projects/1")
                        .principal(ownerAuth)
                )
                    .andExpect(status().isOk)

                verify { projectService.deleteProject(1L) }
            }
        }
    }
})
