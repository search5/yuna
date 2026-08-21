package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.board.Posting
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.board.PostingService
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUser
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.board.PostingCommentRepository
import com.github.search5.yona.domain.watch.WatchService
import com.github.search5.yona.domain.attachment.AttachmentRepository
import com.github.search5.yona.domain.attachment.AttachmentService
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.attachment.Attachment
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import io.mockk.verify
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import tools.jackson.databind.ObjectMapper
import java.util.Optional
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.pullrequest.ReviewCommentRepository
import com.github.search5.yona.domain.pullrequest.CommitCommentRepository
import com.github.search5.yona.domain.milestone.MilestoneRepository

class BoardViewControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val postingService = mockk<PostingService>()
    val postingRepository = mockk<PostingRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val postingCommentRepository = mockk<PostingCommentRepository>()
    val watchService = mockk<WatchService>()
    val attachmentRepository = mockk<AttachmentRepository>()
    val repositoryService = mockk<com.github.search5.yona.domain.vcs.RepositoryService>()
    val objectMapper = ObjectMapper()
    val recentIssueService = mockk<com.github.search5.yona.domain.issue.RecentIssueService>(relaxed = true)
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
    val boardViewController = BoardViewController(
        projectRepository,
        postingService,
        postingRepository,
        projectUserRepository,
        userRepository,
        postingCommentRepository,
        watchService,
        attachmentRepository,
        objectMapper,
        repositoryService,
        "/tmp/yuna/git",
        recentIssueService,
        accessControl,
        attachmentService
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(boardViewController)
        .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
        .build()

    beforeTest {
        io.mockk.clearMocks(projectRepository, postingService, postingRepository, projectUserRepository, userRepository,
            postingCommentRepository, watchService, attachmentRepository)
    }

    describe("BoardViewController 템플릿 연동 테스트") {
        val project = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PRIVATE)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        // isAllowed(user, project, Operation.READ)는 엔티티 관계(user.isMemberOf) 기반이라, 이 describe
        // 블록에서 공유되는 `user`를 직접 멤버로 바꾸면 아래 "비멤버 403" 테스트가 깨진다 — 필요한 개별
        // 테스트에서만 별도의 memberUser를 만들어 쓴다.
        val posting = Posting(id = 5L, title = "게시물 제목", project = project, number = 1L)

        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
        val pageRequest = PageRequest.of(0, 20)

        describe("GET /{owner}/{projectName}/posts") {
            it("비공개 프로젝트일 때 멤버라면 200 OK와 board/list 뷰를 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 900L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingRepository.findByProject(project, any<Pageable>()) } returns PageImpl(listOf(posting), pageRequest, 1)
                every { postingService.getNotices(1L) } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/posts").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("board/list"))
                    .andExpect(model().attributeExists("project", "postingPage", "notices"))
            }

            it("프로젝트 멤버가 아닐 경우 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

                mockMvc.perform(get("/owner/TestProj/posts").principal(userAuth))
                    .andExpect(view().name("error/403"))
            }

            // yona AbstractPostingApp.java:35 ITEMS_PER_PAGE 대응 (P1-105) — 게시글 목록은 항상 고정 15.
            it("페이지 크기는 항상 15로 고정되어야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 900L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                val pageableSlot = io.mockk.slot<Pageable>()
                every { postingRepository.findByProject(project, capture(pageableSlot)) } returns PageImpl(listOf(posting), pageRequest, 1)
                every { postingService.getNotices(1L) } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/posts").principal(userAuth))
                    .andExpect(status().isOk)

                pageableSlot.captured.pageSize shouldBe 15
            }

            // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57) — 직접 멤버가 아니어도
            // PROTECTED 프로젝트가 속한 조직의 멤버라면 읽을 수 있어야 한다.
            it("직접 멤버가 아니어도 프로젝트가 속한 조직의 멤버라면 200 OK를 반환해야 한다") {
                val org = com.github.search5.yona.domain.organization.Organization(id = 1L, name = "org")
                val groupProject = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PROTECTED, organization = org)
                org.organizationUsers.add(
                    com.github.search5.yona.domain.organization.OrganizationUser(
                        id = 1L, user = user, organization = org,
                        role = com.github.search5.yona.domain.role.Role(id = com.github.search5.yona.domain.role.RoleType.ORG_MEMBER.roleType)
                    )
                )

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false
                every { postingRepository.findByProject(groupProject, any<Pageable>()) } returns PageImpl(listOf(posting), pageRequest, 1)
                every { postingService.getNotices(1L) } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/posts").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("board/list"))
            }

            it("labelIds 파라미터가 있으면 라벨 필터 쿼리를 사용해야 한다 (P1-19)") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 901L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every {
                    postingRepository.findByProjectAndLabelIdsIn(project, listOf(3L, 4L), null, any<Pageable>())
                } returns PageImpl(listOf(posting), pageRequest, 1)
                every { postingService.getNotices(1L) } returns emptyList()

                mockMvc.perform(
                    get("/owner/TestProj/posts")
                        .param("labelIds", "3", "4")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("board/list"))
                    .andExpect(model().attribute("labelIds", listOf(3L, 4L)))

                io.mockk.verify(exactly = 0) { postingRepository.findByProject(any(), any<Pageable>()) }
            }
        }

        describe("GET /{owner}/{projectName}/post/{number}") {
            it("멤버라면 200 OK와 board/view 뷰를 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 902L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingService.getPosting(1L, 1L) } returns posting
                every { postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(5L) } returns emptyList()
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.BOARD_POST, "5") } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/post/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("board/view"))
                    .andExpect(model().attributeExists("project", "post"))
            }
        }

        describe("GET /{owner}/{projectName}/post/new") {
            it("멤버라면 200 OK와 board/create 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true

                mockMvc.perform(get("/owner/TestProj/post/new").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("board/create"))
                    .andExpect(model().attributeExists("project"))
            }
        }

        describe("POST /{owner}/{projectName}/post/{number}/edit (P1-44)") {
            it("sendNotificationMail 옵션을 postingService.updatePosting에 그대로 전달해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { postingService.getPosting(1L, 1L) } returns posting
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingService.updatePosting(1L, 1L, "수정 제목", "수정 본문", false, false, 10L, true) } returns posting

                mockMvc.perform(
                    post("/owner/TestProj/post/1/edit")
                        .param("title", "수정 제목")
                        .param("body", "수정 본문")
                        .param("sendNotificationMail", "true")
                        .principal(userAuth)
                )
                    .andExpect(status().is3xxRedirection)

                verify(exactly = 1) { postingService.updatePosting(1L, 1L, "수정 제목", "수정 본문", false, false, 10L, true) }
            }

            it("sendNotificationMail을 선택하지 않으면 false로 전달해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { postingService.getPosting(1L, 1L) } returns posting
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingService.updatePosting(1L, 1L, "수정 제목", "수정 본문", false, false, 10L, false) } returns posting

                mockMvc.perform(
                    post("/owner/TestProj/post/1/edit")
                        .param("title", "수정 제목")
                        .param("body", "수정 본문")
                        .principal(userAuth)
                )
                    .andExpect(status().is3xxRedirection)

                verify(exactly = 1) { postingService.updatePosting(1L, 1L, "수정 제목", "수정 본문", false, false, 10L, false) }
            }
        }

        // yona Attachment.moveOnlySelected() 대응 (P0-22) — 요청받은 첨부파일 ID를 검증 없이 그대로
        // 재배선하지 않고, 실제로 이 로그인 사용자가 업로드한 임시 첨부만 옮기는지 검증한다.
        describe("POST /{owner}/{projectName}/posts - 임시 업로드 첨부파일 연결") {
            it("temporaryUploadFiles로 넘어온 첨부파일 ID들이 moveOnlySelected를 통해 생성된 게시글로 옮겨져야 한다") {
                val savedPosting = Posting(id = 100L, number = 5L, title = "제목", body = "본문", project = project)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingService.createPosting(1L, any(), 10L) } returns savedPosting
                every {
                    attachmentService.moveOnlySelected(
                        ResourceType.NOT_A_RESOURCE, "",
                        ResourceType.BOARD_POST, "100",
                        listOf(900L), "testuser"
                    )
                } returns 1

                val request = PostingForm(title = "제목", body = "본문", temporaryUploadFiles = "900")

                val result = boardViewController.createPost("owner", "TestProj", request, userAuth)

                result shouldBe "redirect:/owner/TestProj/post/5"
                verify(exactly = 1) {
                    attachmentService.moveOnlySelected(
                        ResourceType.NOT_A_RESOURCE, "",
                        ResourceType.BOARD_POST, "100",
                        listOf(900L), "testuser"
                    )
                }
            }
        }
    }
})
