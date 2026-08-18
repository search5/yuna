package com.github.search5.yona.domain.pullrequest

import com.github.search5.yona.AbstractIntegrationTest
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.support.CodeRange
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

@Transactional
class CodeReviewServiceSpec @Autowired constructor(
    private val codeReviewService: CodeReviewService,
    private val commentThreadRepository: CommentThreadRepository,
    private val reviewCommentRepository: ReviewCommentRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val pullRequestRepository: PullRequestRepository
) : AbstractIntegrationTest() {

    init {
        describe("CodeReviewService 통합 테스트") {
            lateinit var project: Project
            lateinit var user: User
            lateinit var pullRequest: PullRequest

            lateinit var otherUser: User

            beforeEach {
                reviewCommentRepository.deleteAll()
                commentThreadRepository.deleteAll()
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
                    Project(name = "test-repo", owner = "owner-x", vcs = "GIT")
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
        }
    }
}
