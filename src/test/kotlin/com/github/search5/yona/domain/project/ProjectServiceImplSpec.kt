package com.github.search5.yona.domain.project

import com.github.search5.yona.domain.attachment.AttachmentService
import com.github.search5.yona.domain.board.Posting
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.board.PostingService
import com.github.search5.yona.domain.issue.Assignee
import com.github.search5.yona.domain.issue.AssigneeRepository
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueLabelCategoryRepository
import com.github.search5.yona.domain.issue.IssueLabelService
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.issue.IssueService
import com.github.search5.yona.domain.organization.Organization
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.organization.OrganizationUser
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.pullrequest.CommentThreadRepository
import com.github.search5.yona.domain.pullrequest.PullRequest
import com.github.search5.yona.domain.pullrequest.PullRequestCommitRepository
import com.github.search5.yona.domain.pullrequest.PullRequestEventRepository
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleRepository
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.user.FavoriteProject
import com.github.search5.yona.domain.user.FavoriteProjectRepository
import com.github.search5.yona.domain.vcs.RepositoryService
import com.github.search5.yona.domain.webhook.Webhook
import com.github.search5.yona.domain.webhook.WebhookRepository
import com.github.search5.yona.domain.webhook.WebhookThreadRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
    val issueRepository = mockk<IssueRepository>()
    val issueService = mockk<IssueService>()
    val issueLabelCategoryRepository = mockk<IssueLabelCategoryRepository>()
    val issueLabelService = mockk<IssueLabelService>()
    val assigneeRepository = mockk<AssigneeRepository>()
    val webhookRepository = mockk<WebhookRepository>()
    val webhookThreadRepository = mockk<WebhookThreadRepository>()
    val postingRepository = mockk<PostingRepository>()
    val postingService = mockk<PostingService>()
    val commentThreadRepository = mockk<CommentThreadRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val pullRequestEventRepository = mockk<PullRequestEventRepository>()
    val pullRequestCommitRepository = mockk<PullRequestCommitRepository>()
    val favoriteProjectRepository = mockk<FavoriteProjectRepository>(relaxed = true)

    val projectService = ProjectServiceImpl(
        projectRepository,
        projectUserRepository,
        repositoryService,
        userRepository,
        projectTransferRepository,
        roleRepository,
        organizationRepository,
        organizationUserRepository,
        labelRepository,
        issueRepository,
        issueService,
        issueLabelCategoryRepository,
        issueLabelService,
        assigneeRepository,
        webhookRepository,
        webhookThreadRepository,
        postingRepository,
        postingService,
        commentThreadRepository,
        pullRequestRepository,
        pullRequestEventRepository,
        pullRequestCommitRepository,
        favoriteProjectRepository
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

        // yona FavoriteProject.java:41-50 updateFavoriteProject() 대응 (P2-27). yona 원본은 이
        // 동기화를 개명(ProjectApp.settingProject())에서만 호출하지만, yuna는 개명 전용 경로가
        // 없어 이름/소유자 변경이 실제로 일어나는 유일한 지점인 acceptTransfer에서 수행한다.
        it("이관이 완료되면 이 프로젝트를 즐겨찾기한 사용자들의 owner/projectName도 갱신해야 한다 (P2-27)") {
            val pt = pendingTransfer("intended-owner")
            val destUser = User(id = 2L, loginId = "intended-owner", name = "받는사람")
            val managerRole = Role(id = RoleType.MANAGER.roleType)
            val favoriter = User(id = 3L, loginId = "favoriter", name = "즐겨찾기유저")
            val favorite = FavoriteProject(id = 900L, user = favoriter, project = project, owner = "sender", projectName = "yona-project")

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
            every { favoriteProjectRepository.findByProjectId(10L) } returns listOf(favorite)
            every { favoriteProjectRepository.save(any()) } returns favorite

            projectService.acceptTransfer(50L, "correct-key", 2L)

            verify(exactly = 1) {
                favoriteProjectRepository.save(match { it.owner == "intended-owner" && it.projectName == "yona-project" })
            }
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

        // yona Project.recordRenameOrTransferHistoryIfLastChangePassed24HoursFrom() 대응 (P1-76).
        it("이전 완료 시 예전 owner/name이 기록돼야 한다(previousOwnerLoginId/previousName)") {
            val pt = pendingTransfer("intended-owner")
            val destUser = User(id = 2L, loginId = "intended-owner", name = "받는사람")
            val managerRole = Role(id = RoleType.MANAGER.roleType)

            every {
                projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(50L, false, any())
            } returns Optional.of(pt)
            every { userRepository.findById(2L) } returns Optional.of(destUser)
            every { organizationRepository.findByName("intended-owner") } returns Optional.empty()
            every { projectRepository.save(any()) } returns project
            every { projectUserRepository.findByProjectIdAndUserId(10L, 1L) } returns Optional.empty()
            every { userRepository.findByLoginId("intended-owner") } returns Optional.of(destUser)
            every { projectUserRepository.findByProjectIdAndUserId(10L, 2L) } returns Optional.empty()
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectUserRepository.save(any()) } returns mockk()
            every { projectTransferRepository.delete(any()) } returns Unit

            projectService.acceptTransfer(50L, "correct-key", 2L)

            project.previousOwnerLoginId shouldBe "sender"
            project.previousName shouldBe "yona-project"
            project.previousNameChangedTime shouldNotBe null
        }

        it("24시간 이내에 이미 예전 위치가 기록됐으면 다시 덮어쓰지 않아야 한다") {
            val recentChange = Instant.now().minusSeconds(3600) // 1시간 전
            val projectWithRecentHistory = Project(
                id = 12L, name = "yona-project", owner = "sender", vcs = "GIT",
                previousOwnerLoginId = "very-old-owner", previousName = "very-old-name",
                previousNameChangedTime = recentChange
            )
            val pt = ProjectTransfer(
                id = 52L, project = projectWithRecentHistory, sender = sender, destination = "intended-owner",
                confirmKey = "correct-key", newProjectName = "yona-project", requested = Instant.now()
            )
            val destUser = User(id = 2L, loginId = "intended-owner", name = "받는사람")
            val managerRole = Role(id = RoleType.MANAGER.roleType)

            every {
                projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(52L, false, any())
            } returns Optional.of(pt)
            every { userRepository.findById(2L) } returns Optional.of(destUser)
            every { organizationRepository.findByName("intended-owner") } returns Optional.empty()
            every { projectRepository.save(any()) } returns projectWithRecentHistory
            every { projectUserRepository.findByProjectIdAndUserId(12L, 1L) } returns Optional.empty()
            every { userRepository.findByLoginId("intended-owner") } returns Optional.of(destUser)
            every { projectUserRepository.findByProjectIdAndUserId(12L, 2L) } returns Optional.empty()
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectUserRepository.save(any()) } returns mockk()
            every { projectTransferRepository.delete(any()) } returns Unit

            projectService.acceptTransfer(52L, "correct-key", 2L)

            // 24시간이 안 지났으므로 예전 기록이 "sender/yona-project"로 갱신되지 않고 그대로 유지돼야 한다.
            projectWithRecentHistory.previousOwnerLoginId shouldBe "very-old-owner"
            projectWithRecentHistory.previousName shouldBe "very-old-name"
            projectWithRecentHistory.previousNameChangedTime shouldBe recentChange
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

    // yona ProjectApp.settingProject()의 이름 변경(개명) 분기 대응 (P1-144). 소유자는 그대로 두고
    // 이름만 바꾸는 경로 자체가 yuna에 없었다 — validateWhenUpdate()의 projectNameChangeable() 중복
    // 검사, recordRenameOrTransferHistoryIfLastChangePassed24HoursFrom(), repository.renameTo(),
    // FavoriteProject.updateFavoriteProject() 네 가지를 전부 그대로 재현한다.
    describe("ProjectServiceImpl.updateProject - 개명 (P1-144)") {
        val playRepository = mockk<com.github.search5.yona.domain.vcs.PlayRepository>()

        beforeTest {
            io.mockk.clearMocks(playRepository, answers = false)
        }

        fun baseParam(name: String? = null) = UpdateProjectParam(
            name = name,
            overview = "설명",
            projectScope = ProjectScope.PRIVATE,
            isCodeAccessibleMemberOnly = false,
            isUsingReviewerCount = false,
            defaultReviewerCount = 1,
            defaultBranch = null,
            isCodeEnabled = true,
            isIssueEnabled = true,
            isPullRequestEnabled = true,
            isReviewEnabled = true,
            isMilestoneEnabled = true,
            isBoardEnabled = true
        )

        it("이름이 바뀌지 않으면 저장소 rename을 호출하지 않아야 한다") {
            val project = Project(id = 20L, name = "old-name", owner = "owner1", vcs = "GIT")
            every { projectRepository.findById(20L) } returns Optional.of(project)
            every { projectRepository.save(any()) } returns project

            projectService.updateProject(20L, baseParam(name = "old-name"))

            io.mockk.verify(exactly = 0) { repositoryService.getRepository(any()) }
        }

        it("같은 소유자 내 이미 존재하는 이름으로 바꾸려 하면 예외가 발생하고 아무것도 바뀌지 않아야 한다") {
            val project = Project(id = 21L, name = "old-name", owner = "owner1", vcs = "GIT")
            every { projectRepository.findById(21L) } returns Optional.of(project)
            every {
                projectRepository.existsByOwnerIgnoreCaseAndNameIgnoreCaseAndIdNot("owner1", "taken-name", 21L)
            } returns true

            shouldThrow<IllegalArgumentException> {
                projectService.updateProject(21L, baseParam(name = "taken-name"))
            }

            project.name shouldBe "old-name"
            io.mockk.verify(exactly = 0) { repositoryService.getRepository(any()) }
        }

        it("저장소 rename에 실패하면 예외가 발생하고 이름이 바뀌지 않아야 한다") {
            val project = Project(id = 22L, name = "old-name", owner = "owner1", vcs = "GIT")
            every { projectRepository.findById(22L) } returns Optional.of(project)
            every {
                projectRepository.existsByOwnerIgnoreCaseAndNameIgnoreCaseAndIdNot("owner1", "new-name", 22L)
            } returns false
            every { repositoryService.getRepository(project) } returns playRepository
            every { playRepository.renameTo("new-name") } returns false

            shouldThrow<IllegalStateException> {
                projectService.updateProject(22L, baseParam(name = "new-name"))
            }

            project.name shouldBe "old-name"
        }

        it("이름을 바꾸면 저장소도 rename되고, 개명 이력과 즐겨찾기 owner/projectName이 함께 갱신돼야 한다") {
            val project = Project(id = 23L, name = "old-name", owner = "owner1", vcs = "GIT")
            val favorite = FavoriteProject(id = 900L, user = mockk(relaxed = true), project = project, owner = "owner1", projectName = "old-name")

            every { projectRepository.findById(23L) } returns Optional.of(project)
            every {
                projectRepository.existsByOwnerIgnoreCaseAndNameIgnoreCaseAndIdNot("owner1", "new-name", 23L)
            } returns false
            every { repositoryService.getRepository(project) } returns playRepository
            every { playRepository.renameTo("new-name") } returns true
            every { projectRepository.save(any()) } returns project
            every { favoriteProjectRepository.findByProjectId(23L) } returns listOf(favorite)
            every { favoriteProjectRepository.save(any()) } returns favorite

            projectService.updateProject(23L, baseParam(name = "new-name"))

            project.name shouldBe "new-name"
            project.previousName shouldBe "old-name"
            project.previousOwnerLoginId shouldBe "owner1"
            project.previousNameChangedTime shouldNotBe null
            favorite.owner shouldBe "owner1"
            favorite.projectName shouldBe "new-name"
            io.mockk.verify(exactly = 1) { playRepository.renameTo("new-name") }
        }

        it("최근 24시간 내 이미 개명 이력이 있으면 previousName을 다시 덮어쓰지 않아야 한다") {
            val recentChange = Instant.now().minusSeconds(3600) // 1시간 전
            val project = Project(
                id = 24L, name = "old-name", owner = "owner1", vcs = "GIT",
                previousName = "very-old-name", previousOwnerLoginId = "owner1",
                previousNameChangedTime = recentChange
            )

            every { projectRepository.findById(24L) } returns Optional.of(project)
            every {
                projectRepository.existsByOwnerIgnoreCaseAndNameIgnoreCaseAndIdNot("owner1", "new-name", 24L)
            } returns false
            every { repositoryService.getRepository(project) } returns playRepository
            every { playRepository.renameTo("new-name") } returns true
            every { projectRepository.save(any()) } returns project
            every { favoriteProjectRepository.findByProjectId(24L) } returns emptyList()

            projectService.updateProject(24L, baseParam(name = "new-name"))

            // yona isRenamedOrTransferredIn24Hours()가 false를 반환하는 경우 — 예전 위치 포인터를
            // 그대로 보존해 사용자가 "가장 먼저" 있었던 위치로 계속 폴백 조회할 수 있게 한다.
            project.previousName shouldBe "very-old-name"
            project.previousNameChangedTime shouldBe recentChange
        }
    }

    // yona Project.delete() 대응 (P0-19) — 계단식 삭제 전수 이식. 프로젝트 삭제 시 연관된 모든
    // 리소스(이전요청/리뷰스레드/PR/이슈/라벨카테고리/담당자/웹훅/게시글/멤버)가 함께 정리되고,
    // fork 자식 프로젝트는 삭제되지 않고 원본 연결만 끊어져야 한다.
    describe("ProjectServiceImpl.deleteProject") {
        beforeTest {
            io.mockk.clearMocks(
                projectRepository, projectUserRepository, projectTransferRepository,
                issueRepository, issueService, issueLabelCategoryRepository, issueLabelService,
                assigneeRepository, webhookRepository, webhookThreadRepository,
                postingRepository, postingService, commentThreadRepository,
                pullRequestRepository, pullRequestEventRepository, pullRequestCommitRepository,
                answers = false
            )
        }

        it("연관 데이터를 모두 정리한 뒤 프로젝트 자체를 삭제해야 한다") {
            val project = Project(id = 1L, name = "will-delete", owner = "owner")

            val transfer = mockk<ProjectTransfer>(relaxed = true)
            every { projectTransferRepository.findByProjectId(1L) } returns listOf(transfer)
            every { projectTransferRepository.deleteAll(listOf(transfer)) } returns Unit

            val thread = mockk<com.github.search5.yona.domain.pullrequest.CommentThread>(relaxed = true)
            every { commentThreadRepository.findByProject(project) } returns listOf(thread)
            every { commentThreadRepository.deleteAll(listOf(thread)) } returns Unit

            val pr = mockk<PullRequest>(relaxed = true)
            every { pullRequestRepository.findByFromProject(project) } returns listOf(pr)
            every { pullRequestRepository.findByToProject(project) } returns emptyList()
            val prEvent = mockk<com.github.search5.yona.domain.pullrequest.PullRequestEvent>(relaxed = true)
            every { pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pr) } returns listOf(prEvent)
            every { pullRequestEventRepository.deleteAll(listOf(prEvent)) } returns Unit
            val prCommit = mockk<com.github.search5.yona.domain.pullrequest.PullRequestCommit>(relaxed = true)
            every { pullRequestCommitRepository.findByPullRequest(pr) } returns listOf(prCommit)
            every { pullRequestCommitRepository.deleteAll(listOf(prCommit)) } returns Unit
            every { pullRequestRepository.delete(pr) } returns Unit

            val issue = mockk<Issue>(relaxed = true)
            every { issueRepository.findByProject(project) } returns listOf(issue)
            every { issueService.deleteIssueCascade(issue) } returns Unit

            val category = mockk<com.github.search5.yona.domain.issue.IssueLabelCategory>(relaxed = true)
            every { category.id } returns 30L
            every { issueLabelCategoryRepository.findByProject(project) } returns listOf(category)
            every { issueLabelService.deleteCategory(30L) } returns Unit

            val assignee = mockk<Assignee>(relaxed = true)
            every { assigneeRepository.findByProjectId(1L) } returns listOf(assignee)
            every { assigneeRepository.deleteAll(listOf(assignee)) } returns Unit

            val webhook = mockk<Webhook>(relaxed = true)
            every { webhook.id } returns 40L
            every { webhookRepository.findByProjectId(1L) } returns listOf(webhook)
            val webhookThread = mockk<com.github.search5.yona.domain.webhook.WebhookThread>(relaxed = true)
            every { webhookThreadRepository.findByWebhookId(40L) } returns listOf(webhookThread)
            every { webhookThreadRepository.deleteAll(listOf(webhookThread)) } returns Unit
            every { webhookRepository.delete(webhook) } returns Unit

            val posting = mockk<Posting>(relaxed = true)
            every { postingRepository.findByProject(project) } returns listOf(posting)
            every { postingService.deletePostingCascade(posting) } returns Unit

            val projectUser = mockk<ProjectUser>(relaxed = true)
            every { projectUserRepository.findByProjectId(1L) } returns listOf(projectUser)
            every { projectUserRepository.deleteAll(listOf(projectUser)) } returns Unit

            every { projectRepository.findById(1L) } returns Optional.of(project)
            every { projectRepository.delete(project) } returns Unit

            projectService.deleteProject(1L)

            verify(exactly = 1) { projectTransferRepository.deleteAll(listOf(transfer)) }
            verify(exactly = 1) { commentThreadRepository.deleteAll(listOf(thread)) }
            verify(exactly = 1) { pullRequestEventRepository.deleteAll(listOf(prEvent)) }
            verify(exactly = 1) { pullRequestCommitRepository.deleteAll(listOf(prCommit)) }
            verify(exactly = 1) { pullRequestRepository.delete(pr) }
            verify(exactly = 1) { issueService.deleteIssueCascade(issue) }
            verify(exactly = 1) { issueLabelService.deleteCategory(30L) }
            verify(exactly = 1) { assigneeRepository.deleteAll(listOf(assignee)) }
            verify(exactly = 1) { webhookThreadRepository.deleteAll(listOf(webhookThread)) }
            verify(exactly = 1) { webhookRepository.delete(webhook) }
            verify(exactly = 1) { postingService.deletePostingCascade(posting) }
            verify(exactly = 1) { projectUserRepository.deleteAll(listOf(projectUser)) }
            verify(exactly = 1) { projectRepository.delete(project) }
        }

        it("이 프로젝트를 fork한 자식 프로젝트는 삭제하지 않고, 그 fork의 PR만 정리한 뒤 원본 연결을 끊어야 한다") {
            val fork = Project(id = 2L, name = "fork-of-1", owner = "forker")
            val project = Project(id = 1L, name = "original", owner = "owner", forkingProjects = mutableListOf(fork))

            every { projectTransferRepository.findByProjectId(1L) } returns emptyList()
            every { projectTransferRepository.deleteAll(emptyList()) } returns Unit
            every { commentThreadRepository.findByProject(project) } returns emptyList()
            every { commentThreadRepository.deleteAll(emptyList()) } returns Unit
            every { pullRequestRepository.findByFromProject(project) } returns emptyList()
            every { pullRequestRepository.findByToProject(project) } returns emptyList()

            val forkPr = mockk<PullRequest>(relaxed = true)
            every { pullRequestRepository.findByFromProject(fork) } returns listOf(forkPr)
            every { pullRequestRepository.findByToProject(fork) } returns emptyList()
            every { pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(forkPr) } returns emptyList()
            every { pullRequestEventRepository.deleteAll(emptyList<com.github.search5.yona.domain.pullrequest.PullRequestEvent>()) } returns Unit
            every { pullRequestCommitRepository.findByPullRequest(forkPr) } returns emptyList()
            every { pullRequestCommitRepository.deleteAll(emptyList<com.github.search5.yona.domain.pullrequest.PullRequestCommit>()) } returns Unit
            every { pullRequestRepository.delete(forkPr) } returns Unit
            every { projectRepository.save(fork) } returns fork

            every { issueRepository.findByProject(project) } returns emptyList()
            every { issueLabelCategoryRepository.findByProject(project) } returns emptyList()
            every { assigneeRepository.findByProjectId(1L) } returns emptyList()
            every { assigneeRepository.deleteAll(emptyList()) } returns Unit
            every { webhookRepository.findByProjectId(1L) } returns emptyList()
            every { postingRepository.findByProject(project) } returns emptyList()
            every { projectUserRepository.findByProjectId(1L) } returns emptyList()
            every { projectUserRepository.deleteAll(emptyList()) } returns Unit

            every { projectRepository.findById(1L) } returns Optional.of(project)
            every { projectRepository.delete(project) } returns Unit

            projectService.deleteProject(1L)

            verify(exactly = 1) { pullRequestRepository.delete(forkPr) }
            verify(exactly = 1) { projectRepository.save(fork) }
            fork.originalProject shouldBe null
            verify(exactly = 0) { projectRepository.delete(fork) }
            verify(exactly = 1) { projectRepository.delete(project) }
        }
    }
})
