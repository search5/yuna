package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.organization.Organization
import com.github.search5.yona.domain.organization.OrganizationUser
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.project.ProjectUserService
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.context.MessageSource
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

// yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57) 회귀 테스트.
class ProjectMemberControllerSpec : DescribeSpec({
    val projectUserService = mockk<ProjectUserService>()
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val messageSource = mockk<MessageSource>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    every { organizationUserRepository.findByOrganizationIdAndUserId(any(), any()) } returns Optional.empty()
    val accessControl = AccessControl(projectUserRepository, organizationUserRepository)

    val projectMemberController = ProjectMemberController(
        projectUserService,
        projectRepository,
        projectUserRepository,
        userRepository,
        messageSource,
        accessControl
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(projectMemberController).build()

    beforeTest {
        io.mockk.clearMocks(projectUserService, projectRepository, projectUserRepository, userRepository, messageSource)
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
            every { messageSource.getMessage("issue.assignToMe", null, "나에게 할당하기", any()) } returns "나에게 할당하기"

            mockMvc.perform(get("/api/projects/9/assignableUsers").principal(userAuth))
                .andExpect(status().isOk)
        }
    }
})
