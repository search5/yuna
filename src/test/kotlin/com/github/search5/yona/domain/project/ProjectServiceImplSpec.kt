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
import io.mockk.verify
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
    val labelRepository = mockk<LabelRepository>()

    val projectService = ProjectServiceImpl(
        projectRepository,
        projectUserRepository,
        repositoryService,
        userRepository,
        projectTransferRepository,
        roleRepository,
        organizationRepository,
        organizationUserRepository,
        labelRepository
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
            every { organizationRepository.findByName("intended-owner") } returns Optional.empty()
            every { projectUserRepository.findByProjectIdAndUserId(10L, 2L) } returns Optional.empty()
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectUserRepository.save(any()) } returns mockk()
            every { projectTransferRepository.delete(any()) } returns Unit

            // When & Then (예외가 발생하지 않아야 함)
            projectService.acceptTransfer(50L, "correct-key", 2L)

            pt.accepted shouldBe true
            // yona acceptTransfer()의 "project.organization = null"(개인에게 이관) 대응 (P1-73).
            project.organization shouldBe null
            // yona disableProjectTransferLink()가 실제로는 ProjectTransfer 행을 삭제하는 것 대응
            // (P1-74) — accepted=true로 남겨두지 않는다.
            verify(exactly = 1) { projectTransferRepository.delete(pt) }
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
            every { projectTransferRepository.delete(any()) } returns Unit

            // When & Then (예외가 발생하지 않아야 함)
            projectService.acceptTransfer(50L, "correct-key", 4L)

            pt.accepted shouldBe true
            // yona acceptTransfer()의 "project.organization = newOwnerOrg"(조직에게 이관) 대응 (P1-73).
            project.organization shouldBe org
        }

        // yona ProjectApp.acceptTransfer()의 "project.organization = newOwnerOrg 또는 null" 대응 (P1-73).
        it("조직 소속 프로젝트를 개인에게 이관하면 organization이 명시적으로 null로 지워져야 한다") {
            val orgOwnedProject = Project(
                id = 11L, name = "org-owned-project", owner = "some-org", vcs = "GIT",
                organization = Organization(id = 5L, name = "some-org")
            )
            val pt = ProjectTransfer(
                id = 51L, project = orgOwnedProject, sender = sender, destination = "intended-owner",
                confirmKey = "correct-key", newProjectName = "org-owned-project", requested = Instant.now()
            )
            val destUser = User(id = 2L, loginId = "intended-owner", name = "받는사람")
            val managerRole = Role(id = RoleType.MANAGER.roleType)

            every {
                projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(51L, false, any())
            } returns Optional.of(pt)
            every { userRepository.findById(2L) } returns Optional.of(destUser)
            every { organizationRepository.findByName("intended-owner") } returns Optional.empty()
            every { projectRepository.save(any()) } returns orgOwnedProject
            every { projectUserRepository.findByProjectIdAndUserId(11L, 1L) } returns Optional.empty()
            every { userRepository.findByLoginId("intended-owner") } returns Optional.of(destUser)
            every { projectUserRepository.findByProjectIdAndUserId(11L, 2L) } returns Optional.empty()
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectUserRepository.save(any()) } returns mockk()
            every { projectTransferRepository.delete(any()) } returns Unit

            projectService.acceptTransfer(51L, "correct-key", 2L)

            orgOwnedProject.organization shouldBe null
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

    // yona Project.newProjectName(loginId, projectName) 대응 (P1-72).
    describe("ProjectServiceImpl.requestNewTransfer") {
        val sender = User(id = 1L, loginId = "sender", name = "보내는사람")
        val project = Project(id = 10L, name = "yona-project", owner = "sender", vcs = "GIT")

        it("목적지에 동명 프로젝트가 없으면 이름 변경 없이 그대로 이관 요청되어야 한다") {
            every { projectRepository.findById(10L) } returns Optional.of(project)
            every { userRepository.findById(1L) } returns Optional.of(sender)
            every { userRepository.findByLoginId("new-owner") } returns Optional.empty()
            every { projectRepository.findByOwner("new-owner") } returns emptyList()
            every { projectRepository.findByOwnerAndName("new-owner", "yona-project") } returns Optional.empty()
            every {
                projectTransferRepository.findByProjectAndSenderAndDestination(project, sender, "new-owner")
            } returns Optional.empty()
            every { projectTransferRepository.save(any()) } answers { firstArg() }

            val pt = projectService.requestNewTransfer(10L, 1L, "new-owner")

            pt.newProjectName shouldBe "yona-project"
        }

        it("목적지에 동명 프로젝트가 있으면 충돌이 없을 때까지 뒤에 -1, -2...를 붙여야 한다") {
            every { projectRepository.findById(10L) } returns Optional.of(project)
            every { userRepository.findById(1L) } returns Optional.of(sender)
            every { userRepository.findByLoginId("new-owner") } returns Optional.empty()
            every { projectRepository.findByOwner("new-owner") } returns emptyList()
            every { projectRepository.findByOwnerAndName("new-owner", "yona-project") } returns
                Optional.of(Project(id = 99L, name = "yona-project", owner = "new-owner"))
            every { projectRepository.findByOwnerAndName("new-owner", "yona-project-1") } returns
                Optional.of(Project(id = 98L, name = "yona-project-1", owner = "new-owner"))
            every { projectRepository.findByOwnerAndName("new-owner", "yona-project-2") } returns Optional.empty()
            every {
                projectTransferRepository.findByProjectAndSenderAndDestination(project, sender, "new-owner")
            } returns Optional.empty()
            every { projectTransferRepository.save(any()) } answers { firstArg() }

            val pt = projectService.requestNewTransfer(10L, 1L, "new-owner")

            pt.newProjectName shouldBe "yona-project-2"
        }
    }

    describe("ProjectServiceImpl.attachLabel/detachLabel (P1-13)") {
        val project = Project(id = 1L, name = "yona-project", owner = "owner", vcs = "GIT")

        it("같은 category+name의 라벨이 없으면 새로 생성하여 프로젝트에 붙여야 한다") {
            every { projectRepository.findById(1L) } returns Optional.of(project)
            every { labelRepository.findByCategoryAndName("os", "linux") } returns Optional.empty()
            every { labelRepository.save(any()) } answers { (it.invocation.args[0] as Label).apply { id = 100L } }
            every { projectRepository.save(any()) } returns project

            val result = projectService.attachLabel(1L, "os", "linux")

            result.isCreated shouldBe true
            result.isAttached shouldBe true
            result.label.name shouldBe "linux"
            project.labels.any { it.id == 100L } shouldBe true
        }

        it("category가 없으면 기본값 'Label'로 처리해야 한다") {
            val freshProject = Project(id = 2L, name = "p2", owner = "owner", vcs = "GIT")
            every { projectRepository.findById(2L) } returns Optional.of(freshProject)
            every { labelRepository.findByCategoryAndName("Label", "urgent") } returns Optional.empty()
            every { labelRepository.save(any()) } answers { (it.invocation.args[0] as Label).apply { id = 101L } }
            every { projectRepository.save(any()) } returns freshProject

            val result = projectService.attachLabel(2L, null, "urgent")

            result.label.category shouldBe "Label"
        }

        it("이미 존재하는 라벨이지만 이 프로젝트엔 아직 없으면 새로 만들지 않고 붙이기만 해야 한다") {
            val freshProject = Project(id = 3L, name = "p3", owner = "owner", vcs = "GIT")
            val existingLabel = Label(id = 200L, category = "os", name = "linux")
            every { projectRepository.findById(3L) } returns Optional.of(freshProject)
            every { labelRepository.findByCategoryAndName("os", "linux") } returns Optional.of(existingLabel)
            every { projectRepository.save(any()) } returns freshProject

            val result = projectService.attachLabel(3L, "os", "linux")

            result.isCreated shouldBe false
            result.isAttached shouldBe true
        }

        it("이미 이 프로젝트에 붙어있는 라벨이면 isAttached=false를 반환해야 한다") {
            val existingLabel = Label(id = 200L, category = "os", name = "linux")
            val projectWithLabel = Project(id = 4L, name = "p4", owner = "owner", vcs = "GIT")
            projectWithLabel.labels.add(existingLabel)
            every { projectRepository.findById(4L) } returns Optional.of(projectWithLabel)
            every { labelRepository.findByCategoryAndName("os", "linux") } returns Optional.of(existingLabel)

            val result = projectService.attachLabel(4L, "os", "linux")

            result.isCreated shouldBe false
            result.isAttached shouldBe false
        }

        it("존재하지 않는 라벨 id로 detach를 시도하면 false를 반환해야 한다(404 대응)") {
            every { projectRepository.findById(1L) } returns Optional.of(project)
            every { labelRepository.findById(999L) } returns Optional.empty()

            val result = projectService.detachLabel(1L, 999L)

            result shouldBe false
        }

        it("detach 후 다른 프로젝트가 더 이상 없으면 라벨 자체를 삭제해야 한다") {
            val label = Label(id = 200L, category = "os", name = "linux")
            val projectWithLabel = Project(id = 5L, name = "p5", owner = "owner", vcs = "GIT")
            projectWithLabel.labels.add(label)
            every { projectRepository.findById(5L) } returns Optional.of(projectWithLabel)
            every { labelRepository.findById(200L) } returns Optional.of(label)
            every { projectRepository.save(any()) } returns projectWithLabel
            every { projectRepository.countByLabelsId(200L) } returns 0L
            every { labelRepository.delete(label) } returns Unit

            val result = projectService.detachLabel(5L, 200L)

            result shouldBe true
            projectWithLabel.labels.any { it.id == 200L } shouldBe false
        }

        it("detach 후에도 다른 프로젝트가 그 라벨을 쓰고 있으면 라벨을 삭제하지 않아야 한다") {
            val label = Label(id = 201L, category = "os", name = "mac")
            val projectWithLabel = Project(id = 6L, name = "p6", owner = "owner", vcs = "GIT")
            projectWithLabel.labels.add(label)
            every { projectRepository.findById(6L) } returns Optional.of(projectWithLabel)
            every { labelRepository.findById(201L) } returns Optional.of(label)
            every { projectRepository.save(any()) } returns projectWithLabel
            every { projectRepository.countByLabelsId(201L) } returns 1L

            val result = projectService.detachLabel(6L, 201L)

            result shouldBe true
            io.mockk.verify(exactly = 0) { labelRepository.delete(label) }
        }
    }
})
