package com.github.search5.yona.web

import com.github.search5.yona.domain.project.Label
import com.github.search5.yona.domain.project.LabelRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageRequest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class LabelControllerSpec : DescribeSpec({
    val labelRepository = mockk<LabelRepository>()
    val labelController = LabelController(labelRepository)
    val mockMvc = MockMvcBuilders.standaloneSetup(labelController).build()

    beforeTest {
        io.mockk.clearMocks(labelRepository)
    }

    describe("LabelController 자동완성 API 테스트") {

        describe("GET /labels") {
            it("[Test-15-1-1] limit 파라미터가 없으면 400 Bad Request를 반환해야 한다") {
                mockMvc.perform(get("/labels").param("query", "test"))
                    .andExpect(status().isBadRequest)
            }

            it("[Test-15-1-2] limit 파라미터가 주어지면 조건에 매칭되는 라벨 목록을 200 OK로 반환해야 한다") {
                val label1 = Label(id = 1L, category = "issue", name = "bug")
                val label2 = Label(id = 2L, category = "issue", name = "feature")

                every { labelRepository.countByCategoryContainingIgnoreCaseAndNameContainingIgnoreCase("issue", "bu") } returns 1L
                every {
                    labelRepository.findByCategoryContainingIgnoreCaseAndNameContainingIgnoreCase(
                        "issue",
                        "bu",
                        PageRequest.of(0, 10)
                    )
                } returns listOf(label1)

                mockMvc.perform(
                    get("/labels")
                        .param("category", "issue")
                        .param("query", "bu")
                        .param("limit", "10")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.size()").value(1))
                    .andExpect(jsonPath("$[0]").value("bug"))
            }
        }

        describe("GET /categories") {
            it("[Test-15-2-1] limit 파라미터가 없으면 400 Bad Request를 반환해야 한다") {
                mockMvc.perform(get("/categories"))
                    .andExpect(status().isBadRequest)
            }

            it("[Test-15-2-2] limit 파라미터가 주어지면 중복을 제거한 DISTINCT 카테고리 목록을 200 OK로 반환해야 한다") {
                every { labelRepository.countDistinctCategories() } returns 2L
                every { labelRepository.findDistinctCategories(PageRequest.of(0, 5)) } returns listOf("issue", "release")

                mockMvc.perform(
                    get("/categories")
                        .param("limit", "5")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.size()").value(2))
                    .andExpect(jsonPath("$[0]").value("issue"))
                    .andExpect(jsonPath("$[1]").value("release"))
            }
        }
    }
})
