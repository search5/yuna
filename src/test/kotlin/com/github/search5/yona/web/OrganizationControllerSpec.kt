package com.github.search5.yona.web

import com.github.search5.yona.domain.organization.Organization
import com.github.search5.yona.domain.organization.OrganizationService
import com.github.search5.yona.domain.organization.OrganizationUser
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.user.UserState
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import io.mockk.clearMocks

// yona AccessControl.java:119-203 isGlobalResourceAllowed()의 ORGANIZATION 케이스
// "user.isSiteManager() || isOrganizationAdmin" 대응 (P0-21). 조직 REST API가 조직 관리자
// 여부만 검사하고 사이트매니저 전역 우회가 빠져 있던 회귀를 검증한다.
class OrganizationControllerSpec : DescribeSpec({
    val organizationService = mockk<OrganizationService>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val userRepository = mockk<UserRepository>()

    val organizationController = OrganizationController(
        organizationService,
        organizationUserRepository,
        userRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(organizationController).build()

    beforeTest {
        clearMocks(organizationService, organizationUserRepository, userRepository)
    }

    val siteManager = User(id = 1L, loginId = "sitemanager", name = "사이트매니저", state = UserState.SITE_ADMIN)
    val regularUser = User(id = 2L, loginId = "regular", name = "일반유저", state = UserState.ACTIVE)
    val siteManagerAuth = UsernamePasswordAuthenticationToken("sitemanager", "password")
    val regularAuth = UsernamePasswordAuthenticationToken("regular", "password")

    describe("PUT /api/organizations/{orgId}/settings") {
        it("조직 관리자가 아니어도 사이트매니저면 설정을 변경할 수 있어야 한다") {
            every { userRepository.findByLoginId("sitemanager") } returns Optional.of(siteManager)
            every { userRepository.findById(1L) } returns Optional.of(siteManager)
            every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 1L) } returns Optional.empty()
            every { organizationService.updateOrganizationSettings(10L, "new-name", null, 1L) } returns Unit

            mockMvc.perform(
                put("/api/organizations/10/settings")
                    .param("name", "new-name")
                    .principal(siteManagerAuth)
            ).andExpect(status().isOk)
        }

        it("조직 관리자도 사이트매니저도 아니면 403을 반환해야 한다") {
            every { userRepository.findByLoginId("regular") } returns Optional.of(regularUser)
            every { userRepository.findById(2L) } returns Optional.of(regularUser)
            every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 2L) } returns Optional.empty()

            mockMvc.perform(
                put("/api/organizations/10/settings")
                    .param("name", "new-name")
                    .principal(regularAuth)
            ).andExpect(status().isForbidden)
        }

        it("조직 관리자면 사이트매니저가 아니어도 설정을 변경할 수 있어야 한다") {
            val orgAdmin = User(id = 3L, loginId = "orgadmin", name = "조직관리자", state = UserState.ACTIVE)
            val orgAdminAuth = UsernamePasswordAuthenticationToken("orgadmin", "password")
            every { userRepository.findByLoginId("orgadmin") } returns Optional.of(orgAdmin)
            every { userRepository.findById(3L) } returns Optional.of(orgAdmin)
            every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 3L) } returns
                Optional.of(
                    OrganizationUser(
                        user = orgAdmin,
                        organization = Organization(id = 10L, name = "testorg"),
                        role = Role(id = RoleType.ORG_ADMIN.roleType)
                    )
                )
            every { organizationService.updateOrganizationSettings(10L, "new-name", null, 3L) } returns Unit

            mockMvc.perform(
                put("/api/organizations/10/settings")
                    .param("name", "new-name")
                    .principal(orgAdminAuth)
            ).andExpect(status().isOk)
        }
    }

    describe("DELETE /api/organizations/{orgId}") {
        it("조직 관리자가 아니어도 사이트매니저면 조직을 삭제할 수 있어야 한다") {
            every { userRepository.findByLoginId("sitemanager") } returns Optional.of(siteManager)
            every { userRepository.findById(1L) } returns Optional.of(siteManager)
            every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 1L) } returns Optional.empty()
            every { organizationService.deleteOrganization(10L, 1L) } returns Unit

            mockMvc.perform(delete("/api/organizations/10").principal(siteManagerAuth))
                .andExpect(status().isOk)
        }
    }
})
