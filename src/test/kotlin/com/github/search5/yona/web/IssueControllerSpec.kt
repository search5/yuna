package com.github.search5.yona.web

import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.issue.Assignee
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.issue.IssueService
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUser
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.issue.IssueCommentRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

class IssueControllerSpec : DescribeSpec({
    val issueService = mockk<IssueService>()
    val issueRepository = mockk<IssueRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val attachmentService = mockk<com.github.search5.yona.domain.attachment.AttachmentService>()
    val issueCommentRepository = mockk<IssueCommentRepository>()
    val issueEventRepository = mockk<com.github.search5.yona.domain.issue.IssueEventRepository>()

    val issueController = IssueController(
        issueService,
        issueRepository,
        projectRepository,
        projectUserRepository,
        userRepository,
        attachmentService,
        issueCommentRepository,
        issueEventRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(issueController)
        .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
        .build()

    beforeTest {
        io.mockk.clearMocks(issueService, issueRepository, projectRepository, projectUserRepository, userRepository, attachmentService, issueCommentRepository, issueEventRepository)
    }

    describe("IssueController 웹 API 테스트") {
        val project = Project(id = 1L, name = "TestProject", projectScope = ProjectScope.PRIVATE)
        val publicProject = Project(id = 2L, name = "PublicProject", projectScope = ProjectScope.PUBLIC)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val managerUser = User(id = 20L, loginId = "manageruser", name = "관리자유저")
        val otherUser = User(id = 30L, loginId = "otheruser", name = "외부유저")
        
        val memberRole = Role(id = RoleType.MEMBER.roleType)
        val managerRole = Role(id = RoleType.MANAGER.roleType)

        val projectUser = ProjectUser(id = 100L, user = user, project = project, role = memberRole)
        val projectManagerUser = ProjectUser(id = 101L, user = managerUser, project = project, role = managerRole)

        val issue = Issue(id = 5L, number = 5L, title = "이슈 제목", body = "이슈 내용", project = project, authorId = user.id, state = State.OPEN)

        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
        val managerAuth = UsernamePasswordAuthenticationToken("manageruser", "password")
        val otherAuth = UsernamePasswordAuthenticationToken("otheruser", "password")
        val pageRequest = PageRequest.of(0, 25)

        describe("GET /api/projects/{projectId}/issues") {
            it("공개 프로젝트의 경우 비로그인 상태여도 이슈 목록을 반환해야 한다") {
                every { projectRepository.findById(2L) } returns Optional.of(publicProject)
                every { userRepository.findByLoginId(any()) } returns Optional.empty()
                every { issueRepository.findByProject(publicProject, any<Pageable>()) } returns PageImpl(listOf(issue), pageRequest, 1)

                mockMvc.perform(get("/api/projects/2/issues"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.content[0].title").value("이슈 제목"))
            }

            it("비공개 프로젝트의 경우 권한이 없는 유저가 조회하면 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 30L) } returns false

                mockMvc.perform(get("/api/projects/1/issues").principal(otherAuth))
                    .andExpect(status().isForbidden)
            }

            it("비공개 프로젝트의 경우 프로젝트 멤버인 유저가 조회하면 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { issueRepository.findByProject(project, any<Pageable>()) } returns PageImpl(listOf(issue), pageRequest, 1)

                mockMvc.perform(get("/api/projects/1/issues").principal(userAuth))
                    .andExpect(status().isOk)
            }
        }

        describe("GET /api/projects/{projectId}/issues/{issueId}") {
            it("권한이 있는 유저가 상세 조회하면 이슈 정보를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true

                mockMvc.perform(get("/api/projects/1/issues/5").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.title").value("이슈 제목"))
            }

            // yona AccessControl.java:274-279,368-383 isAllowedIfSharer() 대응 (P1-82)
            it("프로젝트 멤버가 아니어도 이슈 공유자(IssueSharer)면 상세 조회를 허용해야 한다") {
                val sharedIssue = Issue(
                    id = 6L, number = 6L, title = "공유된 이슈", body = "본문", project = project,
                    authorId = user.id, state = State.OPEN
                )
                sharedIssue.sharers.add(
                    com.github.search5.yona.domain.issue.IssueSharer(
                        loginId = otherUser.loginId, user = otherUser, issue = sharedIssue
                    )
                )

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 6L) } returns sharedIssue
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 30L) } returns false

                mockMvc.perform(get("/api/projects/1/issues/6").principal(otherAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.title").value("공유된 이슈"))
            }

            it("이슈 공유자가 아니고 프로젝트 멤버도 아니면 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 30L) } returns false

                mockMvc.perform(get("/api/projects/1/issues/5").principal(otherAuth))
                    .andExpect(status().isForbidden)
            }
        }

        describe("GET /api/projects/{projectId}/issues/{issueId}/timeline") {
            it("권한이 있는 유저가 조회하면 이슈의 변경 이력을 시간순으로 반환해야 한다") {
                val issueEvent = com.github.search5.yona.domain.issue.IssueEvent(
                    id = 1L, issue = issue, eventType = com.github.search5.yona.domain.enumeration.EventType.ISSUE_STATE_CHANGED,
                    oldValue = "OPEN", newValue = "CLOSED"
                )
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { issueEventRepository.findByIssueOrderByCreatedAsc(issue) } returns listOf(issueEvent)

                mockMvc.perform(get("/api/projects/1/issues/5/timeline").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].eventType").value("ISSUE_STATE_CHANGED"))
            }

            it("존재하지 않는 이슈면 404를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 999L) } returns null

                mockMvc.perform(get("/api/projects/1/issues/999/timeline").principal(userAuth))
                    .andExpect(status().isNotFound)
            }

            // yona IssueApp.java:299 timeline()의 @IsAllowed(resourceType = ISSUE_POST, READ) 대응 (P1-82)
            it("프로젝트 멤버가 아니어도 이슈 공유자면 변경 이력 조회를 허용해야 한다") {
                val sharedIssue = Issue(
                    id = 7L, number = 7L, title = "공유된 이슈2", body = "본문", project = project,
                    authorId = user.id, state = State.OPEN
                )
                sharedIssue.sharers.add(
                    com.github.search5.yona.domain.issue.IssueSharer(
                        loginId = otherUser.loginId, user = otherUser, issue = sharedIssue
                    )
                )

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 7L) } returns sharedIssue
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 30L) } returns false
                every { issueEventRepository.findByIssueOrderByCreatedAsc(sharedIssue) } returns emptyList()

                mockMvc.perform(get("/api/projects/1/issues/7/timeline").principal(otherAuth))
                    .andExpect(status().isOk)
            }
        }

        describe("POST /api/projects/{projectId}/issues") {
            it("프로젝트 멤버인 유저가 새 이슈를 작성하면 201 Created를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { issueService.createIssue(any(), user, null, null, null, false) } returns issue

                val jsonContent = """
                    {
                        "title": "이슈 제목",
                        "body": "이슈 내용",
                        "milestoneId": null,
                        "assigneeId": null,
                        "labelIds": null
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/api/projects/1/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isCreated)
            }

            // yona AbstractPosting.isPublish 대응 (P1-65).
            it("isDraft=true로 요청하면 초안 생성 요청이 서비스에 그대로 전달되어야 한다") {
                val draftIssue = Issue(id = 6L, number = null, title = "초안 제목", body = "초안 내용", project = project, authorId = user.id, state = State.DRAFT)

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { issueService.createIssue(any(), user, null, null, null, true) } returns draftIssue

                val jsonContent = """
                    {
                        "title": "초안 제목",
                        "body": "초안 내용",
                        "milestoneId": null,
                        "assigneeId": null,
                        "labelIds": null,
                        "isDraft": true
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/api/projects/1/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$.state").value("DRAFT"))

                verify(exactly = 1) { issueService.createIssue(any(), user, null, null, null, true) }
            }
        }

        describe("PUT /api/projects/{projectId}/issues/{issueId}") {
            it("작성자가 이슈를 수정하면 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every { issueService.updateIssue(5L, "수정된 제목", "수정된 내용", user, null, null, null) } returns issue

                val jsonContent = """
                    {
                        "title": "수정된 제목",
                        "body": "수정된 내용",
                        "milestoneId": null,
                        "assigneeId": null,
                        "labelIds": null
                    }
                """.trimIndent()

                mockMvc.perform(
                    put("/api/projects/1/issues/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
            }

            // yona AccessControl.java:244-248 isAllowedIfAssignee() 대응 (P2-12). 담당자는
            // isManagerOrAuthor 여부와 무관하게 author와 동급 쓰기 권한을 가진다.
            it("작성자도 관리자도 아니지만 담당자면 이슈를 수정할 수 있어야 한다") {
                val assigneeIssue = Issue(
                    id = 6L, number = 6L, title = "담당 이슈", body = "본문", project = project,
                    authorId = user.id, state = State.OPEN, assignee = Assignee(user = otherUser, project = project)
                )

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 6L) } returns assigneeIssue
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 30L) } returns Optional.empty()
                every { issueService.updateIssue(6L, "담당자가 수정", "수정 내용", otherUser, null, null, null) } returns assigneeIssue

                val jsonContent = """
                    {
                        "title": "담당자가 수정",
                        "body": "수정 내용",
                        "milestoneId": null,
                        "assigneeId": null,
                        "labelIds": null
                    }
                """.trimIndent()

                mockMvc.perform(
                    put("/api/projects/1/issues/6")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(otherAuth)
                )
                    .andExpect(status().isOk)
            }
        }

        // yona IssueApp.editIssue()의 hasTargetProject() 대응 (P1-48).
        describe("POST /api/projects/{projectId}/issues/{issueId}/move") {
            it("작성자가 대상 프로젝트로 이슈를 이동시키면 200 OK를 반환해야 한다") {
                val targetProject = Project(id = 3L, name = "TargetProject", projectScope = ProjectScope.PUBLIC)
                val movedIssue = Issue(id = 5L, number = 1L, title = "이슈 제목", body = "이슈 내용", project = targetProject, authorId = user.id, state = State.OPEN)

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every { projectRepository.findById(3L) } returns Optional.of(targetProject)
                every { issueService.moveIssue(5L, 3L, user) } returns movedIssue

                val jsonContent = """{ "targetProjectId": 3 }"""

                mockMvc.perform(
                    post("/api/projects/1/issues/5/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.project.id").value(3))

                verify(exactly = 1) { issueService.moveIssue(5L, 3L, user) }
            }

            it("원본 이슈의 관리자/작성자가 아니면 403 Forbidden을 반환하고 이동을 호출하지 않아야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 30L) } returns Optional.empty()

                val jsonContent = """{ "targetProjectId": 3 }"""

                mockMvc.perform(
                    post("/api/projects/1/issues/5/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(otherAuth)
                )
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { issueService.moveIssue(any(), any(), any()) }
            }

            it("대상 프로젝트에 이슈 생성 권한이 없으면(비공개+비멤버) 403 Forbidden을 반환하고 이동을 호출하지 않아야 한다") {
                val privateTargetProject = Project(id = 4L, name = "PrivateTarget", owner = "someoneelse", projectScope = ProjectScope.PRIVATE)

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every { projectRepository.findById(4L) } returns Optional.of(privateTargetProject)

                val jsonContent = """{ "targetProjectId": 4 }"""

                mockMvc.perform(
                    post("/api/projects/1/issues/5/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { issueService.moveIssue(any(), any(), any()) }
            }

            it("대상 프로젝트가 존재하지 않으면 400 Bad Request를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every { projectRepository.findById(999L) } returns Optional.empty()

                val jsonContent = """{ "targetProjectId": 999 }"""

                mockMvc.perform(
                    post("/api/projects/1/issues/5/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isBadRequest)
            }

            // yona AccessControl.java:244-248 isAllowedIfAssignee() 대응 (P2-12)
            it("작성자도 관리자도 아니지만 담당자면 이슈를 이동시킬 수 있어야 한다") {
                val targetProject = Project(id = 3L, name = "TargetProject", projectScope = ProjectScope.PUBLIC)
                val assigneeIssue = Issue(
                    id = 6L, number = 6L, title = "담당 이슈", body = "본문", project = project,
                    authorId = user.id, state = State.OPEN, assignee = Assignee(user = otherUser, project = project)
                )
                val movedIssue = Issue(id = 6L, number = 1L, title = "담당 이슈", body = "본문", project = targetProject, authorId = user.id, state = State.OPEN)

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 6L) } returns assigneeIssue
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 30L) } returns Optional.empty()
                every { projectRepository.findById(3L) } returns Optional.of(targetProject)
                every { issueService.moveIssue(6L, 3L, otherUser) } returns movedIssue

                val jsonContent = """{ "targetProjectId": 3 }"""

                mockMvc.perform(
                    post("/api/projects/1/issues/6/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(otherAuth)
                )
                    .andExpect(status().isOk)

                verify(exactly = 1) { issueService.moveIssue(6L, 3L, otherUser) }
            }
        }

        // yona IssueApp.editIssue()의 "if (issue.isPublish) { ... }" 발행 전환 대응 (P1-65).
        describe("POST /api/projects/{projectId}/issues/{issueId}/publish") {
            it("작성자가 초안을 발행하면 200 OK와 함께 발행된 이슈를 반환해야 한다") {
                val draftIssue = Issue(id = 5L, number = 5L, title = "이슈 제목", body = "이슈 내용", project = project, authorId = user.id, state = State.DRAFT)
                val publishedIssue = Issue(id = 5L, number = 9L, title = "이슈 제목", body = "이슈 내용", project = project, authorId = user.id, state = State.OPEN)

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns draftIssue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every { issueService.publishIssue(5L, user) } returns publishedIssue

                mockMvc.perform(post("/api/projects/1/issues/5/publish").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.state").value("OPEN"))
                    .andExpect(jsonPath("$.number").value(9))

                verify(exactly = 1) { issueService.publishIssue(5L, user) }
            }

            it("관리자/작성자가 아니면 403 Forbidden을 반환하고 발행을 호출하지 않아야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 30L) } returns Optional.empty()

                mockMvc.perform(post("/api/projects/1/issues/5/publish").principal(otherAuth))
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { issueService.publishIssue(any(), any()) }
            }

            it("존재하지 않는 이슈면 404를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 999L) } returns null

                mockMvc.perform(post("/api/projects/1/issues/999/publish").principal(userAuth))
                    .andExpect(status().isNotFound)
            }

            // yona AccessControl.java:244-248 isAllowedIfAssignee() 대응 (P2-12)
            it("작성자도 관리자도 아니지만 담당자면 초안을 발행할 수 있어야 한다") {
                val draftIssue = Issue(
                    id = 6L, number = 6L, title = "담당 초안", body = "본문", project = project,
                    authorId = user.id, state = State.DRAFT, assignee = Assignee(user = otherUser, project = project)
                )
                val publishedIssue = Issue(id = 6L, number = 9L, title = "담당 초안", body = "본문", project = project, authorId = user.id, state = State.OPEN)

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 6L) } returns draftIssue
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 30L) } returns Optional.empty()
                every { issueService.publishIssue(6L, otherUser) } returns publishedIssue

                mockMvc.perform(post("/api/projects/1/issues/6/publish").principal(otherAuth))
                    .andExpect(status().isOk)

                verify(exactly = 1) { issueService.publishIssue(6L, otherUser) }
            }
        }

        describe("DELETE /api/projects/{projectId}/issues/{issueId}") {
            it("관리자가 이슈를 삭제하면 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("manageruser") } returns Optional.of(managerUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 20L) } returns Optional.of(projectManagerUser)
                every { issueRepository.delete(issue) } returns Unit
                every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(5L) } returns emptyList()
                every { attachmentService.deleteAll(com.github.search5.yona.domain.enumeration.ResourceType.ISSUE_POST, "5") } returns Unit

                mockMvc.perform(delete("/api/projects/1/issues/5").principal(managerAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))
            }

            // yona AccessControl.java:244-248 isAllowedIfAssignee() 대응 (P2-12)
            it("작성자도 관리자도 아니지만 담당자면 이슈를 삭제할 수 있어야 한다") {
                val assigneeIssue = Issue(
                    id = 6L, number = 6L, title = "담당 이슈", body = "본문", project = project,
                    authorId = user.id, state = State.OPEN, assignee = Assignee(user = otherUser, project = project)
                )

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 6L) } returns assigneeIssue
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 30L) } returns Optional.empty()
                every { issueRepository.delete(assigneeIssue) } returns Unit
                every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(6L) } returns emptyList()
                every { attachmentService.deleteAll(com.github.search5.yona.domain.enumeration.ResourceType.ISSUE_POST, "6") } returns Unit

                mockMvc.perform(delete("/api/projects/1/issues/6").principal(otherAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))
            }
        }
    }
})
