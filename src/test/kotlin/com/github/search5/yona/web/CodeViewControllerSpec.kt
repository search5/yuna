package com.github.search5.yona.web

import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.pullrequest.CommentThreadRepository
import com.github.search5.yona.domain.pullrequest.CommitCommentRepository
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.vcs.GitBranch
import com.github.search5.yona.domain.vcs.PlayRepository
import com.github.search5.yona.domain.vcs.RepositoryService
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.*
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.pullrequest.ReviewCommentRepository
import com.github.search5.yona.domain.milestone.MilestoneRepository
import com.github.search5.yona.domain.support.MarkdownService
import io.mockk.clearMocks
import com.github.search5.yona.domain.organization.Organization
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.organization.OrganizationUser
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.eclipse.jgit.api.errors.NoHeadException

class CodeViewControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val repositoryService = mockk<RepositoryService>()
    val commentThreadRepository = mockk<CommentThreadRepository>()
    val commitCommentRepository = mockk<CommitCommentRepository>()
    val markdownService = mockk<MarkdownService>()
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

    val controller = CodeViewController(
        projectRepository,
        projectUserRepository,
        userRepository,
        repositoryService,
        commentThreadRepository,
        commitCommentRepository,
        accessControl,
        markdownService,
        "Yona"
    )

    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    beforeTest {
        clearMocks(
            projectRepository,
            projectUserRepository,
            userRepository,
            repositoryService,
            commentThreadRepository,
            commitCommentRepository
        )
    }

    describe("CodeViewController 단위 테스트") {
        val project = Project(id = 1L, owner = "testowner", name = "testproject", projectScope = ProjectScope.PUBLIC, vcs = "GIT")
        val playRepo = mockk<PlayRepository>()

        describe("GET /{owner}/{projectName}/code") {
            it("저장소가 비어 있는 경우 code/nohead 뷰로 가야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.isEmpty() } returns true

                mockMvc.perform(get("/testowner/testproject/code"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/nohead"))
                    .andExpect(model().attribute("project", project))
            }

            it("저장소가 비어 있지 않은 경우 기본 브랜치로 리다이렉트되어야 한다") {
                val gitBranch = mockk<GitBranch>()
                every { gitBranch.shortName } returns "main"
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.isEmpty() } returns false
                every { playRepo.getHeadBranch() } returns gitBranch

                mockMvc.perform(get("/testowner/testproject/code"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/testowner/testproject/code/main"))
            }

            it("[Test-12-2] 공개 프로젝트이지만 isCodeAccessibleMemberOnly가 true이고 비멤버인 경우 403 Forbidden을 반환해야 한다") {
                val memberOnlyProject = Project(id = 4L, owner = "testowner", name = "memberonly-project", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "memberonly-project") } returns Optional.of(memberOnlyProject)

                // yona CodeApp.java:60-62 forbidden(ErrorViews.Forbidden.render("error.forbidden", project))
                // 대응 (P-템플릿 #47) — Forbidden의 (String,Project) 2-arg 오버로드는 컨텍스트 인지형
                // forbidden.render(messageKey, project)로 귀결되므로 error/forbidden으로 바로잡았다
                // (기존 assertion은 제네릭 error/403이었음).
                mockMvc.perform(get("/testowner/memberonly-project/code"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/forbidden"))
                    .andExpect(model().attribute("project", memberOnlyProject))
            }
        }

        describe("GET /{owner}/{projectName}/code/{branch}/{*path}") {
            it("지정된 브랜치와 경로의 메타데이터를 담아 code/view 템플릿을 호출해야 한다") {
                val objectMapper = ObjectMapper()
                val mockNode = objectMapper.createObjectNode()
                mockNode.put("type", "file")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns listOf("refs/heads/main", "refs/heads/dev")
                every { repositoryService.getMetaDataFromAncestorDirectories(playRepo, "main", "src/Main.kt") } returns listOf(mockNode)

                mockMvc.perform(get("/testowner/testproject/code/main/src/Main.kt"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/view"))
                    .andExpect(model().attribute("project", project))
                    .andExpect(model().attribute("branches", listOf("refs/heads/main", "refs/heads/dev")))
                    .andExpect(model().attribute("branch", "main"))
                    .andExpect(model().attribute("path", "src/Main.kt"))
            }

            // yona views/code/partial_view_file.scala.html:109-114 isMarkdownExtension() 분기 대응 (P1-139).
            it(".md 파일이면 렌더링된 마크다운 HTML을 markdownHtml 모델 속성으로 담아야 한다") {
                val objectMapper = ObjectMapper()
                val mockNode = objectMapper.createObjectNode()
                mockNode.put("type", "file")
                mockNode.put("data", "# 제목")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns listOf("refs/heads/main")
                every { repositoryService.getMetaDataFromAncestorDirectories(playRepo, "main", "README.md") } returns listOf(mockNode)
                every { markdownService.renderFileInCodeBrowser("# 제목", project) } returns "<h1>제목</h1>"

                mockMvc.perform(get("/testowner/testproject/code/main/README.md"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/view"))
                    .andExpect(model().attribute("markdownHtml", "<h1>제목</h1>"))
            }

            it(".md가 아닌 일반 파일이면 markdownHtml 모델 속성을 담지 않아야 한다") {
                val objectMapper = ObjectMapper()
                val mockNode = objectMapper.createObjectNode()
                mockNode.put("type", "file")
                mockNode.put("data", "fun main() {}")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns listOf("refs/heads/main")
                every { repositoryService.getMetaDataFromAncestorDirectories(playRepo, "main", "src/Main.kt") } returns listOf(mockNode)

                mockMvc.perform(get("/testowner/testproject/code/main/src/Main.kt"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/view"))
                    .andExpect(model().attributeDoesNotExist("markdownHtml"))
            }

            it("[Test-12-2-1] 공개 프로젝트이지만 isCodeAccessibleMemberOnly가 true이고 비멤버인 경우 상세 경로 접근 시 403 Forbidden을 반환해야 한다") {
                val memberOnlyProject = Project(id = 4L, owner = "testowner", name = "memberonly-project", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "memberonly-project") } returns Optional.of(memberOnlyProject)

                mockMvc.perform(get("/testowner/memberonly-project/code/main/src/Main.kt"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/403"))
            }

            // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57)
            it("직접 멤버가 아니어도 프로젝트가 속한 조직의 멤버라면 200 OK를 반환해야 한다") {
                val groupOrg = Organization(id = 1L, name = "org")
                val groupUser = User(id = 10L, loginId = "groupuser", name = "그룹멤버")
                groupOrg.organizationUsers.add(
                    OrganizationUser(
                        id = 1L, user = groupUser, organization = groupOrg,
                        role = Role(id = RoleType.ORG_MEMBER.roleType)
                    )
                )
                val groupProject = Project(id = 5L, owner = "testowner", name = "group-project", vcs = "GIT", projectScope = ProjectScope.PROTECTED, organization = groupOrg)
                val groupAuth = UsernamePasswordAuthenticationToken("groupuser", "password")
                val objectMapper = ObjectMapper()
                val mockNode = objectMapper.createObjectNode()
                mockNode.put("type", "file")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "group-project") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("groupuser") } returns Optional.of(groupUser)
                every { projectUserRepository.existsByProjectIdAndUserId(5L, 10L) } returns false
                every { repositoryService.getRepository(groupProject) } returns playRepo
                every { playRepo.getRefNames() } returns listOf("refs/heads/main")
                every { repositoryService.getMetaDataFromAncestorDirectories(playRepo, "main", "src/Main.kt") } returns listOf(mockNode)

                mockMvc.perform(get("/testowner/group-project/code/main/src/Main.kt").principal(groupAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/view"))
            }
        }

        // yona CodeHistoryApp.history()의 "catch (NoHeadException e) { return notFound(nohead.render(project)); }"
        // 대응 (P1-136) — 빈 저장소(커밋이 하나도 없는 상태)에서 커밋 히스토리를 조회하면 JGit이
        // NoHeadException을 던지는데, yuna는 이를 잡지 않아 500으로 전파되고 있었다.
        describe("GET /{owner}/{projectName}/commits/{branch} — 빈 저장소 NoHeadException (P1-136)") {
            it("빈 저장소면 500 대신 code/nohead 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.getRefNames() } returns emptyList()
                every { playRepo.getHistory(0, 25, "HEAD", null) } throws
                    NoHeadException("no HEAD")

                mockMvc.perform(get("/testowner/testproject/commits/HEAD"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("code/nohead"))
                    .andExpect(model().attribute("project", project))
            }
        }

        describe("GET /{owner}/{projectName}/rawcode/{rev}/{*path}") {
            it("바이너리/텍스트 데이터를 응답 헤더와 함께 올바르게 스트리밍해야 한다") {
                val rawBytes = "println('Hello')".toByteArray()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "testproject") } returns Optional.of(project)
                every { repositoryService.getFileAsRaw("testowner", "testproject", "main", "src/Main.kt") } returns rawBytes

                mockMvc.perform(get("/testowner/testproject/rawcode/main/src/Main.kt"))
                    .andExpect(status().isOk)
                    .andExpect(header().string("Content-Disposition", "inline; filename=\"Main.kt\""))
                    .andExpect(content().bytes(rawBytes))
            }

            it("[Test-12-3] 공개 프로젝트이지만 isCodeAccessibleMemberOnly가 true이고 비멤버인 경우 rawcode 다운로드 시 403 Forbidden을 반환해야 한다") {
                val memberOnlyProject = Project(id = 4L, owner = "testowner", name = "memberonly-project", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testowner", "memberonly-project") } returns Optional.of(memberOnlyProject)

                mockMvc.perform(get("/testowner/memberonly-project/rawcode/main/src/Main.kt"))
                    .andExpect(status().isForbidden)
            }
        }
    }
})
