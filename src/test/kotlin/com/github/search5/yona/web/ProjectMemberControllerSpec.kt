package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.organization.Organization
import com.github.search5.yona.domain.organization.OrganizationUser
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUser
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.project.ProjectUserService
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.user.UserState
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.MessageSource
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.pullrequest.ReviewCommentRepository
import com.github.search5.yona.domain.pullrequest.CommitCommentRepository
import com.github.search5.yona.domain.milestone.MilestoneRepository
import io.mockk.clearMocks

// yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57) 회귀 테스트.
class ProjectMemberControllerSpec : DescribeSpec({
    val projectUserService = mockk<ProjectUserService>()
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val messageSource = mockk<MessageSource>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    every { organizationUserRepository.findByOrganizationIdAndUserId(any(), any()) } returns Optional.empty()
    val userRepositoryForAccessControl = mockk<UserRepository>()
    val organizationRepositoryForAccessControl = mockk<OrganizationRepository>()
    val issueRepositoryForAccessControl = mockk<IssueRepository>()
    val postingRepositoryForAccessControl = mockk<PostingRepository>()
    val reviewCommentRepositoryForAccessControl = mockk<ReviewCommentRepository>()
    val commitCommentRepositoryForAccessControl = mockk<CommitCommentRepository>()
    val milestoneRepositoryForAccessControl = mockk<MilestoneRepository>()
    val accessControl = AccessControl(
        projectUserRepository, organizationUserRepository,
        userRepositoryForAccessControl, organizationRepositoryForAccessControl,
        issueRepositoryForAccessControl, postingRepositoryForAccessControl,
        reviewCommentRepositoryForAccessControl, commitCommentRepositoryForAccessControl,
        milestoneRepositoryForAccessControl
    )

    val projectMemberController = ProjectMemberController(
        projectUserService,
        projectRepository,
        projectUserRepository,
        userRepository,
        messageSource,
        accessControl,
        organizationUserRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(projectMemberController).build()

    beforeTest {
        clearMocks(projectUserService, projectRepository, projectUserRepository, userRepository, messageSource)
    }

    describe("GET /api/projects/{projectId}/assignableUsers") {
        val user = User(id = 10L, loginId = "groupuser", name = "그룹멤버")
        val userAuth = UsernamePasswordAuthenticationToken("groupuser", "password")

        it("직접 멤버가 아니면 403 Forbidden을 반환해야 한다") {
            val project = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PRIVATE)

            every { userRepository.findByLoginId("groupuser") } returns Optional.of(user)
            every { userRepository.findById(10L) } returns Optional.of(user)
            every { projectRepository.findById(1L) } returns Optional.of(project)
            every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

            mockMvc.perform(get("/api/projects/1/assignableUsers").principal(userAuth))
                .andExpect(status().isForbidden)
        }

        it("직접 멤버가 아니어도 프로젝트가 속한 조직의 멤버라면 200 OK를 반환해야 한다") {
            val groupOrg = Organization(id = 1L, name = "org")
            groupOrg.organizationUsers.add(
                OrganizationUser(id = 1L, user = user, organization = groupOrg, role = Role(id = RoleType.ORG_MEMBER.roleType))
            )
            val groupProject = Project(id = 9L, name = "group-project", owner = "owner", projectScope = ProjectScope.PROTECTED, organization = groupOrg)

            every { userRepository.findByLoginId("groupuser") } returns Optional.of(user)
            every { userRepository.findById(10L) } returns Optional.of(user)
            every { projectRepository.findById(9L) } returns Optional.of(groupProject)
            every { projectUserRepository.existsByProjectIdAndUserId(9L, 10L) } returns false
            every { projectUserRepository.findByProjectId(9L) } returns emptyList()
            every { organizationUserRepository.findByOrganizationId(1L) } returns
                listOf(OrganizationUser(id = 1L, user = user, organization = groupOrg, role = Role(id = RoleType.ORG_MEMBER.roleType)))
            every { userRepository.findAllById(any()) } returns listOf(user)
            every { messageSource.getMessage("issue.assignToMe", null, "나에게 할당하기", any()) } returns "나에게 할당하기"

            mockMvc.perform(get("/api/projects/9/assignableUsers").principal(userAuth))
                .andExpect(status().isOk)
        }

        // yona Project.java:566-568 getAssignableUsers() → User.java:446-478
        // findUsersByProjectAndOrganization() 대응 (P1-117).
        it("PRIVATE 프로젝트가 속한 조직이면 조직 관리자만 후보에 포함하고 일반 조직멤버는 제외해야 한다") {
            val groupOrg = Organization(id = 2L, name = "private-org")
            val orgAdmin = User(id = 20L, loginId = "orgadmin", name = "조직관리자")
            val project = Project(id = 11L, name = "private-group-project", owner = "owner", projectScope = ProjectScope.PRIVATE, organization = groupOrg)

            val requester = User(id = 40L, loginId = "requester", name = "요청자")
            requester.projectUsers.add(
                ProjectUser(id = 401L, user = requester, project = project, role = Role(id = RoleType.MEMBER.roleType))
            )
            val requesterAuth = UsernamePasswordAuthenticationToken("requester", "password")

            every { userRepository.findByLoginId("requester") } returns Optional.of(requester)
            every { userRepository.findById(40L) } returns Optional.of(requester)
            every { projectRepository.findById(11L) } returns Optional.of(project)
            every { projectUserRepository.findByProjectId(11L) } returns
                listOf(ProjectUser(id = 401L, user = requester, project = project, role = Role(id = RoleType.MEMBER.roleType)))
            every { organizationUserRepository.findByOrganizationIdAndRoleId(2L, RoleType.ORG_ADMIN.roleType) } returns
                listOf(OrganizationUser(id = 2L, user = orgAdmin, organization = groupOrg, role = Role(id = RoleType.ORG_ADMIN.roleType)))
            every { userRepository.findAllById(setOf(40L, 20L)) } returns listOf(requester, orgAdmin)
            every { messageSource.getMessage("issue.assignToMe", null, "나에게 할당하기", any()) } returns "나에게 할당하기"

            val result = mockMvc.perform(get("/api/projects/11/assignableUsers").principal(requesterAuth))
                .andExpect(status().isOk)
                .andReturn()

            val body = result.response.contentAsString
            (body.contains("orgadmin")) shouldBe true
            (body.contains("orgmember")) shouldBe false
            verify(exactly = 0) { organizationUserRepository.findByOrganizationId(2L) }
        }

        it("사이트관리자는 프로젝트/조직 멤버가 아니어도 후보에 포함되어야 한다") {
            val siteManager = User(id = 30L, loginId = "siteadmin", name = "사이트관리자", state = UserState.SITE_ADMIN)
            val siteManagerAuth = UsernamePasswordAuthenticationToken("siteadmin", "password")
            val project = Project(id = 12L, name = "no-group-project", owner = "owner", projectScope = ProjectScope.PRIVATE)

            every { userRepository.findByLoginId("siteadmin") } returns Optional.of(siteManager)
            every { userRepository.findById(30L) } returns Optional.of(siteManager)
            every { projectRepository.findById(12L) } returns Optional.of(project)
            every { projectUserRepository.existsByProjectIdAndUserId(12L, 30L) } returns false
            every { projectUserRepository.findByProjectId(12L) } returns emptyList()
            every { userRepository.findAllById(setOf(30L)) } returns listOf(siteManager)
            every { messageSource.getMessage("issue.assignToMe", null, "나에게 할당하기", any()) } returns "나에게 할당하기"

            val result = mockMvc.perform(get("/api/projects/12/assignableUsers").principal(siteManagerAuth))
                .andExpect(status().isOk)
                .andReturn()

            result.response.contentAsString.contains("siteadmin") shouldBe true
        }
    }
})
