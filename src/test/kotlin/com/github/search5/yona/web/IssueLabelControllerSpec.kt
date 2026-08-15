package com.github.search5.yona.web

import com.github.search5.yona.domain.issue.IssueLabel
import com.github.search5.yona.domain.issue.IssueLabelCategory
import com.github.search5.yona.domain.issue.IssueLabelService
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

class IssueLabelControllerSpec : DescribeSpec({
    val issueLabelService = mockk<IssueLabelService>()
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()

    val issueLabelController = IssueLabelController(
        issueLabelService,
        projectRepository,
        projectUserRepository,
        userRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(issueLabelController).build()

    beforeTest {
        io.mockk.clearMocks(issueLabelService, projectRepository, projectUserRepository, userRepository)
    }

    describe("IssueLabelController 웹 API 테스트") {
        val project = Project(id = 1L, name = "TestProject", projectScope = ProjectScope.PRIVATE)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val managerUser = User(id = 20L, loginId = "manageruser", name = "관리자유저")
        
        val memberRole = Role(id = RoleType.MEMBER.roleType)
        val managerRole = Role(id = RoleType.MANAGER.roleType)

        val projectUser = ProjectUser(id = 100L, user = user, project = project, role = memberRole)
        val projectManagerUser = ProjectUser(id = 101L, user = managerUser, project = project, role = managerRole)

        val category = IssueLabelCategory(id = 200L, name = "카테고리 1", isExclusive = false, project = project)
        val label = IssueLabel(id = 300L, category = category, color = "#ffffff", name = "버그", project = project)

        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
        val managerAuth = UsernamePasswordAuthenticationToken("manageruser", "password")

        describe("GET /api/projects/{projectId}/labels") {
            it("비공개 프로젝트일 때 프로젝트 멤버라면 200 OK와 라벨/카테고리 목록을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { issueLabelService.getLabels(1L) } returns listOf(label)
                every { issueLabelService.getCategories(1L) } returns listOf(category)

                mockMvc.perform(get("/api/projects/1/labels").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.labels[0].name").value("버그"))
                    .andExpect(jsonPath("$.categories[0].name").value("카테고리 1"))
            }
        }

        describe("POST /api/projects/{projectId}/labels") {
            it("관리자가 새 라벨을 생성하면 201 Created를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("manageruser") } returns Optional.of(managerUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 20L) } returns Optional.of(projectManagerUser)
                every { issueLabelService.createLabel(1L, 200L, "새라벨", "#000000") } returns label

                val jsonContent = """
                    {
                        "categoryId": 200,
                        "name": "새라벨",
                        "color": "#000000"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/api/projects/1/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(managerAuth)
                )
                    .andExpect(status().isCreated)
            }
        }

        describe("POST /api/projects/{projectId}/labels/categories") {
            it("관리자가 새 라벨 카테고리를 생성하면 201 Created를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("manageruser") } returns Optional.of(managerUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 20L) } returns Optional.of(projectManagerUser)
                every { issueLabelService.createCategory(1L, "새카테고리", true) } returns category

                val jsonContent = """
                    {
                        "name": "새카테고리",
                        "isExclusive": true
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/api/projects/1/labels/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(managerAuth)
                )
                    .andExpect(status().isCreated)
            }
        }

        describe("DELETE /api/projects/{projectId}/labels/{labelId}") {
            it("관리자가 라벨을 삭제하면 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("manageruser") } returns Optional.of(managerUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 20L) } returns Optional.of(projectManagerUser)
                every { issueLabelService.deleteLabel(300L) } returns Unit

                mockMvc.perform(delete("/api/projects/1/labels/300").principal(managerAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))
            }
        }

        describe("DELETE /api/projects/{projectId}/labels/categories/{categoryId}") {
            it("관리자가 라벨 카테고리를 삭제하면 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("manageruser") } returns Optional.of(managerUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 20L) } returns Optional.of(projectManagerUser)
                every { issueLabelService.deleteCategory(200L) } returns Unit

                mockMvc.perform(delete("/api/projects/1/labels/categories/200").principal(managerAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))
            }
        }
    }
})
