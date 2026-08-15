package com.github.search5.yona.domain.comment

import com.github.search5.yona.AbstractIntegrationTest
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.issue.IssueCommentRepository
import com.github.search5.yona.domain.notification.NotificationEventRepository
import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.enumeration.ResourceType
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
    private val userRepository: UserRepository,
    private val notificationEventRepository: NotificationEventRepository
) : AbstractIntegrationTest() {

    init {
        describe("CommentService 댓글 및 멘션 연동 테스트") {
            beforeEach {
                notificationEventRepository.deleteAll()
                issueCommentRepository.deleteAll()
                issueRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
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
        }
    }
}
