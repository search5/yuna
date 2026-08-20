package com.github.search5.yona.web

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

class StatisticsViewControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val userRepository = mockk<UserRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()

    val statisticsViewController = StatisticsViewController(
        projectRepository,
        userRepository,
        projectUserRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(statisticsViewController).build()

    beforeTest {
        io.mockk.clearMocks(projectRepository, userRepository, projectUserRepository)
    }

    describe("StatisticsViewController 템플릿 연동 테스트") {
        val privateProject = Project(id = 1L, name = "PrivateProj", owner = "owner", projectScope = ProjectScope.PRIVATE)
        val publicProject = Project(id = 2L, name = "PublicProj", owner = "owner", projectScope = ProjectScope.PUBLIC)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")

        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        describe("GET /{owner}/{projectName}/statistics") {
            it("비공개 프로젝트일 때 로그인한 멤버라면 200 OK와 project/statistics 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "PrivateProj") } returns Optional.of(privateProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true

                mockMvc.perform(get("/owner/PrivateProj/statistics").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/statistics"))
                    .andExpect(model().attributeExists("project", "currentUser"))
            }

            it("공개 프로젝트일 때 로그인한 사용자라면 멤버가 아니더라도 200 OK와 project/statistics 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "PublicProj") } returns Optional.of(publicProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)

                mockMvc.perform(get("/owner/PublicProj/statistics").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/statistics"))
                    .andExpect(model().attributeExists("project", "currentUser"))
            }

            it("로그인하지 않은 익명 사용자일 때 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "PublicProj") } returns Optional.of(publicProject)

                mockMvc.perform(get("/owner/PublicProj/statistics"))
                    .andExpect(view().name("error/403"))
            }

            it("비공개 프로젝트이고 로그인한 사용자이지만 멤버가 아닐 때 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "PrivateProj") } returns Optional.of(privateProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

                mockMvc.perform(get("/owner/PrivateProj/statistics").principal(userAuth))
                    .andExpect(view().name("error/403"))
            }

            // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57)
            it("직접 멤버가 아니어도 프로젝트가 속한 조직의 멤버라면 200 OK를 반환해야 한다") {
                val groupOrg = com.github.search5.yona.domain.organization.Organization(id = 1L, name = "org")
                groupOrg.organizationUsers.add(
                    com.github.search5.yona.domain.organization.OrganizationUser(
                        id = 1L, user = user, organization = groupOrg,
                        role = com.github.search5.yona.domain.role.Role(id = com.github.search5.yona.domain.role.RoleType.ORG_MEMBER.roleType)
                    )
                )
                val groupProject = Project(id = 14L, name = "GroupProj", owner = "owner", projectScope = ProjectScope.PROTECTED, organization = groupOrg)

                every { projectRepository.findByOwnerAndName("owner", "GroupProj") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(14L, 10L) } returns false

                mockMvc.perform(get("/owner/GroupProj/statistics").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/statistics"))
            }

            it("프로젝트가 존재하지 않을 때 404 Not Found 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "NonExistProj") } returns Optional.empty()

                mockMvc.perform(get("/owner/NonExistProj/statistics").principal(userAuth))
                    .andExpect(view().name("error/404"))
            }
        }
    }
})
