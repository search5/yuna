package com.github.search5.yona.domain.pullrequest

import com.github.search5.yona.AbstractIntegrationTest
import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.attachment.AttachmentService
import com.github.search5.yona.domain.comment.CommentService
import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.notification.NotificationEventRecorder
import com.github.search5.yona.domain.notification.NotificationEventRepository
import com.github.search5.yona.domain.notification.NotificationMailRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.support.CodeRange
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.user.UserState
import com.github.search5.yona.domain.vcs.RepositoryService
import com.github.search5.yona.domain.watch.Watch
import com.github.search5.yona.domain.watch.WatchRepository
import com.github.search5.yona.domain.watch.WatchService
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.RefSpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.nio.file.Files

private fun createTestCommit(
    bareRepoDir: File,
    branch: String,
    filePath: String,
    content: String,
    authorName: String = "tester",
    authorEmail: String = "tester@yona.io"
) {
    val tempWorkingDir = Files.createTempDirectory("yuna-test-commit").toFile()
    try {
        val git = Git.init().setDirectory(tempWorkingDir).call()
        val config = git.repository.config
        config.setString("remote", "origin", "url", bareRepoDir.absolutePath)
        config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*")
        config.save()

        try {
            git.fetch().setRemote("origin").call()
            val ref = git.repository.resolve("refs/remotes/origin/$branch")
            if (ref != null) {
                git.checkout().setCreateBranch(true).setName(branch).setStartPoint("origin/$branch").call()
            }
        } catch (e: Exception) {
            // 빈 저장소인 경우 checkout 생략
        }

        val file = File(tempWorkingDir, filePath)
        file.parentFile.mkdirs()
        file.writeText(content)

        git.add().addFilepattern(filePath).call()
        git.commit().setSign(false).setAuthor(authorName, authorEmail).setMessage("commit").call()
        git.push()
            .setRemote("origin")
            .setRefSpecs(RefSpec("HEAD:refs/heads/$branch"))
            .setForce(true)
            .call()

        git.repository.close()
        git.close()
    } finally {
        tempWorkingDir.deleteRecursively()
    }
}

