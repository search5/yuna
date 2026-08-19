package com.github.search5.yona.domain.issue

import com.github.search5.yona.AbstractIntegrationTest
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.notification.NotificationEventRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.assertions.throwables.shouldThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Transactional
class IssueServiceSpec @Autowired constructor(
    private val issueService: IssueService,
    private val issueRepository: IssueRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val notificationEventRepository: NotificationEventRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val issueEventRepository: IssueEventRepository,
    private val milestoneRepository: com.github.search5.yona.domain.milestone.MilestoneRepository
) : AbstractIntegrationTest() {

    init {
        describe("IssueService 비즈니스 테스트") {
            beforeEach {
                issueEventRepository.deleteAll()
                notificationEventRepository.deleteAll()
                issueCommentRepository.deleteAll()
                issueRepository.deleteAll()
                milestoneRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
            }

            it("이슈 상태를 변경하면 상태가 DB에 갱신되고 알림 이벤트가 생성되어야 한다") {
                // Given
                val author = userRepository.save(
                    User(loginId = "tester", name = "테스터", email = "tester@yona.io")
                )
                val project = projectRepository.save(
                    Project(name = "test-project", owner = "tester")
                )

                val issue = Issue(
                    title = "수정할 버그",
                    body = "버그 설명입니다.",
                    project = project,
                    authorId = author.id,
                    authorLoginId = author.loginId,
                    authorName = author.name,
                    createdDate = Instant.now(),
                    state = State.OPEN
                )
                val savedIssue = issueRepository.save(issue)

                // When
                val updatedIssue = issueService.changeState(savedIssue.id!!, State.CLOSED, "tester")

                // Then
                updatedIssue.state shouldBe State.CLOSED

                // 알림 이벤트 생성 검증
                val events = notificationEventRepository.findAll()
                events.size shouldBe 1
                val event = events.first()
                event.eventType shouldBe EventType.ISSUE_STATE_CHANGED
                event.oldValue shouldBe State.OPEN.toString()
                event.newValue shouldBe State.CLOSED.toString()
                event.senderId shouldBe author.id

                // 이슈 타임라인(IssueEvent) 생성 검증 (P1-07)
                val issueEvents = issueEventRepository.findByIssueOrderByCreatedAsc(savedIssue)
                issueEvents.size shouldBe 1
                issueEvents.first().eventType shouldBe EventType.ISSUE_STATE_CHANGED
                issueEvents.first().oldValue shouldBe State.OPEN.toString()
                issueEvents.first().newValue shouldBe State.CLOSED.toString()
                issueEvents.first().senderLoginId shouldBe "tester"
            }

            it("담당자를 변경하면 IssueEvent 타임라인 항목이 생성되어야 한다") {
                val author = userRepository.save(User(loginId = "tester2", name = "테스터2", email = "tester2@yona.io"))
                val assignee = userRepository.save(User(loginId = "assignee1", name = "담당자", email = "assignee1@yona.io"))
                val project = projectRepository.save(Project(name = "assignee-test-project", owner = "tester2"))
                val issue = Issue(
                    title = "담당자 배정 테스트", body = "...", project = project,
                    authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                    createdDate = Instant.now(), state = State.OPEN
                )
                val savedIssue = issueRepository.save(issue)

                issueService.changeAssignee(savedIssue.id!!, assignee, "tester2")

                val issueEvents = issueEventRepository.findByIssueOrderByCreatedAsc(savedIssue)
                issueEvents.size shouldBe 1
                issueEvents.first().eventType shouldBe EventType.ISSUE_ASSIGNEE_CHANGED
                issueEvents.first().newValue shouldBe assignee.name
            }

            it("마일스톤을 변경하면 IssueEvent 타임라인 항목이 생성되어야 한다") {
                val author = userRepository.save(User(loginId = "tester3", name = "테스터3", email = "tester3@yona.io"))
                val project = projectRepository.save(Project(name = "milestone-test-project", owner = "tester3"))
                val milestone = milestoneRepository.save(
                    com.github.search5.yona.domain.milestone.Milestone(title = "1.0 출시", project = project)
                )
                val issue = Issue(
                    title = "마일스톤 배정 테스트", body = "...", project = project,
                    authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                    createdDate = Instant.now(), state = State.OPEN
                )
                val savedIssue = issueRepository.save(issue)

                val updated = issueService.changeMilestone(savedIssue.id!!, milestone.id, "tester3")
                updated.milestone?.id shouldBe milestone.id

                val issueEvents = issueEventRepository.findByIssueOrderByCreatedAsc(savedIssue)
                issueEvents.size shouldBe 1
                issueEvents.first().eventType shouldBe EventType.ISSUE_MILESTONE_CHANGED
                issueEvents.first().newValue shouldBe "1.0 출시"
            }

            it("사용자가 이슈에 투표를 던지거나 취소할 수 있어야 한다") {
                // Given
                val author = userRepository.save(
                    User(loginId = "tester", name = "테스터", email = "tester@yona.io")
                )
                val project = projectRepository.save(
                    Project(name = "test-project", owner = "tester")
                )
                val issue = issueRepository.save(
                    Issue(
                        title = "투표할 이슈",
                        body = "이슈 본문",
                        project = project,
                        authorId = author.id,
                        authorLoginId = author.loginId,
                        authorName = author.name,
                        createdDate = Instant.now()
                    )
                )

                // 1) 투표 수행 검증
                issueService.voteIssue(issue.id!!, author)

                val issueAfterVote = issueRepository.findById(issue.id!!).get()
                issueAfterVote.voters.size shouldBe 1
                issueAfterVote.voters.first().id shouldBe author.id

                // 2) 동일 사용자 중복 투표 시도 시 예외 발생 검증
                shouldThrow<IllegalStateException> {
                    issueService.voteIssue(issue.id!!, author)
                }

                // 3) 투표 취소 수행 검증
                issueService.unvoteIssue(issue.id!!, author)

                val issueAfterUnvote = issueRepository.findById(issue.id!!).get()
                issueAfterUnvote.voters.size shouldBe 0

                // 4) 투표하지 않은 사용자가 취소 시도 시 예외 발생 검증
                shouldThrow<IllegalStateException> {
                    issueService.unvoteIssue(issue.id!!, author)
                }
            }

            it("사용자가 이슈 댓글에 투표를 던지거나 취소할 수 있어야 한다") {
                // Given
                val author = userRepository.save(
                    User(loginId = "tester", name = "테스터", email = "tester@yona.io")
                )
                val project = projectRepository.save(
                    Project(name = "test-project", owner = "tester")
                )
                val issue = issueRepository.save(
                    Issue(
                        title = "댓글 투표할 이슈",
                        body = "이슈 본문",
                        project = project,
                        authorId = author.id,
                        authorLoginId = author.loginId,
                        authorName = author.name,
                        createdDate = Instant.now()
                    )
                )
                
                val comment = issueCommentRepository.save(
                    IssueComment(
                        contents = "댓글 내용",
                        issue = issue,
                        createdDate = Instant.now(),
                        authorId = author.id,
                        authorLoginId = author.loginId,
                        authorName = author.name,
                        projectId = project.id
                    )
                )

                // 1) 댓글 투표 수행 검증
                issueService.voteComment(comment.id!!, author)

                val commentAfterVote = issueCommentRepository.findById(comment.id!!).get()
                commentAfterVote.voters.size shouldBe 1
                commentAfterVote.voters.first().id shouldBe author.id

                // 2) 동일 사용자 중복 투표 시도 시 예외 발생 검증
                shouldThrow<IllegalStateException> {
                    issueService.voteComment(comment.id!!, author)
                }

                // 3) 댓글 투표 취소 수행 검증
                issueService.unvoteComment(comment.id!!, author)

                val commentAfterUnvote = issueCommentRepository.findById(comment.id!!).get()
                commentAfterUnvote.voters.size shouldBe 0

                // 4) 투표하지 않은 사용자가 취소 시도 시 예외 발생 검증
                shouldThrow<IllegalStateException> {
                    issueService.unvoteComment(comment.id!!, author)
                }
            }
        }
    }
}
