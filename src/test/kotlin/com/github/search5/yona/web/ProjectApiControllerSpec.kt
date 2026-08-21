package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.attachment.Attachment
import com.github.search5.yona.domain.attachment.AttachmentRepository
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.issue.Assignee
import com.github.search5.yona.domain.issue.AssigneeRepository
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueComment
import com.github.search5.yona.domain.issue.IssueCommentRepository
import com.github.search5.yona.domain.issue.IssueLabel
import com.github.search5.yona.domain.issue.IssueLabelCategory
import com.github.search5.yona.domain.issue.IssueLabelRepository
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.milestone.Milestone
import com.github.search5.yona.domain.milestone.MilestoneRepository
import com.github.search5.yona.domain.notification.NotificationUrlResolver
import com.github.search5.yona.domain.organization.Organization
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.organization.OrganizationUser
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.board.Posting
import com.github.search5.yona.domain.board.PostingComment
import com.github.search5.yona.domain.board.PostingCommentRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectUser
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.pullrequest.CommitCommentRepository
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.domain.pullrequest.ReviewCommentRepository
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleRepository
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.vcs.PlayRepository
import com.github.search5.yona.domain.vcs.RepositoryService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import com.github.search5.yona.domain.user.UserState
import com.github.search5.yona.domain.project.ProjectScope
import java.text.SimpleDateFormat
import java.util.Locale

