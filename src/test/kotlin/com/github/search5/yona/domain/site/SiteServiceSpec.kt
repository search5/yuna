package com.github.search5.yona.domain.site

import com.github.search5.yona.domain.attachment.Attachment
import com.github.search5.yona.domain.attachment.AttachmentRepository
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.issue.RecentIssueService
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectService
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.user.UserState
import io.kotest.assertions.throwables.shouldThrow
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
    val attachmentRepository = mockk<AttachmentRepository>()

    val service = SiteService(userRepository, projectRepository, projectUserRepository, projectService, recentIssueService, attachmentRepository)

    val targetUser = User(id = 10L, loginId = "gildong", name = "홍길동", state = UserState.ACTIVE)

    beforeTest {
        clearMocks(userRepository, projectRepository, projectUserRepository, projectService, recentIssueService, attachmentRepository, answers = false)
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

    describe("SiteService.getNoAvatarUsers (P2-03)") {
        it("아바타 첨부파일(USER_AVATAR 컨테이너)이 없는 활성 사용자만 반환해야 한다") {
            val withAvatar = User(id = 1L, loginId = "hasAvatar", name = "아바타있음", state = UserState.ACTIVE)
            val withoutAvatar = User(id = 2L, loginId = "noAvatar", name = "아바타없음", state = UserState.ACTIVE)
            every { userRepository.findAll() } returns listOf(withAvatar, withoutAvatar)
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.USER_AVATAR, "1") } returns
                listOf(Attachment(id = 900L, containerType = ResourceType.USER_AVATAR, containerId = "1"))
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.USER_AVATAR, "2") } returns emptyList()

            val result = service.getNoAvatarUsers()

            result.size shouldBe 1
            result.first()["loginId"] shouldBe "noAvatar"
        }
    }

    describe("SiteService.setUserAvatar (P2-03, yona SiteApp.setAttachmentToUserAvatar 대응)") {
        it("이미지 첨부파일을 대상 사용자의 아바타(USER_AVATAR 컨테이너)로 지정하고 기존 아바타는 삭제해야 한다") {
            val attachment = Attachment(id = 100L, name = "photo.png", mimeType = "image/png")
            val oldAvatar = Attachment(id = 50L, containerType = ResourceType.USER_AVATAR, containerId = "10")
            every { attachmentRepository.findById(100L) } returns Optional.of(attachment)
            every { userRepository.findByEmail("gildong@example.com") } returns Optional.of(targetUser)
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.USER_AVATAR, "10") } returns listOf(oldAvatar)
            every { attachmentRepository.deleteAll(listOf(oldAvatar)) } returns Unit
            every { attachmentRepository.save(attachment) } returns attachment

            service.setUserAvatar(100L, "gildong@example.com")

            attachment.containerType shouldBe ResourceType.USER_AVATAR
            attachment.containerId shouldBe "10"
            verify(exactly = 1) { attachmentRepository.deleteAll(listOf(oldAvatar)) }
            verify(exactly = 1) { attachmentRepository.save(attachment) }
        }

        it("이미지가 아닌 첨부파일이면 예외가 발생해야 한다") {
            val attachment = Attachment(id = 101L, name = "doc.pdf", mimeType = "application/pdf")
            every { attachmentRepository.findById(101L) } returns Optional.of(attachment)
            every { userRepository.findByEmail("gildong@example.com") } returns Optional.of(targetUser)

            shouldThrow<IllegalArgumentException> {
                service.setUserAvatar(101L, "gildong@example.com")
            }

            verify(exactly = 0) { attachmentRepository.save(any()) }
        }

        it("대상 사용자를 찾을 수 없으면 예외가 발생해야 한다") {
            val attachment = Attachment(id = 102L, name = "photo.png", mimeType = "image/png")
            every { attachmentRepository.findById(102L) } returns Optional.of(attachment)
            every { userRepository.findByEmail("unknown@example.com") } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                service.setUserAvatar(102L, "unknown@example.com")
            }
        }
    }
})
