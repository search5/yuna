package com.github.search5.yona.web

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.vcs.PlayRepository
import com.github.search5.yona.domain.vcs.RepositoryService
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

class BranchApiControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val repositoryService = mockk<RepositoryService>()
    val playRepository = mockk<PlayRepository>()

    val branchApiController = BranchApiController(
        projectRepository,
        projectUserRepository,
        userRepository,
        repositoryService
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(branchApiController).build()

    beforeTest {
        io.mockk.clearMocks(projectRepository, projectUserRepository, userRepository, repositoryService, playRepository)
    }

    describe("BranchApiController 웹 API 테스트") {
        val project = Project(id = 1L, name = "TestProject", owner = "owner", vcs = "git", projectScope = ProjectScope.PUBLIC)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        describe("POST /{owner}/{projectName}/code/{branch}/setAsDefault") {
            it("성공 시 302 리다이렉트와 setDefaultBranch 메소드가 정상 호출되어야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "TestProject") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { repositoryService.getRepository(project) } returns playRepository
                every { playRepository.setDefaultBranch("feature-a") } returns Unit

                mockMvc.perform(
                    post("/owner/TestProject/code/feature-a/setAsDefault").principal(userAuth)
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/owner/TestProject/branches"))

                verify { playRepository.setDefaultBranch("feature-a") }
            }
        }

        describe("DELETE /{owner}/{projectName}/code/{branch}") {
            it("성공 시 302 리다이렉트와 deleteBranch 메소드가 정상 호출되어야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "TestProject") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { repositoryService.getRepository(project) } returns playRepository
                every { playRepository.deleteBranch("feature-a") } returns Unit

                mockMvc.perform(
                    delete("/owner/TestProject/code/feature-a").principal(userAuth)
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/owner/TestProject/branches"))

                verify { playRepository.deleteBranch("feature-a") }
            }
        }
    }
})
