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
                    .andExpect(view().name("error/403"))
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
                    .andExpect(view().name("error/403"))
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
