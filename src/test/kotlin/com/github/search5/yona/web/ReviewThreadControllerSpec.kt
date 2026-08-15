package com.github.search5.yona.web

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.support.ReviewSearchCondition
import com.github.search5.yona.domain.support.ReviewThreadService
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

class ReviewThreadControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val reviewThreadService = mockk<ReviewThreadService>()
    val userRepository = mockk<UserRepository>()

    val reviewThreadController = ReviewThreadController(
        projectRepository,
        reviewThreadService,
        userRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(reviewThreadController).build()

    beforeTest {
        io.mockk.clearMocks(projectRepository, reviewThreadService, userRepository)
    }

    describe("ReviewThreadController TDD 검증") {
        val project = Project(id = 1L, name = "TestProject", owner = "owner")

        it("리뷰 스레드 목록 화면 요청 시 200 OK와 reviewthread/list 뷰를 반환해야 한다 (TDD Red 예상)") {
            every { projectRepository.findByOwnerAndName("owner", "TestProject") } returns Optional.of(project)
            every { reviewThreadService.getReviewThreads(eq(project), any(), any()) } returns PageImpl(emptyList())
            every { reviewThreadService.countReviewThreads(eq(project), any()) } returns 0L

            mockMvc.perform(
                get("/owner/TestProject/reviews")
            )
                .andExpect(status().isOk)
                .andExpect(view().name("reviewthread/list"))
                .andExpect(model().attributeExists("project"))
        }

        it("엑셀 다운로드 요청 시 200 OK와 엑셀 바이너리를 반환해야 한다 (TDD Red 예상)") {
            every { projectRepository.findByOwnerAndName("owner", "TestProject") } returns Optional.of(project)
            every { reviewThreadService.getReviewThreads(eq(project), any()) } returns emptyList()

            mockMvc.perform(
                get("/owner/TestProject/reviews")
                    .param("format", "xls")
            )
                .andExpect(status().isOk)
                .andExpect(header().exists("Content-Disposition"))
        }
    }
})
