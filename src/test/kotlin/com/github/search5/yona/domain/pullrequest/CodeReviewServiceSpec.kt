package com.github.search5.yona.domain.pullrequest

import com.github.search5.yona.AbstractIntegrationTest
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.support.CodeRange
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.vcs.RepositoryService
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.eclipse.jgit.api.Git
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.nio.file.Files

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
    private val notificationEventRepository: com.github.search5.yona.domain.notification.NotificationEventRepository
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
                pullRequestEventRepository.deleteAll()
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

            it("리뷰어를 추가/해제하면 NotificationEvent와 PullRequestEvent가 모두 생성되어야 한다(P1-39)") {
                codeReviewService.addReviewer(pullRequest.id!!, otherUser.id!!)

                val prEventsAfterAdd = pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pullRequest)
                prEventsAfterAdd.size shouldBe 1
                prEventsAfterAdd.first().eventType shouldBe com.github.search5.yona.domain.enumeration.EventType.PULL_REQUEST_REVIEW_STATE_CHANGED
                prEventsAfterAdd.first().newValue shouldBe "DONE"
                prEventsAfterAdd.first().senderLoginId shouldBe otherUser.loginId

                notificationEventRepository.findAll().size shouldBe 1

                codeReviewService.removeReviewer(pullRequest.id!!, otherUser.id!!)

                val prEventsAfterRemove = pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pullRequest)
                prEventsAfterRemove.size shouldBe 2
                prEventsAfterRemove.last().newValue shouldBe "CANCEL"

                notificationEventRepository.findAll().size shouldBe 2
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

            describe("isThreadOutdated (P1-20, yona CodeCommentThread.isOutdated() 대응)") {
                fun createCommit(bareRepoDir: File, branch: String, filePath: String, content: String) {
                    val tempWorkingDir = Files.createTempDirectory("yuna-outdated-test").toFile()
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
                        git.commit().setSign(false).setAuthor("tester", "tester@yona.io").setMessage("commit").call()
                        git.push()
                            .setRemote("origin")
                            .setRefSpecs(org.eclipse.jgit.transport.RefSpec("HEAD:refs/heads/$branch"))
                            .setForce(true)
                            .call()

                        git.repository.close()
                        git.close()
                    } finally {
                        tempWorkingDir.deleteRecursively()
                    }
                }

                it("병합 시점과 코멘트 시점의 코드가 동일하면 outdated가 아니어야 한다") {
                    val outdatedProject = projectRepository.save(Project(name = "outdated-repo-1", owner = "owner-x", vcs = "GIT"))
                    repositoryService.getRepository(outdatedProject).create()
                    try {
                        val bareDir = repositoryService.getRepository(outdatedProject).getDirectory()
                        createCommit(bareDir, "master", "test.txt", "v1")
                        val c1 = repositoryService.getRepository(outdatedProject).getBranches()
                            .first { it.name == "refs/heads/master" }.headCommit.getId()
                        createCommit(bareDir, "master", "test.txt", "v2")
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
                        createCommit(bareDir, "master", "test.txt", "v1")
                        val c1 = repositoryService.getRepository(outdatedProject).getBranches()
                            .first { it.name == "refs/heads/master" }.headCommit.getId()
                        createCommit(bareDir, "master", "test.txt", "v2")
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
                        createCommit(bareDir, "master", "test.txt", "v3")
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