@Transactional
class CodeReviewServiceSpec @Autowired constructor(
    private val codeReviewService: CodeReviewService,
    private val commentThreadRepository: CommentThreadRepository,
    private val reviewCommentRepository: ReviewCommentRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val pullRequestCommitRepository: PullRequestCommitRepository,
    private val repositoryService: RepositoryService,
    private val pullRequestEventRepository: PullRequestEventRepository,
    private val notificationEventRepository: NotificationEventRepository,
    private val notificationMailRepository: NotificationMailRepository,
    private val commitCommentRepository: CommitCommentRepository,
    private val watchRepository: WatchRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val projectUserRepository: ProjectUserRepository,
    private val attachmentService: AttachmentService,
    private val commentService: CommentService,
    private val watchService: WatchService,
    private val pullRequestService: PullRequestService,
    private val accessControl: AccessControl
) : AbstractIntegrationTest() {

    init {
        describe("CodeReviewService 통합 테스트") {
            lateinit var project: Project
            lateinit var user: User
            lateinit var pullRequest: PullRequest

            lateinit var otherUser: User

            beforeEach {
                watchRepository.deleteAll()
                commitCommentRepository.deleteAll()
                reviewCommentRepository.deleteAll()
                commentThreadRepository.deleteAll()
                pullRequestEventRepository.deleteAll()
                notificationMailRepository.deleteAll()
                notificationEventRepository.deleteAll()
                pullRequestRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()

                user = userRepository.save(
                    User(loginId = "tester", name = "테스터", email = "tester@yona.io")
                )
                otherUser = userRepository.save(
                    User(loginId = "other", name = "타인", email = "other@yona.io")
                )
                project = projectRepository.save(
                    Project(name = "test-repo", owner = "owner-x", vcs = "GIT", projectScope = ProjectScope.PUBLIC)
                )
                pullRequest = pullRequestRepository.save(
                    PullRequest(
                        title = "테스트 PR",
                        toProject = project,
                        fromProject = project,
                        toBranch = "master",
                        fromBranch = "feature",
                        contributor = user
                    )
                )
            }

            it("1. 새 코드 리뷰 댓글과 라인지정 스레드가 생성되어야 한다") {
                val codeRange = CodeRange(
                    path = "src/main/kotlin/App.kt",
                    startSide = CodeRange.Side.B,
                    startLine = 10,
                    startColumn = 0,
                    endSide = CodeRange.Side.B,
                    endLine = 10,
                    endColumn = 0
                )

                val comment = codeReviewService.createReviewComment(
                    project = project,
                    pullRequest = pullRequest,
                    commitId = "1234567890abcdef",
                    contents = "이 부분 수정이 필요해 보입니다.",
                    codeRange = codeRange,
                    threadId = null,
                    currentUser = user
                )

                comment.id shouldNotBe null
                comment.contents shouldBe "이 부분 수정이 필요해 보입니다."
                comment.thread shouldNotBe null
                (comment.thread is CodeCommentThread) shouldBe true

                val codeThread = comment.thread as CodeCommentThread
                codeThread.codeRange.path shouldBe "src/main/kotlin/App.kt"
                codeThread.state shouldBe CommentThread.ThreadState.OPEN
            }

            // yona NotificationEvent.forNewComment(sender, pullRequest, newComment) 대응 (P1-50)
            describe("리뷰 댓글 알림 (P1-50)") {
                it("PR 위 리뷰 댓글을 작성하면 PR 감시자에게 NEW_REVIEW_COMMENT 알림이 발행되어야 한다") {
                    watchRepository.save(Watch(user = otherUser, resourceType = ResourceType.PULL_REQUEST, resourceId = pullRequest.id.toString()))

                    val comment = codeReviewService.createReviewComment(
                        project = project, pullRequest = pullRequest, commitId = "abc123",
                        contents = "댓글 내용", codeRange = null, threadId = null, currentUser = user
                    )

                    val events = notificationEventRepository.findAll()
                    events.size shouldBe 1
                    val event = events.first()
                    event.eventType shouldBe EventType.NEW_REVIEW_COMMENT
                    event.resourceType shouldBe ResourceType.REVIEW_COMMENT
                    event.resourceId shouldBe comment.id.toString()
                    event.newValue shouldBe "댓글 내용"
                    event.senderId shouldBe user.id
                    event.receivers.map { it.id } shouldBe listOf(otherUser.id)
                }

                it("댓글에 멘션된 사용자는 감시자가 아니어도 수신자에 포함되어야 한다") {
                    val mentioned = userRepository.save(User(loginId = "mentioned1", name = "멘션대상", email = "mentioned1@yona.io"))

                    codeReviewService.createReviewComment(
                        project = project, pullRequest = pullRequest, commitId = "abc123",
                        contents = "@mentioned1 확인 부탁드려요", codeRange = null, threadId = null, currentUser = user
                    )

                    val events = notificationEventRepository.findAll()
                    events.size shouldBe 1
                    events.first().receivers.map { it.id } shouldBe listOf(mentioned.id)
                }

                it("작성자 본인은 수신자에서 제외되어야 한다") {
                    watchRepository.save(Watch(user = user, resourceType = ResourceType.PULL_REQUEST, resourceId = pullRequest.id.toString()))

                    codeReviewService.createReviewComment(
                        project = project, pullRequest = pullRequest, commitId = "abc123",
                        contents = "댓글 내용", codeRange = null, threadId = null, currentUser = user
                    )

                    notificationEventRepository.findAll().size shouldBe 0
                }

                it("기존 스레드에 답글을 달아도(threadId != null) 알림이 발행되어야 한다") {
                    watchRepository.save(Watch(user = otherUser, resourceType = ResourceType.PULL_REQUEST, resourceId = pullRequest.id.toString()))
                    val first = codeReviewService.createReviewComment(
                        project = project, pullRequest = pullRequest, commitId = "abc123",
                        contents = "첫 댓글", codeRange = null, threadId = null, currentUser = user
                    )
                    notificationMailRepository.deleteAll()
                    notificationEventRepository.deleteAll()

                    codeReviewService.createReviewComment(
                        project = project, pullRequest = pullRequest, commitId = "abc123",
                        contents = "답글", codeRange = null, threadId = first.thread!!.id, currentUser = user
                    )

                    val events = notificationEventRepository.findAll()
                    events.size shouldBe 1
                    events.first().newValue shouldBe "답글"
                }

                // yona Commit.getWatchers(project) 대응: 커밋 리소스(resourceType=COMMIT,
                // resourceId="{projectId}:{commitId}", legacy Commit.asResource()와 동일한 합성 키)를
                // 명시적으로 감시 중인 사용자에게 알림이 가야 한다.
                it("PR 밖(commitId만 있는) 리뷰 댓글은 그 커밋을 감시 중인 사용자에게 NEW_REVIEW_COMMENT 알림이 발행되어야 한다") {
                    watchRepository.save(Watch(user = otherUser, resourceType = ResourceType.COMMIT, resourceId = "${project.id}:deadbeef"))

                    codeReviewService.createReviewComment(
                        project = project, pullRequest = null, commitId = "deadbeef",
                        contents = "커밋에 대한 댓글", codeRange = null, threadId = null, currentUser = user
                    )

                    val events = notificationEventRepository.findAll()
                    events.size shouldBe 1
                    events.first().eventType shouldBe EventType.NEW_REVIEW_COMMENT
                    events.first().receivers.map { it.id } shouldBe listOf(otherUser.id)
                }

                // yona Commit.getWatchers()의 "이미 이 커밋에 댓글을 남긴 사용자는 기본 감시자" 대응 —
                // Watch 엔티티로 명시적으로 감시하지 않았어도 자동으로 수신자가 된다.
                it("PR 밖 커밋에 이미 댓글을 남긴 사용자는 명시적으로 감시하지 않아도 다음 댓글 알림을 받아야 한다") {
                    codeReviewService.createReviewComment(
                        project = project, pullRequest = null, commitId = "deadbeef",
                        contents = "먼저 남긴 댓글", codeRange = null, threadId = null, currentUser = otherUser
                    )
                    notificationMailRepository.deleteAll()
                    notificationEventRepository.deleteAll()

                    codeReviewService.createReviewComment(
                        project = project, pullRequest = null, commitId = "deadbeef",
                        contents = "두 번째 댓글", codeRange = null, threadId = null, currentUser = user
                    )

                    val events = notificationEventRepository.findAll()
                    events.size shouldBe 1
                    events.first().receivers.map { it.id } shouldBe listOf(otherUser.id)
                }

                it("수신자가 없으면 알림을 저장하지 않아야 한다") {
                    codeReviewService.createReviewComment(
                        project = project, pullRequest = pullRequest, commitId = "abc123",
                        contents = "아무도 안 볼 댓글", codeRange = null, threadId = null, currentUser = user
                    )

                    notificationEventRepository.findAll().size shouldBe 0
                }
            }

            // yona NotificationEvent.forNewSVNCommitComment(project, codeComment, author) 대응 (P1-50).
            // legacy는 이 경로만 이벤트 타입이 NEW_COMMENT(NEW_REVIEW_COMMENT 아님)다.
            describe("커밋 댓글(CommitComment) 알림 (P1-50)") {
                it("커밋 댓글을 작성하면 그 커밋을 감시 중인 사용자에게 NEW_COMMENT 알림이 발행되어야 한다") {
                    watchRepository.save(Watch(user = otherUser, resourceType = ResourceType.COMMIT, resourceId = "${project.id}:cafebabe"))

                    val comment = codeReviewService.createCommitComment(
                        project = project, commitId = "cafebabe", contents = "커밋 댓글 내용",
                        path = null, line = null, side = null, currentUser = user
                    )

                    val events = notificationEventRepository.findAll()
                    events.size shouldBe 1
                    val event = events.first()
                    event.eventType shouldBe EventType.NEW_COMMENT
                    event.resourceType shouldBe ResourceType.COMMIT_COMMENT
                    event.resourceId shouldBe comment.id.toString()
                    event.newValue shouldBe "커밋 댓글 내용"
                }

                it("같은 커밋에 이미 CommitComment를 남긴 사용자는 자동으로 다음 댓글 알림을 받아야 한다") {
                    codeReviewService.createCommitComment(
                        project = project, commitId = "cafebabe", contents = "첫 커밋 댓글",
                        path = null, line = null, side = null, currentUser = otherUser
                    )
                    notificationMailRepository.deleteAll()
                    notificationEventRepository.deleteAll()

                    codeReviewService.createCommitComment(
                        project = project, commitId = "cafebabe", contents = "두 번째 커밋 댓글",
                        path = null, line = null, side = null, currentUser = user
                    )

                    val events = notificationEventRepository.findAll()
                    events.size shouldBe 1
                    events.first().receivers.map { it.id } shouldBe listOf(otherUser.id)
                }
            }

            // yona NotificationEvent.afterStateChanged(CommentThread.ThreadState, CommentThread) 대응 (P1-50)
            describe("리뷰 스레드 상태 변경 알림 (P1-50)") {
                it("스레드를 닫으면(open->closed) PR 감시자에게 REVIEW_THREAD_STATE_CHANGED 알림이 발행되어야 한다") {
                    watchRepository.save(Watch(user = otherUser, resourceType = ResourceType.PULL_REQUEST, resourceId = pullRequest.id.toString()))
                    val comment = codeReviewService.createReviewComment(
                        project = project, pullRequest = pullRequest, commitId = "abc123",
                        contents = "댓글", codeRange = null, threadId = null, currentUser = otherUser
                    )
                    notificationMailRepository.deleteAll()
                    notificationEventRepository.deleteAll()

                    codeReviewService.updateThreadState(comment.thread!!.id!!, CommentThread.ThreadState.CLOSED, user)

                    val events = notificationEventRepository.findAll()
                    events.size shouldBe 1
                    val event = events.first()
                    event.eventType shouldBe EventType.REVIEW_THREAD_STATE_CHANGED
                    event.resourceType shouldBe ResourceType.COMMENT_THREAD
                    event.oldValue shouldBe "OPEN"
                    event.newValue shouldBe "CLOSED"
                    event.receivers.map { it.id } shouldBe listOf(otherUser.id)
                }

                it("이미 같은 상태면 알림을 발행하지 않아야 한다") {
                    watchRepository.save(Watch(user = otherUser, resourceType = ResourceType.PULL_REQUEST, resourceId = pullRequest.id.toString()))
                    val comment = codeReviewService.createReviewComment(
                        project = project, pullRequest = pullRequest, commitId = "abc123",
                        contents = "댓글", codeRange = null, threadId = null, currentUser = otherUser
                    )
                    notificationMailRepository.deleteAll()
                    notificationEventRepository.deleteAll()

                    codeReviewService.updateThreadState(comment.thread!!.id!!, CommentThread.ThreadState.OPEN, user)

                    notificationEventRepository.findAll().size shouldBe 0
                }

                it("상태를 바꾼 본인은 수신자에서 제외되어야 한다") {
                    watchRepository.save(Watch(user = user, resourceType = ResourceType.PULL_REQUEST, resourceId = pullRequest.id.toString()))
                    val comment = codeReviewService.createReviewComment(
                        project = project, pullRequest = pullRequest, commitId = "abc123",
                        contents = "댓글", codeRange = null, threadId = null, currentUser = otherUser
                    )
                    notificationMailRepository.deleteAll()
                    notificationEventRepository.deleteAll()

                    codeReviewService.updateThreadState(comment.thread!!.id!!, CommentThread.ThreadState.CLOSED, user)

                    notificationEventRepository.findAll().size shouldBe 0
                }

                it("PR 밖(순수 커밋 위) 스레드는 그 커밋을 감시 중인 사용자에게 알림이 발행되어야 한다") {
                    watchRepository.save(Watch(user = otherUser, resourceType = ResourceType.COMMIT, resourceId = "${project.id}:deadbeef"))
                    val comment = codeReviewService.createReviewComment(
                        project = project, pullRequest = null, commitId = "deadbeef",
                        contents = "댓글", codeRange = null, threadId = null, currentUser = user
                    )
                    notificationMailRepository.deleteAll()
                    notificationEventRepository.deleteAll()

                    codeReviewService.updateThreadState(comment.thread!!.id!!, CommentThread.ThreadState.CLOSED, user)

                    val events = notificationEventRepository.findAll()
                    events.size shouldBe 1
                    events.first().receivers.map { it.id } shouldBe listOf(otherUser.id)
                }
            }

            // yona Commit.getWatchers()의 "커밋 작성자는 항상 기본 감시자" 규칙(getAuthor().isAnonymous()
            // 체크만 통과하면 Watch 여부와 무관) 대응 — 실제 git 저장소로 커밋을 만들어 검증한다.
            describe("커밋 작성자 자동 감시 (P1-50, yona Commit.getWatchers()의 author 포함 규칙 대응)") {
                it("PR 밖 커밋에 댓글이 달리면, 그 커밋의 작성자는 감시하지 않았어도 알림을 받아야 한다") {
                    val commitProject = projectRepository.save(Project(name = "commit-author-repo", owner = "owner-x", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
                    repositoryService.getRepository(commitProject).create()
                    val bareDir = repositoryService.getRepository(commitProject).getDirectory()
                    createTestCommit(bareDir, "master", "test.txt", "v1", authorName = "타인", authorEmail = "other@yona.io")
                    val commitId = repositoryService.getRepository(commitProject).getBranches()
                        .first { it.name == "refs/heads/master" }.headCommit.getId()

                    codeReviewService.createReviewComment(
                        project = commitProject, pullRequest = null, commitId = commitId,
                        contents = "커밋 작성자에게 가야 할 댓글", codeRange = null, threadId = null, currentUser = user
                    )

                    val events = notificationEventRepository.findAll()
                    events.size shouldBe 1
                    events.first().receivers.map { it.id } shouldBe listOf(otherUser.id)
                }
            }

            it("리뷰어를 추가하면 NotificationEvent와 PullRequestEvent가 모두 생성되어야 한다(P1-39)") {
                codeReviewService.addReviewer(pullRequest.id!!, otherUser.id!!)

                val prEventsAfterAdd = pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pullRequest)
                prEventsAfterAdd.size shouldBe 1
                prEventsAfterAdd.first().eventType shouldBe EventType.PULL_REQUEST_REVIEW_STATE_CHANGED
                prEventsAfterAdd.first().newValue shouldBe "DONE"
                prEventsAfterAdd.first().senderLoginId shouldBe otherUser.loginId

                notificationEventRepository.findAll().size shouldBe 1
            }

            it("같은 리뷰어가 30초 내에 참여/해제를 반복하면 PullRequestEvent가 상쇄돼야 한다(P1-40)") {
                codeReviewService.addReviewer(pullRequest.id!!, otherUser.id!!)
                codeReviewService.removeReviewer(pullRequest.id!!, otherUser.id!!)

                // 참여/해제 두 이벤트가 서로 상쇄돼 타임라인에는 아무것도 남지 않아야 한다(legacy 동작 그대로)
                pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pullRequest).size shouldBe 0
                // 알림(NotificationEvent)도 legacy NotificationEvent.afterReviewed()가 oldValue를
                // opposite action으로 채워 add()(draft-time 병합)를 타므로, 정확히 원상복구된 A(DONE)->B(CANCEL)는
                // 상쇄되어 아무 것도 저장되지 않는다(P1-27, NotificationEventRecorder).
                notificationEventRepository.findAll().size shouldBe 0
            }

            it("같은 리뷰어가 30초 내에 참여/해제/참여를 반복하면 마지막 참여만 남아야 한다(P1-40)") {
                codeReviewService.addReviewer(pullRequest.id!!, otherUser.id!!)
                codeReviewService.removeReviewer(pullRequest.id!!, otherUser.id!!)
                codeReviewService.addReviewer(pullRequest.id!!, otherUser.id!!)

                val prEvents = pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pullRequest)
                prEvents.size shouldBe 1
                prEvents.first().newValue shouldBe "DONE"
            }

            it("2. 기존 스레드에 대댓글을 달면 동일 스레드 하위에 묶여야 한다") {
                val codeRange = CodeRange(
                    path = "src/main/kotlin/App.kt",
                    startSide = CodeRange.Side.B,
                    startLine = 10,
                    startColumn = 0,
                    endSide = CodeRange.Side.B,
                    endLine = 10,
                    endColumn = 0
                )

                val firstComment = codeReviewService.createReviewComment(
                    project = project,
                    pullRequest = pullRequest,
                    commitId = "1234567890abcdef",
                    contents = "첫번째 리뷰",
                    codeRange = codeRange,
                    threadId = null,
                    currentUser = user
                )

                val threadId = firstComment.thread?.id!!

                val secondComment = codeReviewService.createReviewComment(
                    project = project,
                    pullRequest = pullRequest,
                    commitId = "1234567890abcdef",
                    contents = "답변 드립니다.",
                    codeRange = null,
                    threadId = threadId,
                    currentUser = user
                )

                secondComment.id shouldNotBe null
                secondComment.thread?.id shouldBe threadId
                secondComment.thread?.reviewComments?.size shouldBe 2
            }

            it("3. 스레드의 상태를 OPEN에서 CLOSED로 전환할 수 있어야 한다") {
                val codeRange = CodeRange(
                    path = "src/main/kotlin/App.kt",
                    startSide = CodeRange.Side.B,
                    startLine = 10,
                    startColumn = 0,
                    endSide = CodeRange.Side.B,
                    endLine = 10,
                    endColumn = 0
                )

                val comment = codeReviewService.createReviewComment(
                    project = project,
                    pullRequest = pullRequest,
                    commitId = "1234567890abcdef",
                    contents = "첫번째 리뷰",
                    codeRange = codeRange,
                    threadId = null,
                    currentUser = user
                )

                val threadId = comment.thread?.id!!

                val updatedThread = codeReviewService.updateThreadState(
                    threadId = threadId,
                    state = CommentThread.ThreadState.CLOSED,
                    currentUser = user
                )

                updatedThread.state shouldBe CommentThread.ThreadState.CLOSED
            }

            // yona CommentThreadApp.java:66-70의 try/catch(알림 발행 실패해도 상태변경은 항상 커밋)
            // 대응 (P1-79).
            it("3-1. 상태변경 알림 발행이 실패해도 스레드 상태변경 자체는 커밋되어야 한다") {
                val codeRange = CodeRange(
                    path = "src/main/kotlin/App.kt",
                    startSide = CodeRange.Side.B,
                    startLine = 20,
                    startColumn = 0,
                    endSide = CodeRange.Side.B,
                    endLine = 20,
                    endColumn = 0
                )

                val comment = codeReviewService.createReviewComment(
                    project = project,
                    pullRequest = pullRequest,
                    commitId = "1234567890abcdef",
                    contents = "알림 실패 격리 테스트",
                    codeRange = codeRange,
                    threadId = null,
                    currentUser = user
                )
                val threadId = comment.thread?.id!!

                // 수신자가 없으면 notificationEventRecorder.record() 자체가 호출되지 않으므로
                // (receivers.isEmpty()면 조기 반환), 실제로 예외 경로를 타도록 감시자를 하나 둔다.
                watchRepository.save(Watch(user = otherUser, resourceType = ResourceType.PULL_REQUEST, resourceId = pullRequest.id.toString()))

                val throwingRecorder = mockk<NotificationEventRecorder>()
                every { throwingRecorder.record(any(), any()) } throws RuntimeException("메일 발송 인프라 장애 시뮬레이션")

                val isolatedService = CodeReviewServiceImpl(
                    commentThreadRepository, reviewCommentRepository, pullRequestRepository, repositoryService,
                    userRepository, throwingRecorder, commitCommentRepository, eventPublisher,
                    projectUserRepository, attachmentService, pullRequestCommitRepository, commentService,
                    watchService, pullRequestService, accessControl
                )

                // 예외가 이 메서드 밖으로 전파되지 않아야 한다(전파되면 @Transactional에 의해
                // 방금 커밋하려던 상태변경까지 롤백된다).
                val updatedThread = isolatedService.updateThreadState(
                    threadId = threadId,
                    state = CommentThread.ThreadState.CLOSED,
                    currentUser = user
                )

                updatedThread.state shouldBe CommentThread.ThreadState.CLOSED

                val persisted = commentThreadRepository.findById(threadId).orElseThrow()
                persisted.state shouldBe CommentThread.ThreadState.CLOSED
            }

             it("4. 마지막 댓글이 삭제되면 스레드도 함께 삭제되어야 한다") {
                val codeRange = CodeRange(
                    path = "src/main/kotlin/App.kt",
                    startSide = CodeRange.Side.B,
                    startLine = 10,
                    startColumn = 0,
                    endSide = CodeRange.Side.B,
                    endLine = 10,
                    endColumn = 0
                )

                val comment = codeReviewService.createReviewComment(
                    project = project,
                    pullRequest = pullRequest,
                    commitId = "1234567890abcdef",
                    contents = "첫번째 리뷰",
                    codeRange = codeRange,
                    threadId = null,
                    currentUser = user
                )

                val commentId = comment.id!!
                val threadId = comment.thread?.id!!

                codeReviewService.deleteReviewComment(commentId, user)

                reviewCommentRepository.findById(commentId).isPresent shouldBe false
                commentThreadRepository.findById(threadId).isPresent shouldBe false
            }

            it("[Test-13-1-6] 타인이 다른 유저의 리뷰 댓글 삭제 시 Permission denied 예외가 발생해야 한다") {
                val codeRange = CodeRange(
                    path = "src/main/kotlin/App.kt",
                    startSide = CodeRange.Side.B,
                    startLine = 10,
                    startColumn = 0,
                    endSide = CodeRange.Side.B,
                    endLine = 10,
                    endColumn = 0
                )

                val comment = codeReviewService.createReviewComment(
                    project = project,
                    pullRequest = pullRequest,
                    commitId = "1234567890abcdef",
                    contents = "첫번째 리뷰",
                    codeRange = codeRange,
                    threadId = null,
                    currentUser = user
                )

                val commentId = comment.id!!

                val exception = io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
                    codeReviewService.deleteReviewComment(commentId, otherUser)
                }
                exception.message shouldBe "Permission denied"
            }

            // yona AccessControl.java:205-301 isProjectResourceAllowed() 대응 (P1-116). 삭제 권한이
            // "작성자 또는 프로젝트 role==MANAGER"로만 좁게 구현돼 있었으나, yona는 사이트매니저/조직관리자도
            // 항상 우회할 수 있다 — 이 우회가 yuna에는 빠져 있어 사이트관리자조차 타인의 리뷰/커밋 댓글을
            // 지울 수 없는 과도한 제한이었다.
            it("사이트관리자는 작성자도 프로젝트 매니저도 아니어도 타인의 리뷰 댓글을 삭제할 수 있어야 한다") {
                val siteAdmin = userRepository.save(
                    User(loginId = "siteadmin", name = "사이트관리자", email = "siteadmin@yona.io", state = UserState.SITE_ADMIN)
                )

                val codeRange = CodeRange(
                    path = "src/main/kotlin/App.kt",
                    startSide = CodeRange.Side.B,
                    startLine = 10,
                    startColumn = 0,
                    endSide = CodeRange.Side.B,
                    endLine = 10,
                    endColumn = 0
                )

                val comment = codeReviewService.createReviewComment(
                    project = project,
                    pullRequest = pullRequest,
                    commitId = "1234567890abcdef",
                    contents = "삭제될 리뷰",
                    codeRange = codeRange,
                    threadId = null,
                    currentUser = user
                )
                val commentId = comment.id!!

                codeReviewService.deleteReviewComment(commentId, siteAdmin)

                reviewCommentRepository.findById(commentId).isPresent shouldBe false
            }

            it("사이트관리자는 작성자도 프로젝트 매니저도 아니어도 타인의 커밋 댓글을 삭제할 수 있어야 한다") {
                val siteAdmin = userRepository.save(
                    User(loginId = "siteadmin2", name = "사이트관리자2", email = "siteadmin2@yona.io", state = UserState.SITE_ADMIN)
                )

                val comment = codeReviewService.createCommitComment(
                    project = project,
                    commitId = "1234567890abcdef",
                    contents = "커밋에 대한 댓글",
                    path = null,
                    line = null,
                    side = null,
                    currentUser = user
                )
                val commentId = comment.id!!

                codeReviewService.deleteCommitComment(commentId, siteAdmin)

                commitCommentRepository.findById(commentId).isPresent shouldBe false
            }

            describe("isThreadOutdated (P1-20, yona CodeCommentThread.isOutdated() 대응)") {
                it("병합 시점과 코멘트 시점의 코드가 동일하면 outdated가 아니어야 한다") {
                    val outdatedProject = projectRepository.save(Project(name = "outdated-repo-1", owner = "owner-x", vcs = "GIT"))
                    repositoryService.getRepository(outdatedProject).create()
                    try {
                        val bareDir = repositoryService.getRepository(outdatedProject).getDirectory()
                        createTestCommit(bareDir, "master", "test.txt", "v1")
                        val c1 = repositoryService.getRepository(outdatedProject).getBranches()
                            .first { it.name == "refs/heads/master" }.headCommit.getId()
                        createTestCommit(bareDir, "master", "test.txt", "v2")
                        val c2 = repositoryService.getRepository(outdatedProject).getBranches()
                            .first { it.name == "refs/heads/master" }.headCommit.getId()

                        val pr = pullRequestRepository.save(
                            PullRequest(
                                title = "outdated 테스트 PR",
                                toProject = outdatedProject,
                                fromProject = outdatedProject,
                                toBranch = "master",
                                fromBranch = "feature",
                                contributor = user,
                                mergedCommitIdFrom = c1,
                                mergedCommitIdTo = c2
                            )
                        )
                        val thread = commentThreadRepository.save(
                            CodeCommentThread(
                                pullRequest = pr,
                                project = outdatedProject,
                                prevCommitId = c1,
                                commitId = c2,
                                codeRange = CodeRange(path = "test.txt", startLine = 1)
                            )
                        )

                        codeReviewService.isThreadOutdated(thread.id!!) shouldBe false
                    } finally {
                        try { repositoryService.getRepository(outdatedProject).delete() } catch (e: Exception) {}
                    }
                }

                it("병합 이후 같은 경로에 추가 커밋이 들어오면 outdated여야 한다") {
                    val outdatedProject = projectRepository.save(Project(name = "outdated-repo-2", owner = "owner-x", vcs = "GIT"))
                    repositoryService.getRepository(outdatedProject).create()
                    try {
                        val bareDir = repositoryService.getRepository(outdatedProject).getDirectory()
                        createTestCommit(bareDir, "master", "test.txt", "v1")
                        val c1 = repositoryService.getRepository(outdatedProject).getBranches()
                            .first { it.name == "refs/heads/master" }.headCommit.getId()
                        createTestCommit(bareDir, "master", "test.txt", "v2")
                        val c2 = repositoryService.getRepository(outdatedProject).getBranches()
                            .first { it.name == "refs/heads/master" }.headCommit.getId()

                        val pr = pullRequestRepository.save(
                            PullRequest(
                                title = "outdated 테스트 PR2",
                                toProject = outdatedProject,
                                fromProject = outdatedProject,
                                toBranch = "master",
                                fromBranch = "feature",
                                contributor = user,
                                mergedCommitIdFrom = c1,
                                mergedCommitIdTo = c2
                            )
                        )
                        val thread = commentThreadRepository.save(
                            CodeCommentThread(
                                pullRequest = pr,
                                project = outdatedProject,
                                prevCommitId = c1,
                                commitId = c2,
                                codeRange = CodeRange(path = "test.txt", startLine = 1)
                            )
                        )

                        // 병합 이후 test.txt가 v3로 다시 바뀌고, PR의 mergedCommitIdTo도 그 시점으로 갱신됐다고 가정
                        createTestCommit(bareDir, "master", "test.txt", "v3")
                        val c3 = repositoryService.getRepository(outdatedProject).getBranches()
                            .first { it.name == "refs/heads/master" }.headCommit.getId()
                        pr.mergedCommitIdTo = c3
                        pullRequestRepository.save(pr)

                        codeReviewService.isThreadOutdated(thread.id!!) shouldBe true
                    } finally {
                        try { repositoryService.getRepository(outdatedProject).delete() } catch (e: Exception) {}
                    }
                }

                it("커밋 댓글(prevCommitId 없음)은 PullRequestCommit에 없으면 outdated여야 한다") {
                    val thread = commentThreadRepository.save(
                        CodeCommentThread(
                            pullRequest = pullRequest,
                            project = project,
                            prevCommitId = "",
                            commitId = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef",
                            codeRange = CodeRange(path = "test.txt", startLine = 1)
                        )
                    )
                    pullRequest.mergedCommitIdFrom = "a"
                    pullRequest.mergedCommitIdTo = "b"
                    pullRequestRepository.save(pullRequest)

                    codeReviewService.isThreadOutdated(thread.id!!) shouldBe true
                }
            }
        }
    }
}
