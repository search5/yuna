package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.vcs.Commit
import com.github.search5.yona.domain.vcs.GitBranch
import com.github.search5.yona.domain.vcs.PlayRepository
import com.github.search5.yona.domain.vcs.RepositoryService
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

class BranchViewControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val repositoryService = mockk<RepositoryService>()
    val playRepository = mockk<PlayRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    every { organizationUserRepository.findByOrganizationIdAndUserId(any(), any()) } returns Optional.empty()
    val accessControl = AccessControl(projectUserRepository, organizationUserRepository)

    val branchViewController = BranchViewController(
        projectRepository,
        projectUserRepository,
        userRepository,
        repositoryService,
        accessControl
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(branchViewController).build()

    beforeTest {
        io.mockk.clearMocks(projectRepository, projectUserRepository, userRepository, repositoryService, playRepository)
    }

    describe("BranchViewController 웹 API 테스트") {
        val project = Project(id = 1L, name = "TestProject", owner = "owner", vcs = "git", projectScope = ProjectScope.PUBLIC)
        val mockCommit = mockk<Commit>()
        val headBranch = GitBranch(name = "refs/heads/master", headCommit = mockCommit)
        val otherBranch = GitBranch(name = "refs/heads/feature-a", headCommit = mockCommit)

        describe("GET /{owner}/{projectName}/branches") {
            it("성공 시 200 OK와 올바른 뷰 이름, 모델 속성을 반환해야 한다 (HEAD 브랜치 제외 확인)") {
                every { projectRepository.findByOwnerAndName("owner", "TestProject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepository
                every { playRepository.getBranches() } returns listOf(headBranch, otherBranch)
                every { playRepository.getHeadBranch() } returns headBranch

                mockMvc.perform(
                    get("/owner/TestProject/branches")
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/branches"))
                    .andExpect(model().attributeExists("project", "allBranches", "headBranch"))
                    .andExpect(model().attribute("allBranches", listOf(otherBranch))) // HEAD인 refs/heads/master 가 필터링되었는지 검증

                verify { playRepository.getBranches() }
                verify { playRepository.getHeadBranch() }
            }

            it("[Test-12-4] 공개 프로젝트이지만 isCodeAccessibleMemberOnly가 true이고 비멤버인 경우 브랜치 조회를 403 Forbidden 차단해야 한다") {
                val memberOnlyProject = Project(id = 4L, owner = "owner", name = "memberonly-project", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
                every { projectRepository.findByOwnerAndName("owner", "memberonly-project") } returns Optional.of(memberOnlyProject)

                mockMvc.perform(
                    get("/owner/memberonly-project/branches")
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/403"))
            }

            // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57)
            it("직접 멤버가 아니어도 프로젝트가 속한 조직의 멤버라면 200 OK를 반환해야 한다") {
                val groupOrg = com.github.search5.yona.domain.organization.Organization(id = 1L, name = "org")
                val groupUser = com.github.search5.yona.domain.user.User(id = 10L, loginId = "groupuser", name = "그룹멤버")
                groupOrg.organizationUsers.add(
                    com.github.search5.yona.domain.organization.OrganizationUser(
                        id = 1L, user = groupUser, organization = groupOrg,
                        role = com.github.search5.yona.domain.role.Role(id = com.github.search5.yona.domain.role.RoleType.ORG_MEMBER.roleType)
                    )
                )
                val groupProject = Project(id = 5L, owner = "owner", name = "group-project", vcs = "git", projectScope = ProjectScope.PROTECTED, organization = groupOrg)
                val groupAuth = org.springframework.security.authentication.UsernamePasswordAuthenticationToken("groupuser", "password")

                every { projectRepository.findByOwnerAndName("owner", "group-project") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("groupuser") } returns Optional.of(groupUser)
                every { projectUserRepository.existsByProjectIdAndUserId(5L, 10L) } returns false
                every { repositoryService.getRepository(groupProject) } returns playRepository
                every { playRepository.getBranches() } returns listOf(headBranch, otherBranch)
                every { playRepository.getHeadBranch() } returns headBranch

                mockMvc.perform(get("/owner/group-project/branches").principal(groupAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/branches"))
            }
        }
    }
})
