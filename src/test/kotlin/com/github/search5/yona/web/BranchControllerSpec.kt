package com.github.search5.yona.web

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.vcs.Commit
import com.github.search5.yona.domain.vcs.GitBranch
import com.github.search5.yona.domain.vcs.PlayRepository
import com.github.search5.yona.domain.vcs.RepositoryService
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

class BranchControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val repositoryService = mockk<RepositoryService>()
    val playRepository = mockk<PlayRepository>()

    val branchController = BranchController(
        projectRepository,
        repositoryService
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(branchController).build()

    beforeTest {
        io.mockk.clearMocks(projectRepository, repositoryService, playRepository)
    }

    describe("BranchController 웹 API 테스트") {
        val project = Project(id = 1L, name = "TestProject", owner = "owner", vcs = "git")
        val mockCommit = mockk<Commit>()
        val headBranch = GitBranch(name = "refs/heads/master", headCommit = mockCommit)
        val otherBranch = GitBranch(name = "refs/heads/feature-a", headCommit = mockCommit)

        describe("GET /projects/{owner}/{projectName}/branches") {
            it("성공 시 200 OK와 올바른 뷰 이름, 모델 속성을 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "TestProject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepository
                every { playRepository.getBranches() } returns listOf(headBranch, otherBranch)
                every { playRepository.getHeadBranch() } returns headBranch

                mockMvc.perform(
                    get("/projects/owner/TestProject/branches")
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/branches"))
                    .andExpect(model().attributeExists("project", "allBranches", "headBranch"))

                verify { playRepository.getBranches() }
                verify { playRepository.getHeadBranch() }
            }
        }

        describe("POST /projects/{owner}/{projectName}/code/{branch}/setAsDefault") {
            it("성공 시 302 리다이렉트와 setDefaultBranch 메소드가 정상 호출되어야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "TestProject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepository
                every { playRepository.setDefaultBranch("feature-a") } returns Unit

                mockMvc.perform(
                    post("/projects/owner/TestProject/code/feature-a/setAsDefault")
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/projects/owner/TestProject/branches"))

                verify { playRepository.setDefaultBranch("feature-a") }
            }
        }

        describe("DELETE /projects/{owner}/{projectName}/code/{branch}") {
            it("성공 시 302 리다이렉트와 deleteBranch 메소드가 정상 호출되어야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "TestProject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepository
                every { playRepository.deleteBranch("feature-a") } returns Unit

                mockMvc.perform(
                    delete("/projects/owner/TestProject/code/feature-a")
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/projects/owner/TestProject/branches"))

                verify { playRepository.deleteBranch("feature-a") }
            }
        }
    }
})
