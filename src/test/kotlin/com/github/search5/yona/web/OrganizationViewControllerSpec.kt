package com.github.search5.yona.web

import com.github.search5.yona.domain.organization.Organization
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.organization.OrganizationUser
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.user.UserState
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.organization.OrganizationService
import com.github.search5.yona.domain.attachment.AttachmentRepository
import com.github.search5.yona.domain.attachment.AttachmentService
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
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

    val organizationViewController = OrganizationViewController(
        organizationRepository,
        organizationUserRepository,
        userRepository,
        issueRepository,
        postingRepository,
        pullRequestRepository,
        organizationService,
        attachmentRepository,
        attachmentService
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
    }

    describe("OrganizationViewController 템플릿 연동 테스트") {
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저", state = UserState.ACTIVE)
        val siteManager = User(id = 20L, loginId = "admin", name = "관리자", state = UserState.SITE_ADMIN)
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
        val adminAuth = UsernamePasswordAuthenticationToken("admin", "password")

        val org = Organization(id = 1L, name = "testorg")
        val roleMember = Role(id = RoleType.ORG_MEMBER.roleType, name = "ORG_MEMBER")
        val roleAdmin = Role(id = RoleType.ORG_ADMIN.roleType, name = "ORG_ADMIN")

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
    }
})
