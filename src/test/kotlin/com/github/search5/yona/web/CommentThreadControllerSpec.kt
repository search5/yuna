package com.github.search5.yona.web

import com.github.search5.yona.domain.pullrequest.CommentThread
import com.github.search5.yona.domain.pullrequest.CommentThread.ThreadState
import com.github.search5.yona.domain.pullrequest.CodeReviewService
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.security.core.Authentication
import java.util.Optional

class CommentThreadControllerSpec : DescribeSpec({
    val codeReviewService = mockk<CodeReviewService>()
    val userRepository = mockk<UserRepository>()

    val commentThreadController = CommentThreadController(
        codeReviewService,
        userRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(commentThreadController).build()

    beforeTest {
        io.mockk.clearMocks(codeReviewService, userRepository)
    }

    describe("CommentThreadController TDD 검증") {
        val user = mockk<User>(relaxed = true)
        val thread = mockk<CommentThread>(relaxed = true)
        val authentication = mockk<Authentication>()

        it("스레드 오픈 API 호출 시 200 OK와 상태 변경 영속화가 일어나야 한다") {
            every { authentication.name } returns "mockuser"
            every { userRepository.findByLoginId("mockuser") } returns Optional.of(user)
            every { codeReviewService.updateThreadState(100L, ThreadState.OPEN, user) } returns thread

            mockMvc.perform(
                post("/threads/100/open").principal(authentication)
            )
                .andExpect(status().isOk)

            verify(exactly = 1) {
                codeReviewService.updateThreadState(100L, ThreadState.OPEN, user)
            }
        }

        it("스레드 닫기 API 호출 시 200 OK와 상태 변경 영속화가 일어나야 한다") {
            every { authentication.name } returns "mockuser"
            every { userRepository.findByLoginId("mockuser") } returns Optional.of(user)
            every { codeReviewService.updateThreadState(100L, ThreadState.CLOSED, user) } returns thread

            mockMvc.perform(
                post("/threads/100/close").principal(authentication)
            )
                .andExpect(status().isOk)

            verify(exactly = 1) {
                codeReviewService.updateThreadState(100L, ThreadState.CLOSED, user)
            }
        }
    }
})
