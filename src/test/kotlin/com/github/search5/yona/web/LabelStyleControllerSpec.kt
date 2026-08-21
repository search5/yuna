package com.github.search5.yona.web

import com.github.search5.yona.domain.issue.IssueLabel
import com.github.search5.yona.domain.issue.IssueLabelCategory
import com.github.search5.yona.domain.issue.IssueLabelRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.*

class LabelStyleControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val issueLabelRepository = mockk<IssueLabelRepository>()

    val controller = LabelStyleController(projectRepository, issueLabelRepository)
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    beforeTest {
        io.mockk.clearMocks(projectRepository, issueLabelRepository)
    }

    describe("LabelStyleController 단위 테스트") {
        val project = Project(id = 10L, name = "testproject", owner = "testowner")
        val category = mockk<IssueLabelCategory>()

        describe("GET /{user}/{projectName}/issue/labels.css") {
            it("라벨 목록에 대해 올바른 CSS 콘텐츠를 생성하여 반환해야 한다") {
                val label1 = IssueLabel(id = 1L, category = category, color = "#ff0000", name = "Bug", project = project)
                val label2 = IssueLabel(id = 2L, category = category, color = "#ffffff", name = "Question", project = project)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { issueLabelRepository.findByProject(project) } returns listOf(label1, label2)

                val eTag = "\"${listOf(label1, label2).map { "${it.id}-${it.color}" }.hashCode()}\""

                mockMvc.perform(
                    get("/testowner/testproject/issue/labels.css")
                )
                    .andExpect(status().isOk)
                    .andExpect(content().contentType("text/css;charset=UTF-8"))
                    .andExpect(header().string("ETag", eTag))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(".issue-label[data-label-id=\"1\"]")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("box-shadow: inset 2px 0 0px #ff0000;")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("color: white;"))) // #ff0000는 어두우므로 white
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("color: dimgray;"))) // #ffffff는 밝으므로 dimgray
            }

            it("If-None-Match가 헤더 ETag와 같을 때 304 Not Modified를 반환해야 한다") {
                val label1 = IssueLabel(id = 1L, category = category, color = "#ff0000", name = "Bug", project = project)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { issueLabelRepository.findByProject(project) } returns listOf(label1)

                val eTag = "\"${listOf(label1).map { "${it.id}-${it.color}" }.hashCode()}\""

                mockMvc.perform(
                    get("/testowner/testproject/issue/labels.css")
                        .header("If-None-Match", eTag)
                )
                    .andExpect(status().isNotModified)
                    .andExpect(header().string("ETag", eTag))
            }
        }
    }
})
