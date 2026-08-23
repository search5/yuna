package com.github.search5.yona.web

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUser
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.vcs.RepositoryService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import java.util.Optional

import com.github.search5.yona.domain.project.ProjectService
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.attachment.AttachmentRepository
import com.github.search5.yona.domain.attachment.AttachmentService
import com.github.search5.yona.domain.organization.OrganizationRepository
import org.springframework.context.MessageSource
import com.github.search5.yona.domain.mail.MailService
import com.github.search5.yona.domain.support.MarkdownService
import com.github.search5.yona.domain.role.RoleRepository
import com.github.search5.yona.domain.project.ProjectTransferRepository
import com.github.search5.yona.domain.issue.IssueLabelService
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.domain.milestone.MilestoneRepository
import com.github.search5.yona.domain.project.RecentProjectRepository
import com.github.search5.yona.domain.watch.WatchService
import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.pullrequest.ReviewCommentRepository
import com.github.search5.yona.domain.pullrequest.CommitCommentRepository
import io.mockk.clearMocks
import com.github.search5.yona.domain.vcs.PlayRepository
import com.github.search5.yona.domain.board.Posting
import com.github.search5.yona.domain.organization.Organization
import com.github.search5.yona.domain.organization.OrganizationUser
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.ui.ExtendedModelMap
import org.springframework.http.HttpStatus

class ProjectViewControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val repositoryService = mockk<RepositoryService>()
    val projectService = mockk<ProjectService>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val attachmentRepository = mockk<AttachmentRepository>()
    val attachmentService = mockk<AttachmentService>()
    val organizationRepository = mockk<OrganizationRepository>()
    val messageSource = mockk<MessageSource>()
    val mailService = mockk<MailService>()
    val markdownService = mockk<MarkdownService>()
    val roleRepository = mockk<RoleRepository>()
    val projectTransferRepository = mockk<ProjectTransferRepository>()
    val issueLabelService = mockk<IssueLabelService>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val milestoneRepository = mockk<MilestoneRepository>()
    val watchService = mockk<WatchService>()
    val recentProjectRepository = mockk<RecentProjectRepository>()
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

    val projectViewController = ProjectViewController(
        projectRepository,
        projectUserRepository,
        userRepository,
        repositoryService,
        projectService,
        organizationUserRepository,
        attachmentRepository,
        attachmentService,
        organizationRepository,
        messageSource,
        mailService,
        markdownService,
        roleRepository,
        projectTransferRepository,
        issueLabelService,
        issueRepository,
        postingRepository,
        pullRequestRepository,
        milestoneRepository,
        watchService,
        recentProjectRepository,
        accessControl
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(projectViewController)
        .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
        .build()

    beforeTest {
        clearMocks(
            projectRepository, projectUserRepository, userRepository, repositoryService, projectService,
            organizationUserRepository, attachmentRepository, attachmentService, organizationRepository,
            messageSource, mailService, markdownService, roleRepository, projectTransferRepository,
            issueLabelService, issueRepository, postingRepository, pullRequestRepository, milestoneRepository,
            watchService, recentProjectRepository
        )
        every { recentProjectRepository.recordVisit(any(), any()) } just Runs
        every { organizationUserRepository.findByOrganizationIdAndUserId(any(), any()) } returns Optional.empty()
        every {
            milestoneRepository.findByProjectAndState(any(), any(), any<org.springframework.data.domain.Sort>())
        } returns emptyList()
    }

    describe("ProjectViewController 템플릿 연동 테스트") {
        val project = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PRIVATE)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val managerRole = Role(id = RoleType.MANAGER.roleType)
        val projectUser = ProjectUser(id = 100L, user = user, project = project, role = managerRole)

        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        describe("GET /{owner}/{projectName}") {
            it("비공개 프로젝트일 때 멤버라면 200 OK와 project/home 뷰를 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 900L, user = memberUser, project = project, role = managerRole))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectId(1L) } returns listOf(projectUser)
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { watchService.findWatchers(any(), any()) } returns emptySet()

                mockMvc.perform(get("/owner/TestProj").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/home"))
                    .andExpect(model().attributeExists("project", "projectUsers"))
            }

            // yona partial_readme.scala.html:41 Markdown.renderFileInReadme() 대응 (P1-139) —
            // README 렌더링에 상대경로 링크 치환이 포함된 renderFileInReadme()를 써야 한다(일반 render() 아님).
            it("readme 탭이면 README.md를 renderFileInReadme로 렌더링해 markdownHtml에 담아야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 901L, user = memberUser, project = project, role = managerRole))
                val playRepo = mockk<PlayRepository>()
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectId(1L) } returns listOf(projectUser)
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { watchService.findWatchers(any(), any()) } returns emptySet()
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.isFile("README.md") } returns true
                every { playRepo.getRawFile("HEAD", "README.md") } returns "# 안내".toByteArray(Charsets.UTF_8)
                every { markdownService.renderFileInReadme("# 안내", project) } returns "<h1>안내</h1>"

                mockMvc.perform(get("/owner/TestProj").param("tabId", "readme").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/home"))
                    .andExpect(model().attribute("readmeHtml", "<h1>안내</h1>"))
                verify(exactly = 0) { markdownService.render("# 안내", true, project) }
            }

            // yona partial_readme.scala.html:38-42 대응 (P2-42) — 코드브라우저 메뉴가 꺼진
            // 프로젝트는 게시판 README 글(readme=true) 본문을 git 파일 대신 우선 사용한다.
            it("코드브라우저가 꺼진 프로젝트는 게시판 README 글의 본문을 렌더링해야 한다") {
                val noCodeProject = Project(id = 5L, name = "NoCodeProj", owner = "owner", projectScope = ProjectScope.PRIVATE, isCodeEnabled = false)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 902L, user = memberUser, project = noCodeProject, role = managerRole))
                val readmePosting = Posting(
                    id = 700L, title = "README", body = "게시판 README 본문", project = noCodeProject, number = 1L, readme = true
                )
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoCodeProj") } returns Optional.of(noCodeProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(5L, 10L) } returns true
                every { projectUserRepository.findByProjectId(5L) } returns emptyList()
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { watchService.findWatchers(any(), any()) } returns emptySet()
                val playRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(noCodeProject) } returns playRepo
                every { playRepo.isFile("README.md") } returns true
                every { postingRepository.findByProjectAndReadme(noCodeProject, true) } returns listOf(readmePosting)
                every { markdownService.render("게시판 README 본문", true, noCodeProject) } returns "<p>게시판 README 본문</p>"

                mockMvc.perform(get("/owner/NoCodeProj").param("tabId", "readme").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("readmeHtml", "<p>게시판 README 본문</p>"))
                verify(exactly = 0) { markdownService.renderFileInReadme(any(), any()) }
            }

            it("코드브라우저가 꺼져도 게시판 README 글이 없으면 기존처럼 git 파일을 렌더링해야 한다") {
                val noCodeProject = Project(id = 6L, name = "NoCodeProj2", owner = "owner", projectScope = ProjectScope.PRIVATE, isCodeEnabled = false)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 903L, user = memberUser, project = noCodeProject, role = managerRole))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoCodeProj2") } returns Optional.of(noCodeProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(6L, 10L) } returns true
                every { projectUserRepository.findByProjectId(6L) } returns emptyList()
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { watchService.findWatchers(any(), any()) } returns emptySet()
                val playRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(noCodeProject) } returns playRepo
                every { playRepo.isFile("README.md") } returns true
                every { playRepo.getRawFile("HEAD", "README.md") } returns "# git readme".toByteArray(Charsets.UTF_8)
                every { postingRepository.findByProjectAndReadme(noCodeProject, true) } returns emptyList()
                every { markdownService.renderFileInReadme("# git readme", noCodeProject) } returns "<h1>git readme</h1>"

                mockMvc.perform(get("/owner/NoCodeProj2").param("tabId", "readme").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("readmeHtml", "<h1>git readme</h1>"))
            }

            it("프로젝트 멤버가 아닐 경우 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

                mockMvc.perform(get("/owner/TestProj").principal(userAuth))
                    .andExpect(view().name("error/forbidden"))
            }

            // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57)
            it("직접 멤버가 아니어도 프로젝트가 속한 조직의 멤버라면 200 OK를 반환해야 한다") {
                val groupOrg = Organization(id = 1L, name = "org")
                groupOrg.organizationUsers.add(
                    OrganizationUser(
                        id = 1L, user = user, organization = groupOrg,
                        role = Role(id = RoleType.ORG_MEMBER.roleType)
                    )
                )
                val groupProject = Project(id = 11L, name = "group-project", owner = "owner", projectScope = ProjectScope.PROTECTED, organization = groupOrg)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "group-project") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(11L, 10L) } returns false
                every { projectUserRepository.findByProjectId(11L) } returns emptyList()
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { watchService.findWatchers(any(), any()) } returns emptySet()

                mockMvc.perform(get("/owner/group-project").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/home"))
            }

            // yona GitApp.java:95-104 findByPreviousPlaceOf() 폴백 대응, 웹 라우트 배선 (P1-100).
            it("프로젝트가 이전(rename/소유자 변경)된 뒤에도 예전 owner/name URL로 접근하면 새 위치의 프로젝트를 찾아야 한다") {
                val movedProject = Project(id = 20L, name = "new-name", owner = "new-owner", projectScope = ProjectScope.PUBLIC)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("old-owner", "old-name") } returns Optional.of(movedProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(20L, 10L) } returns false
                every { projectUserRepository.findByProjectId(20L) } returns emptyList()
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { watchService.findWatchers(any(), any()) } returns emptySet()

                mockMvc.perform(get("/old-owner/old-name").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/home"))
                    .andExpect(model().attribute("project", movedProject))
            }
        }

        describe("GET /{owner}/{projectName}/members") {
            it("멤버라면 200 OK와 project/members 뷰를 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 901L, user = memberUser, project = project, role = managerRole))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectId(1L) } returns listOf(projectUser)

                mockMvc.perform(get("/owner/TestProj/members").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/members"))
                    .andExpect(model().attributeExists("project", "projectUsers"))
            }
        }

        describe("GET /{owner}/{projectName}/setting") {
            it("MANAGER 권한을 지닌 멤버라면 200 OK와 project/setting 뷰를 반환해야 한다") {
                val playRepository = mockk<PlayRepository>()
                every { repositoryService.getRepository(project) } returns playRepository
                every { playRepository.getRefNames() } returns listOf("refs/heads/master")
                every { playRepository.getDefaultBranch() } returns "refs/heads/master"

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)

                mockMvc.perform(get("/owner/TestProj/setting").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/setting"))
                    .andExpect(model().attributeExists("project", "branches", "defaultBranch"))
            }

            it("MANAGER 권한이 없는 일반 멤버라면 403 Forbidden 뷰를 반환해야 한다") {
                val memberRole = Role(id = RoleType.MEMBER.roleType)
                val memberProjectUser = ProjectUser(id = 100L, user = user, project = project, role = memberRole)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(memberProjectUser)

                mockMvc.perform(get("/owner/TestProj/setting").principal(userAuth))
                    .andExpect(view().name("error/forbidden"))
            }
        }

        describe("GET /{owner}/{projectName}/changeVCS") {
            it("MANAGER 권한이 있는 멤버는 change_vcs 뷰를 반환받아야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)

                mockMvc.perform(get("/owner/TestProj/changeVCS").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/change_vcs"))
            }
        }

        describe("POST /{owner}/{projectName}/changeVCS") {
            it("MANAGER 권한을 지닌 멤버는 VCS 변경을 성공적으로 요청할 수 있어야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every { projectService.changeVCS(1L) } returns project

                mockMvc.perform(MockMvcRequestBuilders.post("/owner/TestProj/changeVCS").principal(userAuth))
                    .andExpect(status().isNoContent)
            }
        }

        // yona ProjectApp.java:168-186 newProject()의 "owner가 기존 조직명이면 그 조직 admin만
        // 생성 가능" 가드 + "그 조직에 project.organization 연동" 대응 (P2-34).
        describe("POST /projectform (프로젝트 생성)") {
            fun newProjectRequest(owner: String) =
                MockMvcRequestBuilders.post("/projectform")
                    .principal(userAuth)
                    .param("owner", owner)
                    .param("name", "newproj")
                    .param("overview", "설명")
                    .param("projectScope", "PUBLIC")
                    .param("vcs", "GIT")

            it("owner가 조직명이 아니면(개인 프로젝트) 조직 가드 없이 생성된다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                val created = Project(id = 900L, owner = "testuser", name = "newproj")
                every { projectService.createProject(any(), user) } returns created
                every { watchService.watch(any(), any(), any()) } just Runs

                mockMvc.perform(newProjectRequest("testuser"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/testuser/newproj"))

                verify(exactly = 1) { projectService.createProject(any(), user) }
            }

            it("owner가 기존 조직명이고 사용자가 그 조직의 admin이면 조직이 연동된 채 생성된다") {
                val org = Organization(id = 50L, name = "myorg")
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { organizationRepository.findByName("myorg") } returns Optional.of(org)
                every { organizationUserRepository.findByOrganizationIdAndUserId(50L, 10L) } returns
                    Optional.of(OrganizationUser(user = user, organization = org, role = Role(id = RoleType.ORG_ADMIN.roleType)))
                val projectSlot = slot<Project>()
                val created = Project(id = 901L, owner = "myorg", name = "newproj", organization = org)
                every { projectService.createProject(capture(projectSlot), user) } returns created
                every { watchService.watch(any(), any(), any()) } just Runs

                mockMvc.perform(newProjectRequest("myorg"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/myorg/newproj"))

                projectSlot.captured.organization shouldBe org
            }

            it("owner가 기존 조직명인데 사용자가 그 조직의 admin이 아니면 403 Forbidden을 반환하고 생성하지 않는다") {
                val org = Organization(id = 51L, name = "otherorg")
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { organizationRepository.findByName("otherorg") } returns Optional.of(org)
                every { organizationUserRepository.findByOrganizationIdAndUserId(51L, 10L) } returns Optional.empty()

                mockMvc.perform(newProjectRequest("otherorg"))
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { projectService.createProject(any(), any()) }
            }
        }

        describe("GET /{owner}/{projectName}/code/{branch}/download") {
            val memberOnlyProject = Project(id = 4L, owner = "owner", name = "memberonly-project", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")

            it("[Test-12-5-1] 공개 프로젝트이지만 isCodeAccessibleMemberOnly가 true이고 비멤버인 경우 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "memberonly-project") } returns Optional.of(memberOnlyProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(4L, 10L) } returns false

                mockMvc.perform(get("/owner/memberonly-project/code/main/download").principal(userAuth))
                    .andExpect(status().isForbidden)
            }

            it("[Test-12-5-2] 공개 프로젝트이며 isCodeAccessibleMemberOnly가 true이고 멤버인 경우 200 OK와 올바른 zip 출력을 해야 한다") {
                val playRepo = mockk<PlayRepository>()
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "memberonly-project") } returns Optional.of(memberOnlyProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(4L, 10L) } returns true
                every { repositoryService.getRepository(memberOnlyProject) } returns playRepo
                every {
                    repositoryService.getMetaDataFromAncestorDirectories(playRepo, "main", "")
                } returns listOf(mockk())
                every { playRepo.getArchive(any(), "main") } returns Unit

                mockMvc.perform(get("/owner/memberonly-project/code/main/download").principal(userAuth))
                    .andExpect(status().isOk)
            }

            // yona CodeApp.java:135-164 download()의 getMetaDataFromAncestorDirectories() 존재
            // 검증 대응 (P2-30) — 존재하지 않는 브랜치를 요청하면 아카이브 스트리밍을 시도하기 전에
            // 404로 명확히 거부해야 한다(응답 헤더를 이미 써버린 뒤 스트리밍 도중 예외가 나는 것을
            // 방지).
            it("존재하지 않는 브랜치면 아카이브를 생성하지 않고 404를 반환해야 한다 (P2-30)") {
                val playRepo = mockk<PlayRepository>()
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "memberonly-project") } returns Optional.of(memberOnlyProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(4L, 10L) } returns true
                every { repositoryService.getRepository(memberOnlyProject) } returns playRepo
                every {
                    repositoryService.getMetaDataFromAncestorDirectories(playRepo, "no-such-branch", "")
                } returns null

                mockMvc.perform(get("/owner/memberonly-project/code/no-such-branch/download").principal(userAuth))
                    .andExpect(status().isNotFound)

                verify(exactly = 0) { playRepo.getArchive(any(), any()) }
            }
        }

        // yona IssueLabelApp.newLabel/delete/update/updateCategory/copyLabels() 대응 (P-템플릿 #108
        // 재검토, TASK-0262) — project/issuelabels.html이 REST JSON 커스텀 구현 대신 legacy와 동일한
        // 폼 제출 라우트를 쓰도록 교체하며 신설.
        describe("이슈 라벨/카테고리 CRUD 폼 라우트") {
            val labelProject = Project(id = 5L, name = "LabelProj", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val labelManager = User(id = 50L, loginId = "labelmanager", name = "라벨매니저")
            labelManager.projectUsers.add(ProjectUser(id = 500L, user = labelManager, project = labelProject, role = managerRole))
            val labelManagerAuth = UsernamePasswordAuthenticationToken("labelmanager", "password")

            val outsider = User(id = 60L, loginId = "labeloutsider", name = "외부인")
            val outsiderAuth = UsernamePasswordAuthenticationToken("labeloutsider", "password")

            beforeTest {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "LabelProj") } returns Optional.of(labelProject)
                every { userRepository.findByLoginId("labelmanager") } returns Optional.of(labelManager)
                every { userRepository.findByLoginId("labeloutsider") } returns Optional.of(outsider)
            }

            describe("POST /{owner}/{projectName}/issue/labels") {
                it("생성 권한이 있으면 201 Created와 새 라벨 JSON을 반환해야 한다") {
                    val category = com.github.search5.yona.domain.issue.IssueLabelCategory(id = 1L, name = "새카테고리", project = labelProject)
                    val newLabel = com.github.search5.yona.domain.issue.IssueLabel(id = 10L, name = "새라벨", color = "#2196f3", category = category, project = labelProject)
                    every {
                        issueLabelService.newLabelByCategoryName(5L, "새카테고리", false, "새라벨", "#2196f3")
                    } returns newLabel

                    mockMvc.perform(
                        MockMvcRequestBuilders.post("/owner/LabelProj/issue/labels")
                            .principal(labelManagerAuth)
                            .param("labelName", "새라벨")
                            .param("labelColor", "#2196f3")
                            .param("categoryName", "새카테고리")
                    ).andExpect(status().isCreated)
                        .andExpect(jsonPath("$.name").value("새라벨"))
                        .andExpect(jsonPath("$.category").value("새카테고리"))
                }

                it("이미 같은 카테고리+이름의 라벨이 있으면 204 No Content를 반환해야 한다") {
                    every {
                        issueLabelService.newLabelByCategoryName(5L, "기존카테고리", false, "중복라벨", "#000000")
                    } returns null

                    mockMvc.perform(
                        MockMvcRequestBuilders.post("/owner/LabelProj/issue/labels")
                            .principal(labelManagerAuth)
                            .param("labelName", "중복라벨")
                            .param("labelColor", "#000000")
                            .param("categoryName", "기존카테고리")
                    ).andExpect(status().isNoContent)
                }

                it("프로젝트 멤버가 아니면 403 Forbidden을 반환해야 한다") {
                    mockMvc.perform(
                        MockMvcRequestBuilders.post("/owner/LabelProj/issue/labels")
                            .principal(outsiderAuth)
                            .param("labelName", "x")
                            .param("labelColor", "#000000")
                            .param("categoryName", "y")
                    ).andExpect(status().isForbidden)

                    verify(exactly = 0) { issueLabelService.newLabelByCategoryName(any(), any(), any(), any(), any()) }
                }
            }

            describe("POST /{owner}/{projectName}/issue/label/{id}/delete") {
                it("_method=delete와 함께 요청하면 라벨을 삭제하고 200 OK를 반환해야 한다") {
                    every { issueLabelService.deleteLabel(10L) } just Runs

                    mockMvc.perform(
                        MockMvcRequestBuilders.post("/owner/LabelProj/issue/label/10/delete")
                            .principal(labelManagerAuth)
                            .param("_method", "delete")
                    ).andExpect(status().isOk)

                    verify(exactly = 1) { issueLabelService.deleteLabel(10L) }
                }

                it("_method가 delete가 아니면 400 Bad Request를 반환해야 한다") {
                    mockMvc.perform(
                        MockMvcRequestBuilders.post("/owner/LabelProj/issue/label/10/delete")
                            .principal(labelManagerAuth)
                            .param("_method", "put")
                    ).andExpect(status().isBadRequest)

                    verify(exactly = 0) { issueLabelService.deleteLabel(any()) }
                }

                it("프로젝트 멤버가 아니면 403 Forbidden을 반환해야 한다") {
                    mockMvc.perform(
                        MockMvcRequestBuilders.post("/owner/LabelProj/issue/label/10/delete")
                            .principal(outsiderAuth)
                            .param("_method", "delete")
                    ).andExpect(status().isForbidden)
                }
            }

            describe("PUT /{owner}/{projectName}/issue/label/{id}") {
                it("수정 권한이 있으면 라벨을 수정하고 200 OK를 반환해야 한다") {
                    val category = com.github.search5.yona.domain.issue.IssueLabelCategory(id = 1L, name = "카테고리", project = labelProject)
                    every {
                        issueLabelService.updateLabel(10L, "수정된이름", "#ff0000", 1L)
                    } returns com.github.search5.yona.domain.issue.IssueLabel(id = 10L, name = "수정된이름", color = "#ff0000", category = category, project = labelProject)

                    mockMvc.perform(
                        MockMvcRequestBuilders.put("/owner/LabelProj/issue/label/10")
                            .principal(labelManagerAuth)
                            .param("name", "수정된이름")
                            .param("color", "#ff0000")
                            .param("category.id", "1")
                    ).andExpect(status().isOk)

                    verify(exactly = 1) { issueLabelService.updateLabel(10L, "수정된이름", "#ff0000", 1L) }
                }
            }

            describe("PUT /{owner}/{projectName}/issue/label/category/{id}") {
                it("수정 권한이 있으면 카테고리를 수정하고 200 OK를 반환해야 한다") {
                    val category = com.github.search5.yona.domain.issue.IssueLabelCategory(id = 1L, name = "수정된카테고리", project = labelProject)
                    every { issueLabelService.updateCategory(1L, "수정된카테고리", true) } returns category

                    mockMvc.perform(
                        MockMvcRequestBuilders.put("/owner/LabelProj/issue/label/category/1")
                            .principal(labelManagerAuth)
                            .param("name", "수정된카테고리")
                            .param("isExclusive", "true")
                    ).andExpect(status().isOk)
                }

                it("같은 프로젝트 내 다른 카테고리와 이름이 중복되면 400 Bad Request를 반환해야 한다") {
                    every {
                        issueLabelService.updateCategory(1L, "중복이름", false)
                    } throws com.github.search5.yona.domain.issue.DuplicateLabelCategoryNameException("dup")

                    mockMvc.perform(
                        MockMvcRequestBuilders.put("/owner/LabelProj/issue/label/category/1")
                            .principal(labelManagerAuth)
                            .param("name", "중복이름")
                    ).andExpect(status().isBadRequest)
                }
            }

            describe("POST /{owner}/{projectName}/copyLabels") {
                it("원본 프로젝트를 읽을 수 있으면 라벨을 복사하고 labelsform으로 리다이렉트해야 한다") {
                    val fromProject = Project(id = 6L, name = "FromProj", owner = "owner", projectScope = ProjectScope.PUBLIC)
                    every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "FromProj") } returns Optional.of(fromProject)
                    every { issueLabelService.copyLabels(6L, 5L) } returns emptyList()

                    mockMvc.perform(
                        MockMvcRequestBuilders.post("/owner/LabelProj/copyLabels")
                            .principal(labelManagerAuth)
                            .param("owner", "owner")
                            .param("projectName", "FromProj")
                    ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/owner/LabelProj/issue/labelsform"))

                    verify(exactly = 1) { issueLabelService.copyLabels(6L, 5L) }
                }

                it("원본 프로젝트가 존재하지 않으면 조용히 무시하고 labelsform으로 리다이렉트해야 한다") {
                    every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchProj") } returns Optional.empty()

                    mockMvc.perform(
                        MockMvcRequestBuilders.post("/owner/LabelProj/copyLabels")
                            .principal(labelManagerAuth)
                            .param("owner", "owner")
                            .param("projectName", "NoSuchProj")
                    ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/owner/LabelProj/issue/labelsform"))

                    verify(exactly = 0) { issueLabelService.copyLabels(any(), any()) }
                }
            }
        }
    }

    // yona ProjectApp.java:1055-1058 대응 (P0-23) — HIDE_PROJECT_LISTING이 켜져 있으면 사이트매니저를
    // 포함해 누구도 전체 프로젝트 목록(HTML/JSON 둘 다)을 볼 수 없다.
    describe("HIDE_PROJECT_LISTING=true일 때 GET /projects") {
        val hiddenController = ProjectViewController(
            projectRepository, projectUserRepository, userRepository, repositoryService, projectService,
            organizationUserRepository, attachmentRepository, attachmentService, organizationRepository,
            messageSource, mailService, markdownService, roleRepository, projectTransferRepository,
            issueLabelService, issueRepository, postingRepository, pullRequestRepository, milestoneRepository,
            watchService, recentProjectRepository, accessControl, hideProjectListing = true
        )

        it("HTML 목록은 error/403 뷰를 반환해야 한다") {
            val result = hiddenController.projects(filter = "", pageNum = 1, authentication = null, model = ExtendedModelMap())
            result shouldBe "error/403"
        }

        it("JSON API는 403 Forbidden을 반환해야 한다") {
            val response = hiddenController.projectsJson(query = "", filter = "", authentication = null)
            response.statusCode shouldBe HttpStatus.FORBIDDEN
        }
    }
})
