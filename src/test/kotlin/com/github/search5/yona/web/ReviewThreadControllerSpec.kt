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

import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.project.ProjectScope
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken

class ReviewThreadControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val reviewThreadService = mockk<ReviewThreadService>()
    val userRepository = mockk<UserRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()

    val reviewThreadController = ReviewThreadController(
        projectRepository,
        reviewThreadService,
        userRepository,
        projectUserRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(reviewThreadController).build()

    beforeTest {
        io.mockk.clearMocks(projectRepository, reviewThreadService, userRepository, projectUserRepository)
    }

    describe("ReviewThreadController TDD 검증") {
        val project = Project(id = 1L, name = "TestProject", owner = "owner", projectScope = ProjectScope.PUBLIC)
        val memberOnlyProject = Project(id = 2L, name = "MemberOnlyProject", owner = "owner", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true)
        val user = com.github.search5.yona.domain.user.User(id = 10L, loginId = "testuser", name = "테스트유저")
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "")

        it("리뷰 스레드 목록 화면 요청 시 200 OK와 reviewthread/list 뷰를 반환해야 한다") {
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

        it("엑셀 다운로드 요청 시 200 OK와 엑셀 바이너리를 반환해야 한다") {
            every { projectRepository.findByOwnerAndName("owner", "TestProject") } returns Optional.of(project)
            every { reviewThreadService.getReviewThreads(eq(project), any()) } returns emptyList()

            mockMvc.perform(
                get("/owner/TestProject/reviews")
                    .param("format", "xls")
            )
                .andExpect(status().isOk)
                .andExpect(header().exists("Content-Disposition"))
        }

        it("[Test-16-3-1] isCodeAccessibleMemberOnly가 true이고 비회원(비인증)인 경우 403 에러 뷰를 반환해야 한다") {
            every { projectRepository.findByOwnerAndName("owner", "MemberOnlyProject") } returns Optional.of(memberOnlyProject)

            mockMvc.perform(
                get("/owner/MemberOnlyProject/reviews")
            )
                .andExpect(status().isOk)
                .andExpect(view().name("error/403"))
        }

        it("[Test-16-3-2] isCodeAccessibleMemberOnly가 true이고 가입된 멤버인 경우 정상적으로 200 OK를 반환해야 한다") {
            every { projectRepository.findByOwnerAndName("owner", "MemberOnlyProject") } returns Optional.of(memberOnlyProject)
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { projectUserRepository.existsByProjectIdAndUserId(2L, 10L) } returns true
            every { reviewThreadService.getReviewThreads(eq(memberOnlyProject), any(), any()) } returns PageImpl(emptyList())
            every { reviewThreadService.countReviewThreads(eq(memberOnlyProject), any()) } returns 0L

            mockMvc.perform(
                get("/owner/MemberOnlyProject/reviews").principal(userAuth)
            )
                .andExpect(status().isOk)
                .andExpect(view().name("reviewthread/list"))
        }
    }
})
