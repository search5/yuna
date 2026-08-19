package com.github.search5.yona.domain.notification

import com.github.search5.yona.domain.board.PostingCommentRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueCommentRepository
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.organization.Organization
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.pullrequest.CommentThreadRepository
import com.github.search5.yona.domain.pullrequest.CommitCommentRepository
import com.github.search5.yona.domain.pullrequest.PullRequestRepository
import com.github.search5.yona.domain.pullrequest.ReviewCommentRepository
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.Optional

// yona utils/RouteUtil.java + NotificationEvent.getUrlToView()/getProject() 대응 (P1-27).
class NotificationUrlResolverSpec : DescribeSpec({
    val issueRepository = mockk<IssueRepository>()
    val issueCommentRepository = mockk<IssueCommentRepository>()
    val postingRepository = mockk<PostingRepository>()
    val postingCommentRepository = mockk<PostingCommentRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val commitCommentRepository = mockk<CommitCommentRepository>()
    val reviewCommentRepository = mockk<ReviewCommentRepository>()
    val commentThreadRepository = mockk<CommentThreadRepository>()
    val userRepository = mockk<UserRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val organizationRepository = mockk<OrganizationRepository>()
    val resolver = NotificationUrlResolver(
        issueRepository, issueCommentRepository, postingRepository, postingCommentRepository,
        pullRequestRepository, commitCommentRepository, reviewCommentRepository, commentThreadRepository,
        userRepository, projectRepository, organizationRepository, "https://yona.example.com"
    )

    fun event(eventType: EventType, resourceType: ResourceType, resourceId: String) = NotificationEvent(
        title = "제목", created = Instant.now(),
        resourceType = resourceType, resourceId = resourceId, eventType = eventType
    )

    describe("getUrlToView") {
        it("ISSUE_POST는 project owner/name/issue/number 형식의 URL을 만든다") {
            val project = Project(id = 1L, name = "myproj", owner = "gildong")
            val issue = Issue(id = 10L, title = "제목", body = "본문", project = project, number = 7L)
            every { issueRepository.findById(10L) } returns Optional.of(issue)

            resolver.getUrlToView(event(EventType.NEW_ISSUE, ResourceType.ISSUE_POST, "10")) shouldBe
                "https://yona.example.com/gildong/myproj/issue/7"
        }

        it("ISSUE_COMMENT는 이슈 URL에 #comment-{id} 앵커를 붙인다") {
            val project = Project(id = 1L, name = "myproj", owner = "gildong")
            val issue = Issue(id = 10L, title = "제목", body = "본문", project = project, number = 7L)
            val comment = com.github.search5.yona.domain.issue.IssueComment(id = 55L, contents = "댓글", issue = issue)
            every { issueCommentRepository.findById(55L) } returns Optional.of(comment)
            every { issueRepository.findById(10L) } returns Optional.of(issue)

            resolver.getUrlToView(event(EventType.NEW_COMMENT, ResourceType.ISSUE_COMMENT, "55")) shouldBe
                "https://yona.example.com/gildong/myproj/issue/7#comment-55"
        }

        it("MEMBER_ENROLL_REQUEST는 프로젝트 멤버 페이지로 링크한다") {
            val project = Project(id = 1L, name = "myproj", owner = "gildong")
            every { projectRepository.findById(1L) } returns Optional.of(project)

            resolver.getUrlToView(event(EventType.MEMBER_ENROLL_REQUEST, ResourceType.PROJECT, "1")) shouldBe
                "https://yona.example.com/gildong/myproj/members"
        }

        it("MEMBER_ENROLL_ACCEPT는 프로젝트 메인 페이지로 링크한다") {
            val project = Project(id = 1L, name = "myproj", owner = "gildong")
            every { projectRepository.findById(1L) } returns Optional.of(project)

            resolver.getUrlToView(event(EventType.MEMBER_ENROLL_ACCEPT, ResourceType.PROJECT, "1")) shouldBe
                "https://yona.example.com/gildong/myproj"
        }

        it("ORGANIZATION_MEMBER_ENROLL_REQUEST는 그룹 멤버 페이지로 링크한다") {
            val org = Organization(id = 2L, name = "myorg")
            every { organizationRepository.findById(2L) } returns Optional.of(org)

            resolver.getUrlToView(event(EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST, ResourceType.ORGANIZATION, "2")) shouldBe
                "https://yona.example.com/organizations/myorg/members"
        }

        it("NEW_COMMIT은 커밋 SHA만으로는 프로젝트를 되짚을 수 없어 null을 반환한다") {
            resolver.getUrlToView(event(EventType.NEW_COMMIT, ResourceType.COMMIT, "abcdef123")) shouldBe null
        }

        it("리소스를 찾을 수 없으면 null을 반환한다") {
            every { issueRepository.findById(999L) } returns Optional.empty()

            resolver.getUrlToView(event(EventType.NEW_ISSUE, ResourceType.ISSUE_POST, "999")) shouldBe null
        }
    }
})
