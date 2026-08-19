package com.github.search5.yona.domain.issue

import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.notification.NotificationEventRepository
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional

class IssueShareServiceImplSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val issueSharerRepository = mockk<IssueSharerRepository>()
    val issueRepository = mockk<IssueRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val notificationEventRepository = mockk<NotificationEventRepository>()
    val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    val issueEventRepository = mockk<IssueEventRepository>()

    val service = IssueShareServiceImpl(
        userRepository,
        projectRepository,
        issueSharerRepository,
        issueRepository,
        projectUserRepository,
        organizationUserRepository,
        notificationEventRepository,
        eventPublisher,
        issueEventRepository
    )

    val project = Project(id = 1L, name = "TestProject", owner = "gildong")
    val currentUser = User(id = 10L, loginId = "gildong", name = "홍길동")
    val sharerUser = User(id = 20L, loginId = "sharer1", name = "공유대상")
    val issue = Issue(id = 100L, title = "이슈", body = "본문", project = project, number = 1L)

    beforeTest {
        clearMocks(
            userRepository, projectRepository, issueSharerRepository, issueRepository,
            projectUserRepository, organizationUserRepository, notificationEventRepository,
            issueEventRepository, answers = false
        )
    }

    describe("IssueShareServiceImpl.changeSharer (P1-37)") {
        it("공유자를 추가하면 IssueEvent 타임라인 항목(ISSUE_SHARER_CHANGED)이 생성되어야 한다") {
            every { issueSharerRepository.findByLoginIdAndIssueId("sharer1", 100L) } returns Optional.empty()
            every { issueSharerRepository.save(any()) } answers { firstArg() }
            every { userRepository.findByLoginId("sharer1") } returns Optional.of(sharerUser)
            every { notificationEventRepository.save(any()) } answers { firstArg() }
            every { issueEventRepository.findFirstByIssueAndCreatedAfterOrderByIdDesc(issue, any()) } returns null

            val captured = slot<IssueEvent>()
            every { issueEventRepository.save(capture(captured)) } answers { firstArg() }

            service.changeSharer(issue, "sharer1", "user", "add", currentUser)

            captured.captured.eventType shouldBe EventType.ISSUE_SHARER_CHANGED
            captured.captured.newValue shouldBe "sharer1"
            captured.captured.senderLoginId shouldBe "gildong"
        }
    }
})
