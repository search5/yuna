package com.github.search5.yona.domain.issue

import com.github.search5.yona.AbstractIntegrationTest
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.notification.NotificationEventRepository
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.milestone.Milestone
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUser
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.watch.Watch
import com.github.search5.yona.domain.watch.WatchRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldBeEmpty
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
    private val issueLabelCategoryRepository: IssueLabelCategoryRepository,
    private val watchRepository: WatchRepository,
    private val projectUserRepository: ProjectUserRepository
) : AbstractIntegrationTest() {

    init {
        describe("IssueService 비즈니스 테스트") {
            beforeEach {
                watchRepository.deleteAll()
                issueEventRepository.deleteAll()
                notificationEventRepository.deleteAll()
                issueCommentRepository.deleteAll()
                projectUserRepository.deleteAll()
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
                    Project(name = "test-project", owner = "tester", projectScope = ProjectScope.PUBLIC)
                )
                // NotificationEventRecorder(P1-27)는 legacy와 동일하게 수신자가 없으면 저장하지 않으므로
                // (본인이 본인 이슈 상태를 바꾸면 본인은 수신자에서 제외된다), 실제 수신자가 될 프로젝트
                // 감시자를 한 명 둔다.
                val watcher = userRepository.save(User(loginId = "watcher", name = "감시자", email = "watcher@yona.io"))
                watchRepository.save(Watch(user = watcher, resourceType = ResourceType.PROJECT, resourceId = project.id.toString()))

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

            // yona IssueApp.editIssue()의 hasTargetProject()/moveIssueToOtherProject()/
            // addIssueMovedNotification() 대응 (P1-48).
            describe("moveIssue (P1-48, 이슈를 다른 프로젝트로 이동)") {
                it("다른 프로젝트로 이동하면 project/번호/생성일이 갱신되고 마일스톤이 초기화되어야 한다") {
                    val mover = userRepository.save(User(loginId = "mover", name = "이동실행자", email = "mover@yona.io"))
                    val fromProject = projectRepository.save(Project(name = "from-proj", owner = "owner-a", projectScope = ProjectScope.PUBLIC))
                    val toProject = projectRepository.save(Project(name = "to-proj", owner = "owner-b", projectScope = ProjectScope.PUBLIC))
                    val milestone = milestoneRepository.save(Milestone(title = "v1.0", project = fromProject))

                    val originalCreatedDate = Instant.now().minusSeconds(3600)
                    val issue = issueRepository.save(
                        Issue(
                            title = "이동할 이슈", body = "본문", project = fromProject,
                            authorId = mover.id, authorLoginId = mover.loginId, authorName = mover.name,
                            createdDate = originalCreatedDate, milestone = milestone
                        )
                    )

                    val moved = issueService.moveIssue(issue.id!!, toProject.id!!, mover)

                    moved.project.id shouldBe toProject.id
                    moved.number shouldBe 1L
                    moved.milestone shouldBe null
                    moved.createdDate shouldNotBe originalCreatedDate

                    val reloadedToProject = projectRepository.findById(toProject.id!!).get()
                    reloadedToProject.lastIssueNumber shouldBe 1L
                }

                it("같은 프로젝트로 이동을 시도하면 아무 변화 없이 그대로 반환하고 알림도 발행하지 않아야 한다") {
                    val mover = userRepository.save(User(loginId = "mover2", name = "이동실행자2", email = "mover2@yona.io"))
                    val project = projectRepository.save(Project(name = "same-proj", owner = "owner-a", projectScope = ProjectScope.PUBLIC))
                    val issue = issueRepository.save(
                        Issue(
                            title = "제자리 이슈", body = "본문", project = project,
                            authorId = mover.id, authorLoginId = mover.loginId, authorName = mover.name,
                            createdDate = Instant.now(), number = 1L
                        )
                    )
                    project.lastIssueNumber = 1L
                    projectRepository.save(project)

                    val result = issueService.moveIssue(issue.id!!, project.id!!, mover)

                    result.number shouldBe 1L
                    notificationEventRepository.findAll().size shouldBe 0
                }

                it("이슈의 댓글들도 대상 프로젝트로 projectId가 갱신되어야 한다") {
                    val mover = userRepository.save(User(loginId = "mover3", name = "이동실행자3", email = "mover3@yona.io"))
                    val fromProject = projectRepository.save(Project(name = "from-proj3", owner = "owner-a", projectScope = ProjectScope.PUBLIC))
                    val toProject = projectRepository.save(Project(name = "to-proj3", owner = "owner-b", projectScope = ProjectScope.PUBLIC))
                    val issue = issueRepository.save(
                        Issue(
                            title = "댓글 있는 이슈", body = "본문", project = fromProject,
                            authorId = mover.id, authorLoginId = mover.loginId, authorName = mover.name,
                            createdDate = Instant.now()
                        )
                    )
                    val comment = issueCommentRepository.save(
                        IssueComment(
                            contents = "댓글", issue = issue, createdDate = Instant.now(),
                            authorId = mover.id, authorLoginId = mover.loginId, authorName = mover.name,
                            projectId = fromProject.id
                        )
                    )

                    issueService.moveIssue(issue.id!!, toProject.id!!, mover)

                    val reloadedComment = issueCommentRepository.findById(comment.id!!).get()
                    reloadedComment.projectId shouldBe toProject.id
                }

                it("이동하는 사용자가 대상 프로젝트 멤버이고 라벨이 있으면 라벨이 대상 프로젝트로 이전되어야 한다") {
                    val mover = userRepository.save(User(loginId = "mover4", name = "이동실행자4", email = "mover4@yona.io"))
                    val fromProject = projectRepository.save(Project(name = "from-proj4", owner = "owner-a", projectScope = ProjectScope.PUBLIC))
                    val toProject = projectRepository.save(Project(name = "to-proj4", owner = "owner-b", projectScope = ProjectScope.PUBLIC))
                    // ProjectUser를 리포지토리로만 저장하면 mover 객체의 in-memory projectUsers
                    // 컬렉션이 갱신되지 않는다(같은 영속성 컨텍스트의 식별자 맵 캐시 때문에 findById로
                    // 다시 조회해도 동일 인스턴스가 반환되고, User(...) 생성자로 직접 만든 엔티티는
                    // Hibernate가 지연로딩 프록시로 바꿔치기하지 않는다) — isMemberOf()가 읽는 바로 그
                    // 컬렉션에 직접 추가해 실제 멤버십 상태를 반영한다.
                    val projectUser = ProjectUser(user = mover, project = toProject, role = Role(id = RoleType.MEMBER.roleType))
                    mover.projectUsers.add(projectUser)
                    projectUserRepository.save(projectUser)

                    val category = issueLabelCategoryRepository.save(IssueLabelCategory(name = "버그", isExclusive = false, project = fromProject))
                    val label = issueLabelRepository.save(IssueLabel(category = category, color = "#ff0000", name = "critical", project = fromProject))
                    val issue = issueRepository.save(
                        Issue(
                            title = "라벨 있는 이슈", body = "본문", project = fromProject,
                            authorId = mover.id, authorLoginId = mover.loginId, authorName = mover.name,
                            createdDate = Instant.now(), labels = mutableSetOf(label)
                        )
                    )

                    val moved = issueService.moveIssue(issue.id!!, toProject.id!!, mover)

                    moved.labels.size shouldBe 1
                    val transferredLabel = moved.labels.first()
                    transferredLabel.name shouldBe "critical"
                    transferredLabel.project.id shouldBe toProject.id
                    transferredLabel.category.name shouldBe "버그"
                }

                it("이동하는 사용자가 대상 프로젝트 멤버가 아니면 라벨이 비워져야 한다") {
                    val mover = userRepository.save(User(loginId = "mover5", name = "이동실행자5", email = "mover5@yona.io"))
                    val fromProject = projectRepository.save(Project(name = "from-proj5", owner = "owner-a", projectScope = ProjectScope.PUBLIC))
                    val toProject = projectRepository.save(Project(name = "to-proj5", owner = "owner-b", projectScope = ProjectScope.PUBLIC))

                    val category = issueLabelCategoryRepository.save(IssueLabelCategory(name = "버그", isExclusive = false, project = fromProject))
                    val label = issueLabelRepository.save(IssueLabel(category = category, color = "#ff0000", name = "critical", project = fromProject))
                    val issue = issueRepository.save(
                        Issue(
                            title = "라벨 있는 이슈2", body = "본문", project = fromProject,
                            authorId = mover.id, authorLoginId = mover.loginId, authorName = mover.name,
                            createdDate = Instant.now(), labels = mutableSetOf(label)
                        )
                    )

                    val moved = issueService.moveIssue(issue.id!!, toProject.id!!, mover)

                    moved.labels.shouldBeEmpty()
                }

                it("서브태스크(하위 이슈)도 함께 대상 프로젝트로 이동하되 별도 알림은 발행하지 않아야 한다") {
                    val mover = userRepository.save(User(loginId = "mover6", name = "이동실행자6", email = "mover6@yona.io"))
                    val fromProject = projectRepository.save(Project(name = "from-proj6", owner = "owner-a", projectScope = ProjectScope.PUBLIC))
                    val toProject = projectRepository.save(Project(name = "to-proj6", owner = "owner-b", projectScope = ProjectScope.PUBLIC))

                    val parent = issueRepository.save(
                        Issue(
                            title = "부모 이슈", body = "본문", project = fromProject,
                            authorId = mover.id, authorLoginId = mover.loginId, authorName = mover.name,
                            createdDate = Instant.now()
                        )
                    )
                    val child = issueRepository.save(
                        Issue(
                            title = "자식 이슈", body = "본문", project = fromProject,
                            authorId = mover.id, authorLoginId = mover.loginId, authorName = mover.name,
                            createdDate = Instant.now(), parent = parent
                        )
                    )

                    issueService.moveIssue(parent.id!!, toProject.id!!, mover)

                    val reloadedChild = issueRepository.findById(child.id!!).get()
                    reloadedChild.project.id shouldBe toProject.id
                    reloadedChild.number shouldNotBe null

                    // NEW_ISSUE(부모 자신에 대한 재알림)만 있을 뿐, 자식 이슈에 대한 별도 알림은 없어야 한다
                    // (수신자가 없어 저장되지 않을 수 있으므로 이벤트 수 자체가 이 케이스의 핵심 단언은 아니지만,
                    // resourceId가 child를 가리키는 이벤트가 없어야 한다).
                    notificationEventRepository.findAll().none { it.resourceId == child.id.toString() } shouldBe true
                }

                it("이동하면 ISSUE_MOVED(이전 프로젝트 감시자 수신)와 NEW_ISSUE(새 프로젝트 감시자 수신) 알림이 모두 발행되어야 한다") {
                    val mover = userRepository.save(User(loginId = "mover7", name = "이동실행자7", email = "mover7@yona.io"))
                    val fromProject = projectRepository.save(Project(name = "from-proj7", owner = "owner-a", projectScope = ProjectScope.PUBLIC))
                    val toProject = projectRepository.save(Project(name = "to-proj7", owner = "owner-b", projectScope = ProjectScope.PUBLIC))

                    val fromWatcherUser = userRepository.save(User(loginId = "fromwatcher7", name = "이전감시자", email = "fw7@yona.io"))
                    watchRepository.save(Watch(user = fromWatcherUser, resourceType = ResourceType.PROJECT, resourceId = fromProject.id.toString()))
                    val toWatcherUser = userRepository.save(User(loginId = "towatcher7", name = "새감시자", email = "tw7@yona.io"))
                    watchRepository.save(Watch(user = toWatcherUser, resourceType = ResourceType.PROJECT, resourceId = toProject.id.toString()))

                    val issue = issueRepository.save(
                        Issue(
                            title = "알림 검증용 이슈", body = "본문", project = fromProject,
                            authorId = mover.id, authorLoginId = mover.loginId, authorName = mover.name,
                            createdDate = Instant.now()
                        )
                    )

                    issueService.moveIssue(issue.id!!, toProject.id!!, mover)

                    val events = notificationEventRepository.findAll()
                    val movedEvent = events.first { it.eventType == EventType.ISSUE_MOVED }
                    movedEvent.oldValue shouldBe "owner-a/from-proj7"
                    movedEvent.newValue shouldBe "owner-b/to-proj7"
                    // yona afterIssueMoved()는 다른 알림들과 달리 mover 자신을 수신자에서 빼지 않는다 —
                    // mover가 이슈 작성자(author)이기도 하므로 baseWatchers에 포함돼 함께 수신해야 한다.
                    movedEvent.receivers.map { it.id }.toSet() shouldBe setOf(mover.id, fromWatcherUser.id)

                    val newIssueEvent = events.first { it.eventType == EventType.NEW_ISSUE }
                    newIssueEvent.receivers.map { it.id } shouldBe listOf(toWatcherUser.id)
                }

                it("자신의 비공개 프로젝트에서 이동하면 history가 비워지고 알림이 발행되지 않아야 한다") {
                    val mover = userRepository.save(User(loginId = "mover8", name = "이동실행자8", email = "mover8@yona.io"))
                    val fromProject = projectRepository.save(
                        Project(name = "private-proj8", owner = "mover8", projectScope = ProjectScope.PRIVATE)
                    )
                    val toProject = projectRepository.save(Project(name = "to-proj8", owner = "owner-b", projectScope = ProjectScope.PUBLIC))

                    val issue = issueRepository.save(
                        Issue(
                            title = "비공개 프로젝트 이슈", body = "본문", project = fromProject,
                            authorId = mover.id, authorLoginId = mover.loginId, authorName = mover.name,
                            createdDate = Instant.now(), history = "이전 편집 이력"
                        )
                    )

                    val moved = issueService.moveIssue(issue.id!!, toProject.id!!, mover)

                    moved.history shouldBe ""
                    notificationEventRepository.findAll().size shouldBe 0
                }

                it("대상 프로젝트가 존재하지 않으면 IllegalArgumentException을 던져야 한다") {
                    val mover = userRepository.save(User(loginId = "mover9", name = "이동실행자9", email = "mover9@yona.io"))
                    val fromProject = projectRepository.save(Project(name = "from-proj9", owner = "owner-a", projectScope = ProjectScope.PUBLIC))
                    val issue = issueRepository.save(
                        Issue(
                            title = "이슈9", body = "본문", project = fromProject,
                            authorId = mover.id, authorLoginId = mover.loginId, authorName = mover.name,
                            createdDate = Instant.now()
                        )
                    )

                    shouldThrow<IllegalArgumentException> {
                        issueService.moveIssue(issue.id!!, 999999L, mover)
                    }
                }
            }
        }
    }
}
