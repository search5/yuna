package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.board.Posting
import com.github.search5.yona.domain.organization.Organization
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.organization.OrganizationUser
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.pullrequest.CommitCommentRepository
import com.github.search5.yona.domain.pullrequest.ReviewCommentRepository
import com.github.search5.yona.domain.milestone.MilestoneRepository
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.user.UserState
import com.github.search5.yona.domain.issue.IssueRepository
import org.springframework.ui.ExtendedModelMap
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.organization.OrganizationService
import com.github.search5.yona.domain.attachment.AttachmentRepository
import com.github.search5.yona.domain.attachment.AttachmentService
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import java.util.Optional

class OrganizationViewControllerSpec : DescribeSpec({
    val organizationRepository = mockk<OrganizationRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val userRepository = mockk<UserRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val organizationService = mockk<OrganizationService>()
    val attachmentRepository = mockk<AttachmentRepository>()
    val attachmentService = mockk<AttachmentService>()
    val accessControl = AccessControl(
        mockk<ProjectUserRepository>(),
        organizationUserRepository,
        userRepository,
        organizationRepository,
        issueRepository,
        postingRepository,
        mockk<ReviewCommentRepository>(),
        mockk<CommitCommentRepository>(),
        mockk<MilestoneRepository>()
    )

    val organizationViewController = OrganizationViewController(
        organizationRepository,
        organizationUserRepository,
        userRepository,
        issueRepository,
        postingRepository,
        pullRequestRepository,
        organizationService,
        attachmentRepository,
        attachmentService,
        accessControl
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(organizationViewController)
        .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
        .build()

    beforeTest {
        io.mockk.clearMocks(
            organizationRepository, organizationUserRepository, userRepository,
            issueRepository, postingRepository, pullRequestRepository,
            organizationService, attachmentRepository, attachmentService
        )
        every { organizationUserRepository.findByOrganizationIdAndUserId(any(), any()) } returns java.util.Optional.empty()
    }

    describe("OrganizationViewController 템플릿 연동 테스트") {
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저", state = UserState.ACTIVE)
        val siteManager = User(id = 20L, loginId = "admin", name = "관리자", state = UserState.SITE_ADMIN)
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
        val adminAuth = UsernamePasswordAuthenticationToken("admin", "password")

        val org = Organization(id = 1L, name = "testorg")
        val roleMember = Role(id = RoleType.ORG_MEMBER.roleType, name = "ORG_MEMBER")
        val roleAdmin = Role(id = RoleType.ORG_ADMIN.roleType, name = "ORG_ADMIN")

        // yona OrganizationApp.java:90-91 @GuestProhibit 대응 (P1-121). orgList와 동일하게
        // isGuest 계정만 차단하고 비로그인 사용자는 (별도 @AnonymousCheck가 담당하는) error/403으로
        // 처리된다.
        describe("POST /organizations/new") {
            it("게스트 계정이면 조직을 생성하지 않고 인덱스로 리다이렉트해야 한다") {
                val guestAuth = UsernamePasswordAuthenticationToken("guestuser", "password")
                val guestUser = User(id = 31L, loginId = "guestuser", name = "게스트", state = UserState.ACTIVE, isGuest = true)
                every { userRepository.findByLoginId("guestuser") } returns Optional.of(guestUser)

                mockMvc.perform(
                    post("/organizations/new").principal(guestAuth)
                        .param("name", "neworg")
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/"))

                verify(exactly = 0) { organizationService.createOrganization(any(), any(), any()) }
            }

            it("일반 사용자면 조직을 생성해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { organizationService.createOrganization("neworg", null, 10L) } returns Organization(id = 5L, name = "neworg")

                mockMvc.perform(
                    post("/organizations/new").principal(userAuth)
                        .param("name", "neworg")
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/organizations/neworg"))
            }
        }

        describe("GET /org/{orgName}") {
            it("조직이 존재하지 않으면 404 에러 뷰를 반환해야 한다") {
                every { organizationRepository.findByName("nonexistent") } returns Optional.empty()

                mockMvc.perform(get("/org/nonexistent").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/404"))
            }

            it("조직이 존재하면 200 OK와 organization/view 뷰를 반환해야 한다") {
                every { organizationRepository.findByName("testorg") } returns Optional.of(org)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)

                mockMvc.perform(get("/org/testorg").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("organization/view"))
                    .andExpect(model().attributeExists("org", "projects", "orgUsers", "currentUser"))
            }
        }

        describe("GET /org/{orgName}/members") {
            it("조직이 존재하지 않으면 404 에러 뷰를 반환해야 한다") {
                every { organizationRepository.findByName("nonexistent") } returns Optional.empty()

                mockMvc.perform(get("/org/nonexistent/members").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/404"))
            }

            it("일반 유저(조직 Admin 아님)가 접근하면 403 Forbidden 뷰를 반환해야 한다") {
                val orgUser = OrganizationUser(id = 1L, user = user, organization = org, role = roleMember)
                org.organizationUsers = mutableListOf(orgUser)

                every { organizationRepository.findByName("testorg") } returns Optional.of(org)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)

                mockMvc.perform(get("/org/testorg/members").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/403"))
            }

            it("조직 Admin인 유저가 접근하면 200 OK와 organization/members 뷰를 반환해야 한다") {
                val orgUser = OrganizationUser(id = 1L, user = user, organization = org, role = roleAdmin)
                org.organizationUsers = mutableListOf(orgUser)

                every { organizationRepository.findByName("testorg") } returns Optional.of(org)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)

                mockMvc.perform(get("/org/testorg/members").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("organization/members"))
                    .andExpect(model().attributeExists("org", "orgUsers", "currentUser"))
            }

            it("SiteManager인 유저가 접근하면 권한 검사를 패스하고 200 OK와 organization/members 뷰를 반환해야 한다") {
                val orgUser = OrganizationUser(id = 1L, user = user, organization = org, role = roleMember)
                org.organizationUsers = mutableListOf(orgUser)

                every { organizationRepository.findByName("testorg") } returns Optional.of(org)
                every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)

                mockMvc.perform(get("/org/testorg/members").principal(adminAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("organization/members"))
                    .andExpect(model().attributeExists("org", "orgUsers", "currentUser"))
            }
        }

        // yona BoardApp.organizationBoards()가 Organization.getVisibleProjects(User)로 비공개 프로젝트를
        // 걸러내던 것을 대응(P0-17). 조직 게시판 목록에 비공개 프로젝트 게시글이 노출되지 않아야 한다.
        describe("GET /org/{orgName}/boards") {
            val publicProject = Project(id = 100L, name = "pub", projectScope = ProjectScope.PUBLIC, organization = org)
            val privateProject = Project(id = 101L, name = "priv", projectScope = ProjectScope.PRIVATE, organization = org)

            it("조직 비회원에게는 비공개 프로젝트를 제외한 게시글 목록만 노출해야 한다") {
                org.projects = mutableListOf(publicProject, privateProject)
                org.organizationUsers = mutableListOf()
                every { organizationRepository.findByName("testorg") } returns Optional.of(org)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                val projectsSlot = slot<List<Project>>()
                every {
                    postingRepository.findByProjectIn(capture(projectsSlot), any())
                } returns PageImpl(emptyList<Posting>())

                mockMvc.perform(get("/org/testorg/boards").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("organization/boardList"))

                projectsSlot.captured.map { it.id } shouldBe listOf(publicProject.id)
            }

            it("조직 관리자에게는 비공개 프로젝트를 포함한 게시글 목록을 노출해야 한다") {
                org.projects = mutableListOf(publicProject, privateProject)
                every { organizationRepository.findByName("testorg") } returns Optional.of(org)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { organizationUserRepository.findByOrganizationIdAndUserId(org.id!!, user.id!!) } returns
                    Optional.of(OrganizationUser(id = 3L, user = user, organization = org, role = roleAdmin))
                val projectsSlot = slot<List<Project>>()
                every {
                    postingRepository.findByProjectIn(capture(projectsSlot), any())
                } returns PageImpl(emptyList<Posting>())

                mockMvc.perform(get("/org/testorg/boards").principal(userAuth))
                    .andExpect(status().isOk)

                projectsSlot.captured.map { it.id }.toSet() shouldBe setOf(publicProject.id, privateProject.id)
            }
        }

        // yona OrganizationApp.java:485-488 대응 (P0-23) — HIDE_PROJECT_LISTING이 켜져 있으면
        // 누구도 전체 조직 목록을 볼 수 없다.
        describe("HIDE_PROJECT_LISTING=true일 때 GET /orgs") {
            val hiddenController = OrganizationViewController(
                organizationRepository, organizationUserRepository, userRepository, issueRepository,
                postingRepository, pullRequestRepository, organizationService, attachmentRepository,
                attachmentService, accessControl, hideProjectListing = true
            )

            it("error/403 뷰를 반환해야 한다") {
                val result = hiddenController.orgList(filter = "", pageNum = 1, authentication = null, model = ExtendedModelMap())
                result shouldBe "error/403"
            }
        }

        // yona OrganizationApp.java:485-486 @GuestProhibit 대응 (P1-120). 게스트 계정은 조직 목록으로
        // 들어오지 못하고 인덱스로 리다이렉트된다(로그인하지 않은 익명 사용자는 그대로 통과 — isGuest는
        // 로그인 상태의 특수 계정 플래그이지 익명 여부가 아니다).
        describe("GET /orgs 게스트 계정 차단") {
            it("게스트 계정으로 조회하면 인덱스로 리다이렉트해야 한다") {
                val guestAuth = UsernamePasswordAuthenticationToken("guestuser", "password")
                val guestUser = User(id = 30L, loginId = "guestuser", name = "게스트", state = UserState.ACTIVE, isGuest = true)
                every { userRepository.findByLoginId("guestuser") } returns Optional.of(guestUser)

                val result = organizationViewController.orgList(
                    filter = "", pageNum = 1, authentication = guestAuth, model = ExtendedModelMap()
                )
                result shouldBe "redirect:/"
            }

            it("비로그인(익명) 사용자는 차단되지 않고 목록을 그대로 볼 수 있어야 한다") {
                every { organizationRepository.findByNameContainingIgnoreCaseOrDescrContainingIgnoreCase(any(), any(), any()) } returns
                    PageImpl(emptyList<Organization>())

                val result = organizationViewController.orgList(
                    filter = "", pageNum = 1, authentication = null, model = ExtendedModelMap()
                )
                result shouldBe "organization/list"
            }
        }
    }
})
