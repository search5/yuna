package com.github.search5.yona.web

import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

// yona-wiki P3-02 4라운드(Step8.5 서버 보강) — UserIssueStatusRestApiController(GET
// /api/v1/user/issues/status). `gh issue status`의 최소 버전(담당/작성 이슈 개수·목록)만 검증한다.
class UserIssueStatusRestApiControllerSpec : DescribeSpec({
    val issueRepository = mockk<IssueRepository>()
    val userRepository = mockk<UserRepository>()

    val controller = UserIssueStatusRestApiController(issueRepository, userRepository)
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    beforeTest {
        clearMocks(issueRepository, userRepository)
    }

    val auth = UsernamePasswordAuthenticationToken("tester", "password")
    val user = User(id = 1L, loginId = "tester", name = "테스터")
    val project = Project(id = 1L, owner = "yona", name = "yuna")

    describe("GET /api/v1/user/issues/status") {
        it("비로그인 사용자는 401을 반환한다") {
            mockMvc.perform(get("/api/v1/user/issues/status"))
                .andExpect(status().isUnauthorized)
        }

        it("담당/작성 이슈 개수와 목록을 반환한다") {
            val assignedIssue = Issue(id = 1L, number = 1L, title = "담당 이슈", project = project)
            val createdIssue = Issue(id = 2L, number = 2L, title = "작성 이슈", project = project)
            every { userRepository.findByLoginId("tester") } returns Optional.of(user)
            every { issueRepository.findByAssigneeAndState(1L, State.OPEN, null, any()) } returns
                PageImpl(listOf(assignedIssue), PageRequest.of(0, 20), 1)
            every { issueRepository.findByAuthorIdAndState(1L, State.OPEN, null, any()) } returns
                PageImpl(listOf(createdIssue), PageRequest.of(0, 20), 1)
            every { issueRepository.countByAssigneeAndState(1L, State.OPEN) } returns 1L
            every { issueRepository.countByAssigneeAndState(1L, State.CLOSED) } returns 3L
            every { issueRepository.countByAuthorIdAndState(1L, State.OPEN) } returns 2L
            every { issueRepository.countByAuthorIdAndState(1L, State.CLOSED) } returns 5L

            mockMvc.perform(get("/api/v1/user/issues/status").principal(auth))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.assigned.openCount").value(1))
                .andExpect(jsonPath("$.assigned.closedCount").value(3))
                .andExpect(jsonPath("$.assigned.items[0].title").value("담당 이슈"))
                .andExpect(jsonPath("$.created.openCount").value(2))
                .andExpect(jsonPath("$.created.closedCount").value(5))
                .andExpect(jsonPath("$.created.items[0].title").value("작성 이슈"))
        }
    }
})
