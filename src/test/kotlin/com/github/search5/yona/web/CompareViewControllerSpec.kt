package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.pullrequest.CommentThreadRepository
import com.github.search5.yona.domain.vcs.Commit
import com.github.search5.yona.domain.vcs.PlayRepository
import com.github.search5.yona.domain.vcs.RepositoryService
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.pullrequest.ReviewCommentRepository
import com.github.search5.yona.domain.pullrequest.CommitCommentRepository
import com.github.search5.yona.domain.milestone.MilestoneRepository

class CompareViewControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val repositoryService = mockk<RepositoryService>()
    val playRepository = mockk<PlayRepository>()
    val commentThreadRepository = mockk<CommentThreadRepository>()
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

    val compareViewController = CompareViewController(
        projectRepository,
        projectUserRepository,
        userRepository,
        repositoryService,
        commentThreadRepository,
        accessControl
    )

    val mockMvc = MockMvcBuilders.standaloneSetup(compareViewController)
        .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
        .build()

    beforeTest {
        io.mockk.clearMocks(projectRepository, projectUserRepository, userRepository, repositoryService, playRepository, commentThreadRepository)
    }

    describe("CompareViewController 템플릿 연동 테스트") {
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        val publicProject = Project(id = 1L, owner = "testowner", name = "public-project", projectScope = ProjectScope.PUBLIC, vcs = "GIT")
        val privateProject = Project(id = 2L, owner = "testowner", name = "private-project", projectScope = ProjectScope.PRIVATE, vcs = "GIT")
        val svnProject = Project(id = 3L, owner = "testowner", name = "svn-project", projectScope = ProjectScope.PUBLIC, vcs = "SUBVERSION")

        val commitA = mockk<Commit>()
        val commitB = mockk<Commit>()

        every { commitA.getId() } returns "aaaaaaa"
        every { commitB.getId() } returns "bbbbbbb"

        describe("GET /{owner}/{projectName}/compare/{revA}..{revB}") {
            it("프로젝트가 존재하지 않으면 404 응답을 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("testowner", "nonexistent") } returns Optional.empty()

                mockMvc.perform(get("/testowner/nonexistent/compare/aaaaaaa..bbbbbbb").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/404"))
            }

            it("비공개 프로젝트일 때 프로젝트 멤버가 아니면 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("testowner", "private-project") } returns Optional.of(privateProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(2L, 10L) } returns false

                mockMvc.perform(get("/testowner/private-project/compare/aaaaaaa..bbbbbbb").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/403"))
            }

            // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57)
            it("직접 멤버가 아니어도 프로젝트가 속한 조직의 멤버라면 200 OK를 반환해야 한다") {
                val org = com.github.search5.yona.domain.organization.Organization(id = 1L, name = "org")
                org.organizationUsers.add(
                    com.github.search5.yona.domain.organization.OrganizationUser(
                        id = 1L, user = user, organization = org,
                        role = com.github.search5.yona.domain.role.Role(id = com.github.search5.yona.domain.role.RoleType.ORG_MEMBER.roleType)
                    )
                )
                val groupProject = Project(id = 6L, owner = "testowner", name = "group-project", projectScope = ProjectScope.PROTECTED, vcs = "GIT", organization = org)

                every { projectRepository.findByOwnerAndName("testowner", "group-project") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(6L, 10L) } returns false
                every { repositoryService.getRepository(groupProject) } returns playRepository
                every { playRepository.getCommit("aaaaaaa") } returns commitA
                every { playRepository.getCommit("bbbbbbb") } returns commitB
                every { playRepository.getDiff("aaaaaaa", "bbbbbbb") } returns emptyList()
                every { commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(groupProject, "bbbbbbb") } returns emptyList()

                mockMvc.perform(get("/testowner/group-project/compare/aaaaaaa..bbbbbbb").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/compare"))
            }

            it("[Test-12-1-1] 공개 프로젝트이지만 isCodeAccessibleMemberOnly가 true이고 비로그인 익명 유저가 접근 시 403 Forbidden을 반환해야 한다") {
                val memberOnlyProject = Project(id = 4L, owner = "testowner", name = "memberonly-project", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
                every { projectRepository.findByOwnerAndName("testowner", "memberonly-project") } returns Optional.of(memberOnlyProject)

                mockMvc.perform(get("/testowner/memberonly-project/compare/aaaaaaa..bbbbbbb"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/403"))
            }

            it("[Test-12-1-2] 공개 프로젝트이지만 isCodeAccessibleMemberOnly가 true이고 프로젝트 비멤버가 로그인 상태로 접근 시 403 Forbidden을 반환해야 한다") {
                val memberOnlyProject = Project(id = 4L, owner = "testowner", name = "memberonly-project", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
                every { projectRepository.findByOwnerAndName("testowner", "memberonly-project") } returns Optional.of(memberOnlyProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(4L, 10L) } returns false

                mockMvc.perform(get("/testowner/memberonly-project/compare/aaaaaaa..bbbbbbb").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/403"))
            }

            it("[Test-12-1-3] 공개 프로젝트이며 isCodeAccessibleMemberOnly가 true이고 프로젝트 멤버가 접근 시 정상 200 OK를 반환해야 한다") {
                val memberOnlyProject = Project(id = 4L, owner = "testowner", name = "memberonly-project", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
                every { projectRepository.findByOwnerAndName("testowner", "memberonly-project") } returns Optional.of(memberOnlyProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(4L, 10L) } returns true
                every { repositoryService.getRepository(memberOnlyProject) } returns playRepository
                every { playRepository.getCommit("aaaaaaa") } returns commitA
                every { playRepository.getCommit("bbbbbbb") } returns commitB
                every { playRepository.getDiff("aaaaaaa", "bbbbbbb") } returns emptyList()
                every { commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(memberOnlyProject, "bbbbbbb") } returns emptyList()

                mockMvc.perform(get("/testowner/memberonly-project/compare/aaaaaaa..bbbbbbb").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/compare"))
            }

            it("공개 프로젝트이며 Git 저장소일 때 200 OK와 code/compare 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("testowner", "public-project") } returns Optional.of(publicProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { repositoryService.getRepository(publicProject) } returns playRepository
                every { playRepository.getCommit("aaaaaaa") } returns commitA
                every { playRepository.getCommit("bbbbbbb") } returns commitB
                every { playRepository.getDiff("aaaaaaa", "bbbbbbb") } returns emptyList()
                every { commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(publicProject, "bbbbbbb") } returns emptyList()

                mockMvc.perform(get("/testowner/public-project/compare/aaaaaaa..bbbbbbb").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/compare"))
                    .andExpect(model().attributeExists("project", "commitA", "commitB", "diffs"))
            }

            it("공개 프로젝트이며 SVN 저장소일 때 200 OK와 code/compare_svn 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("testowner", "svn-project") } returns Optional.of(svnProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { repositoryService.getRepository(svnProject) } returns playRepository
                every { playRepository.getCommit("aaaaaaa") } returns commitA
                every { playRepository.getCommit("bbbbbbb") } returns commitB
                every { playRepository.getPatch("aaaaaaa", "bbbbbbb") } returns "svn-patch-diff-content"
                every { commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(svnProject, "bbbbbbb") } returns emptyList()

                mockMvc.perform(get("/testowner/svn-project/compare/aaaaaaa..bbbbbbb").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/compare_svn"))
                    .andExpect(model().attributeExists("project", "commitA", "commitB", "patch"))
            }

            it("커밋이 존재하지 않는 리비전일 경우 404 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndName("testowner", "public-project") } returns Optional.of(publicProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { repositoryService.getRepository(publicProject) } returns playRepository
                every { playRepository.getCommit("aaaaaaa") } returns null
                every { playRepository.getCommit("bbbbbbb") } returns commitB

                mockMvc.perform(get("/testowner/public-project/compare/aaaaaaa..bbbbbbb").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/404"))
            }
        }
    }
})
