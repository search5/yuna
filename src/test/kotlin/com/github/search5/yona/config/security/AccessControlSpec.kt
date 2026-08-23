package com.github.search5.yona.config.security

import com.github.search5.yona.domain.board.Posting
import com.github.search5.yona.domain.board.PostingComment
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.enumeration.Operation
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.issue.Assignee
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueComment
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.issue.IssueSharer
import com.github.search5.yona.domain.milestone.Milestone
import com.github.search5.yona.domain.milestone.MilestoneRepository
import com.github.search5.yona.domain.organization.Organization
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.organization.OrganizationUser
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectTransfer
import com.github.search5.yona.domain.project.ProjectUser
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.pullrequest.CommentThread
import com.github.search5.yona.domain.pullrequest.CommitComment
import com.github.search5.yona.domain.pullrequest.CommitCommentRepository
import com.github.search5.yona.domain.pullrequest.PullRequest
import com.github.search5.yona.domain.pullrequest.ReviewComment
import com.github.search5.yona.domain.pullrequest.ReviewCommentRepository
import com.github.search5.yona.domain.pullrequest.SimpleCommentThread
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserIdent
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.user.UserState
import com.github.search5.yona.domain.webhook.Webhook
import com.github.search5.yona.domain.attachment.Attachment
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import java.util.Optional

