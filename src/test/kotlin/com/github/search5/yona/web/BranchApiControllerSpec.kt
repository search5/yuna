package com.github.search5.yona.web

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.vcs.PlayRepository
import com.github.search5.yona.domain.vcs.RepositoryService
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.pullrequest.ReviewCommentRepository
import com.github.search5.yona.domain.pullrequest.CommitCommentRepository
import com.github.search5.yona.domain.milestone.MilestoneRepository

class BranchApiControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val repositoryService = mockk<RepositoryService>()
    val playRepository = mockk<PlayRepository>()
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

    val branchApiController = BranchApiController(
        projectRepository,
        projectUserRepository,
        userRepository,
        repositoryService,
        accessControl
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(branchApiController).build()

    beforeTest {
        io.mockk.clearMocks(projectRepository, projectUserRepository, userRepository, repositoryService, playRepository)
    }

    describe("BranchApiController 웹 API 테스트") {
        val project = Project(id = 1L, name = "TestProject", owner = "owner", vcs = "git", projectScope = ProjectScope.PUBLIC)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        describe("POST /{owner}/{projectName}/code/{branch}/setAsDefault") {
            it("성공 시 302 리다이렉트와 setDefaultBranch 메소드가 정상 호출되어야 한다") {
                every { projectRepository.findByOwnerAndName("owner", "TestProject") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { repositoryService.getRepository(project) } returns playRepository
                every { playRepository.setDefaultBranch("feature-a") } returns Unit

                mockMvc.perform(
                    post("/owner/TestProject/code/feature-a/setAsDefault").principal(userAuth)
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/owner/TestProject/branches"))

                verify { playRepository.setDefaultBranch("feature-a") }
            }

            // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57)
            it("직접 멤버가 아니어도 프로젝트가 속한 조직의 멤버라면 성공해야 한다") {
                val org = com.github.search5.yona.domain.organization.Organization(id = 1L, name = "org")
                val groupProject = Project(id = 1L, name = "TestProject", owner = "owner", vcs = "git", projectScope = ProjectScope.PROTECTED, organization = org)
                org.organizationUsers.add(
                    com.github.search5.yona.domain.organization.OrganizationUser(
                        id = 1L, user = user, organization = org,
                        role = com.github.search5.yona.domain.role.Role(id = com.github.search5.yona.domain.role.RoleType.ORG_MEMBER.roleType)
                    )
                )

                every { projectRepository.findByOwnerAndName("owner", "TestProject") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false
                every { repositoryService.getRepository(groupProject) } returns playRepository
                every { playRepository.setDefaultBranch("feature-a") } returns Unit

                mockMvc.perform(
                    post("/owner/TestProject/code/feature-a/setAsDefault").principal(userAuth)
                )
                    .andExpect(status().is3xxRedirection)

                verify { playRepository.setDefaultBranch("feature-a") }
            }
        }

        describe("DELETE /{owner}/{projectName}/code/{branch}") {
            it("매니저가 삭제를 요청하면 302 리다이렉트와 deleteBranch 메소드가 정상 호출되어야 한다 (P1-97, legacy PROJECT DELETE는 매니저/조직관리자 전용)") {
                val managerUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                managerUser.projectUsers.add(
                    com.github.search5.yona.domain.project.ProjectUser(
                        id = 200L, user = managerUser, project = project,
                        role = com.github.search5.yona.domain.role.Role(id = com.github.search5.yona.domain.role.RoleType.MANAGER.roleType)
                    )
                )

                every { projectRepository.findByOwnerAndName("owner", "TestProject") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(managerUser)
                every { repositoryService.getRepository(project) } returns playRepository
                every { playRepository.deleteBranch("feature-a") } returns Unit

                mockMvc.perform(
                    delete("/owner/TestProject/code/feature-a").principal(userAuth)
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/owner/TestProject/branches"))

                verify { playRepository.deleteBranch("feature-a") }
            }

            it("매니저가 아닌 일반 멤버가 삭제를 요청하면 403 Forbidden 화면을 반환해야 한다 (P1-97)") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(
                    com.github.search5.yona.domain.project.ProjectUser(
                        id = 201L, user = memberUser, project = project,
                        role = com.github.search5.yona.domain.role.Role(id = com.github.search5.yona.domain.role.RoleType.MEMBER.roleType)
                    )
                )

                every { projectRepository.findByOwnerAndName("owner", "TestProject") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)

                mockMvc.perform(
                    delete("/owner/TestProject/code/feature-a").principal(userAuth)
                )
                    .andExpect(status().isOk)

                verify(exactly = 0) { playRepository.deleteBranch(any()) }
            }
        }
    }
})