// yona ProjectApi.java newProject() 대응 (P2-45).
class ProjectApiControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val organizationRepository = mockk<OrganizationRepository>()
    val roleRepository = mockk<RoleRepository>()
    val repositoryService = mockk<RepositoryService>()

    // AccessControl은 실제 로직(isGlobalResourceCreatable/isOrganizationAdmin)을 검증하기 위해
    // 이 세션에서 확립된 관례대로 실제 인스턴스를 쓰고, 그 내부 리포지토리만 mock한다.
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val reviewCommentRepository = mockk<ReviewCommentRepository>()
    val commitCommentRepository = mockk<CommitCommentRepository>()
    val milestoneRepository = mockk<MilestoneRepository>()
    val accessControl = AccessControl(
        projectUserRepository, organizationUserRepository,
        userRepository, organizationRepository,
        issueRepository, postingRepository,
        reviewCommentRepository, commitCommentRepository,
        milestoneRepository
    )

    val issueCommentRepository = mockk<IssueCommentRepository>()
    val postingCommentRepository = mockk<PostingCommentRepository>()
    val issueLabelRepository = mockk<IssueLabelRepository>()
    val assigneeRepository = mockk<AssigneeRepository>()
    val attachmentRepository = mockk<AttachmentRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val notificationUrlResolver = mockk<NotificationUrlResolver>()

    val controller = ProjectApiController(
        projectRepository,
        projectUserRepository,
        userRepository,
        organizationRepository,
        roleRepository,
        repositoryService,
        accessControl,
        issueRepository,
        postingRepository,
        issueCommentRepository,
        postingCommentRepository,
        milestoneRepository,
        issueLabelRepository,
        assigneeRepository,
        attachmentRepository,
        pullRequestRepository,
        notificationUrlResolver
    )

    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    beforeTest {
        clearMocks(
            projectRepository, projectUserRepository, userRepository, organizationRepository,
            roleRepository, repositoryService, organizationUserRepository,
            issueRepository, postingRepository, reviewCommentRepository, commitCommentRepository,
            milestoneRepository, issueCommentRepository, postingCommentRepository, issueLabelRepository,
            assigneeRepository, attachmentRepository, pullRequestRepository, notificationUrlResolver,
            answers = false
        )
        every { organizationUserRepository.findByOrganizationIdAndUserId(any(), any()) } returns Optional.empty()
    }

    describe("POST /api/projects/{owner} (P2-45)") {
        val siteManager = User(
            id = 1L, loginId = "admin", name = "관리자",
            state = UserState.SITE_ADMIN
        )
        val normalUser = User(id = 2L, loginId = "normal", name = "일반유저")
        val siteManagerAuth = UsernamePasswordAuthenticationToken("admin", "password")
        val normalAuth = UsernamePasswordAuthenticationToken("normal", "password")
        val sitemanagerRole = Role(id = RoleType.SITEMANAGER.roleType)

        it("인증되지 않은 요청은 401을 반환해야 한다") {
            mockMvc.perform(
                post("/api/projects/someowner")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"projectName": "newproj"}""")
            ).andExpect(status().isUnauthorized)
        }

        it("사이트매니저가 아니면 400과 안내 메시지를 반환해야 한다") {
            every { userRepository.findByLoginId("normal") } returns Optional.of(normalUser)

            mockMvc.perform(
                post("/api/projects/someowner")
                    .principal(normalAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"projectName": "newproj"}""")
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("User creation with api is allowed by Site admin only."))
        }

        it("이미 같은 owner/projectName 프로젝트가 있으면 HTTP 400에 status:409 바디를 담아 반환해야 한다(legacy 원문 그대로)") {
            val existed = Project(id = 50L, owner = "someowner", name = "newproj", overview = "기존", vcs = "GIT")
            every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
            every { projectRepository.findByOwnerAndName("someowner", "newproj") } returns Optional.of(existed)

            mockMvc.perform(
                post("/api/projects/someowner")
                    .principal(siteManagerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"projectName": "newproj"}""")
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.reason").value("Conflict"))
                .andExpect(jsonPath("$.project.id").value(50))
        }

        it("owner가 기존 조직명이고 호출자가 그 조직 admin이 아니면 403을 반환해야 한다") {
            val org = Organization(id = 10L, name = "myorg")
            every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
            every { projectRepository.findByOwnerAndName("myorg", "newproj") } returns Optional.empty()
            every { organizationRepository.findByName("myorg") } returns Optional.of(org)
            every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 1L) } returns Optional.empty()

            mockMvc.perform(
                post("/api/projects/myorg")
                    .principal(siteManagerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"projectName": "newproj"}""")
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.message").value("'관리자' has no permission"))
        }

        it("개인 소유(owner가 조직명이 아님) 프로젝트를 성공적으로 생성해야 한다") {
            every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
            every { projectRepository.findByOwnerAndName("admin", "newproj") } returns Optional.empty()
            every { organizationRepository.findByName("admin") } returns Optional.empty()

            val projectSlot = slot<Project>()
            every { projectRepository.save(capture(projectSlot)) } answers {
                projectSlot.captured.apply { id = 100L }
            }
            every { roleRepository.findById(RoleType.SITEMANAGER.roleType) } returns Optional.of(sitemanagerRole)
            every { projectUserRepository.save(any()) } answers { firstArg() }

            val playRepo = mockk<PlayRepository>(relaxed = true)
            every { repositoryService.getRepository(any()) } returns playRepo

            val result = mockMvc.perform(
                post("/api/projects/admin")
                    .principal(siteManagerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "projectName": "newproj",
                            "projectDescription": "설명",
                            "projectVcs": "GIT",
                            "projectScope": "PUBLIC"
                        }
                        """.trimIndent()
                    )
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.owner").value("admin"))
                .andExpect(jsonPath("$.name").value("newproj"))
                .andExpect(jsonPath("$.overview").value("설명"))
                .andExpect(jsonPath("$.vcs").value("GIT"))
                .andReturn()

            projectSlot.captured.projectScope shouldBe ProjectScope.PUBLIC
            projectSlot.captured.organization shouldBe null
            verify(exactly = 1) { playRepo.create() }
            val savedProjectUserSlot = slot<ProjectUser>()
            verify(exactly = 1) { projectUserRepository.save(capture(savedProjectUserSlot)) }
            savedProjectUserSlot.captured.user shouldBe siteManager
            savedProjectUserSlot.captured.role shouldBe sitemanagerRole
        }

        it("owner가 기존 조직명이고 호출자가 그 조직 admin이면 조직이 연동된 채 생성돼야 한다") {
            val org = Organization(id = 11L, name = "myorg2")
            every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
            every { projectRepository.findByOwnerAndName("myorg2", "newproj") } returns Optional.empty()
            every { organizationRepository.findByName("myorg2") } returns Optional.of(org)
            every { organizationUserRepository.findByOrganizationIdAndUserId(11L, 1L) } returns
                Optional.of(OrganizationUser(user = siteManager, organization = org, role = Role(id = RoleType.ORG_ADMIN.roleType)))

            val projectSlot = slot<Project>()
            every { projectRepository.save(capture(projectSlot)) } answers {
                projectSlot.captured.apply { id = 101L }
            }
            every { roleRepository.findById(RoleType.SITEMANAGER.roleType) } returns Optional.of(sitemanagerRole)
            every { projectUserRepository.save(any()) } answers { firstArg() }
            val playRepo = mockk<PlayRepository>(relaxed = true)
            every { repositoryService.getRepository(any()) } returns playRepo

            mockMvc.perform(
                post("/api/projects/myorg2")
                    .principal(siteManagerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"projectName": "newproj"}""")
            ).andExpect(status().isCreated)

            projectSlot.captured.organization shouldBe org
        }

        it("projectScope가 없거나 알 수 없는 값이면 PRIVATE로 기본 처리돼야 한다") {
            every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
            every { projectRepository.findByOwnerAndName("admin", "newproj2") } returns Optional.empty()
            every { organizationRepository.findByName("admin") } returns Optional.empty()
            val projectSlot = slot<Project>()
            every { projectRepository.save(capture(projectSlot)) } answers { projectSlot.captured.apply { id = 102L } }
            every { roleRepository.findById(RoleType.SITEMANAGER.roleType) } returns Optional.of(sitemanagerRole)
            every { projectUserRepository.save(any()) } answers { firstArg() }
            val playRepo = mockk<PlayRepository>(relaxed = true)
            every { repositoryService.getRepository(any()) } returns playRepo

            mockMvc.perform(
                post("/api/projects/admin")
                    .principal(siteManagerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"projectName": "newproj2", "projectScope": "UNKNOWN_SCOPE"}""")
            ).andExpect(status().isCreated)

            projectSlot.captured.projectScope shouldBe ProjectScope.PRIVATE
        }

        it("projectVcs가 없으면 GIT을 기본값으로 사용해야 한다") {
            every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
            every { projectRepository.findByOwnerAndName("admin", "newproj3") } returns Optional.empty()
            every { organizationRepository.findByName("admin") } returns Optional.empty()
            val projectSlot = slot<Project>()
            every { projectRepository.save(capture(projectSlot)) } answers { projectSlot.captured.apply { id = 103L } }
            every { roleRepository.findById(RoleType.SITEMANAGER.roleType) } returns Optional.of(sitemanagerRole)
            every { projectUserRepository.save(any()) } answers { firstArg() }
            val playRepo = mockk<PlayRepository>(relaxed = true)
            every { repositoryService.getRepository(any()) } returns playRepo

            mockMvc.perform(
                post("/api/projects/admin")
                    .principal(siteManagerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"projectName": "newproj3"}""")
            ).andExpect(status().isCreated)

            projectSlot.captured.vcs shouldBe "GIT"
        }

        it("projectCreatedDate가 legacy 포맷이면 그 시각으로 파싱돼야 한다") {
            every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
            every { projectRepository.findByOwnerAndName("admin", "newproj4") } returns Optional.empty()
            every { organizationRepository.findByName("admin") } returns Optional.empty()
            val projectSlot = slot<Project>()
            every { projectRepository.save(capture(projectSlot)) } answers { projectSlot.captured.apply { id = 104L } }
            every { roleRepository.findById(RoleType.SITEMANAGER.roleType) } returns Optional.of(sitemanagerRole)
            every { projectUserRepository.save(any()) } answers { firstArg() }
            val playRepo = mockk<PlayRepository>(relaxed = true)
            every { repositoryService.getRepository(any()) } returns playRepo

            mockMvc.perform(
                post("/api/projects/admin")
                    .principal(siteManagerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"projectName": "newproj4", "projectCreatedDate": "2020-01-15 PM 03:30:00 +0900"}"""
                    )
            ).andExpect(status().isCreated)

            val expectedInstant = SimpleDateFormat("yyyy-MM-dd a hh:mm:ss Z", Locale.ENGLISH)
                .parse("2020-01-15 PM 03:30:00 +0900").toInstant()
            projectSlot.captured.createdDate shouldBe expectedInstant
        }

        it("projectCreatedDate가 파싱 불가능한 형식이면 조용히 null로 남겨야 한다") {
            every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
            every { projectRepository.findByOwnerAndName("admin", "newproj5") } returns Optional.empty()
            every { organizationRepository.findByName("admin") } returns Optional.empty()
            val projectSlot = slot<Project>()
            every { projectRepository.save(capture(projectSlot)) } answers { projectSlot.captured.apply { id = 105L } }
            every { roleRepository.findById(RoleType.SITEMANAGER.roleType) } returns Optional.of(sitemanagerRole)
            every { projectUserRepository.save(any()) } answers { firstArg() }
            val playRepo = mockk<PlayRepository>(relaxed = true)
            every { repositoryService.getRepository(any()) } returns playRepo

            mockMvc.perform(
                post("/api/projects/admin")
                    .principal(siteManagerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"projectName": "newproj5", "projectCreatedDate": "이상한날짜"}""")
            ).andExpect(status().isCreated)

            projectSlot.captured.createdDate shouldBe null
        }

        it("members 배열의 member/manager 역할을 각각 부여해야 한다") {
            val memberUser = User(id = 20L, loginId = "member1", name = "멤버1", email = "member1@example.com")
            val managerUser = User(id = 21L, loginId = "manager1", name = "매니저1", email = "manager1@example.com")
            val memberRole = Role(id = RoleType.MEMBER.roleType)
            val managerRole = Role(id = RoleType.MANAGER.roleType)

            every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
            every { projectRepository.findByOwnerAndName("admin", "newproj6") } returns Optional.empty()
            every { organizationRepository.findByName("admin") } returns Optional.empty()
            val projectSlot = slot<Project>()
            every { projectRepository.save(capture(projectSlot)) } answers { projectSlot.captured.apply { id = 106L } }
            every { roleRepository.findById(RoleType.SITEMANAGER.roleType) } returns Optional.of(sitemanagerRole)
            every { roleRepository.findById(RoleType.MEMBER.roleType) } returns Optional.of(memberRole)
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectUserRepository.save(any()) } answers { firstArg() }
            every { userRepository.findByEmail("member1@example.com") } returns Optional.of(memberUser)
            every { userRepository.findByEmail("manager1@example.com") } returns Optional.of(managerUser)
            every { projectUserRepository.findByProjectIdAndUserId(106L, 20L) } returns Optional.empty()
            every { projectUserRepository.findByProjectIdAndUserId(106L, 21L) } returns Optional.empty()
            val playRepo = mockk<PlayRepository>(relaxed = true)
            every { repositoryService.getRepository(any()) } returns playRepo

            mockMvc.perform(
                post("/api/projects/admin")
                    .principal(siteManagerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "projectName": "newproj6",
                            "members": [
                                {"email": "member1@example.com", "role": "member"},
                                {"email": "manager1@example.com", "role": "manager"}
                            ]
                        }
                        """.trimIndent()
                    )
            ).andExpect(status().isCreated)

            val savedSlot = mutableListOf<ProjectUser>()
            verify { projectUserRepository.save(capture(savedSlot)) }
            savedSlot.any { it.user == memberUser && it.role == memberRole } shouldBe true
            savedSlot.any { it.user == managerUser && it.role == managerRole } shouldBe true
        }

        it("존재하지 않는 이메일의 멤버는 조용히 건너뛰어야 한다") {
            every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
            every { projectRepository.findByOwnerAndName("admin", "newproj7") } returns Optional.empty()
            every { organizationRepository.findByName("admin") } returns Optional.empty()
            val projectSlot = slot<Project>()
            every { projectRepository.save(capture(projectSlot)) } answers { projectSlot.captured.apply { id = 107L } }
            every { roleRepository.findById(RoleType.SITEMANAGER.roleType) } returns Optional.of(sitemanagerRole)
            every { projectUserRepository.save(any()) } answers { firstArg() }
            every { userRepository.findByEmail("nobody@example.com") } returns Optional.empty()
            val playRepo = mockk<PlayRepository>(relaxed = true)
            every { repositoryService.getRepository(any()) } returns playRepo

            mockMvc.perform(
                post("/api/projects/admin")
                    .principal(siteManagerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"projectName": "newproj7", "members": [{"email": "nobody@example.com", "role": "member"}]}"""
                    )
            ).andExpect(status().isCreated)

            // SITEMANAGER 역할 배정(1회) 외에 멤버 배정 저장이 없어야 한다.
            verify(exactly = 1) { projectUserRepository.save(any()) }
        }
    }

    // yona ProjectApi.java:46-72 exports() 대응 (P2-46).
    describe("GET /api/projects/{owner}/{projectName}/exports (P2-46)") {
        val manager = User(id = 30L, loginId = "manager", name = "매니저", email = "manager@example.com")
        val managerAuth = UsernamePasswordAuthenticationToken("manager", "password")
        val nonMember = User(id = 31L, loginId = "outsider", name = "외부인")
        val nonMemberAuth = UsernamePasswordAuthenticationToken("outsider", "password")
        val managerRole = Role(id = RoleType.MANAGER.roleType)

        val project = Project(
            id = 200L, owner = "acme", name = "widget", overview = "위젯 프로젝트",
            vcs = "GIT", projectScope = ProjectScope.PRIVATE
        )
        val managerProjectUser = ProjectUser(id = 900L, user = manager, project = project, role = managerRole)
        project.projectUsers.add(managerProjectUser)
        manager.projectUsers.add(managerProjectUser)

        it("존재하지 않는 프로젝트는 404를 반환해야 한다") {
            every { projectRepository.findByOwnerAndName("acme", "nope") } returns Optional.empty()

            mockMvc.perform(get("/api/projects/acme/nope/exports").principal(managerAuth))
                .andExpect(status().isNotFound)
        }

        it("매니저/조직관리자가 아니면 403을 반환해야 한다") {
            every { projectRepository.findByOwnerAndName("acme", "widget") } returns Optional.of(project)
            every { userRepository.findByLoginId("outsider") } returns Optional.of(nonMember)

            mockMvc.perform(get("/api/projects/acme/widget/exports").principal(nonMemberAuth))
                .andExpect(status().isForbidden)
        }

        it("매니저는 프로젝트 전체(이슈/게시글/멤버/라벨/마일스톤/작성자/담당가능자)를 JSON으로 내보낼 수 있어야 한다") {
            every { projectRepository.findByOwnerAndName("acme", "widget") } returns Optional.of(project)
            every { userRepository.findByLoginId("manager") } returns Optional.of(manager)

            val assigneeUser = User(id = 40L, loginId = "assignee1", name = "담당자1", email = "assignee1@example.com")
            val issueAuthor = User(id = 41L, loginId = "issueauthor", name = "이슈작성자", email = "issueauthor@example.com")
            val postAuthor = User(id = 42L, loginId = "postauthor", name = "글작성자", email = "postauthor@example.com")
            val prContributor = User(id = 43L, loginId = "contributor1", name = "기여자1", email = "contributor1@example.com")

            val milestone = Milestone(id = 500L, title = "1.0", contents = "첫 릴리즈", project = project, state = State.OPEN)
            val category = IssueLabelCategory(id = 600L, name = "우선순위", isExclusive = true, project = project)
            val label = IssueLabel(id = 601L, name = "긴급", color = "#ff0000", category = category, project = project)

            val issueCreated = java.time.Instant.parse("2026-01-01T00:00:00Z")
            val issue = Issue(
                id = 700L, title = "버그 발생", body = "이슈 본문", project = project, number = 1L,
                authorId = issueAuthor.id, createdDate = issueCreated, updatedDate = issueCreated,
                state = State.OPEN, milestone = milestone,
                assignee = Assignee(id = 701L, user = assigneeUser, project = project)
            )
            issue.labels.add(label)

            val posting = Posting(
                id = 800L, title = "공지사항", body = "게시글 본문", project = project, number = 1L,
                authorId = postAuthor.id, createdDate = issueCreated, updatedDate = issueCreated
            )

            val topComment = IssueComment(
                id = 900L, contents = "댓글입니다", issue = issue, authorId = issueAuthor.id, createdDate = issueCreated
            )
            val replyComment = IssueComment(
                id = 901L, contents = "답글입니다", issue = issue, authorId = manager.id,
                parentComment = topComment, createdDate = issueCreated
            )
            // legacy는 부모가 top-level 목록에 없는(2단계 이상 중첩) 답글이 섞이면 NPE로 exports() 전체가
            // 죽는데, yuna는 이를 재현하지 않고 조용히 무시한다 — 그 케이스도 여기서 함께 검증.
            val orphanReply = IssueComment(
                id = 902L, contents = "고아 답글", issue = issue, authorId = manager.id,
                parentComment = replyComment, createdDate = issueCreated
            )

            val attachment = Attachment(
                id = 1000L, name = "screenshot.png", hash = "abc123",
                containerType = ResourceType.ISSUE_POST, containerId = "700",
                mimeType = "image/png", size = 1024L, createdDate = issueCreated, ownerLoginId = "issueauthor"
            )

            every { issueRepository.findByProject(project) } returns listOf(issue)
            every { postingRepository.findByProject(project) } returns listOf(posting)
            every { milestoneRepository.findByProject(project) } returns listOf(milestone)
            every { issueLabelRepository.findByProject(project) } returns listOf(label)
            every { projectUserRepository.findByProjectId(200L) } returns project.projectUsers
            every { assigneeRepository.findByProjectId(200L) } returns listOf(Assignee(id = 702L, user = assigneeUser, project = project))
            every { pullRequestRepository.findByToProject(project) } returns listOf(
                com.github.search5.yona.domain.pullrequest.PullRequest(
                    id = 1100L, number = 1L, toProject = project, fromProject = project, contributor = prContributor
                )
            )
            every { userRepository.findById(issueAuthor.id!!) } returns Optional.of(issueAuthor)
            every { userRepository.findById(postAuthor.id!!) } returns Optional.of(postAuthor)
            every { userRepository.findById(prContributor.id!!) } returns Optional.of(prContributor)
            every { userRepository.findById(manager.id!!) } returns Optional.of(manager)
            every { notificationUrlResolver.getUrl(ResourceType.ISSUE_POST, "700") } returns "http://localhost/acme/widget/issue/1"
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.ISSUE_POST, "700") } returns listOf(attachment)
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.BOARD_POST, "800") } returns emptyList()
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.ISSUE_COMMENT, any()) } returns emptyList()
            every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(700L) } returns listOf(topComment, replyComment, orphanReply)
            every { postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(800L) } returns emptyList()

            mockMvc.perform(get("/api/projects/acme/widget/exports").principal(managerAuth))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.owner").value("acme"))
                .andExpect(jsonPath("$.projectName").value("widget"))
                .andExpect(jsonPath("$.projectDescription").value("위젯 프로젝트"))
                .andExpect(jsonPath("$.projectVcs").value("GIT"))
                .andExpect(jsonPath("$.projectScope").value("PRIVATE"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.members[0].loginId").value("manager"))
                .andExpect(jsonPath("$.members[0].role").value(managerRole.name))
                .andExpect(jsonPath("$.assignees[0].loginId").value("assignee1"))
                // authors: 이슈작성자, 게시글작성자, PR기여자 순서로 중복없이 모두 포함
                .andExpect(jsonPath("$.authors[0].loginId").value("issueauthor"))
                .andExpect(jsonPath("$.authors[1].loginId").value("postauthor"))
                .andExpect(jsonPath("$.authors[2].loginId").value("contributor1"))
                .andExpect(jsonPath("$.issueCount").value(1))
                .andExpect(jsonPath("$.postCount").value(1))
                .andExpect(jsonPath("$.milestoneCount").value(1))
                // project 최상위 labels는 isExclusive 포함
                .andExpect(jsonPath("$.labels[0].labelName").value("긴급"))
                .andExpect(jsonPath("$.labels[0].isExclusive").value(true))
                .andExpect(jsonPath("$.milestones[0].id").value(500))
                .andExpect(jsonPath("$.milestones[0].state").value("open"))
                // 이슈 본문
                .andExpect(jsonPath("$.issues[0].id").value(700))
                .andExpect(jsonPath("$.issues[0].type").value("ISSUE_POST"))
                .andExpect(jsonPath("$.issues[0].author.loginId").value("issueauthor"))
                .andExpect(jsonPath("$.issues[0].assignees[0].loginId").value("assignee1"))
                .andExpect(jsonPath("$.issues[0].state").value("OPEN"))
                // 이슈 안의 labels는 isExclusive가 없어야 한다(project 최상위 labels와 다른 형태)
                .andExpect(jsonPath("$.issues[0].labels[0].labelName").value("긴급"))
                .andExpect(jsonPath("$.issues[0].labels[0].isExclusive").doesNotExist())
                .andExpect(jsonPath("$.issues[0].milestoneId").value(500))
                .andExpect(jsonPath("$.issues[0].milestoneTitle").value("1.0"))
                .andExpect(jsonPath("$.issues[0].refUrl").value("http://localhost/acme/widget/issue/1"))
                .andExpect(jsonPath("$.issues[0].attachments[0].name").value("screenshot.png"))
                // 댓글: top-level 1개(답글 포함), 고아 답글은 NPE 없이 조용히 누락
                .andExpect(jsonPath("$.issues[0].comments.length()").value(1))
                .andExpect(jsonPath("$.issues[0].comments[0].id").value(900))
                .andExpect(jsonPath("$.issues[0].comments[0].childComments[0].id").value(901))
                .andExpect(jsonPath("$.issues[0].comments[0].childComments[0].childComments").doesNotExist())
                // 게시글 본문 — 이슈 전용 필드(assignees/state/labels/milestoneId/dueDate/refUrl) 없음
                .andExpect(jsonPath("$.posts[0].id").value(800))
                .andExpect(jsonPath("$.posts[0].type").value("BOARD_POST"))
                .andExpect(jsonPath("$.posts[0].author.loginId").value("postauthor"))
                .andExpect(jsonPath("$.posts[0].state").doesNotExist())
                .andExpect(jsonPath("$.posts[0].assignees").doesNotExist())
                .andExpect(jsonPath("$.posts[0].refUrl").doesNotExist())
        }
    }
})
