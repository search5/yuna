package com.github.search5.yona.web

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.support.MarkdownService
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.filter.CharacterEncodingFilter
import io.mockk.clearMocks
import org.springframework.test.web.servlet.result.MockMvcResultHandlers

class MarkdownControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val markdownService = mockk<MarkdownService>()

    val markdownController = MarkdownController(
        projectRepository,
        markdownService
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(markdownController)
        .addFilters<StandaloneMockMvcBuilder>(
            CharacterEncodingFilter("UTF-8", true)
        )
        .build()

    beforeTest {
        clearMocks(projectRepository, markdownService)
    }

    describe("MarkdownController 마크다운 렌더링 API TDD 검증") {
        val project = Project(id = 1L, name = "TestProject", owner = "owner")

        it("마크다운 렌더링 API 요청 시 200 OK와 렌더링된 HTML을 반환해야 한다 (TDD Red 예상)") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(project)
            every { markdownService.render("## 테스트 마크다운", true, project) } returns "<h2>테스트 마크다운</h2>\n"

            val requestJson = """
                {
                    "body": "## 테스트 마크다운",
                    "breaks": true
                }
            """.trimIndent()

            mockMvc.perform(
                post("/markdown/owner/TestProject")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson)
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk)
                .andExpect(content().string("<h2>테스트 마크다운</h2>\n"))
        }
    }
})
