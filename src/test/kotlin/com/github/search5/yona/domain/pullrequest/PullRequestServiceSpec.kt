package com.github.search5.yona.domain.pullrequest

import com.github.search5.yona.AbstractIntegrationTest
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.vcs.RepositoryService
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.issue.IssueService
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.event.PullRequestMergeEventListener
import com.github.search5.yona.domain.notification.NotificationEventRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.eclipse.jgit.api.Git
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.nio.file.Files
import java.time.Instant

@Transactional
class PullRequestServiceSpec @Autowired constructor(
    private val pullRequestService: PullRequestService,
    private val pullRequestRepository: PullRequestRepository,
    private val pullRequestCommitRepository: PullRequestCommitRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val repositoryService: RepositoryService,
    private val issueRepository: IssueRepository,
    private val issueService: IssueService,
    private val pullRequestMergeEventListener: PullRequestMergeEventListener,
    private val pullRequestEventRepository: PullRequestEventRepository,
    private val notificationEventRepository: NotificationEventRepository
) : AbstractIntegrationTest() {

    init {
        describe("PullRequestService 통합 테스트") {
            lateinit var contributor: User
            lateinit var receiver: User
            lateinit var toProject: Project
            lateinit var fromProject: Project

            beforeEach {
                pullRequestEventRepository.deleteAll()
                notificationEventRepository.deleteAll()
                pullRequestCommitRepository.deleteAll()
                pullRequestRepository.deleteAll()
                issueRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()

                val uniqueSuffix = System.currentTimeMillis().toString() + "-" + java.util.UUID.randomUUID().toString().take(6)

                contributor = userRepository.save(
                    User(loginId = "contrib-$uniqueSuffix", name = "기여자", email = "contrib-$uniqueSuffix@yona.io")
                )
                receiver = userRepository.save(
                    User(loginId = "receive-$uniqueSuffix", name = "수신자", email = "receive-$uniqueSuffix@yona.io")
                )

                toProject = projectRepository.save(
                    Project(name = "to-repo-$uniqueSuffix", owner = "owner-a", vcs = "GIT")
                )
                fromProject = projectRepository.save(
                    Project(name = "from-repo-$uniqueSuffix", owner = "owner-b", vcs = "GIT")
                )

                // 물리 Git 저장소 초기 생성
                repositoryService.getRepository(toProject).create()
                repositoryService.getRepository(fromProject).create()
            }

            afterEach {
                try {
                    repositoryService.getRepository(toProject).delete()
                } catch (e: Exception) {}
                try {
                    repositoryService.getRepository(fromProject).delete()
                } catch (e: Exception) {}
            }

            fun createCommit(
                bareRepoDir: File,
                branch: String,
                filePath: String,
                content: String,
                commitMsg: String
            ) {
                val tempWorkingDir = Files.createTempDirectory("yuna-test-work").toFile()
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
                            git.checkout()
                                .setCreateBranch(true)
                                .setName(branch)
                                .setStartPoint("origin/$branch")
                                .call()
                        } else {
                            val originMaster = git.repository.resolve("refs/remotes/origin/master")
                            if (originMaster != null) {
                                git.checkout()
                                    .setCreateBranch(true)
                                    .setName(branch)
                                    .setStartPoint("origin/master")
                                    .call()
                            }
                        }
                    } catch (e: Exception) {
                        // 빈 저장소인 경우 checkout 생략 (기본 master 브랜치 상태로 작업)
                    }

                    // 파일 작성
                    val file = File(tempWorkingDir, filePath)
                    file.parentFile.mkdirs()
                    file.writeText(content)

                    // 커밋 및 푸시
                    git.add().addFilepattern(filePath).call()
                    git.commit()
                        .setSign(false)
                        .setAuthor("tester", "tester@yona.io")
                        .setMessage(commitMsg)
                        .call()
                    
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
            fun syncRepository(srcBareDir: File, destBareDir: File, branch: String) {
                val tempWorkingDir = Files.createTempDirectory("yuna-test-sync").toFile()
                try {
                    val git = Git.init().setDirectory(tempWorkingDir).call()
                    val config = git.repository.config
                    config.setString("remote", "origin", "url", srcBareDir.absolutePath)
                    config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*")
                    config.save()
                    
                    git.fetch().setRemote("origin").call()
                    git.checkout()
                        .setCreateBranch(true)
                        .setName(branch)
                        .setStartPoint("origin/$branch")
                        .call()
                        
                    config.setString("remote", "dest", "url", destBareDir.absolutePath)
                    config.save()
                    git.push()
                        .setRemote("dest")
                        .setRefSpecs(org.eclipse.jgit.transport.RefSpec("HEAD:refs/heads/$branch"))
                        .setForce(true)
                        .call()
                        
                    git.repository.close()
                    git.close()
                } finally {
                    tempWorkingDir.deleteRecursively()
                }
            }

            it("1. 충돌이 없는 경우 - attemptMerge 및 merge 성공 검증") {
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                // 공통 조상 생성 (toProject의 master 브랜치에 test.txt 작성)
                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                // toProject master 커밋을 fromProject master 로 동기화하여 공통 조상 해시 일치
                syncRepository(toBareDir, fromBareDir, "master")

                // 1) Target 프로젝트 (toProject) master 브랜치에 변경 사항 추가
                createCommit(toBareDir, "master", "test2.txt", "target modification", "Update target")

                // 2) Source 프로젝트 (fromProject) feature 브랜치에 변경 사항 추가
                createCommit(fromBareDir, "feature", "test3.txt", "source modification", "Update source")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "자동 머지 테스트 PR",
                        body = "충돌이 없는 PR입니다.",
                        toProject = toProject,
                        fromProject = fromProject,
                        toBranch = "refs/heads/master",
                        fromBranch = "refs/heads/feature",
                        contributor = contributor,
                        receiver = receiver,
                        created = Instant.now(),
                        state = State.OPEN
                    )
                )

                // 3) attemptMerge 수행 - 충돌 미발생이어야 함
                val attemptResult = pullRequestService.attemptMerge(pr.id!!)
                attemptResult.conflicts() shouldBe false
                attemptResult.gitCommits.size shouldBe 1
                attemptResult.gitCommits.first().getShortMessage() shouldBe "Update source"

                val updatedPrAfterAttempt = pullRequestRepository.findById(pr.id!!).orElse(null)
                updatedPrAfterAttempt.isConflict shouldBe false

                // 4) merge 수행 - 머지 완료 및 커밋 영속화 확인
                val mergeResult = pullRequestService.merge(pr.id!!, receiver)
                mergeResult.conflicts() shouldBe false

                val mergedPr = pullRequestRepository.findById(pr.id!!).orElse(null)
                mergedPr.state shouldBe State.MERGED
                mergedPr.isConflict shouldBe false
                mergedPr.mergedCommitIdTo shouldNotBe null
                mergedPr.receiver?.id shouldBe receiver.id

                // PullRequestCommit 엔티티 저장 검증
                val commits = pullRequestCommitRepository.findByPullRequest(mergedPr)
                commits.size shouldBe 1
                commits.first().commitMessage.trim() shouldBe "Update source"
                commits.first().state shouldBe PullRequestCommit.State.CURRENT
            }

            it("2. 충돌이 발생하는 경우 - attemptMerge 및 merge 실패 검증") {
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                // 공통 조상 생성
                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                syncRepository(toBareDir, fromBareDir, "master")

                // 1) Target 프로젝트 (toProject) master 브랜치에 동일 파일(test.txt) 수정
                createCommit(toBareDir, "master", "test.txt", "hello common\ntarget edit", "Target conflict commit")

                // 2) Source 프로젝트 (fromProject) feature-conflict 브랜치에 동일 파일(test.txt) 충돌 수정
                createCommit(fromBareDir, "feature-conflict", "test.txt", "hello common\nsource edit", "Source conflict commit")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "충돌 머지 테스트 PR",
                        body = "충돌이 발생하는 PR입니다.",
                        toProject = toProject,
                        fromProject = fromProject,
                        toBranch = "refs/heads/master",
                        fromBranch = "refs/heads/feature-conflict",
                        contributor = contributor,
                        receiver = receiver,
                        created = Instant.now(),
                        state = State.OPEN
                    )
                )

                // 3) attemptMerge 수행 - 충돌 감지되어야 함
                val attemptResult = pullRequestService.attemptMerge(pr.id!!)
                attemptResult.conflicts() shouldBe true

                val updatedPrAfterAttempt = pullRequestRepository.findById(pr.id!!).orElse(null)
                updatedPrAfterAttempt.isConflict shouldBe true

                // 4) merge 수행 - 머지 실패(충돌 상태 보존 및 MERGED 미변경)
                val mergeResult = pullRequestService.merge(pr.id!!, receiver)
                mergeResult.conflicts() shouldBe true

                val mergedPr = pullRequestRepository.findById(pr.id!!).orElse(null)
                mergedPr.state shouldBe State.OPEN
                mergedPr.isConflict shouldBe true
            }

            it("3. 이슈 자동 닫기(Issue Auto-Close) 연동 검증") {
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                // 공통 조상 생성
                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                syncRepository(toBareDir, fromBareDir, "master")

                // 이슈 3개 생성 (이슈 번호는 1, 2, 3으로 순차 생성됨)
                val issue1 = issueService.createIssue(
                    Issue(title = "이슈 1", body = "첫 번째 이슈", project = toProject),
                    receiver, null, null, null
                )
                val issue2 = issueService.createIssue(
                    Issue(title = "이슈 2", body = "두 번째 이슈", project = toProject),
                    receiver, null, null, null
                )
                val issue3 = issueService.createIssue(
                    Issue(title = "이슈 3", body = "세 번째 이슈", project = toProject),
                    receiver, null, null, null
                )

                issue1.number shouldBe 1L
                issue2.number shouldBe 2L
                issue3.number shouldBe 3L

                // Target 프로젝트 (toProject) master 브랜치에 변경 사항 추가
                createCommit(toBareDir, "master", "test2.txt", "target modification", "Update target")

                // Source 프로젝트 (fromProject) feature 브랜치에 변경 사항 추가 (커밋 메시지에 Resolves #2 작성)
                createCommit(fromBareDir, "feature", "test3.txt", "source modification", "Update source\n\nResolves #2")

                // PR 본문에 Closes #1 작성하여 PullRequest 생성
                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "자동 머지 및 이슈 닫기 테스트 PR",
                        body = "이 PR은 closes #1 을 해결합니다.",
                        toProject = toProject,
                        fromProject = fromProject,
                        toBranch = "refs/heads/master",
                        fromBranch = "refs/heads/feature",
                        contributor = contributor,
                        receiver = receiver,
                        created = Instant.now(),
                        state = State.OPEN
                    )
                )

                // attemptMerge 수행
                val attemptResult = pullRequestService.attemptMerge(pr.id!!)
                attemptResult.conflicts() shouldBe false

                // merge 수행
                val mergeResult = pullRequestService.merge(pr.id!!, receiver)
                mergeResult.conflicts() shouldBe false

                // 트랜잭션 격리 및 비동기 우회를 위해 리스너의 이슈 자동 닫기 로직을 직접 동기 호출
                pullRequestMergeEventListener.closeReferredIssues(pr, receiver.loginId)

                val updatedIssue1 = issueRepository.findById(issue1.id!!).get()
                val updatedIssue2 = issueRepository.findById(issue2.id!!).get()
                val updatedIssue3 = issueRepository.findById(issue3.id!!).get()

                updatedIssue1.state shouldBe State.CLOSED
                updatedIssue2.state shouldBe State.CLOSED
                updatedIssue3.state shouldBe State.OPEN
            }

            it("4. 리뷰어 참여 및 해제 기능 테스트") {
                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "리뷰어 테스트 PR",
                        body = "리뷰어 추가 삭제 검증용 PR",
                        toProject = toProject,
                        fromProject = fromProject,
                        toBranch = "refs/heads/master",
                        fromBranch = "refs/heads/feature",
                        contributor = contributor,
                        receiver = receiver,
                        created = Instant.now(),
                        state = State.OPEN
                    )
                )

                // 1) 리뷰어 추가 검증
                pullRequestService.addReviewer(pr.id!!, receiver)
                
                val prAfterAdd = pullRequestRepository.findById(pr.id!!).get()
                prAfterAdd.reviewers.size shouldBe 1
                prAfterAdd.reviewers.first().id shouldBe receiver.id

                // 2) 리뷰어 해제 검증
                pullRequestService.removeReviewer(pr.id!!, receiver)

                val prAfterRemove = pullRequestRepository.findById(pr.id!!).get()
                prAfterRemove.reviewers.size shouldBe 0
            }

            it("5. 최소 리뷰어 수 미달 시 머지 실패 검증") {
                // 프로젝트 설정 변경: 리뷰어 수 제한 설정 활성화, 최소 리뷰어 수 1명 요구
                toProject.isUsingReviewerCount = true
                toProject.defaultReviewerCount = 1
                projectRepository.save(toProject)

                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                syncRepository(toBareDir, fromBareDir, "master")

                createCommit(toBareDir, "master", "test2.txt", "target modification", "Update target")
                createCommit(fromBareDir, "feature", "test3.txt", "source modification", "Update source")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "최소 리뷰어 미달 병합 제어 테스트 PR",
                        body = "최소 리뷰어가 부족할 때 병합 차단 검증",
                        toProject = toProject,
                        fromProject = fromProject,
                        toBranch = "refs/heads/master",
                        fromBranch = "refs/heads/feature",
                        contributor = contributor,
                        receiver = receiver,
                        created = Instant.now(),
                        state = State.OPEN
                    )
                )

                // attemptMerge는 병합 시도가 아니므로 통과해야 함
                val attemptResult = pullRequestService.attemptMerge(pr.id!!)
                attemptResult.conflicts() shouldBe false

                // 1) 리뷰어가 전혀 등록되지 않은 상태에서 merge 호출 시 LackingReviewerException 예외가 던져져야 함
                io.kotest.assertions.throwables.shouldThrow<LackingReviewerException> {
                    pullRequestService.merge(pr.id!!, receiver)
                }

                // 2) 리뷰어를 추가하여 기준을 만족시킨 뒤 merge 호출 시 성공해야 함
                pullRequestService.addReviewer(pr.id!!, receiver)

                val mergeResult = pullRequestService.merge(pr.id!!, receiver)
                mergeResult.conflicts() shouldBe false

                val mergedPr = pullRequestRepository.findById(pr.id!!).get()
                mergedPr.state shouldBe State.MERGED
            }

            it("6. 풀 리퀘스트 변경 사항(getDiff) 추출 기능 테스트") {
                val toBareDir = repositoryService.getRepository(toProject).getDirectory()
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()

                createCommit(toBareDir, "master", "test.txt", "hello common", "Initial commit")
                syncRepository(toBareDir, fromBareDir, "master")

                createCommit(toBareDir, "master", "test2.txt", "target modification", "Update target")
                createCommit(fromBareDir, "feature", "test3.txt", "source modification", "Update source")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "코드 비교 테스트 PR",
                        body = "코드 비교 검증용 PR",
                        toProject = toProject,
                        fromProject = fromProject,
                        toBranch = "refs/heads/master",
                        fromBranch = "refs/heads/feature",
                        contributor = contributor,
                        receiver = receiver,
                        created = Instant.now(),
                        state = State.OPEN
                    )
                )

                // attemptMerge를 해서 pr.lastCommitId 등을 세팅
                val attemptResult = pullRequestService.attemptMerge(pr.id!!)
                val savedPr = pullRequestRepository.findById(pr.id!!).get()

                // 1) 전체 Diff 가져오기 검증
                val diffs = pullRequestService.getDiff(savedPr)
                diffs.size shouldNotBe 0
                diffs.any { it.pathB == "test3.txt" } shouldBe true

                // 2) 특정 커밋 Diff 가져오기 검증
                val lastCommitId = savedPr.lastCommitId!!
                val specificDiffs = pullRequestService.getDiff(savedPr, lastCommitId)
                specificDiffs.size shouldNotBe 0
                specificDiffs.any { it.pathB == "test3.txt" } shouldBe true
            }

            it("병합된 PR의 원본 브랜치를 삭제하면 lastCommitId가 기록되고 브랜치가 사라져야 한다") {
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()
                createCommit(fromBareDir, "feature-to-delete", "delete-me.txt", "content", "브랜치 삭제 테스트용 커밋")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "브랜치 삭제 테스트 PR",
                        body = "...",
                        toProject = toProject,
                        fromProject = fromProject,
                        toBranch = "refs/heads/master",
                        fromBranch = "refs/heads/feature-to-delete",
                        contributor = contributor,
                        receiver = receiver,
                        created = Instant.now(),
                        state = State.MERGED
                    )
                )

                repositoryService.getRepository(fromProject).getBranches()
                    .any { it.name == "refs/heads/feature-to-delete" } shouldBe true

                val updated = pullRequestService.deleteFromBranch(pr.id!!)

                updated.lastCommitId shouldNotBe null
                repositoryService.getRepository(fromProject).getBranches()
                    .any { it.name == "refs/heads/feature-to-delete" } shouldBe false
            }

            it("병합되지 않은 PR의 브랜치는 삭제할 수 없어야 한다") {
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()
                createCommit(fromBareDir, "feature-open", "open.txt", "content", "미병합 PR 테스트용 커밋")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "미병합 PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature-open",
                        contributor = contributor, receiver = receiver,
                        created = Instant.now(), state = State.OPEN
                    )
                )

                io.kotest.assertions.throwables.shouldThrow<InvalidBranchOperationException> {
                    pullRequestService.deleteFromBranch(pr.id!!)
                }
            }

            it("삭제된 브랜치를 restoreFromBranch로 복원하면 동일한 커밋으로 다시 존재해야 한다") {
                val fromBareDir = repositoryService.getRepository(fromProject).getDirectory()
                createCommit(fromBareDir, "feature-to-restore", "restore-me.txt", "content", "브랜치 복원 테스트용 커밋")

                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "브랜치 복원 테스트 PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature-to-restore",
                        contributor = contributor, receiver = receiver,
                        created = Instant.now(), state = State.MERGED
                    )
                )

                val deleted = pullRequestService.deleteFromBranch(pr.id!!)
                repositoryService.getRepository(fromProject).getBranches()
                    .any { it.name == "refs/heads/feature-to-restore" } shouldBe false

                val savedAfterDelete = pullRequestRepository.findById(pr.id!!).get()
                pullRequestService.restoreFromBranch(savedAfterDelete.id!!)

                val restoredBranch = repositoryService.getRepository(fromProject).getBranches()
                    .firstOrNull { it.name == "refs/heads/feature-to-restore" }

                restoredBranch shouldNotBe null
                restoredBranch!!.headCommit.getId() shouldBe deleted.lastCommitId
            }

            it("PR을 생성하면 NotificationEvent(NEW_PULL_REQUEST)와 PullRequestEvent가 모두 생성되어야 한다(P1-39)") {
                val created = pullRequestService.createPullRequest(
                    title = "신규 PR 이벤트 테스트",
                    body = "본문 내용",
                    fromProjectId = fromProject.id!!,
                    toProjectId = toProject.id!!,
                    fromBranch = "refs/heads/master",
                    toBranch = "refs/heads/master",
                    contributor = contributor
                )

                val notiEvents = notificationEventRepository.findAll()
                notiEvents.size shouldBe 1
                notiEvents.first().eventType shouldBe com.github.search5.yona.domain.enumeration.EventType.NEW_PULL_REQUEST
                notiEvents.first().newValue shouldBe "본문 내용"

                val prEvents = pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(created)
                prEvents.size shouldBe 1
                prEvents.first().eventType shouldBe com.github.search5.yona.domain.enumeration.EventType.NEW_PULL_REQUEST
                prEvents.first().senderLoginId shouldBe contributor.loginId
            }

            it("PR 상태를 변경하면 NotificationEvent와 PullRequestEvent가 모두 생성되어야 한다") {
                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "상태 변경 테스트 PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature-state",
                        contributor = contributor, receiver = receiver,
                        created = Instant.now(), state = State.OPEN
                    )
                )

                val updated = pullRequestService.changeState(pr.id!!, State.CLOSED, contributor.loginId)

                updated.state shouldBe State.CLOSED

                val notiEvents = notificationEventRepository.findAll()
                notiEvents.size shouldBe 1
                notiEvents.first().eventType shouldBe com.github.search5.yona.domain.enumeration.EventType.PULL_REQUEST_STATE_CHANGED
                notiEvents.first().oldValue shouldBe State.OPEN.toString()
                notiEvents.first().newValue shouldBe State.CLOSED.toString()

                val prEvents = pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(updated)
                prEvents.size shouldBe 1
                prEvents.first().eventType shouldBe com.github.search5.yona.domain.enumeration.EventType.PULL_REQUEST_STATE_CHANGED
                prEvents.first().senderLoginId shouldBe contributor.loginId
            }

            it("동일한 상태로 변경을 시도하면 이벤트를 생성하지 않아야 한다") {
                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "동일 상태 테스트 PR", body = "...",
                        toProject = toProject, fromProject = fromProject,
                        toBranch = "refs/heads/master", fromBranch = "refs/heads/feature-same",
                        contributor = contributor, receiver = receiver,
                        created = Instant.now(), state = State.OPEN
                    )
                )

                pullRequestService.changeState(pr.id!!, State.OPEN, contributor.loginId)

                notificationEventRepository.findAll().size shouldBe 0
                pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pr).size shouldBe 0
            }
        }
    }
}