// yona AccessControl.java의 isAllowed()/isGlobalResourceAllowed()/isProjectResourceAllowed() 대응
// (P1-85 1b). docs/P1-85_PLAN.md에 명시된 최소 케이스(공개 프로젝트+게스트 이슈 READ 거부/공유자
// 오버라이드/CODE DELETE 항상 거부/PROJECT_TRANSFER ACCEPT 2가지/WATCH는 READ와 달리 게스트도 허용)를
// 포함해, 각 리소스 타입 오버로드별로 사이트매니저·조직관리자·매니저·작성자 우회 및 일반 연산 스위치를
// 검증한다.
class AccessControlSpec : DescribeSpec({
    val projectUserRepository = mockk<ProjectUserRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val userRepository = mockk<UserRepository>()
    val organizationRepository = mockk<OrganizationRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val reviewCommentRepository = mockk<ReviewCommentRepository>()
    val commitCommentRepository = mockk<CommitCommentRepository>()
    val milestoneRepository = mockk<MilestoneRepository>()

    val accessControl = AccessControl(
        projectUserRepository, organizationUserRepository,
        userRepository, organizationRepository,
        issueRepository, postingRepository,
        reviewCommentRepository, commitCommentRepository,
        milestoneRepository
    )

    beforeTest {
        clearMocks(
            projectUserRepository, organizationUserRepository, userRepository, organizationRepository,
            issueRepository, postingRepository, reviewCommentRepository, commitCommentRepository,
            milestoneRepository,
            answers = false
        )
        every { organizationUserRepository.findByOrganizationIdAndUserId(any(), any()) } returns Optional.empty()
    }

    val org = Organization(id = 1L, name = "org")
    val publicProject = Project(id = 10L, name = "public", projectScope = ProjectScope.PUBLIC)
    val privateProject = Project(id = 11L, name = "private", projectScope = ProjectScope.PRIVATE)
    val protectedProject = Project(id = 12L, name = "protected", projectScope = ProjectScope.PROTECTED, organization = org)
    val orgPublicProject = Project(id = 13L, name = "orgPublic", projectScope = ProjectScope.PUBLIC, organization = org)

    val siteManager = User(id = 1L, loginId = "site", name = "site", state = UserState.SITE_ADMIN)
    val guest = User(id = 2L, loginId = "guest", name = "guest", isGuest = true)
    val member = User(id = 3L, loginId = "member", name = "member")
    val stranger = User(id = 4L, loginId = "stranger", name = "stranger")
    val managerUser = User(id = 5L, loginId = "manager", name = "manager")
    val orgAdminUser = User(id = 6L, loginId = "orgadmin", name = "orgadmin")
    val orgMemberUser = User(id = 7L, loginId = "orgmember", name = "orgmember")

    member.projectUsers.add(ProjectUser(id = 100L, user = member, project = privateProject, role = Role(id = RoleType.MEMBER.roleType)))
    managerUser.projectUsers.add(ProjectUser(id = 101L, user = managerUser, project = privateProject, role = Role(id = RoleType.MANAGER.roleType)))

    fun stubOrgRole(organization: Organization, user: User, roleType: RoleType) {
        every { organizationUserRepository.findByOrganizationIdAndUserId(organization.id!!, user.id!!) } returns
            Optional.of(OrganizationUser(user = user, organization = organization, role = Role(id = roleType.roleType)))
    }

    describe("isAllowed(user, project, operation) - PROJECT 리소스") {
        it("사이트 매니저는 어떤 프로젝트든 항상 허용") {
            accessControl.isAllowed(siteManager, privateProject, Operation.READ) shouldBe true
        }
        it("PUBLIC 프로젝트는 비로그인(익명) 사용자도 READ 허용") {
            accessControl.isAllowed(null, publicProject, Operation.READ) shouldBe true
        }
        it("PUBLIC 프로젝트라도 게스트는 READ 거부") {
            accessControl.isAllowed(guest, publicProject, Operation.READ) shouldBe false
        }
        it("PRIVATE 프로젝트는 비로그인 사용자 READ 거부") {
            accessControl.isAllowed(null, privateProject, Operation.READ) shouldBe false
        }
        it("PRIVATE 프로젝트는 멤버에게 READ 허용") {
            accessControl.isAllowed(member, privateProject, Operation.READ) shouldBe true
        }
        it("PRIVATE 프로젝트는 비멤버에게 READ 거부") {
            accessControl.isAllowed(stranger, privateProject, Operation.READ) shouldBe false
        }
        it("WATCH는 READ와 달리 PUBLIC 프로젝트에서 게스트도 허용") {
            accessControl.isAllowed(guest, publicProject, Operation.WATCH) shouldBe true
        }
        it("WATCH는 PUBLIC 프로젝트에서 익명은 거부") {
            accessControl.isAllowed(null, publicProject, Operation.WATCH) shouldBe false
        }
        it("LEAVE는 프로젝트 소유자 본인은 거부") {
            val owned = Project(id = 20L, name = "owned", projectScope = ProjectScope.PRIVATE, owner = member.loginId)
            member.projectUsers.add(ProjectUser(id = 200L, user = member, project = owned, role = Role(id = RoleType.MEMBER.roleType)))
            accessControl.isAllowed(member, owned, Operation.LEAVE) shouldBe false
        }
        it("LEAVE는 소유자가 아닌 멤버는 허용") {
            accessControl.isAllowed(member, privateProject, Operation.LEAVE) shouldBe true
        }
        it("UPDATE는 매니저에게 허용") {
            accessControl.isAllowed(managerUser, privateProject, Operation.UPDATE) shouldBe true
        }
        it("UPDATE는 일반 멤버에게 거부") {
            accessControl.isAllowed(member, privateProject, Operation.UPDATE) shouldBe false
        }
        it("조직 관리자는 소속 프로젝트 UPDATE 허용") {
            stubOrgRole(org, orgAdminUser, RoleType.ORG_ADMIN)
            accessControl.isAllowed(orgAdminUser, protectedProject, Operation.UPDATE) shouldBe true
        }
        it("ASSIGN_ISSUE는 PUBLIC/PROTECTED 프로젝트에서 조직 멤버에게 허용") {
            stubOrgRole(org, orgMemberUser, RoleType.ORG_MEMBER)
            accessControl.isAllowed(orgMemberUser, orgPublicProject, Operation.ASSIGN_ISSUE) shouldBe true
        }
        it("ASSIGN_ISSUE는 PRIVATE 프로젝트에서는 조직 멤버라도 거부(프로젝트 멤버가 아니면)") {
            stubOrgRole(org, orgMemberUser, RoleType.ORG_MEMBER)
            val privateOrgProject = Project(id = 21L, name = "privOrg", projectScope = ProjectScope.PRIVATE, organization = org)
            accessControl.isAllowed(orgMemberUser, privateOrgProject, Operation.ASSIGN_ISSUE) shouldBe false
        }
    }

    describe("isAllowed(user, organization, operation) - ORGANIZATION 리소스") {
        it("READ는 익명 포함 항상 허용") {
            accessControl.isAllowed(null, org, Operation.READ) shouldBe true
        }
        it("UPDATE는 조직 관리자만 허용") {
            stubOrgRole(org, orgAdminUser, RoleType.ORG_ADMIN)
            accessControl.isAllowed(orgAdminUser, org, Operation.UPDATE) shouldBe true
        }
        it("UPDATE는 조직 관리자가 아니면 거부") {
            accessControl.isAllowed(orgMemberUser, org, Operation.UPDATE) shouldBe false
        }
    }

    describe("isAllowed(user, project, issue, operation) - ISSUE_POST 리소스") {
        val issue = Issue(id = 30L, project = privateProject, authorId = stranger.id, authorLoginId = stranger.loginId)

        it("작성자는 매니저가 아니어도 항상 허용(연산 무관)") {
            accessControl.isAllowed(stranger, privateProject, issue, Operation.DELETE) shouldBe true
        }
        it("담당자로 지정된 사용자는 항상 허용") {
            val assignee = Assignee(user = member, project = privateProject)
            val assignedIssue = Issue(id = 31L, project = privateProject, authorId = stranger.id, assignee = assignee)
            accessControl.isAllowed(member, privateProject, assignedIssue, Operation.UPDATE) shouldBe true
        }
        it("작성자/담당자가 아니어도 프로젝트 멤버라면 UPDATE 허용(legacy 일반 연산 규칙, 매니저 전용 아님)") {
            accessControl.isAllowed(member, privateProject, issue, Operation.UPDATE) shouldBe true
        }
        it("프로젝트 멤버가 아니면 UPDATE 거부") {
            accessControl.isAllowed(stranger, privateProject, Issue(id = 36L, project = privateProject, authorId = 999L), Operation.UPDATE) shouldBe false
        }
        it("PUBLIC 프로젝트 + 게스트는 이슈 READ 거부(P1-85_PLAN.md 최소 케이스)") {
            val publicIssue = Issue(id = 32L, project = publicProject, authorId = stranger.id)
            accessControl.isAllowed(guest, publicProject, publicIssue, Operation.READ) shouldBe false
        }
        it("IssueSharer로 공유받은 사용자는 비멤버라도 READ 허용(공유자 오버라이드, P1-82)") {
            val sharedIssue = Issue(id = 33L, project = privateProject, authorId = stranger.id)
            sharedIssue.sharers.add(IssueSharer(loginId = member.loginId, user = member, issue = sharedIssue))
            accessControl.isAllowed(member, privateProject, sharedIssue, Operation.READ) shouldBe true
        }
        it("부모 이슈의 공유자는 하위 이슈도 READ 허용") {
            val parentIssue = Issue(id = 34L, project = privateProject, authorId = stranger.id)
            parentIssue.sharers.add(IssueSharer(loginId = member.loginId, user = member, issue = parentIssue))
            val childIssue = Issue(id = 35L, project = privateProject, authorId = stranger.id, parent = parentIssue)
            accessControl.isAllowed(member, privateProject, childIssue, Operation.READ) shouldBe true
        }
    }

    describe("isAllowed(user, project, issueComment, operation) - ISSUE_COMMENT 리소스") {
        it("이슈가 공유돼 있으면 댓글도 READ 허용") {
            val issue = Issue(id = 40L, project = privateProject, authorId = stranger.id)
            issue.sharers.add(IssueSharer(loginId = member.loginId, user = member, issue = issue))
            val comment = IssueComment(id = 400L, issue = issue, authorId = stranger.id)
            accessControl.isAllowed(member, privateProject, comment, Operation.READ) shouldBe true
        }
        it("작성자는 UPDATE 허용") {
            val issue = Issue(id = 41L, project = privateProject, authorId = stranger.id)
            val comment = IssueComment(id = 401L, issue = issue, authorId = member.id)
            accessControl.isAllowed(member, privateProject, comment, Operation.UPDATE) shouldBe true
        }
    }

    describe("isAllowed(user, project, posting, operation) - BOARD_POST 리소스") {
        it("작성자는 DELETE 허용") {
            val posting = Posting(id = 50L, project = privateProject, authorId = member.id)
            accessControl.isAllowed(member, privateProject, posting, Operation.DELETE) shouldBe true
        }
        it("작성자가 아니어도 프로젝트 멤버면 DELETE 허용(legacy 일반 연산 규칙)") {
            val posting = Posting(id = 51L, project = privateProject, authorId = stranger.id)
            accessControl.isAllowed(member, privateProject, posting, Operation.DELETE) shouldBe true
        }
        it("프로젝트 멤버가 아니면 DELETE 거부") {
            val posting = Posting(id = 52L, project = privateProject, authorId = 999L)
            accessControl.isAllowed(stranger, privateProject, posting, Operation.DELETE) shouldBe false
        }
    }

    describe("isAllowed(user, project, postingComment, operation) - NONISSUE_COMMENT 리소스") {
        it("작성자는 UPDATE 허용") {
            val posting = Posting(id = 60L, project = privateProject, authorId = stranger.id)
            val comment = PostingComment(id = 600L, posting = posting, authorId = member.id)
            accessControl.isAllowed(member, privateProject, comment, Operation.UPDATE) shouldBe true
        }
    }

    describe("isAllowed(user, project, pullRequest, operation) - PULL_REQUEST 리소스") {
        it("contributor라도 프로젝트 멤버가 아니면 UPDATE 거부(중앙 함수는 author 우회가 없음)") {
            val pr = PullRequest(id = 70L, toProject = privateProject, fromProject = privateProject, contributor = stranger)
            accessControl.isAllowed(stranger, privateProject, pr, Operation.UPDATE) shouldBe false
        }
        it("매니저는 PR UPDATE 허용") {
            val pr = PullRequest(id = 71L, toProject = privateProject, fromProject = privateProject, contributor = stranger)
            accessControl.isAllowed(managerUser, privateProject, pr, Operation.UPDATE) shouldBe true
        }
    }

    describe("isAllowed(user, project, commitComment, operation) - COMMIT_COMMENT 리소스") {
        it("작성자는 DELETE 허용") {
            val comment = CommitComment(id = 80L, project = privateProject, author = UserIdent(member), commitId = "abc")
            accessControl.isAllowed(member, privateProject, comment, Operation.DELETE) shouldBe true
        }
    }

    describe("isAllowed(user, project, commentThread, operation) - COMMENT_THREAD 리소스") {
        it("작성자는 UPDATE 허용") {
            val thread = SimpleCommentThread(id = 90L, project = privateProject, author = UserIdent(member))
            accessControl.isAllowed(member, privateProject, thread, Operation.UPDATE) shouldBe true
        }
    }

    describe("isAllowed(user, project, reviewComment, operation) - REVIEW_COMMENT 리소스") {
        it("작성자는 DELETE 허용") {
            val comment = ReviewComment(id = 91L, author = UserIdent(member))
            accessControl.isAllowed(member, privateProject, comment, Operation.DELETE) shouldBe true
        }
    }

    describe("isAllowed(user, project, milestone, operation) - MILESTONE 리소스(작성자 개념 없음)") {
        it("프로젝트 멤버는 매니저가 아니어도 UPDATE 허용(legacy 일반 연산 규칙)") {
            val milestone = Milestone(id = 92L, project = privateProject)
            accessControl.isAllowed(member, privateProject, milestone, Operation.UPDATE) shouldBe true
        }
        it("프로젝트 멤버가 아니면 UPDATE 거부") {
            val milestone = Milestone(id = 93L, project = privateProject)
            accessControl.isAllowed(stranger, privateProject, milestone, Operation.UPDATE) shouldBe false
        }
    }

    describe("isAllowed(user, project, webhook, operation) - WEBHOOK 리소스") {
        it("legacy 규칙상 일반 멤버도 webhook UPDATE 가능(매니저 전용 아님)") {
            val webhook = Webhook(id = 94L, project = privateProject)
            accessControl.isAllowed(member, privateProject, webhook, Operation.UPDATE) shouldBe true
        }
        it("비멤버는 webhook UPDATE 거부") {
            val webhook = Webhook(id = 95L, project = privateProject)
            accessControl.isAllowed(stranger, privateProject, webhook, Operation.UPDATE) shouldBe false
        }
    }

    describe("isAllowed(user, project, resourceType, operation) - 엔티티 없는 일반 리소스(CODE 등)") {
        it("CODE는 일반 멤버에게는 DELETE 거부(legacy 특수 케이스)") {
            accessControl.isAllowed(member, privateProject, ResourceType.CODE, Operation.DELETE) shouldBe false
        }
        it("CODE라도 매니저/조직관리자 우회는 그 이전에 이미 적용돼 DELETE 허용(legacy AccessControl.java:186-192 우회가 264-267 특수케이스보다 먼저 평가됨)") {
            accessControl.isAllowed(managerUser, privateProject, ResourceType.CODE, Operation.DELETE) shouldBe true
        }
        it("CODE READ는 일반 규칙(멤버 허용)을 따름") {
            accessControl.isAllowed(member, privateProject, ResourceType.CODE, Operation.READ) shouldBe true
        }
        it("CODE 아닌 타입은 멤버면 DELETE 허용") {
            accessControl.isAllowed(member, privateProject, ResourceType.MILESTONE, Operation.DELETE) shouldBe true
        }
    }

    describe("isAllowed(user, projectTransfer, operation) - PROJECT_TRANSFER 리소스") {
        it("ACCEPT: 대상이 실존 사용자면 그 loginId 본인만 허용") {
            every { userRepository.findByLoginId("target") } returns Optional.of(User(id = 200L, loginId = "target", name = "target"))
            val transfer = ProjectTransfer(id = 100L, sender = stranger, destination = "target", project = privateProject)
            val target = User(id = 200L, loginId = "target", name = "target")
            accessControl.isAllowed(target, transfer, Operation.ACCEPT) shouldBe true
            accessControl.isAllowed(stranger, transfer, Operation.ACCEPT) shouldBe false
        }
        it("ACCEPT: 대상이 존재하지 않는 loginId면 같은 이름의 조직 관리자에게 허용") {
            every { userRepository.findByLoginId("target-org") } returns Optional.empty()
            every { organizationRepository.findByName("target-org") } returns Optional.of(org)
            stubOrgRole(org, orgAdminUser, RoleType.ORG_ADMIN)
            val transfer = ProjectTransfer(id = 101L, sender = stranger, destination = "target-org", project = privateProject)
            accessControl.isAllowed(orgAdminUser, transfer, Operation.ACCEPT) shouldBe true
            accessControl.isAllowed(orgMemberUser, transfer, Operation.ACCEPT) shouldBe false
        }
        it("ACCEPT가 아닌 연산은 항상 거부") {
            val transfer = ProjectTransfer(id = 102L, sender = stranger, destination = "target", project = privateProject)
            accessControl.isAllowed(stranger, transfer, Operation.READ) shouldBe false
        }
    }

    describe("isAllowedAttachment(user, attachment, operation) - 컨테이너 동적 해석") {
        it("임시 첨부(NOT_A_RESOURCE)는 업로더 본인만 허용") {
            val attachment = Attachment(id = 110L, containerType = ResourceType.NOT_A_RESOURCE, containerId = "0", ownerLoginId = member.loginId)
            accessControl.isAllowedAttachment(member, attachment, Operation.READ) shouldBe true
            accessControl.isAllowedAttachment(stranger, attachment, Operation.READ) shouldBe false
        }
        it("USER_AVATAR 컨테이너는 UPDATE/DELETE는 본인만, READ는 익명 포함 항상 허용(legacy 'PROJECT가 아닌 리소스는 누구나 읽는다' 규칙)") {
            val attachment = Attachment(id = 111L, containerType = ResourceType.USER_AVATAR, containerId = member.id.toString(), ownerLoginId = member.loginId)
            accessControl.isAllowedAttachment(member, attachment, Operation.UPDATE) shouldBe true
            accessControl.isAllowedAttachment(stranger, attachment, Operation.UPDATE) shouldBe false
            accessControl.isAllowedAttachment(stranger, attachment, Operation.READ) shouldBe true
            accessControl.isAllowedAttachment(null, attachment, Operation.READ) shouldBe true
        }
        it("ISSUE_POST 컨테이너는 이슈 자체의 권한 규칙을 그대로 따른다") {
            val issue = Issue(id = 112L, project = publicProject, authorId = stranger.id)
            every { issueRepository.findById(112L) } returns Optional.of(issue)
            val attachment = Attachment(id = 112L, containerType = ResourceType.ISSUE_POST, containerId = "112", ownerLoginId = stranger.loginId)
            accessControl.isAllowedAttachment(guest, attachment, Operation.READ) shouldBe false
            accessControl.isAllowedAttachment(null, attachment, Operation.READ) shouldBe true
        }
        it("ORGANIZATION 컨테이너는 READ 항상 허용, UPDATE는 조직 관리자만") {
            every { organizationRepository.findById(1L) } returns Optional.of(org)
            val attachment = Attachment(id = 113L, containerType = ResourceType.ORGANIZATION, containerId = "1", ownerLoginId = "someone")
            accessControl.isAllowedAttachment(null, attachment, Operation.READ) shouldBe true
            accessControl.isAllowedAttachment(orgMemberUser, attachment, Operation.UPDATE) shouldBe false
            stubOrgRole(org, orgAdminUser, RoleType.ORG_ADMIN)
            accessControl.isAllowedAttachment(orgAdminUser, attachment, Operation.UPDATE) shouldBe true
        }
        it("컨테이너를 찾을 수 없으면 거부") {
            every { issueRepository.findById(9999L) } returns Optional.empty()
            val attachment = Attachment(id = 114L, containerType = ResourceType.ISSUE_POST, containerId = "9999", ownerLoginId = "x")
            accessControl.isAllowedAttachment(managerUser, attachment, Operation.READ) shouldBe false
        }
    }

    // yona AccessControl.java:21,95-97,336-337 allowsAnonymousAccess/isAnonymousNotAllowed() 대응 (P1-99).
    describe("allowsAnonymousAccess=false (사이트 전역 익명 접근 차단) 설정 시") {
        val restrictedAccessControl = AccessControl(
            projectUserRepository, organizationUserRepository,
            userRepository, organizationRepository,
            issueRepository, postingRepository,
            reviewCommentRepository, commitCommentRepository,
            milestoneRepository,
            allowsAnonymousAccess = false
        )

        it("PUBLIC 프로젝트라도 비로그인(익명) 사용자의 READ를 거부해야 한다") {
            restrictedAccessControl.isAllowed(null, publicProject, Operation.READ) shouldBe false
        }
        it("로그인 사용자에게는 영향이 없어야 한다") {
            restrictedAccessControl.isAllowed(member, privateProject, Operation.READ) shouldBe true
        }
        it("기본값(allowsAnonymousAccess=true)에서는 여전히 PUBLIC 프로젝트를 비로그인 사용자가 READ 가능해야 한다") {
            accessControl.isAllowed(null, publicProject, Operation.READ) shouldBe true
        }
    }

    // yona Organization.getVisibleProjects(User) 대응 (P0-17/P0-20). 조직 소속 프로젝트 목록을 노출하는
    // 화면(조직 게시판/조직 홈)에서 사용자의 조직관리자/조직멤버/비회원 여부에 따라 비공개 프로젝트를 걸러낸다.
    describe("getVisibleProjects(organization, user)") {
        val visibilityOrg = Organization(id = 500L, name = "visOrg")
        val vPublic = Project(id = 501L, name = "b-public", projectScope = ProjectScope.PUBLIC, organization = visibilityOrg)
        val vProtected = Project(id = 502L, name = "a-protected", projectScope = ProjectScope.PROTECTED, organization = visibilityOrg)
        val vPrivate = Project(id = 503L, name = "c-private", projectScope = ProjectScope.PRIVATE, organization = visibilityOrg)
        visibilityOrg.projects = mutableListOf(vPublic, vProtected, vPrivate)

        it("조직관리자는 비공개 포함 전체 프로젝트를 이름순으로 본다") {
            stubOrgRole(visibilityOrg, orgAdminUser, RoleType.ORG_ADMIN)
            val result = accessControl.getVisibleProjects(visibilityOrg, orgAdminUser)
            result.map { it.id } shouldBe listOf(vProtected.id, vPublic.id, vPrivate.id)
        }
        it("사이트매니저는 조직관리자가 아니어도 전체 프로젝트를 본다") {
            val result = accessControl.getVisibleProjects(visibilityOrg, siteManager)
            result.map { it.id }.toSet() shouldBe setOf(vPublic.id, vProtected.id, vPrivate.id)
        }
        it("조직멤버(관리자 아님)는 비공개 프로젝트 중 자신이 프로젝트 멤버가 아닌 것은 제외한다") {
            stubOrgRole(visibilityOrg, orgMemberUser, RoleType.ORG_MEMBER)
            val result = accessControl.getVisibleProjects(visibilityOrg, orgMemberUser)
            result.map { it.id } shouldBe listOf(vProtected.id, vPublic.id)
        }
        it("조직멤버가 비공개 프로젝트의 멤버이기도 하면 그 프로젝트도 포함한다") {
            val memberWithPrivateAccess = User(id = 801L, loginId = "orgMemberPriv", name = "orgMemberPriv")
            memberWithPrivateAccess.projectUsers.add(ProjectUser(id = 900L, user = memberWithPrivateAccess, project = vPrivate, role = Role(id = RoleType.MEMBER.roleType)))
            stubOrgRole(visibilityOrg, memberWithPrivateAccess, RoleType.ORG_MEMBER)
            val result = accessControl.getVisibleProjects(visibilityOrg, memberWithPrivateAccess)
            result.map { it.id }.toSet() shouldBe setOf(vPublic.id, vProtected.id, vPrivate.id)
        }
        it("조직에 속하지 않은 로그인 사용자는 공개 프로젝트만 본다(비공개/보호 제외)") {
            val result = accessControl.getVisibleProjects(visibilityOrg, stranger)
            result.map { it.id } shouldBe listOf(vPublic.id)
        }
        it("비회원이라도 해당 프로젝트의 멤버라면 포함한다") {
            val visitor = User(id = 802L, loginId = "visitor", name = "visitor")
            visitor.projectUsers.add(ProjectUser(id = 901L, user = visitor, project = vProtected, role = Role(id = RoleType.MEMBER.roleType)))
            val result = accessControl.getVisibleProjects(visibilityOrg, visitor)
            result.map { it.id }.toSet() shouldBe setOf(vPublic.id, vProtected.id)
        }
        it("비로그인(익명) 사용자는 공개 프로젝트만 본다") {
            val result = accessControl.getVisibleProjects(visibilityOrg, null)
            result.map { it.id } shouldBe listOf(vPublic.id)
        }
        it("게스트 계정(isGuest=true)은 프로젝트 멤버가 아닌 한 공개 프로젝트도 보지 못한다") {
            val result = accessControl.getVisibleProjects(visibilityOrg, guest)
            result shouldBe emptyList()
        }
    }

    // yona AccessControl.java:32-34 isGlobalResourceCreatable() 대응 (P2-34).
    describe("isGlobalResourceCreatable(user)") {
        it("로그인 사용자는 항상 허용된다") {
            accessControl.isGlobalResourceCreatable(stranger) shouldBe true
        }
        it("비로그인(익명, user=null)은 거부된다") {
            accessControl.isGlobalResourceCreatable(null) shouldBe false
        }
    }

    // yona AccessControl.java:100-118 isResourceCreatable()의 ISSUE_COMMENT/NONISSUE_COMMENT
    // 케이스 대응 (P2-34).
    describe("isIssueCommentCreatable(user, project, issue) / isPostingCommentCreatable(user, project, posting)") {
        val issueAuthor = User(id = 200L, loginId = "issueAuthor", name = "issueAuthor")
        val issueAssignee = User(id = 201L, loginId = "issueAssignee", name = "issueAssignee")
        val issueSharerUser = User(id = 202L, loginId = "issueSharer", name = "issueSharer")
        val postAuthor = User(id = 203L, loginId = "postAuthor", name = "postAuthor")

        val issueOnPrivate = Issue(
            id = 300L, title = "비공개 이슈", project = privateProject, number = 1L,
            authorId = issueAuthor.id, assignee = Assignee(id = 1L, user = issueAssignee, project = privateProject)
        )
        issueOnPrivate.sharers.add(
            IssueSharer(id = 1L, loginId = issueSharerUser.loginId, user = issueSharerUser, issue = issueOnPrivate)
        )

        val issueOnPublic = Issue(id = 301L, title = "공개 이슈", project = publicProject, number = 2L, authorId = issueAuthor.id)

        val postingOnPrivate = Posting(id = 400L, title = "비공개 글", project = privateProject, number = 1L, authorId = postAuthor.id)
        val postingOnPublic = Posting(id = 401L, title = "공개 글", project = publicProject, number = 2L, authorId = postAuthor.id)

        it("이슈 작성자는 비공개 프로젝트 비멤버라도 댓글을 달 수 있다") {
            accessControl.isIssueCommentCreatable(issueAuthor, privateProject, issueOnPrivate) shouldBe true
        }
        it("이슈 담당자는 비공개 프로젝트 비멤버라도 댓글을 달 수 있다") {
            accessControl.isIssueCommentCreatable(issueAssignee, privateProject, issueOnPrivate) shouldBe true
        }
        it("이슈 공유대상은 비공개 프로젝트 비멤버라도 댓글을 달 수 있다") {
            accessControl.isIssueCommentCreatable(issueSharerUser, privateProject, issueOnPrivate) shouldBe true
        }
        it("작성자/담당자/공유대상이 아닌 비공개 프로젝트 비멤버는 댓글을 달 수 없다") {
            accessControl.isIssueCommentCreatable(stranger, privateProject, issueOnPrivate) shouldBe false
        }
        it("프로젝트 멤버는 이슈 작성자가 아니어도 댓글을 달 수 있다") {
            accessControl.isIssueCommentCreatable(member, privateProject, issueOnPrivate) shouldBe true
        }
        it("공개 프로젝트에서는 비멤버 로그인 사용자도 댓글을 달 수 있다(isProjectResourceCreatable로 위임)") {
            accessControl.isIssueCommentCreatable(stranger, publicProject, issueOnPublic) shouldBe true
        }
        it("비로그인(익명)은 공개 프로젝트라도 댓글을 달 수 없다") {
            accessControl.isIssueCommentCreatable(null, publicProject, issueOnPublic) shouldBe false
        }

        it("게시글 작성자는 비공개 프로젝트 비멤버라도 댓글을 달 수 있다") {
            accessControl.isPostingCommentCreatable(postAuthor, privateProject, postingOnPrivate) shouldBe true
        }
        it("작성자가 아닌 비공개 프로젝트 비멤버는 댓글을 달 수 없다") {
            accessControl.isPostingCommentCreatable(stranger, privateProject, postingOnPrivate) shouldBe false
        }
        it("공개 프로젝트에서는 비멤버 로그인 사용자도 게시글 댓글을 달 수 있다") {
            accessControl.isPostingCommentCreatable(stranger, publicProject, postingOnPublic) shouldBe true
        }
    }

    // yona AccessControl.java의 게시글 수정 권한 대응. AttachmentController.deleteAttachment()의
    // BOARD_POST 컨테이너 분기가 이 메서드로 위임하는데(P1-130), 커버리지 감사(JaCoCo)에서 분기·라인
    // 0%로 나와 이 메서드 자체를 직접 호출하는 테스트가 하나도 없었음이 드러나 신설(테스트 커버리지
    // 백로그, docs/COVERAGE_BACKLOG.md). 5개 OR항(siteManager/orgAdmin/managerOf/memberOf/작성자일치)과
    // 앞단 가드(null/guest/loginId=="") 전부 true/false 양쪽 분기를 실제로 태운다.
    describe("isAllowedToUpdatePosting(user, project, authorLoginId)") {
        val emptyLoginIdUser = User(id = 300L, loginId = "", name = "emptyLoginId")

        it("user가 null이면 거부") {
            accessControl.isAllowedToUpdatePosting(null, privateProject, null) shouldBe false
        }
        it("게스트 사용자는 거부") {
            accessControl.isAllowedToUpdatePosting(guest, privateProject, guest.loginId) shouldBe false
        }
        it("loginId가 빈 문자열이면 거부") {
            accessControl.isAllowedToUpdatePosting(emptyLoginIdUser, privateProject, null) shouldBe false
        }
        it("사이트 매니저는 허용") {
            accessControl.isAllowedToUpdatePosting(siteManager, privateProject, null) shouldBe true
        }
        it("조직 관리자는 소속 프로젝트에서 허용") {
            stubOrgRole(org, orgAdminUser, RoleType.ORG_ADMIN)
            accessControl.isAllowedToUpdatePosting(orgAdminUser, protectedProject, null) shouldBe true
        }
        it("프로젝트 매니저는 허용") {
            accessControl.isAllowedToUpdatePosting(managerUser, privateProject, null) shouldBe true
        }
        it("일반 멤버는 작성자가 아니어도 허용") {
            accessControl.isAllowedToUpdatePosting(member, privateProject, stranger.loginId) shouldBe true
        }
        it("멤버가 아니어도 게시글 작성자 본인이면 허용") {
            accessControl.isAllowedToUpdatePosting(stranger, privateProject, stranger.loginId) shouldBe true
        }
        it("authorLoginId가 null이면 작성자 일치로 인정되지 않는다") {
            accessControl.isAllowedToUpdatePosting(stranger, privateProject, null) shouldBe false
        }
        it("사이트매니저/조직관리자/매니저/멤버/작성자 전부 아니면 거부") {
            accessControl.isAllowedToUpdatePosting(stranger, privateProject, "someone-else") shouldBe false
        }
    }

    // isAllowedToUpdateMilestone도 동일한 사유(AttachmentController의 MILESTONE 컨테이너 분기,
    // JaCoCo 0% 발견)로 신설. 작성자 개념이 없어 4개 OR항만 검증한다.
    describe("isAllowedToUpdateMilestone(user, project)") {
        val emptyLoginIdUser = User(id = 301L, loginId = "", name = "emptyLoginId2")

        it("user가 null이면 거부") {
            accessControl.isAllowedToUpdateMilestone(null, privateProject) shouldBe false
        }
        it("게스트 사용자는 거부") {
            accessControl.isAllowedToUpdateMilestone(guest, privateProject) shouldBe false
        }
        it("loginId가 빈 문자열이면 거부") {
            accessControl.isAllowedToUpdateMilestone(emptyLoginIdUser, privateProject) shouldBe false
        }
        it("사이트 매니저는 허용") {
            accessControl.isAllowedToUpdateMilestone(siteManager, privateProject) shouldBe true
        }
        it("조직 관리자는 소속 프로젝트에서 허용") {
            stubOrgRole(org, orgAdminUser, RoleType.ORG_ADMIN)
            accessControl.isAllowedToUpdateMilestone(orgAdminUser, protectedProject) shouldBe true
        }
        it("프로젝트 매니저는 허용") {
            accessControl.isAllowedToUpdateMilestone(managerUser, privateProject) shouldBe true
        }
        it("일반 멤버는 허용") {
            accessControl.isAllowedToUpdateMilestone(member, privateProject) shouldBe true
        }
        it("사이트매니저/조직관리자/매니저/멤버 전부 아니면 거부") {
            accessControl.isAllowedToUpdateMilestone(stranger, privateProject) shouldBe false
        }
    }
})
