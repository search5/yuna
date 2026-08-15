package com.github.search5.yona.web

import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.milestone.Milestone
import com.github.search5.yona.domain.milestone.MilestoneService
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
import java.time.Instant
import java.util.Optional

class MilestoneControllerSpec : DescribeSpec({
    val milestoneService = mockk<MilestoneService>()
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()

    val milestoneController = MilestoneController(
        milestoneService,
        projectRepository,
        projectUserRepository,
        userRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(milestoneController).build()

    beforeTest {
        io.mockk.clearMocks(milestoneService, projectRepository, projectUserRepository, userRepository)
    }

    describe("MilestoneController 웹 API 테스트") {
        val project = Project(id = 1L, name = "TestProject", projectScope = ProjectScope.PRIVATE)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val managerUser = User(id = 20L, loginId = "manageruser", name = "관리자유저")
        
        val memberRole = Role(id = RoleType.MEMBER.roleType)
        val managerRole = Role(id = RoleType.MANAGER.roleType)

        val projectUser = ProjectUser(id = 100L, user = user, project = project, role = memberRole)
        val projectManagerUser = ProjectUser(id = 101L, user = managerUser, project = project, role = managerRole)

        val milestone = Milestone(id = 30L, title = "마일스톤 1", contents = "상세 내용", state = State.OPEN, project = project)

        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
        val managerAuth = UsernamePasswordAuthenticationToken("manageruser", "password")

        describe("GET /api/projects/{projectId}/milestones") {
            it("비공개 프로젝트일 때 프로젝트 멤버라면 200 OK와 마일스톤 목록을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestones(1L, State.OPEN) } returns listOf(milestone)

                mockMvc.perform(get("/api/projects/1/milestones").param("state", "OPEN").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].title").value("마일스톤 1"))
            }
        }

        describe("GET /api/projects/{projectId}/milestones/{milestoneId}") {
            it("마일스톤 상세 정보를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { milestoneService.getMilestone(30L) } returns milestone
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true

                mockMvc.perform(get("/api/projects/1/milestones/30").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.title").value("마일스톤 1"))
            }
        }

        describe("POST /api/projects/{projectId}/milestones") {
            it("관리자가 마일스톤을 생성하면 201 Created를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("manageruser") } returns Optional.of(managerUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 20L) } returns Optional.of(projectManagerUser)
                every { milestoneService.createMilestone(1L, any()) } returns milestone

                val jsonContent = """
                    {
                        "title": "마일스톤 1",
                        "contents": "상세 내용",
                        "dueDate": null,
                        "state": "OPEN"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/api/projects/1/milestones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(managerAuth)
                )
                    .andExpect(status().isCreated)
            }

            it("일반 멤버가 마일스톤을 생성하려 하면 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)

                val jsonContent = """
                    {
                        "title": "마일스톤 1"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/api/projects/1/milestones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isForbidden)
            }
        }

        describe("PUT /api/projects/{projectId}/milestones/{milestoneId}") {
            it("관리자가 마일스톤을 수정하면 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { milestoneService.getMilestone(30L) } returns milestone
                every { userRepository.findByLoginId("manageruser") } returns Optional.of(managerUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 20L) } returns Optional.of(projectManagerUser)
                every { milestoneService.updateMilestone(30L, "수정된 마일스톤", "수정내용", null, State.OPEN) } returns milestone

                val jsonContent = """
                    {
                        "title": "수정된 마일스톤",
                        "contents": "수정내용",
                        "dueDate": null,
                        "state": "OPEN"
                    }
                """.trimIndent()

                mockMvc.perform(
                    put("/api/projects/1/milestones/30")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(managerAuth)
                )
                    .andExpect(status().isOk)
            }
        }

        describe("DELETE /api/projects/{projectId}/milestones/{milestoneId}") {
            it("관리자가 마일스톤을 삭제하면 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { milestoneService.getMilestone(30L) } returns milestone
                every { userRepository.findByLoginId("manageruser") } returns Optional.of(managerUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 20L) } returns Optional.of(projectManagerUser)
                every { milestoneService.deleteMilestone(30L) } returns Unit

                mockMvc.perform(delete("/api/projects/1/milestones/30").principal(managerAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))
            }
        }
    }
})
