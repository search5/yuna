package com.github.search5.yona.web

import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.pullrequest.CommentThreadRepository
import com.github.search5.yona.domain.pullrequest.CommitCommentRepository
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.vcs.GitBranch
import com.github.search5.yona.domain.vcs.PlayRepository
import com.github.search5.yona.domain.vcs.RepositoryService
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.*

class CodeViewControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val repositoryService = mockk<RepositoryService>()
    val commentThreadRepository = mockk<CommentThreadRepository>()
    val commitCommentRepository = mockk<CommitCommentRepository>()

    val controller = CodeViewController(
        projectRepository,
        projectUserRepository,
        userRepository,
        repositoryService,
        commentThreadRepository,
        commitCommentRepository
    )

    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    beforeTest {
        io.mockk.clearMocks(
            projectRepository,
            projectUserRepository,
            userRepository,
            repositoryService,
            commentThreadRepository,
            commitCommentRepository
        )
    }

    describe("CodeViewController 단위 테스트") {
        val project = Project(id = 1L, owner = "testowner", name = "testproject", projectScope = ProjectScope.PUBLIC, vcs = "GIT")
        val playRepo = mockk<PlayRepository>()

        describe("GET /{owner}/{projectName}/code") {
            it("저장소가 비어 있는 경우 code/nohead 뷰로 가야 한다") {
                every { projectRepository.findByOwnerAndName("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.isEmpty() } returns true

                mockMvc.perform(get("/testowner/testproject/code"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/nohead"))
                    .andExpect(model().attribute("project", project))
            }

            it("저장소가 비어 있지 않은 경우 기본 브랜치로 리다이렉트되어야 한다") {
                val gitBranch = mockk<GitBranch>()
                every { gitBranch.shortName } returns "main"
                every { projectRepository.findByOwnerAndName("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.isEmpty() } returns false
                every { playRepo.getHeadBranch() } returns gitBranch

                mockMvc.perform(get("/testowner/testproject/code"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/testowner/testproject/code/main"))
            }

            it("[Test-12-2] 공개 프로젝트이지만 isCodeAccessibleMemberOnly가 true이고 비멤버인 경우 403 Forbidden을 반환해야 한다") {
                val memberOnlyProject = Project(id = 4L, owner = "testowner", name = "memberonly-project", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
                every { projectRepository.findByOwnerAndName("testowner", "memberonly-project") } returns Optional.of(memberOnlyProject)

                mockMvc.perform(get("/testowner/memberonly-project/code"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/403"))
            }
        }

        describe("GET /{owner}/{projectName}/code/{branch}/{*path}") {
            it("지정된 브랜치와 경로의 메타데이터를 담아 code/view 템플릿을 호출해야 한다") {
                val objectMapper = ObjectMapper()
                val mockNode = objectMapper.createObjectNode()
                mockNode.put("type", "file")

                every { projectRepository.findByOwnerAndName("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns listOf("refs/heads/main", "refs/heads/dev")
                every { repositoryService.getMetaDataFromAncestorDirectories(playRepo, "main", "src/Main.kt") } returns listOf(mockNode)

                mockMvc.perform(get("/testowner/testproject/code/main/src/Main.kt"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/view"))
                    .andExpect(model().attribute("project", project))
                    .andExpect(model().attribute("branches", listOf("refs/heads/main", "refs/heads/dev")))
                    .andExpect(model().attribute("branch", "main"))
                    .andExpect(model().attribute("path", "src/Main.kt"))
            }

            it("[Test-12-2-1] 공개 프로젝트이지만 isCodeAccessibleMemberOnly가 true이고 비멤버인 경우 상세 경로 접근 시 403 Forbidden을 반환해야 한다") {
                val memberOnlyProject = Project(id = 4L, owner = "testowner", name = "memberonly-project", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
                every { projectRepository.findByOwnerAndName("testowner", "memberonly-project") } returns Optional.of(memberOnlyProject)

                mockMvc.perform(get("/testowner/memberonly-project/code/main/src/Main.kt"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/403"))
            }
        }

        describe("GET /{owner}/{projectName}/rawcode/{rev}/{*path}") {
            it("바이너리/텍스트 데이터를 응답 헤더와 함께 올바르게 스트리밍해야 한다") {
                val rawBytes = "println('Hello')".toByteArray()

                every { projectRepository.findByOwnerAndName("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getFileAsRaw("testowner", "testproject", "main", "src/Main.kt") } returns rawBytes

                mockMvc.perform(get("/testowner/testproject/rawcode/main/src/Main.kt"))
                    .andExpect(status().isOk)
                    .andExpect(header().string("Content-Disposition", "inline; filename=\"Main.kt\""))
                    .andExpect(content().bytes(rawBytes))
            }

            it("[Test-12-3] 공개 프로젝트이지만 isCodeAccessibleMemberOnly가 true이고 비멤버인 경우 rawcode 다운로드 시 403 Forbidden을 반환해야 한다") {
                val memberOnlyProject = Project(id = 4L, owner = "testowner", name = "memberonly-project", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
                every { projectRepository.findByOwnerAndName("testowner", "memberonly-project") } returns Optional.of(memberOnlyProject)

                mockMvc.perform(get("/testowner/memberonly-project/rawcode/main/src/Main.kt"))
                    .andExpect(status().isForbidden)
            }
        }
    }
})
