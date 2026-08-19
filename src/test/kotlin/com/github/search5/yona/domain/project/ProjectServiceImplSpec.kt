package com.github.search5.yona.domain.project

import com.github.search5.yona.domain.organization.Organization
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.organization.OrganizationUser
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleRepository
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.vcs.RepositoryService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.Optional

class ProjectServiceImplSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val repositoryService = mockk<RepositoryService>()
    val userRepository = mockk<UserRepository>()
    val projectTransferRepository = mockk<ProjectTransferRepository>()
    val roleRepository = mockk<RoleRepository>()
    val organizationRepository = mockk<OrganizationRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()

    val projectService = ProjectServiceImpl(
        projectRepository,
        projectUserRepository,
        repositoryService,
        userRepository,
        projectTransferRepository,
        roleRepository,
        organizationRepository,
        organizationUserRepository
    )

    describe("ProjectServiceImpl.acceptTransfer") {
        val sender = User(id = 1L, loginId = "sender", name = "보내는사람")
        val project = Project(id = 10L, name = "yona-project", owner = "sender", vcs = "GIT")

        fun pendingTransfer(destination: String) = ProjectTransfer(
            id = 50L,
            project = project,
            sender = sender,
            destination = destination,
            confirmKey = "correct-key",
            newProjectName = "yona-project",
            requested = Instant.now()
        )

        it("이관 목적지(destination) 로그인ID와 일치하지 않는 사용자가 수락하면 예외가 발생해야 한다") {
            // Given
            val pt = pendingTransfer("intended-owner")
            val stranger = User(id = 99L, loginId = "stranger", name = "제3자")

            every {
                projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(50L, false, any())
            } returns Optional.of(pt)
            every { userRepository.findById(99L) } returns Optional.of(stranger)
            every { organizationRepository.findByName("intended-owner") } returns Optional.empty()

            // When & Then
            shouldThrow<IllegalArgumentException> {
                projectService.acceptTransfer(50L, "correct-key", 99L)
            }
        }

        it("이관 목적지 로그인ID와 정확히 일치하는 사용자는 수락할 수 있어야 한다") {
            // Given
            val pt = pendingTransfer("intended-owner")
            val destUser = User(id = 2L, loginId = "intended-owner", name = "받는사람")
            val managerRole = Role(id = RoleType.MANAGER.roleType)

            every {
                projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(50L, false, any())
            } returns Optional.of(pt)
            every { userRepository.findById(2L) } returns Optional.of(destUser)
            every { projectRepository.save(any()) } returns project
            every { projectUserRepository.findByProjectIdAndUserId(10L, 1L) } returns Optional.empty()
            every { userRepository.findByLoginId("intended-owner") } returns Optional.of(destUser)
            every { projectUserRepository.findByProjectIdAndUserId(10L, 2L) } returns Optional.empty()
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectUserRepository.save(any()) } returns mockk()
            every { projectTransferRepository.save(any()) } returns pt

            // When & Then (예외가 발생하지 않아야 함)
            projectService.acceptTransfer(50L, "correct-key", 2L)

            pt.accepted shouldBe true
        }

        it("이관 목적지가 조직(Organization) 이름이면 해당 조직의 ORG_ADMIN만 수락할 수 있어야 한다") {
            // Given: 조직의 일반 멤버(ORG_MEMBER)가 수락 시도 -> 거부
            val pt = pendingTransfer("some-org")
            val org = Organization(id = 5L, name = "some-org")
            val orgMemberUser = User(id = 3L, loginId = "org-member", name = "조직멤버")
            val memberRole = Role(id = RoleType.ORG_MEMBER.roleType)
            val orgUser = OrganizationUser(id = 500L, user = orgMemberUser, organization = org, role = memberRole)

            every {
                projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(50L, false, any())
            } returns Optional.of(pt)
            every { userRepository.findById(3L) } returns Optional.of(orgMemberUser)
            every { organizationRepository.findByName("some-org") } returns Optional.of(org)
            every { organizationUserRepository.findByOrganizationIdAndUserId(5L, 3L) } returns Optional.of(orgUser)

            // When & Then
            shouldThrow<IllegalArgumentException> {
                projectService.acceptTransfer(50L, "correct-key", 3L)
            }
        }

        it("이관 목적지가 조직 이름이고 수락자가 해당 조직의 ORG_ADMIN이면 수락할 수 있어야 한다") {
            // Given
            val pt = pendingTransfer("some-org")
            val org = Organization(id = 5L, name = "some-org")
            val orgAdminUser = User(id = 4L, loginId = "org-admin", name = "조직관리자")
            val adminRole = Role(id = RoleType.ORG_ADMIN.roleType)
            val orgUser = OrganizationUser(id = 501L, user = orgAdminUser, organization = org, role = adminRole)
            val managerRole = Role(id = RoleType.MANAGER.roleType)

            every {
                projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(50L, false, any())
            } returns Optional.of(pt)
            every { userRepository.findById(4L) } returns Optional.of(orgAdminUser)
            every { organizationRepository.findByName("some-org") } returns Optional.of(org)
            every { organizationUserRepository.findByOrganizationIdAndUserId(5L, 4L) } returns Optional.of(orgUser)
            every { projectRepository.save(any()) } returns project
            every { projectUserRepository.findByProjectIdAndUserId(10L, 1L) } returns Optional.empty()
            every { userRepository.findByLoginId("some-org") } returns Optional.empty()
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectTransferRepository.save(any()) } returns pt

            // When & Then (예외가 발생하지 않아야 함)
            projectService.acceptTransfer(50L, "correct-key", 4L)

            pt.accepted shouldBe true
        }

        it("confirmKey가 일치하지 않으면 인가 검사 전에 예외가 발생해야 한다") {
            // Given
            val pt = pendingTransfer("intended-owner")

            every {
                projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(50L, false, any())
            } returns Optional.of(pt)

            // When & Then
            shouldThrow<IllegalArgumentException> {
                projectService.acceptTransfer(50L, "wrong-key", 2L)
            }
        }
    }
})
