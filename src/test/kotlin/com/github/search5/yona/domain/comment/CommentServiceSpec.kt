package com.github.search5.yona.domain.comment

import com.github.search5.yona.AbstractIntegrationTest
import com.github.search5.yona.domain.board.Posting
import com.github.search5.yona.domain.board.PostingComment
import com.github.search5.yona.domain.board.PostingCommentRepository
import com.github.search5.yona.domain.board.PostingRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectUser
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleRepository
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueComment
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.issue.IssueCommentRepository
import com.github.search5.yona.domain.notification.NotificationEventRepository
import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.enumeration.ResourceType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Transactional
class CommentServiceSpec @Autowired constructor(
    private val commentService: CommentService,
    private val issueRepository: IssueRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository,
    private val userRepository: UserRepository,
    private val notificationEventRepository: NotificationEventRepository,
    private val postingRepository: PostingRepository,
    private val postingCommentRepository: PostingCommentRepository
) : AbstractIntegrationTest() {

    init {
        describe("CommentService 댓글 및 멘션 연동 테스트") {
            beforeEach {
                notificationEventRepository.deleteAll()
                issueCommentRepository.deleteAll()
                issueRepository.deleteAll()
                postingCommentRepository.deleteAll()
                postingRepository.deleteAll()
                projectUserRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
                roleRepository.deleteAll()
            }

            it("멘션이 포함된 댓글을 작성하면 댓글이 저장되고 멘션된 사용자가 알림 수신자에 포함되어야 한다") {
                // Given
                val author = userRepository.save(
                    User(loginId = "usera", name = "작성자", email = "usera@yona.io")
                )
                val targetUser = userRepository.save(
                    User(loginId = "userb", name = "수신자", email = "userb@yona.io")
                )
                val project = projectRepository.save(
                    Project(name = "comment-project", owner = "tester")
                )

                val issue = Issue(
                    title = "멘션 테스트용 이슈",
                    body = "이슈 본문",
                    project = project,
                    authorId = author.id,
                    authorLoginId = author.loginId,
                    authorName = author.name,
                    createdDate = Instant.now(),
                    state = State.OPEN
                )
                val savedIssue = issueRepository.save(issue)

                val commentContents = "이 문제를 @userb 님께서 검토해 주시겠습니까?"

                // When
                val savedComment = commentService.createIssueComment(
                    issueId = savedIssue.id!!,
                    contents = commentContents,
                    author = author
                )

                // Then
                savedComment.id shouldNotBe null
                savedComment.contents shouldBe commentContents
                savedComment.issue.id shouldBe savedIssue.id

                // 알림 이벤트 및 수신자 멘션 검증
                val events = notificationEventRepository.findAll()
                events.size shouldBe 1
                val event = events.first()
                event.eventType shouldBe EventType.NEW_COMMENT
                event.resourceType shouldBe ResourceType.ISSUE_COMMENT
                event.newValue shouldBe commentContents

                // userb가 수신자로 정상 등록되었는지 확인
                event.receivers.size shouldBe 1
                event.receivers.first().loginId shouldBe "userb"
            }

            it("게시글 댓글을 작성/삭제하면 posting.numOfComments가 실제 댓글 수와 일치해야 한다 (P1-19)") {
                val author = userRepository.save(User(loginId = "boardwriter", name = "글쓴이", email = "boardwriter@yona.io"))
                val commenter = userRepository.save(User(loginId = "boardcommenter", name = "댓글러", email = "boardcommenter@yona.io"))
                val project = projectRepository.save(Project(name = "comment-count-project", owner = "boardwriter"))
                val posting = postingRepository.save(
                    Posting(title = "댓글수 테스트", body = "본문", project = project, number = 1L)
                )
                posting.numOfComments shouldBe 0

                val comment1 = commentService.createPostingComment(posting.id!!, "댓글1", commenter, null)
                postingRepository.findById(posting.id!!).orElseThrow().numOfComments shouldBe 1

                commentService.createPostingComment(posting.id!!, "댓글2", commenter, null)
                postingRepository.findById(posting.id!!).orElseThrow().numOfComments shouldBe 2

                commentService.deletePostingComment(comment1.id!!, commenter)
                postingRepository.findById(posting.id!!).orElseThrow().numOfComments shouldBe 1
            }

            it("작성자도 매니저도 아닌 일반 프로젝트 멤버가 이슈 댓글을 수정할 수 있어야 한다 (P1-90, yona AccessControl.java:280-282 UPDATE는 isMemberOf만 있으면 허용)") {
                val roleMember = roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))
                val author = userRepository.save(User(loginId = "issuewriter", name = "작성자", email = "issuewriter@yona.io"))
                val otherMember = userRepository.save(User(loginId = "othermember", name = "다른멤버", email = "othermember@yona.io"))
                val project = projectRepository.save(Project(name = "member-update-project", owner = "issuewriter"))
                // AccessControl.isMemberOf()는 DB 조회가 아니라 User.projectUsers 엔티티 컬렉션을 직접 읽는다.
                // projectUserRepository.save()만으로는 이미 메모리에 들고 있는 otherMember 인스턴스의
                // projectUsers가 갱신되지 않으므로(재조회 전까지 지연 컬렉션이 초기화되지 않음) 양쪽에 반영한다.
                val projectUser = projectUserRepository.save(ProjectUser(project = project, user = otherMember, role = roleMember))
                otherMember.projectUsers.add(projectUser)

                val issue = issueRepository.save(
                    Issue(title = "이슈", body = "본문", project = project, authorId = author.id, authorLoginId = author.loginId, authorName = author.name)
                )
                val comment = issueCommentRepository.save(
                    IssueComment(contents = "원본 댓글", issue = issue, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, projectId = project.id)
                )

                val updated = commentService.updateIssueComment(comment.id!!, "다른 멤버가 수정한 댓글", otherMember)

                updated.contents shouldBe "다른 멤버가 수정한 댓글"
            }

            it("프로젝트 멤버가 아닌 사용자는 이슈 댓글을 수정할 수 없어야 한다") {
                val roleMember = roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))
                val author = userRepository.save(User(loginId = "issuewriter2", name = "작성자", email = "issuewriter2@yona.io"))
                val stranger = userRepository.save(User(loginId = "stranger", name = "외부인", email = "stranger@yona.io"))
                val project = projectRepository.save(Project(name = "non-member-project", owner = "issuewriter2"))

                val issue = issueRepository.save(
                    Issue(title = "이슈", body = "본문", project = project, authorId = author.id, authorLoginId = author.loginId, authorName = author.name)
                )
                val comment = issueCommentRepository.save(
                    IssueComment(contents = "원본 댓글", issue = issue, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, projectId = project.id)
                )

                shouldThrow<IllegalArgumentException> {
                    commentService.updateIssueComment(comment.id!!, "외부인이 시도한 수정", stranger)
                }
            }

            it("작성자도 매니저도 아닌 일반 프로젝트 멤버가 게시글 댓글을 삭제할 수 있어야 한다 (P1-90)") {
                val roleMember = roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))
                val author = userRepository.save(User(loginId = "postwriter", name = "글쓴이", email = "postwriter@yona.io"))
                val otherMember = userRepository.save(User(loginId = "othermember2", name = "다른멤버2", email = "othermember2@yona.io"))
                val project = projectRepository.save(Project(name = "member-delete-project", owner = "postwriter"))
                val projectUser = projectUserRepository.save(ProjectUser(project = project, user = otherMember, role = roleMember))
                otherMember.projectUsers.add(projectUser)

                val posting = postingRepository.save(Posting(title = "글", body = "본문", project = project, number = 1L))
                val comment = postingCommentRepository.save(
                    PostingComment(contents = "댓글", posting = posting, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, projectId = project.id)
                )

                commentService.deletePostingComment(comment.id!!, otherMember)

                postingCommentRepository.findById(comment.id!!).isPresent shouldBe false
            }
        }
    }
}
