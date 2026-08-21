package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.milestone.Milestone
import com.github.search5.yona.domain.milestone.MilestoneRepository
import com.github.search5.yona.domain.milestone.MilestoneService
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUser
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.attachment.AttachmentRepository
import com.github.search5.yona.domain.attachment.AttachmentService
import com.github.search5.yona.domain.support.MarkdownService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import java.util.Optional
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.pullrequest.ReviewCommentRepository
import com.github.search5.yona.domain.pullrequest.CommitCommentRepository

class MilestoneViewControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val milestoneService = mockk<MilestoneService>()
    val milestoneRepository = mockk<MilestoneRepository>()
    val issueRepository = mockk<IssueRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val attachmentRepository = mockk<AttachmentRepository>()
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

    val attachmentService = mockk<AttachmentService>()
    val milestoneViewController = MilestoneViewController(
        projectRepository,
        milestoneService,
        milestoneRepository,
        issueRepository,
        projectUserRepository,
        userRepository,
        attachmentRepository,
        markdownService,
        accessControl,
        attachmentService
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(milestoneViewController)
        .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
        .build()

    beforeTest {
        io.mockk.clearMocks(
            projectRepository,
            milestoneService,
            milestoneRepository,
            issueRepository,
            projectUserRepository,
            userRepository,
            attachmentRepository,
            markdownService,
            attachmentService
        )
    }

    describe("MilestoneViewController 템플릿 연동 테스트") {
        val project = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PRIVATE)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        // isAllowed(user, project, Operation.READ)는 엔티티 관계(user.isMemberOf) 기반이라, 이 describe
        // 블록에서 공유되는 `user`를 직접 멤버로 바꾸면 아래 "비멤버 403" 테스트가 깨진다 — 필요한 개별
        // 테스트에서만 별도의 memberUser를 만들어 쓴다.
        val milestone = Milestone(id = 2L, title = "마일스톤 테스트", project = project)

        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        describe("GET /{owner}/{projectName}/milestones") {
            it("비공개 프로젝트일 때 멤버라면 200 OK와 milestone/list 뷰를 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 900L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestones(1L, State.OPEN, "dueDate", "asc") } returns listOf(milestone)
                every { issueRepository.findByMilestone(milestone) } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/milestones").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("milestone/list"))
                    .andExpect(model().attributeExists("project", "milestones", "state"))
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
                val groupProject = Project(id = 8L, name = "group-project", owner = "owner", projectScope = ProjectScope.PROTECTED, organization = groupOrg)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "group-project") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(8L, 10L) } returns false
                every { milestoneService.getMilestones(8L, State.OPEN, "dueDate", "asc") } returns emptyList()

                mockMvc.perform(get("/owner/group-project/milestones").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("milestone/list"))
            }

            it("프로젝트 멤버가 아닐 경우 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

                mockMvc.perform(get("/owner/TestProj/milestones").principal(userAuth))
                    .andExpect(view().name("error/403"))
            }

            // yona MilestoneApp.java:50-73 MilestoneCondition(orderBy/orderDir 파라미터) 대응 (P1-128).
            it("orderBy/orderDir 파라미터를 서비스 호출에 그대로 전달해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 902L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestones(1L, State.OPEN, "title", "desc") } returns listOf(milestone)
                every { issueRepository.findByMilestone(milestone) } returns emptyList()

                mockMvc.perform(
                    get("/owner/TestProj/milestones")
                        .param("orderBy", "title")
                        .param("orderDir", "desc")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("orderBy", "title"))
                    .andExpect(model().attribute("orderDir", "desc"))
            }

            // yona Milestone.java:214-227 findMilestones()의 completionRate Comparator 대응 (P1-128).
            // completionRate는 DB 컬럼이 아니라 계산 필드라, 서비스에서 반환된 순서와 무관하게 컨트롤러가
            // 완료율 기준으로 다시 정렬해야 한다.
            it("orderBy=completionRate면 완료율 기준으로 재정렬해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 903L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true

                // 완료율: low=0%(0/2), high=100%(2/2) — 서비스가 반환하는 순서는 일부러 low, high로 둔다.
                val low = Milestone(id = 20L, title = "낮은 완료율", project = project)
                val high = Milestone(id = 21L, title = "높은 완료율", project = project)
                every { milestoneService.getMilestones(1L, State.OPEN, "completionRate", "desc") } returns listOf(low, high)
                every { issueRepository.findByMilestone(low) } returns listOf(
                    Issue(title = "이슈1", project = project, state = com.github.search5.yona.domain.enumeration.State.OPEN),
                    Issue(title = "이슈2", project = project, state = com.github.search5.yona.domain.enumeration.State.OPEN)
                )
                every { issueRepository.findByMilestone(high) } returns listOf(
                    Issue(title = "이슈1", project = project, state = com.github.search5.yona.domain.enumeration.State.CLOSED),
                    Issue(title = "이슈2", project = project, state = com.github.search5.yona.domain.enumeration.State.CLOSED)
                )

                val result = mockMvc.perform(
                    get("/owner/TestProj/milestones")
                        .param("orderBy", "completionRate")
                        .param("orderDir", "desc")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andReturn()

                @Suppress("UNCHECKED_CAST")
                val milestones = result.modelAndView!!.model["milestones"] as List<MilestoneViewController.MilestoneViewDto>
                milestones.map { it.milestone.id } shouldBe listOf(21L, 20L)
            }
        }

        describe("GET /{owner}/{projectName}/milestone/{id}") {
            it("멤버라면 200 OK와 milestone/view 뷰를 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 901L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestone(2L) } returns milestone
                every { issueRepository.findByMilestone(milestone) } returns emptyList()
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.MILESTONE, "2") } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/milestone/2").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("milestone/view"))
                    .andExpect(model().attributeExists("project", "milestoneDto"))
            }

            // yona Milestone.java:99-108 sortedByNumberOfIssue()(이슈 번호 내림차순) 대응 (P2-22).
            // 리포지토리 조회에는 정렬이 없으므로, 컨트롤러가 open/closed 이슈 목록을 번호 내림차순으로
            // 재정렬해야 한다.
            it("이슈 목록은 번호 내림차순으로 정렬돼 있어야 한다 (P2-22)") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 904L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestone(2L) } returns milestone
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.MILESTONE, "2") } returns emptyList()

                every { issueRepository.findByMilestone(milestone) } returns listOf(
                    Issue(number = 3L, title = "이슈3", project = project, state = State.OPEN),
                    Issue(number = 7L, title = "이슈7", project = project, state = State.OPEN),
                    Issue(number = 5L, title = "이슈5", project = project, state = State.OPEN),
                    Issue(number = 1L, title = "이슈1", project = project, state = State.CLOSED),
                    Issue(number = 9L, title = "이슈9", project = project, state = State.CLOSED)
                )

                val result = mockMvc.perform(get("/owner/TestProj/milestone/2").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                val milestoneDto = result.modelAndView!!.model["milestoneDto"] as MilestoneViewController.MilestoneViewDto
                milestoneDto.openIssues.map { it.number } shouldBe listOf(7L, 5L, 3L)
                milestoneDto.closedIssues.map { it.number } shouldBe listOf(9L, 1L)
            }
        }

        describe("GET /{owner}/{projectName}/milestone/new") {
            it("멤버라면 200 OK와 milestone/create 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true

                mockMvc.perform(get("/owner/TestProj/milestone/new").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("milestone/create"))
                    .andExpect(model().attributeExists("project"))
            }
        }

        // yona Attachment.moveOnlySelected() 대응 (P0-22) — 요청받은 첨부파일 ID를 검증 없이 그대로
        // 재배선하지 않고, 실제로 이 로그인 사용자가 업로드한 임시 첨부만 옮기는지 검증한다.
        describe("POST /{owner}/{projectName}/milestones - 임시 업로드 첨부파일 연결") {
            it("temporaryUploadFiles로 넘어온 첨부파일 ID들이 moveOnlySelected를 통해 생성된 마일스톤으로 옮겨져야 한다") {
                val savedMilestone = Milestone(id = 100L, title = "새 마일스톤", project = project)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneRepository.findByProjectAndTitle(project, "새 마일스톤") } returns null
                every { milestoneService.createMilestone(1L, any()) } returns savedMilestone
                every {
                    attachmentService.moveOnlySelected(
                        ResourceType.NOT_A_RESOURCE, "",
                        ResourceType.MILESTONE, "100",
                        listOf(900L), "testuser"
                    )
                } returns 1

                val result = milestoneViewController.createMilestone(
                    owner = "owner",
                    projectName = "TestProj",
                    title = "새 마일스톤",
                    contents = null,
                    dueDate = null,
                    state = State.OPEN,
                    temporaryUploadFiles = "900",
                    authentication = userAuth,
                    redirectAttributes = mockk(relaxed = true),
                    model = org.springframework.ui.ExtendedModelMap()
                )

                result shouldBe "redirect:/owner/TestProj/milestone/100"
                verify(exactly = 1) {
                    attachmentService.moveOnlySelected(
                        ResourceType.NOT_A_RESOURCE, "",
                        ResourceType.MILESTONE, "100",
                        listOf(900L), "testuser"
                    )
                }
            }
        }
    }
})
