package com.github.search5.yona.web

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.project.ProjectUser
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.organization.Organization
import com.github.search5.yona.domain.organization.OrganizationUser
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.issue.IssueLabel
import com.github.search5.yona.domain.issue.IssueLabelCategory
import com.github.search5.yona.domain.issue.IssueLabelRepository
import com.github.search5.yona.domain.issue.IssueCommentRepository
import com.github.search5.yona.domain.board.PostingCommentRepository
import com.github.search5.yona.domain.board.Posting
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.milestone.Milestone
import com.github.search5.yona.domain.milestone.MilestoneRepository
import com.github.search5.yona.domain.attachment.Attachment
import com.github.search5.yona.domain.attachment.AttachmentRepository
import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.enumeration.ResourceType
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.model
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import java.time.Instant

class MigrationControllerSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val issueRepository = mockk<IssueRepository>()
    val issueLabelRepository = mockk<IssueLabelRepository>()
    val issueCommentRepository = mockk<IssueCommentRepository>()
    val postingRepository = mockk<PostingRepository>()
    val postingCommentRepository = mockk<PostingCommentRepository>()
    val milestoneRepository = mockk<MilestoneRepository>()
    val attachmentRepository = mockk<AttachmentRepository>()

    val migrationControllerAllow = MigrationController(
        userRepository = userRepository,
        projectRepository = projectRepository,
        projectUserRepository = projectUserRepository,
        organizationUserRepository = organizationUserRepository,
        issueRepository = issueRepository,
        issueLabelRepository = issueLabelRepository,
        issueCommentRepository = issueCommentRepository,
        postingRepository = postingRepository,
        postingCommentRepository = postingCommentRepository,
        milestoneRepository = milestoneRepository,
        attachmentRepository = attachmentRepository,
        clientId = "client_id",
        clientSecret = "client_secret",
        allowMigration = true
    )

    val migrationControllerDeny = MigrationController(
        userRepository = userRepository,
        projectRepository = projectRepository,
        projectUserRepository = projectUserRepository,
        organizationUserRepository = organizationUserRepository,
        issueRepository = issueRepository,
        issueLabelRepository = issueLabelRepository,
        issueCommentRepository = issueCommentRepository,
        postingRepository = postingRepository,
        postingCommentRepository = postingCommentRepository,
        milestoneRepository = milestoneRepository,
        attachmentRepository = attachmentRepository,
        clientId = "client_id",
        clientSecret = "client_secret",
        allowMigration = false
    )

    val mockMvcAllow = MockMvcBuilders.standaloneSetup(migrationControllerAllow).build()
    val mockMvcDeny = MockMvcBuilders.standaloneSetup(migrationControllerDeny).build()

    beforeTest {
        io.mockk.clearMocks(
            userRepository,
            projectRepository,
            projectUserRepository,
            organizationUserRepository,
            issueRepository,
            issueLabelRepository,
            issueCommentRepository,
            postingRepository,
            postingCommentRepository,
            milestoneRepository,
            attachmentRepository
        )
    }

    describe("MigrationController 기능 및 API 명세") {
        val testUser = User(id = 1L, loginId = "yona_user", name = "요나유저", email = "yona@example.com")
        val userAuth = UsernamePasswordAuthenticationToken("yona_user", "pass")

        describe("GET /migration (allowMigration = false 일 때)") {
            it("403 Forbidden 뷰가 리턴되어야 한다") {
                mockMvcDeny.perform(get("/migration"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/403"))
            }
        }

        describe("GET /migration (allowMigration = true 일 때)") {
            it("로그인되지 않았으면 로그인 페이지로 리다이렉트되어야 한다") {
                every { userRepository.findByLoginId(any()) } returns Optional.empty()

                mockMvcAllow.perform(get("/migration"))
                    .andExpect(status().is3xxRedirection)
            }

            it("로그인되었을 때 code 파라미터가 없으면 token 없이 마이그레이션 홈 뷰가 반환되어야 한다") {
                every { userRepository.findByLoginId("yona_user") } returns Optional.of(testUser)

                mockMvcAllow.perform(get("/migration").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("migration/home"))
                    .andExpect(model().attribute("token", ""))
            }
        }

        describe("GET /migration/projects") {
            it("현재 사용자가 MANAGER 또는 ORG_ADMIN 권한을 가진 프로젝트 목록을 JSON으로 정상 반환해야 한다") {
                val projRole = Role(id = RoleType.MANAGER.roleType, name = "MANAGER")
                val orgRole = Role(id = RoleType.ORG_ADMIN.roleType, name = "ORG_ADMIN")
                val project1 = Project(id = 10L, name = "proj1", owner = "owner1", projectScope = ProjectScope.PRIVATE)
                val project2 = Project(id = 20L, name = "proj2", owner = "owner2", projectScope = ProjectScope.PUBLIC)
                val org = Organization(id = 5L, name = "org1")
                org.projects.add(project2)

                val projUser = ProjectUser(id = 1L, user = testUser, project = project1, role = projRole)
                val orgUser = OrganizationUser(id = 2L, user = testUser, organization = org, role = orgRole)

                every { userRepository.findByLoginId("yona_user") } returns Optional.of(testUser)
                every { projectUserRepository.findByUserId(1L) } returns listOf(projUser)
                every { organizationUserRepository.findByUserIdAndRoleId(1L, RoleType.ORG_ADMIN.roleType) } returns listOf(orgUser)

                mockMvcAllow.perform(get("/migration/projects").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].projectName").value("proj1"))
                    .andExpect(jsonPath("$[0].private").value(true))
                    .andExpect(jsonPath("$[1].projectName").value("proj2"))
                    .andExpect(jsonPath("$[1].private").value(false))
            }
        }

        describe("GET /migration/{owner}/projects/{projectName}") {
            it("프로젝트의 이슈, 포스트, 마일스톤 카운트 등 상세 정보를 JSON으로 반환해야 한다") {
                val project = Project(id = 10L, name = "proj1", owner = "owner1")
                
                every { userRepository.findByLoginId("yona_user") } returns Optional.of(testUser)
                every { projectRepository.findByOwnerAndName("owner1", "proj1") } returns Optional.of(project)
                every { issueRepository.countByProject(project) } returns 5L
                every { postingRepository.countByProject(project) } returns 3L
                every { milestoneRepository.countByProject(project) } returns 2L

                mockMvcAllow.perform(get("/migration/owner1/projects/proj1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.projectName").value("proj1"))
                    .andExpect(jsonPath("$.issueCount").value(5))
                    .andExpect(jsonPath("$.postCount").value(3))
                    .andExpect(jsonPath("$.milestoneCount").value(2))
            }
        }

        describe("GET /migration/{owner}/projects/{projectName}/labels") {
            it("프로젝트 내 생성된 라벨 목록을 JSON으로 반환해야 한다") {
                val project = Project(id = 10L, name = "proj1", owner = "owner1")
                val category = IssueLabelCategory(id = 1L, name = "Category1", isExclusive = false, project = project)
                val label = IssueLabel(id = 100L, name = "Bug", color = "", category = category, project = project)

                every { userRepository.findByLoginId("yona_user") } returns Optional.of(testUser)
                every { projectRepository.findByOwnerAndName("owner1", "proj1") } returns Optional.of(project)
                every { issueLabelRepository.findByProject(project) } returns listOf(label)

                mockMvcAllow.perform(get("/migration/owner1/projects/proj1/labels").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.labels.100.name").value("Bug"))
                    .andExpect(jsonPath("$.labels.100.categoryName").value("Category1"))
            }
        }

        describe("GET /migration/{owner}/projects/{projectName}/issuelabel") {
            it("프로젝트 내 이슈와 라벨의 쌍 매핑 목록을 JSON으로 반환해야 한다") {
                val project = Project(id = 10L, name = "proj1", owner = "owner1")
                val category = IssueLabelCategory(id = 1L, name = "Category1", isExclusive = false, project = project)
                val label = IssueLabel(id = 100L, name = "Bug", color = "", category = category, project = project)
                val issue = Issue(id = 50L, title = "이슈제목", labels = mutableSetOf(label), project = project)

                every { userRepository.findByLoginId("yona_user") } returns Optional.of(testUser)
                every { projectRepository.findByOwnerAndName("owner1", "proj1") } returns Optional.of(project)
                every { issueRepository.findByProject(project) } returns listOf(issue)

                mockMvcAllow.perform(get("/migration/owner1/projects/proj1/issuelabel").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.issueLabelPairs[0].issue_id").value(50))
                    .andExpect(jsonPath("$.issueLabelPairs[0].issue_label_id").value(100))
            }
        }

        describe("GET /migration/{owner}/projects/{projectName}/milestones") {
            it("프로젝트 내 마일스톤 목록 데이터를 JSON으로 반환해야 한다") {
                val project = Project(id = 10L, name = "proj1", owner = "owner1")
                val milestone = Milestone(id = 200L, title = "v1.0", dueDate = null, contents = "첫 릴리즈", state = State.OPEN, project = project)

                every { userRepository.findByLoginId("yona_user") } returns Optional.of(testUser)
                every { projectRepository.findByOwnerAndName("owner1", "proj1") } returns Optional.of(project)
                every { milestoneRepository.findByProject(project) } returns listOf(milestone)

                mockMvcAllow.perform(get("/migration/owner1/projects/proj1/milestones").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.milestones[0].milestone.title").value("v1.0"))
                    .andExpect(jsonPath("$.milestones[0].milestone.description").value("첫 릴리즈"))
            }
        }

        describe("GET /migration/{owner}/projects/{projectName}/issues") {
            it("프로젝트 내 이슈 및 코멘트 목록 데이터를 JSON으로 반환하고 첨부파일 목록이 본문에 덧붙여져야 한다") {
                val project = Project(id = 10L, name = "proj1", owner = "owner1")
                val issue = Issue(id = 50L, number = 1L, title = "이슈1", body = "이슈 본문 내용", createdDate = Instant.now(), project = project)
                val attachment = Attachment(id = 88L, name = "test.txt", hash = "xyz")

                every { userRepository.findByLoginId("yona_user") } returns Optional.of(testUser)
                every { projectRepository.findByOwnerAndName("owner1", "proj1") } returns Optional.of(project)
                every { issueRepository.findByProject(project) } returns listOf(issue)
                every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(50L) } returns emptyList()
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.ISSUE_POST, "50") } returns listOf(attachment)
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.ISSUE_COMMENT, any()) } returns emptyList()

                mockMvcAllow.perform(get("/migration/owner1/projects/proj1/issues").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.issues[0].issue.title").value("이슈1"))
                    .andExpect(jsonPath("$.issues[0].issue.body").value(org.hamcrest.Matchers.containsString("이슈 본문 내용")))
                    .andExpect(jsonPath("$.issues[0].issue.body").value(org.hamcrest.Matchers.containsString("--- attachments ---")))
                    .andExpect(jsonPath("$.issues[0].issue.body").value(org.hamcrest.Matchers.containsString("[test.txt](/files/88)")))
            }
        }

        describe("GET /migration/{owner}/projects/{projectName}/posts") {
            it("프로젝트 내 자유게시판 포스팅 데이터를 JSON으로 반환해야 한다") {
                val project = Project(id = 10L, name = "proj1", owner = "owner1")
                val post = Posting(id = 60L, number = 2L, title = "포스트1", body = "포스팅 본문 내용", createdDate = Instant.now(), project = project)

                every { userRepository.findByLoginId("yona_user") } returns Optional.of(testUser)
                every { projectRepository.findByOwnerAndName("owner1", "proj1") } returns Optional.of(project)
                every { postingRepository.findByProject(project) } returns listOf(post)
                every { postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(60L) } returns emptyList()
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.BOARD_POST, "60") } returns emptyList()

                mockMvcAllow.perform(get("/migration/owner1/projects/proj1/posts").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.issues[0].issue.title").value("포스트1"))
                    .andExpect(jsonPath("$.issues[0].issue.body").value(org.hamcrest.Matchers.containsString("포스팅 본문 내용")))
            }
        }
    }
})
