package com.github.search5.yona.domain.site

import com.github.search5.yona.domain.issue.RecentIssueService
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectService
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.user.UserState
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Optional

class SiteServiceSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val projectService = mockk<ProjectService>()
    val recentIssueService = mockk<RecentIssueService>()

    val service = SiteService(userRepository, projectRepository, projectUserRepository, projectService, recentIssueService)

    val targetUser = User(id = 10L, loginId = "gildong", name = "홍길동", state = UserState.ACTIVE)

    beforeTest {
        clearMocks(userRepository, projectRepository, projectUserRepository, projectService, recentIssueService, answers = false)
    }

    describe("SiteService.deleteUser (P1-41)") {
        it("사용자를 삭제하면 최근 방문 이력(RecentIssue)도 함께 정리해야 한다") {
            every { projectUserRepository.findByUserId(10L) } returns emptyList()
            every { projectUserRepository.deleteAll(any<List<com.github.search5.yona.domain.project.ProjectUser>>()) } returns Unit
            every { userRepository.findById(10L) } returns Optional.of(targetUser)
            every { userRepository.save(any()) } answers { firstArg() }
            every { recentIssueService.deleteAll(targetUser) } returns Unit

            service.deleteUser(10L)

            targetUser.state shouldBe UserState.DELETED
            verify(exactly = 1) { recentIssueService.deleteAll(targetUser) }
        }
    }
})
