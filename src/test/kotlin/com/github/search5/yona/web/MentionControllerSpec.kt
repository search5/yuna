package com.github.search5.yona.web

import com.github.search5.yona.domain.board.PostingCommentRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueCommentRepository
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.organization.Organization
import com.github.search5.yona.domain.organization.OrganizationUser
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUser
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.pullrequest.CommentThreadRepository
import com.github.search5.yona.domain.pullrequest.CommitCommentRepository
import com.github.search5.yona.domain.pullrequest.PullRequest
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.domain.pullrequest.ReviewCommentRepository
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.vcs.RepositoryService
import com.github.search5.yona.domain.watch.WatchRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

class MentionControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val issueRepository = mockk<IssueRepository>()
    val userRepository = mockk<UserRepository>()
    val issueCommentRepository = mockk<IssueCommentRepository>()
    val postingRepository = mockk<PostingRepository>()
    val postingCommentRepository = mockk<PostingCommentRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val watchRepository = mockk<WatchRepository>()
    val repositoryService = mockk<RepositoryService>()
    val commentThreadRepository = mockk<CommentThreadRepository>()
    val reviewCommentRepository = mockk<ReviewCommentRepository>()
    val commitCommentRepository = mockk<CommitCommentRepository>()

    val mentionController = MentionController(
        projectRepository,
        projectUserRepository,
        organizationUserRepository,
        issueRepository,
        userRepository,
        issueCommentRepository,
        postingRepository,
        postingCommentRepository,
        pullRequestRepository,
        watchRepository,
        repositoryService,
        commentThreadRepository,
        reviewCommentRepository,
        commitCommentRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(mentionController).build()

    beforeTest {
        io.mockk.clearMocks(
            projectRepository, projectUserRepository, organizationUserRepository, issueRepository, userRepository,
            issueCommentRepository, postingRepository, postingCommentRepository, pullRequestRepository, watchRepository,
            repositoryService, commentThreadRepository, reviewCommentRepository, commitCommentRepository
        )
        // query가 비어있는(=멤버 후보 수집) 분기에서 P1-42가 추가한 후보 소스들은 기본적으로 비어있다고 스텁한다.
        every { issueRepository.findByProject(any()) } returns emptyList()
        every { postingRepository.findByProject(any()) } returns emptyList()
        every { pullRequestRepository.findByToProject(any<Project>()) } returns emptyList()
        every { watchRepository.findByResourceTypeAndResourceId(any(), any()) } returns emptyList()
    }

    describe("GET /api/{owner}/{projectName}/mentionList (P1-14)") {
        val me = User(id = 1L, loginId = "me", name = "나")
        val meAuth = UsernamePasswordAuthenticationToken("me", "password")
        val memberRole = Role(id = RoleType.MEMBER.roleType)

        it("비공개 프로젝트는 멤버가 아니면 403을 반환해야 한다") {
            val project = Project(id = 10L, name = "priv", owner = "owner", projectScope = ProjectScope.PRIVATE)
            every { projectRepository.findByOwnerAndName("owner", "priv") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            every { projectUserRepository.existsByProjectIdAndUserId(10L, 1L) } returns false

            mockMvc.perform(
                get("/api/owner/priv/mentionList")
                    .param("mentionType", "user")
                    .principal(meAuth)
            ).andExpect(status().isForbidden)
        }

        it("mentionType=user: query가 없으면 프로젝트 멤버+조직 그룹멤버를 후보로 삼고 나를 맨 뒤에 배치해야 한다") {
            val org = Organization(id = 100L, name = "org1")
            val project = Project(id = 11L, name = "p", owner = "owner", projectScope = ProjectScope.PRIVATE, organization = org)
            val other = User(id = 2L, loginId = "other", name = "다른사람")
            val groupMember = User(id = 3L, loginId = "groupie", name = "그룹멤버")

            every { projectRepository.findByOwnerAndName("owner", "p") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            every { projectUserRepository.existsByProjectIdAndUserId(11L, 1L) } returns true
            every { projectUserRepository.findByProjectId(11L) } returns listOf(
                ProjectUser(id = 900L, user = me, project = project, role = memberRole),
                ProjectUser(id = 901L, user = other, project = project, role = memberRole)
            )
            every { organizationUserRepository.findByOrganizationId(100L) } returns listOf(
                OrganizationUser(id = 950L, user = groupMember, organization = org, role = memberRole)
            )

            mockMvc.perform(
                get("/api/owner/p/mentionList")
                    .param("mentionType", "user")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                // 후보 3명 + "@project all:"/"@group all:" 특수 항목 2개(P1-42) = 5
                .andExpect(jsonPath("$.result.length()").value(5))
                .andExpect(jsonPath("$.result[0].loginid").value("other"))
                .andExpect(jsonPath("$.result[1].loginid").value("groupie"))
                .andExpect(jsonPath("$.result[2].loginid").value("me"))
                .andExpect(jsonPath("$.result[3].name").value("@project all:"))
                .andExpect(jsonPath("$.result[4].name").value("@group all: "))
        }

        it("mentionType=user: 공개 프로젝트에서 query가 있으면 전역 사용자 검색 결과를 써야 한다") {
            val project = Project(id = 12L, name = "pub", owner = "owner", projectScope = ProjectScope.PUBLIC)
            val searched = User(id = 4L, loginId = "found", name = "검색됨")

            every { projectRepository.findByOwnerAndName("owner", "pub") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            every { userRepository.searchUsers("sea", PageRequest.of(0, 20)) } returns PageImpl(listOf(searched))

            mockMvc.perform(
                get("/api/owner/pub/mentionList")
                    .param("mentionType", "user")
                    .param("query", "sea")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                // 검색 결과 1명 + 나 + "@project all:"(조직 없음, P1-42) = 3
                .andExpect(jsonPath("$.result.length()").value(3))
                .andExpect(jsonPath("$.result[0].loginid").value("found"))
                .andExpect(jsonPath("$.result[1].loginid").value("me"))
                .andExpect(jsonPath("$.result[2].name").value("@project all:"))
        }

        it("admin 로그인ID는 후보에서 제외해야 한다") {
            val project = Project(id = 13L, name = "p2", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val admin = User(id = 5L, loginId = "admin", name = "관리자")

            every { projectRepository.findByOwnerAndName("owner", "p2") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            every { projectUserRepository.existsByProjectIdAndUserId(13L, 1L) } returns true
            every { projectUserRepository.findByProjectId(13L) } returns listOf(
                ProjectUser(id = 902L, user = admin, project = project, role = memberRole)
            )

            mockMvc.perform(
                get("/api/owner/p2/mentionList")
                    .param("mentionType", "user")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                // admin 제외 후 나만 남고 + "@project all:"(P1-42) = 2
                .andExpect(jsonPath("$.result.length()").value(2))
                .andExpect(jsonPath("$.result[0].loginid").value("me"))
                .andExpect(jsonPath("$.result[1].name").value("@project all:"))
        }

        it("number/resourceType이 주어지면 이슈 댓글 작성자를 최근 순으로, 이슈 작성자를 마지막에 포함해야 한다(P1-42)") {
            val project = Project(id = 20L, name = "p5", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val author = User(id = 6L, loginId = "author1", name = "작성자")
            val commenterA = User(id = 7L, loginId = "commenterA", name = "댓글A")
            val commenterB = User(id = 8L, loginId = "commenterB", name = "댓글B")
            val issue = Issue(id = 600L, title = "이슈", project = project, number = 3L, authorLoginId = "author1")

            every { projectRepository.findByOwnerAndName("owner", "p5") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            every { projectUserRepository.existsByProjectIdAndUserId(20L, 1L) } returns true
            every { projectUserRepository.findByProjectId(20L) } returns emptyList()
            every { issueRepository.findByProjectAndNumber(project, 3L) } returns issue
            every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(600L) } returns listOf(
                com.github.search5.yona.domain.issue.IssueComment(authorLoginId = "commenterA", issue = issue),
                com.github.search5.yona.domain.issue.IssueComment(authorLoginId = "commenterB", issue = issue)
            )
            every { userRepository.findByLoginId("commenterA") } returns Optional.of(commenterA)
            every { userRepository.findByLoginId("commenterB") } returns Optional.of(commenterB)
            every { userRepository.findByLoginId("author1") } returns Optional.of(author)

            mockMvc.perform(
                get("/api/owner/p5/mentionList")
                    .param("mentionType", "user")
                    .param("number", "3")
                    .param("resourceType", "ISSUE_POST")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                // 최근 댓글자(B)가 먼저, 그다음 A, 그다음 작성자, 나, "@project all:"
                .andExpect(jsonPath("$.result[0].loginid").value("commenterB"))
                .andExpect(jsonPath("$.result[1].loginid").value("commenterA"))
                .andExpect(jsonPath("$.result[2].loginid").value("author1"))
                .andExpect(jsonPath("$.result[3].loginid").value("me"))
        }

        it("프로젝트의 이슈/게시글/PR 작성자와 프로젝트 워처를 후보에 포함해야 한다(P1-42)") {
            val project = Project(id = 21L, name = "p6", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val issueAuthor = User(id = 9L, loginId = "issueAuthor", name = "이슈작성자")
            val watcher = User(id = 11L, loginId = "watcher1", name = "워처")
            val issue = Issue(id = 700L, title = "이슈", project = project, authorId = 9L)
            val watch = com.github.search5.yona.domain.watch.Watch(
                user = watcher, resourceType = ResourceType.PROJECT, resourceId = "21"
            )

            every { projectRepository.findByOwnerAndName("owner", "p6") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            every { projectUserRepository.existsByProjectIdAndUserId(21L, 1L) } returns true
            every { projectUserRepository.findByProjectId(21L) } returns emptyList()
            every { issueRepository.findByProject(project) } returns listOf(issue)
            every { watchRepository.findByResourceTypeAndResourceId(ResourceType.PROJECT, "21") } returns listOf(watch)
            every { userRepository.findById(9L) } returns Optional.of(issueAuthor)
            every { userRepository.findById(11L) } returns Optional.of(watcher)

            mockMvc.perform(
                get("/api/owner/p6/mentionList")
                    .param("mentionType", "user")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.result[0].loginid").value("issueAuthor"))
                .andExpect(jsonPath("$.result[1].loginid").value("watcher1"))
        }

        it("이슈 공유자를 후보에 포함해야 한다(P1-42)") {
            val project = Project(id = 22L, name = "p7", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val sharer = User(id = 12L, loginId = "sharer1", name = "공유대상")
            val issue = Issue(id = 800L, title = "이슈", project = project, number = 4L)
            val issueSharer = com.github.search5.yona.domain.issue.IssueSharer(loginId = "sharer1", user = sharer, issue = issue)
            issue.sharers.add(issueSharer)

            every { projectRepository.findByOwnerAndName("owner", "p7") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            every { projectUserRepository.existsByProjectIdAndUserId(22L, 1L) } returns true
            every { projectUserRepository.findByProjectId(22L) } returns emptyList()
            every { issueRepository.findByProjectAndNumber(project, 4L) } returns issue
            every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(800L) } returns emptyList()

            mockMvc.perform(
                get("/api/owner/p7/mentionList")
                    .param("mentionType", "user")
                    .param("number", "4")
                    .param("resourceType", "ISSUE_POST")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.result[0].loginid").value("sharer1"))
        }

        describe("GET /api/{owner}/{projectName}/mentionListAtCommitDiff (P1-43)") {
            it("커밋 작성자와 코드 댓글 작성자를 후보에 포함해야 한다") {
                val project = Project(id = 30L, name = "cd1", owner = "owner", projectScope = ProjectScope.PRIVATE, vcs = "GIT")
                val commitAuthor = User(id = 20L, loginId = "committer1", name = "커미터")
                val codeCommenter = User(id = 21L, loginId = "codeCommenter1", name = "코드댓글러")
                val commit = mockk<com.github.search5.yona.domain.vcs.Commit>()
                val repo = mockk<com.github.search5.yona.domain.vcs.PlayRepository>()
                val thread = com.github.search5.yona.domain.pullrequest.CodeCommentThread(
                    id = 900L, project = project, commitId = "abc123",
                    codeRange = com.github.search5.yona.domain.support.CodeRange(
                        path = "a.kt", startSide = com.github.search5.yona.domain.support.CodeRange.Side.B,
                        startLine = 1, startColumn = 0,
                        endSide = com.github.search5.yona.domain.support.CodeRange.Side.B, endLine = 1, endColumn = 0
                    )
                )
                val reviewComment = com.github.search5.yona.domain.pullrequest.ReviewComment(
                    contents = "댓글", thread = thread,
                    author = com.github.search5.yona.domain.user.UserIdent(codeCommenter)
                )

                every { projectRepository.findByOwnerAndName("owner", "cd1") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                every { projectUserRepository.existsByProjectIdAndUserId(30L, 1L) } returns true
                every { projectUserRepository.findByProjectId(30L) } returns emptyList()
                every { repositoryService.getRepository(project) } returns repo
                every { repo.getCommit("abc123") } returns commit
                every { commit.getAuthor() } returns commitAuthor
                every { commit.getAuthorEmail() } returns "committer1@yona.io"
                every { userRepository.findByLoginId("committer1") } returns Optional.of(commitAuthor)
                every {
                    commentThreadRepository.findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(project, "abc123")
                } returns listOf(thread)
                every { reviewCommentRepository.findByThreadIdOrderByCreatedDateAsc(900L) } returns listOf(reviewComment)
                every { userRepository.findByLoginId("codeCommenter1") } returns Optional.of(codeCommenter)

                mockMvc.perform(
                    get("/api/owner/cd1/mentionListAtCommitDiff")
                        .param("commitId", "abc123")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].loginid").value("committer1"))
                    .andExpect(jsonPath("$.result[1].loginid").value("codeCommenter1"))
            }
        }

        describe("GET /api/{owner}/{projectName}/mentionListAtPullRequest (P1-43)") {
            it("PR 코드리뷰 댓글 작성자와 PR contributor를 후보에 포함해야 한다") {
                val project = Project(id = 31L, name = "pr1", owner = "owner", projectScope = ProjectScope.PRIVATE, vcs = "GIT")
                val contributor = User(id = 22L, loginId = "contributor1", name = "기여자")
                val reviewer = User(id = 23L, loginId = "reviewer1", name = "리뷰어")
                val pullRequest = PullRequest(
                    id = 950L, title = "PR", toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature", contributor = contributor, number = 5L
                )
                val thread = com.github.search5.yona.domain.pullrequest.CodeCommentThread(
                    id = 901L, project = project, pullRequest = pullRequest, commitId = "def456",
                    codeRange = com.github.search5.yona.domain.support.CodeRange(
                        path = "b.kt", startSide = com.github.search5.yona.domain.support.CodeRange.Side.B,
                        startLine = 1, startColumn = 0,
                        endSide = com.github.search5.yona.domain.support.CodeRange.Side.B, endLine = 1, endColumn = 0
                    )
                )
                val reviewComment = com.github.search5.yona.domain.pullrequest.ReviewComment(
                    contents = "리뷰", thread = thread,
                    author = com.github.search5.yona.domain.user.UserIdent(reviewer)
                )

                every { projectRepository.findByOwnerAndName("owner", "pr1") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                every { projectUserRepository.existsByProjectIdAndUserId(31L, 1L) } returns true
                every { projectUserRepository.findByProjectId(31L) } returns emptyList()
                every { pullRequestRepository.findById(950L) } returns Optional.of(pullRequest)
                every { commentThreadRepository.findByPullRequest(pullRequest) } returns listOf(thread)
                every { reviewCommentRepository.findByThreadIdOrderByCreatedDateAsc(901L) } returns listOf(reviewComment)
                every { userRepository.findByLoginId("reviewer1") } returns Optional.of(reviewer)

                mockMvc.perform(
                    get("/api/owner/pr1/mentionListAtPullRequest")
                        .param("pullRequestId", "950")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].loginid").value("reviewer1"))
                    .andExpect(jsonPath("$.result[1].loginid").value("contributor1"))
            }

            it("존재하지 않는 PR이면 404를 반환해야 한다") {
                val project = Project(id = 32L, name = "pr2", owner = "owner", projectScope = ProjectScope.PRIVATE)

                every { projectRepository.findByOwnerAndName("owner", "pr2") } returns Optional.of(project)
                every { userRepository.findByLoginId("me") } returns Optional.of(me)
                every { projectUserRepository.existsByProjectIdAndUserId(32L, 1L) } returns true
                every { pullRequestRepository.findById(9999L) } returns Optional.empty()

                mockMvc.perform(
                    get("/api/owner/pr2/mentionListAtPullRequest")
                        .param("pullRequestId", "9999")
                        .param("mentionType", "user")
                        .principal(meAuth)
                )
                    .andExpect(status().isNotFound)
            }
        }

        it("mentionType=issue: 최근 이슈 목록을 name/issueNo/title로 반환해야 한다") {
            val project = Project(id = 14L, name = "p3", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val issue = Issue(id = 500L, title = "버그 수정", project = project, number = 7L)

            every { projectRepository.findByOwnerAndName("owner", "p3") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            every { projectUserRepository.existsByProjectIdAndUserId(14L, 1L) } returns true
            every { issueRepository.findForMention(project, "", PageRequest.of(0, 20)) } returns listOf(issue)

            mockMvc.perform(
                get("/api/owner/p3/mentionList")
                    .param("mentionType", "issue")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.result[0].issueNo").value("7"))
                .andExpect(jsonPath("$.result[0].title").value("버그 수정"))
        }

        it("mentionType이 user/issue가 아니면 빈 결과를 반환해야 한다") {
            val project = Project(id = 15L, name = "p4", owner = "owner", projectScope = ProjectScope.PRIVATE)

            every { projectRepository.findByOwnerAndName("owner", "p4") } returns Optional.of(project)
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            every { projectUserRepository.existsByProjectIdAndUserId(15L, 1L) } returns true

            mockMvc.perform(
                get("/api/owner/p4/mentionList")
                    .principal(meAuth)
            )
                .andExpect(status().isOk)
                .andExpect(content().json("{}"))
        }
    }
})
