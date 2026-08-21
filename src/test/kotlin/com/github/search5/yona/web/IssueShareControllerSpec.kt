package com.github.search5.yona.web

import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.issue.IssueService
import com.github.search5.yona.domain.issue.IssueShareService
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.*

class IssueShareControllerSpec : DescribeSpec({
    val issueShareService = mockk<IssueShareService>()
    val projectRepository = mockk<ProjectRepository>()
    val issueRepository = mockk<IssueRepository>()
    val userRepository = mockk<UserRepository>()
    val issueService = mockk<IssueService>()

    val controller = IssueShareController(
        issueShareService,
        projectRepository,
        issueRepository,
        userRepository,
        issueService
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    beforeTest {
        io.mockk.clearMocks(
            issueShareService,
            projectRepository,
            issueRepository,
            userRepository,
            issueService
        )
    }

    describe("IssueShareController 단위 테스트") {
        val user = User(id = 1L, loginId = "testuser", name = "테스트유저", email = "test@example.com")
        val auth = UsernamePasswordAuthenticationToken("testuser", "password")
        val project = Project(id = 10L, name = "testproject", owner = "testowner")
        val issue = Issue(id = 100L, title = "testissue", project = project, number = 1L)

        describe("GET /-_-api/v1/owners/{owner}/projects/{projectName}/assignableUsers") {
            it("프로젝트 내 담당자 지정 가능한 유저 목록을 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                
                val resultList = listOf(
                    mapOf("loginId" to "testuser", "name" to "나에게 지정", "type" to "user")
                )
                every { issueShareService.findAssignableUsersOfProject(project, "", user) } returns resultList

                mockMvc.perform(
                    get("/-_-api/v1/owners/testowner/projects/testproject/assignableUsers")
                        .principal(auth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].loginId").value("testuser"))
                    .andExpect(jsonPath("$[0].name").value("나에게 지정"))
            }
        }

        describe("POST /-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/assignees") {
            it("성공적으로 담당자를 변경하고 결과를 반환해야 한다") {
                val targetUser = User(id = 2L, loginId = "assigneeUser", name = "담당자", email = "assignee@example.com")

                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue
                every { userRepository.findByLoginId("assigneeUser") } returns Optional.of(targetUser)

                val updatedIssue = Issue(id = 100L, title = "testissue", project = project, number = 1L)
                every { issueService.changeAssignee(100L, targetUser, "testuser") } returns updatedIssue

                val requestBody = """
                    {
                        "assignees": ["assigneeUser"]
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/-_-api/v1/owners/testowner/projects/testproject/issues/1/assignees")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.assignee.loginId").value("assigneeUser"))
                    .andExpect(jsonPath("$.assignee.name").value("담당자"))
            }
        }

        describe("POST /-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/share") {
            it("공유자를 추가하고 결과를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue

                val mockResult = mapOf("action" to "added", "sharer" to "공유대상자")
                every { issueShareService.changeSharer(issue, "sharerLoginId", "user", "add", user) } returns mockResult

                val requestBody = """
                    {
                        "sharer": {
                            "loginId": "sharerLoginId",
                            "type": "user"
                        },
                        "action": "add"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/-_-api/v1/owners/testowner/projects/testproject/issues/1/share")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.action").value("added"))
                    .andExpect(jsonPath("$.sharer").value("공유대상자"))
            }
        }
    }
})
