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
    private val milestoneRepository: com.github.search5.yona.domain.milestone.MilestoneRepository,
    private val issueLabelRepository: IssueLabelRepository,
    private val issueLabelCategoryRepository: IssueLabelCategoryRepository
) : AbstractIntegrationTest() {

    init {
        describe("IssueService 비즈니스 테스트") {
            beforeEach {
                issueEventRepository.deleteAll()
                notificationEventRepository.deleteAll()
                issueCommentRepository.deleteAll()
                issueRepository.deleteAll()
                issueLabelRepository.deleteAll()
                issueLabelCategoryRepository.deleteAll()
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

            it("updateIssue로 본문을 변경하면 IssueEvent 타임라인 항목(ISSUE_BODY_CHANGED)이 생성되어야 한다") {
                val author = userRepository.save(User(loginId = "tester4", name = "테스터4", email = "tester4@yona.io"))
                val project = projectRepository.save(Project(name = "body-test-project", owner = "tester4"))
                val issue = Issue(
                    title = "본문 변경 테스트", body = "원래 본문", project = project,
                    authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                    createdDate = Instant.now(), state = State.OPEN
                )
                val savedIssue = issueRepository.save(issue)

                issueService.updateIssue(
                    issueId = savedIssue.id!!,
                    title = savedIssue.title,
                    body = "변경된 본문",
                    updater = author,
                    assigneeUser = null,
                    milestoneId = null,
                    labelIds = null
                )

                val issueEvents = issueEventRepository.findByIssueOrderByCreatedAsc(savedIssue)
                issueEvents.size shouldBe 1
                issueEvents.first().eventType shouldBe EventType.ISSUE_BODY_CHANGED
                issueEvents.first().oldValue shouldBe "원래 본문"
                issueEvents.first().newValue shouldBe "변경된 본문"
                issueEvents.first().senderLoginId shouldBe "tester4"
            }

            it("updateIssue로 본문을 변경하면 변경 이력(history)이 기록되어야 한다(P2-02)") {
                val author = userRepository.save(User(loginId = "tester4b", name = "테스터4B", email = "tester4b@yona.io"))
                val project = projectRepository.save(Project(name = "body-history-project", owner = "tester4b"))
                val issue = Issue(
                    title = "본문 이력 테스트", body = "원래 본문", project = project,
                    authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                    createdDate = Instant.now(), state = State.OPEN
                )
                val savedIssue = issueRepository.save(issue)

                val updated = issueService.updateIssue(
                    issueId = savedIssue.id!!,
                    title = savedIssue.title,
                    body = "변경된 본문",
                    updater = author,
                    assigneeUser = null,
                    milestoneId = null,
                    labelIds = null
                )

                updated.history shouldNotBe null
                updated.history!!.contains("history-made-by") shouldBe true
                updated.history!!.contains("테스터4B") shouldBe true
            }

            it("updateIssue로 본문이 바뀌지 않으면 ISSUE_BODY_CHANGED 이벤트가 생성되지 않아야 한다") {
                val author = userRepository.save(User(loginId = "tester5", name = "테스터5", email = "tester5@yona.io"))
                val project = projectRepository.save(Project(name = "body-nochange-project", owner = "tester5"))
                val issue = Issue(
                    title = "본문 유지 테스트", body = "동일 본문", project = project,
                    authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                    createdDate = Instant.now(), state = State.OPEN
                )
                val savedIssue = issueRepository.save(issue)

                issueService.updateIssue(
                    issueId = savedIssue.id!!,
                    title = savedIssue.title,
                    body = "동일 본문",
                    updater = author,
                    assigneeUser = null,
                    milestoneId = null,
                    labelIds = null
                )

                issueEventRepository.findByIssueOrderByCreatedAsc(savedIssue).size shouldBe 0
            }

            it("updateIssue로 라벨을 변경하면 IssueEvent 타임라인 항목(ISSUE_LABEL_CHANGED)이 생성되어야 한다") {
                val author = userRepository.save(User(loginId = "tester6", name = "테스터6", email = "tester6@yona.io"))
                val project = projectRepository.save(Project(name = "label-test-project", owner = "tester6"))
                val category = issueLabelCategoryRepository.save(
                    IssueLabelCategory(name = "종류", isExclusive = false, project = project)
                )
                val bugLabel = issueLabelRepository.save(
                    IssueLabel(category = category, color = "red", name = "버그", project = project)
                )
                val issue = Issue(
                    title = "라벨 변경 테스트", body = "본문", project = project,
                    authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                    createdDate = Instant.now(), state = State.OPEN
                )
                val savedIssue = issueRepository.save(issue)

                issueService.updateIssue(
                    issueId = savedIssue.id!!,
                    title = savedIssue.title,
                    body = savedIssue.body ?: "",
                    updater = author,
                    assigneeUser = null,
                    milestoneId = null,
                    labelIds = listOf(bugLabel.id!!)
                )

                val issueEvents = issueEventRepository.findByIssueOrderByCreatedAsc(savedIssue)
                issueEvents.size shouldBe 1
                issueEvents.first().eventType shouldBe EventType.ISSUE_LABEL_CHANGED
                issueEvents.first().newValue shouldBe "버그"
            }

            it("같은 사용자가 30초 내에 상태를 연속 변경(A->B->C)하면 IssueEvent가 A->C 하나로 병합돼야 한다(P1-38)") {
                val author = userRepository.save(User(loginId = "tester7", name = "테스터7", email = "tester7@yona.io"))
                val project = projectRepository.save(Project(name = "merge-test-project", owner = "tester7"))
                val issue = Issue(
                    title = "상태 병합 테스트", body = "본문", project = project,
                    authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                    createdDate = Instant.now(), state = State.OPEN
                )
                val savedIssue = issueRepository.save(issue)

                issueService.changeState(savedIssue.id!!, State.CLOSED, "tester7")
                issueService.changeState(savedIssue.id!!, State.REJECTED, "tester7")

                val issueEvents = issueEventRepository.findByIssueOrderByCreatedAsc(savedIssue)
                issueEvents.size shouldBe 1
                issueEvents.first().eventType shouldBe EventType.ISSUE_STATE_CHANGED
                issueEvents.first().oldValue shouldBe State.OPEN.toString()
                issueEvents.first().newValue shouldBe State.REJECTED.toString()
            }

            it("같은 사용자가 30초 내에 상태를 원래대로 되돌리면(A->B->A) IssueEvent가 모두 상쇄돼야 한다(P1-38)") {
                val author = userRepository.save(User(loginId = "tester8", name = "테스터8", email = "tester8@yona.io"))
                val project = projectRepository.save(Project(name = "cancel-test-project", owner = "tester8"))
                val issue = Issue(
                    title = "상태 상쇄 테스트", body = "본문", project = project,
                    authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                    createdDate = Instant.now(), state = State.OPEN
                )
                val savedIssue = issueRepository.save(issue)

                issueService.changeState(savedIssue.id!!, State.CLOSED, "tester8")
                issueService.changeState(savedIssue.id!!, State.OPEN, "tester8")

                issueEventRepository.findByIssueOrderByCreatedAsc(savedIssue).size shouldBe 0
            }

            it("updateIssue로 라벨을 30초 내에 연속 변경하면 중간 지점은 남기되 정확히 되돌리는 경우만 상쇄돼야 한다(P1-38)") {
                val author = userRepository.save(User(loginId = "tester9", name = "테스터9", email = "tester9@yona.io"))
                val project = projectRepository.save(Project(name = "label-merge-project", owner = "tester9"))
                val category = issueLabelCategoryRepository.save(
                    IssueLabelCategory(name = "종류", isExclusive = false, project = project)
                )
                val bugLabel = issueLabelRepository.save(
                    IssueLabel(category = category, color = "red", name = "버그", project = project)
                )
                val featureLabel = issueLabelRepository.save(
                    IssueLabel(category = category, color = "blue", name = "기능", project = project)
                )
                val issue = Issue(
                    title = "라벨 연속 변경 테스트", body = "본문", project = project,
                    authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                    createdDate = Instant.now(), state = State.OPEN
                )
                val savedIssue = issueRepository.save(issue)

                // 1) 라벨 없음 -> [버그]
                issueService.updateIssue(
                    issueId = savedIssue.id!!, title = savedIssue.title, body = savedIssue.body ?: "",
                    updater = author, assigneeUser = null, milestoneId = null,
                    labelIds = listOf(bugLabel.id!!)
                )
                // 2) [버그] -> [버그, 기능] (중간 지점, 되돌리는 게 아니므로 남아야 함)
                issueService.updateIssue(
                    issueId = savedIssue.id!!, title = savedIssue.title, body = savedIssue.body ?: "",
                    updater = author, assigneeUser = null, milestoneId = null,
                    labelIds = listOf(bugLabel.id!!, featureLabel.id!!)
                )
                // 3) [버그, 기능] -> [버그] (2번을 정확히 되돌림 -> 2, 3번 모두 상쇄)
                issueService.updateIssue(
                    issueId = savedIssue.id!!, title = savedIssue.title, body = savedIssue.body ?: "",
                    updater = author, assigneeUser = null, milestoneId = null,
                    labelIds = listOf(bugLabel.id!!)
                )

                val issueEvents = issueEventRepository.findByIssueOrderByCreatedAsc(savedIssue)
                issueEvents.size shouldBe 1
                issueEvents.first().newValue shouldBe "버그"
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
