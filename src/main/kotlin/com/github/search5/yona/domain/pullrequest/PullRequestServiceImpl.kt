package com.github.search5.yona.domain.pullrequest

import com.github.search5.yona.domain.comment.CommentService
import com.github.search5.yona.domain.vcs.FileDiff
import com.github.search5.yona.domain.vcs.GitCommit
import com.github.search5.yona.domain.vcs.RepositoryService
import com.github.search5.yona.domain.vcs.GitRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.event.PullRequestMergeEvent
import com.github.search5.yona.domain.notification.NotificationEvent
import com.github.search5.yona.domain.notification.NotificationEventRecorder
import com.github.search5.yona.domain.watch.WatchService
import com.github.search5.yona.domain.issue.IssueEvent
import com.github.search5.yona.domain.issue.IssueEventRepository
import com.github.search5.yona.domain.issue.IssueReferenceParser
import com.github.search5.yona.domain.issue.IssueRepository
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.*
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.merge.ThreeWayMerger
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.RefSpec
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.IOException
import java.time.Instant

@Service
class PullRequestServiceImpl(
    private val pullRequestRepository: PullRequestRepository,
    private val pullRequestCommitRepository: PullRequestCommitRepository,
    private val repositoryService: RepositoryService,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val notificationEventRecorder: NotificationEventRecorder,
    private val pullRequestEventRepository: PullRequestEventRepository,
    private val watchService: WatchService,
    private val issueRepository: IssueRepository,
    private val issueEventRepository: IssueEventRepository,
    private val commentService: CommentService,
    @Value("\${yuna.site-name:Yona}")
    private val siteName: String
) : PullRequestService {

    @Transactional
    override fun attemptMerge(pullRequestId: Long): PullRequestMergeResult {
        val pullRequest = pullRequestRepository.findById(pullRequestId)
            .orElseThrow { IllegalArgumentException("PullRequest with ID $pullRequestId not found") }

        val playRepo = repositoryService.getRepository(pullRequest.toProject)
        val gitDir = playRepo.getDirectory()

        return FileRepositoryBuilder().setGitDir(gitDir).build().use { repo ->
            val fromGitDir = repositoryService.getRepository(pullRequest.fromProject).getDirectory().absolutePath
            val tempBranch = "refs/yobi/pull-check/${pullRequest.fromProject.owner}/${pullRequest.fromProject.name}/${pullRequest.fromBranch}"

            // 소스 리포지토리로부터 임시 브랜치로 fetch
            Git(repo).fetch()
                .setRemote(fromGitDir)
                .setRefSpecs(
                    RefSpec()
                        .setSource(pullRequest.fromBranch)
                        .setDestination(tempBranch)
                        .setForceUpdate(true)
                )
                .call()

            val merger = MergeStrategy.RECURSIVE.newMerger(repo, true) as ThreeWayMerger
            val leftParent = repo.resolve(pullRequest.toBranch)
                ?: throw IllegalArgumentException("Target branch '${pullRequest.toBranch}' not found")
            val rightParent = repo.resolve(tempBranch)
                ?: throw IllegalArgumentException("Fetched source branch not found")

            val success = merger.merge(leftParent, rightParent)
            val result = PullRequestMergeResult(pullRequest = pullRequest)

            if (success) {
                result.setResolvedStateOfPullRequest()
            } else {
                result.setConflictStateOfPullRequest()
            }

            result.gitCommits = diffCommits(repo, leftParent, rightParent)
            pullRequest.lastCommitId = rightParent.name
            pullRequestRepository.save(pullRequest)

            // 임시 브랜치 삭제
            val refUpdate = repo.updateRef(tempBranch)
            refUpdate.isForceUpdate = true
            refUpdate.delete()

            result
        }
    }

    // yona PullRequestApp.mergeResult()/PullRequest.attemptMerge()/getPullRequestMergeResult() 대응
    // (#178, TASK-0257). attemptMerge(pullRequestId)와 동일한 JGit 흐름(임시 ref로 fetch → 3-way
    // merge 시도 → 커밋 diff 계산 → 임시 ref 삭제)이지만, 저장된 PullRequest 엔티티를 조회/저장하지
    // 않고 임의의 fromProject/toProject/fromBranch/toBranch만으로 동작한다(legacy도 이 프리뷰 액션에서
    // 만드는 PullRequest 객체를 저장하지 않는다 — PullRequest.createNewPullRequest()는 순수 in-memory
    // 객체 생성일 뿐이다).
    @Transactional(readOnly = true)
    override fun previewMerge(fromProject: Project, toProject: Project, fromBranch: String, toBranch: String): MergePreviewResult {
        val playRepo = repositoryService.getRepository(toProject)
        val gitDir = playRepo.getDirectory()

        return FileRepositoryBuilder().setGitDir(gitDir).build().use { repo ->
            val fromGitDir = repositoryService.getRepository(fromProject).getDirectory().absolutePath
            val tempBranch = "refs/yobi/pull-check/${fromProject.owner}/${fromProject.name}/$fromBranch"

            // 소스 리포지토리로부터 임시 브랜치로 fetch (legacy fetchSourceTemporarilly() 대응)
            Git(repo).fetch()
                .setRemote(fromGitDir)
                .setRefSpecs(
                    RefSpec()
                        .setSource(fromBranch)
                        .setDestination(tempBranch)
                        .setForceUpdate(true)
                )
                .call()

            val merger = MergeStrategy.RECURSIVE.newMerger(repo, true) as ThreeWayMerger
            val leftParent = repo.resolve(toBranch)
                ?: throw IllegalArgumentException("Target branch '$toBranch' not found")
            val rightParent = repo.resolve(tempBranch)
                ?: throw IllegalArgumentException("Source branch '$fromBranch' not found")

            val success = merger.merge(leftParent, rightParent)
            val commits = diffCommits(repo, leftParent, rightParent)
            val (suggestedTitle, suggestedBody) = suggestTitleAndBody(commits)

            // 임시 브랜치 삭제 (legacy attemptMerge()가 fetchSourceTemporarilly()로 만든 임시 ref를
            // 병합 성공/실패와 무관하게 매번 정리하는 것과 동일)
            val refUpdate = repo.updateRef(tempBranch)
            refUpdate.isForceUpdate = true
            refUpdate.delete()

            MergePreviewResult(
                commits = commits,
                conflict = !success,
                suggestedTitle = suggestedTitle,
                suggestedBody = suggestedBody
            )
        }
    }

    // yona PullRequest.suggestTitleAndBodyFromDiffCommit() 대응 (#178, TASK-0257). 커밋이 1개면 첫
    // 줄을 title로, 나머지 줄들을 body로 쓰고, 2개 이상이면 title 없이 각 커밋의 첫 줄만 모아 body로
    // 쓴다(legacy와 동일하게 title 키 자체가 없음 = null).
    private fun suggestTitleAndBody(commits: List<GitCommit>): Pair<String?, String?> {
        if (commits.isEmpty()) {
            return null to null
        }
        if (commits.size == 1) {
            val messages = (commits[0].getMessage() ?: "").split("\n")
            return if (messages.size > 1) {
                messages[0] to messages.drop(1).joinToString("\n").trim()
            } else {
                messages[0] to ""
            }
        }
        val firstMessages = commits.map { (it.getMessage() ?: "").split("\n").firstOrNull() ?: "" }
        return null to firstMessages.joinToString("\n")
    }

    // yona actors/PullRequestActor.processPullRequestMerging() 대응 (P1-52). attemptMerge()는
    // PullRequestViewController가 페이지 렌더링마다 호출하는 부수효과 없는 미리보기(legacy
    // PullRequest.attemptMerge())라 여기서 부수효과를 추가하면 조회할 때마다 알림/이벤트가 잘못
    // 발생한다 — legacy도 updateMerge()(액터 전용, 부수효과 있음)와 attemptMerge()(뷰 전용, 부수효과
    // 없음)를 분리해뒀으므로 그 경계를 그대로 따라 별도 메서드로 둔다.
    @Transactional
    override fun processMergeCheck(pullRequestId: Long, sender: User, isNewPullRequest: Boolean): PullRequestMergeResult {
        val before = pullRequestRepository.findById(pullRequestId).orElse(null)
        val beforeMergedCommitIdTo = before?.mergedCommitIdTo
        // yona PullRequestActor.processPullRequestMerging()의 "boolean wasConflict = pullRequest.isConflict"
        // 대응 (P1-71) — updateMerge() 호출 전(재검사 이전) 상태를 미리 캡처해둔다.
        val wasConflict = before?.isConflict ?: false

        val result = updateMerge(pullRequestId)
        val pullRequest = pullRequestRepository.findById(pullRequestId)
            .orElseThrow { IllegalArgumentException("PullRequest with ID $pullRequestId not found") }

        if (result.hasDiffCommits()) {
            val newCommits = updatePullRequestCommits(pullRequest, result.gitCommits)
            result.newCommits = newCommits

            if (newCommits.isNotEmpty()) {
                if (!isNewPullRequest) {
                    notifyCommitChanged(pullRequest, sender)
                }
                recordCommitChangedEvent(pullRequest, sender, newCommits, beforeMergedCommitIdTo)

                // yona PullRequest.clearReviewers() 대응 — 새 커밋이 들어왔으니 기존 리뷰를 무효화하고
                // 재검토를 강제한다.
                pullRequest.reviewers.clear()
                pullRequestRepository.save(pullRequest)
            }
        } else if (pullRequest.state != State.MERGED) {
            // yona의 hasDiffCommits()==false 분기(diff가 사라짐 = 이미 다른 경로로 모든 변경이 대상
            // 브랜치에 반영됨) 대응 — 실제 병합 동작 없이 상태만 MERGED로 자동 전환한다.
            pullRequest.isConflict = false
            pullRequest.receiver = sender
            pullRequestRepository.save(pullRequest)
            changeState(pullRequestId, State.MERGED, sender.loginId)
        }

        // yona PullRequestActor.processPullRequestMerging()의 conflict 상태 전환 추적 대응 (P1-71).
        // diff/커밋 처리와 완전히 별개로, 재검사 결과 conflict 여부 자체가 바뀌면(충돌 없다가 발생/
        // 충돌이 해소됨) 알림+타임라인을 남긴다. eventType은 이름과 달리 "머지 완료"가 아니라 이
        // conflict 전환 전용이다(실제 머지 완료는 위 changeState(..., MERGED, ...)의
        // PULL_REQUEST_STATE_CHANGED가 담당).
        if (!wasConflict && result.conflicts()) {
            notifyMergeConflictChanged(pullRequest, sender, State.CONFLICT)
        }
        if (wasConflict && !result.conflicts()) {
            notifyMergeConflictChanged(pullRequest, sender, State.RESOLVED)
        }

        return result
    }

    // yona NotificationEvent.afterMerge(sender, pullRequest, state)/PullRequestEvent.addMergeEvent() 대응 (P1-71).
    private fun notifyMergeConflictChanged(pullRequest: PullRequest, sender: User, state: State) {
        val notificationEvent = NotificationEvent(
            title = formatReplyTitle(pullRequest),
            senderId = sender.id,
            created = Instant.now(),
            resourceType = ResourceType.PULL_REQUEST,
            resourceId = pullRequest.id.toString(),
            eventType = EventType.PULL_REQUEST_MERGED,
            newValue = state.state()
        )
        val receivers = watchService.findActualWatchers(
            baseWatchers = setOf(pullRequest.contributor),
            resourceType = ResourceType.PULL_REQUEST,
            resourceId = pullRequest.id.toString(),
            projectId = pullRequest.toProject.id,
            eventType = notificationEvent.eventType
        ).toMutableSet()
        receivers.removeIf { it.id == sender.id }
        notificationEvent.receivers = receivers
        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }

        recordPullRequestEvent(pullRequest, EventType.PULL_REQUEST_MERGED, sender.loginId, null, state.state())
    }

    // yona NotificationEvent.afterPullRequestCommitChanged() 대응.
    private fun notifyCommitChanged(pullRequest: PullRequest, sender: User) {
        val title = "[${pullRequest.toProject.name}] PR #${pullRequest.number} 새 커밋이 추가되었습니다"
        val notificationEvent = NotificationEvent(
            title = title,
            senderId = sender.id,
            created = Instant.now(),
            resourceType = ResourceType.PULL_REQUEST,
            resourceId = pullRequest.id.toString(),
            eventType = EventType.PULL_REQUEST_COMMIT_CHANGED,
            newValue = buildCommitChangedMessage(pullRequest)
        )
        val receivers = watchService.findActualWatchers(
            baseWatchers = emptySet(),
            resourceType = ResourceType.PULL_REQUEST,
            resourceId = pullRequest.id.toString(),
            projectId = pullRequest.toProject.id,
            eventType = notificationEvent.eventType
        ).toMutableSet()
        receivers.removeIf { it.id == sender.id }
        notificationEvent.receivers = receivers
        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }
    }

    // yona NotificationEvent.newPullRequestCommitChangedMessage() 대응 — 현재 CURRENT 상태인 커밋을
    // 최신순으로 나열한다.
    private fun buildCommitChangedMessage(pullRequest: PullRequest): String {
        val commits = pullRequestCommitRepository.findByPullRequestAndState(pullRequest, PullRequestCommit.State.CURRENT)
            .sortedByDescending { it.authorDate }
        val builder = StringBuilder("### 현재 커밋\n")
        for (commit in commits) {
            builder.append(commit.commitShortId).append(" ").append(commit.getCommitShortMessage()).append("\n")
        }
        return builder.toString()
    }

    // yona PullRequestEvent.addCommitEvents() 대응. legacy가 add()(draft-time 병합/취소, P1-40)를
    // 거치지 않고 항상 그대로 저장하는 유일한 PullRequestEvent 생성 지점이라 recordPullRequestEvent()
    // (recordWithDraftMerge 경유)를 쓰지 않고 직접 저장한다.
    private fun recordCommitChangedEvent(
        pullRequest: PullRequest,
        sender: User,
        newCommits: List<PullRequestCommit>,
        beforeMergedCommitIdTo: String?
    ) {
        // yona PullRequestActor.getCommitEventOldValue(oldMergeCommitId, pullRequest.mergedCommitIdTo)
        // 대응 — 이전 mergedCommitIdTo가 없으면(최초 재검사) oldValue도 null, 있으면 "이전,새" 쌍.
        val oldValue = beforeMergedCommitIdTo?.let { "$it,${pullRequest.mergedCommitIdTo}" }
        val newValue = newCommits.joinToString(",") { it.id.toString() }
        pullRequestEventRepository.save(
            PullRequestEvent(
                pullRequest = pullRequest,
                senderLoginId = sender.loginId,
                eventType = EventType.PULL_REQUEST_COMMIT_CHANGED,
                oldValue = oldValue,
                newValue = newValue,
                created = Instant.now()
            )
        )
    }

    @Transactional
    override fun merge(pullRequestId: Long, updater: User): PullRequestMergeResult {
        val pullRequest = pullRequestRepository.findById(pullRequestId)
            .orElseThrow { IllegalArgumentException("PullRequest with ID $pullRequestId not found") }

        // 최소 리뷰어 검증 조건 체크
        val project = pullRequest.toProject
        if (project.isUsingReviewerCount) {
            val currentReviewersCount = pullRequest.reviewers.size
            if (currentReviewersCount < project.defaultReviewerCount) {
                throw LackingReviewerException(
                    "리뷰어 수가 부족하여 머지할 수 없습니다. " +
                    "(필요: ${project.defaultReviewerCount}명, 현재: ${currentReviewersCount}명)"
                )
            }
        }

        val playRepo = repositoryService.getRepository(pullRequest.toProject)
        val gitDir = playRepo.getDirectory()

        return FileRepositoryBuilder().setGitDir(gitDir).build().use { repo ->
            val fromGitDir = repositoryService.getRepository(pullRequest.fromProject).getDirectory().absolutePath
            val fetchSourceRef = "refs/yobi/pull/${pullRequest.id}/head"

            // 공식 fetch source branch로 fetch
            Git(repo).fetch()
                .setRemote(fromGitDir)
                .setRefSpecs(
                    RefSpec()
                        .setSource(pullRequest.fromBranch)
                        .setDestination(fetchSourceRef)
                        .setForceUpdate(true)
                )
                .call()

            val merger = MergeStrategy.RECURSIVE.newMerger(repo, true) as ThreeWayMerger
            val leftParent = repo.resolve(pullRequest.toBranch)
                ?: throw IllegalArgumentException("Target branch '${pullRequest.toBranch}' not found")
            val rightParent = repo.resolve(fetchSourceRef)
                ?: throw IllegalArgumentException("Source head ref not found")

            val success = merger.merge(leftParent, rightParent)
            val result = PullRequestMergeResult(pullRequest = pullRequest)

            if (!success) {
                result.setConflictStateOfPullRequest()
                result.gitCommits = diffCommits(repo, leftParent, rightParent)
                pullRequestRepository.save(pullRequest)
                return@use result
            }

            // 머지 성공: 머지 커밋 생성
            val whoMerges = PersonIdent(updater.name, updater.email ?: "yuna@yuna.io")
            val diff = diffCommits(repo, leftParent, rightParent)
            val mergeCommitId = createMergeCommitAndUpdateRef(repo, pullRequest, leftParent, rightParent, merger, whoMerges, diff)

            // DB 업데이트
            result.gitCommits = diff
            result.setMergedStateOfPullRequest(updater)
            pullRequest.mergedCommitIdFrom = leftParent.name
            pullRequest.mergedCommitIdTo = mergeCommitId.name
            pullRequest.lastCommitId = rightParent.name
            pullRequest.received = Instant.now()
            pullRequest.isMerging = false

            pullRequestRepository.save(pullRequest)
            updatePullRequestCommits(pullRequest, diff)

            // 비동기 이벤트 발행
            eventPublisher.publishEvent(
                PullRequestMergeEvent(
                    pullRequestId = pullRequest.id!!,
                    sender = updater,
                    isNewPullRequest = false
                )
            )

            result
        }
    }

    // yona PullRequest.Merger.Success.createCommit(PersonIdent)/MergeRefUpdate.updateRef() 대응.
    // merge()(실제 병합)와 updateMerge()(재검사 미리보기, P1-53) 둘 다 "머지 커밋을 만들어
    // refs/yobi/pull/{id}/merged를 갱신"하는 동일한 절차를 쓰므로 공용 헬퍼로 추출했다.
    private fun createMergeCommitAndUpdateRef(
        repo: Repository,
        pullRequest: PullRequest,
        leftParent: ObjectId,
        rightParent: ObjectId,
        merger: ThreeWayMerger,
        whoMerges: PersonIdent,
        diff: List<GitCommit>
    ): ObjectId {
        val reusableTreeId = getMergedTreeIfReusable(repo, leftParent, rightParent, pullRequest)
        val mergeCommit = CommitBuilder().apply {
            setTreeId(reusableTreeId ?: merger.resultTreeId)
            setParentIds(leftParent, rightParent)
            setAuthor(whoMerges)
            setCommitter(whoMerges)
            setMessage(makeMergeCommitMessage(pullRequest, diff))
        }

        val inserter = repo.newObjectInserter()
        val mergeCommitId = inserter.insert(mergeCommit)
        inserter.flush()
        inserter.close()

        val mergedRef = "refs/yobi/pull/${pullRequest.id}/merged"
        val refUpdate = repo.updateRef(mergedRef)
        refUpdate.setNewObjectId(mergeCommitId)
        refUpdate.isForceUpdate = true
        refUpdate.refLogIdent = whoMerges
        refUpdate.setRefLogMessage("merged", true)
        val rc = refUpdate.update()
        if (rc != RefUpdate.Result.NEW && rc != RefUpdate.Result.FAST_FORWARD && rc != RefUpdate.Result.FORCED) {
            throw IOException("Ref update failed for $mergedRef: $rc")
        }

        return mergeCommitId
    }

    // yona PullRequest.updateMerge() 대응 (P1-53). attemptMerge()(뷰 전용, 임시 브랜치로 fetch 후
    // 삭제, 부수효과 없음)와 달리 소스를 영구 ref(refs/yobi/pull/{id}/head)로 fetch하고, 충돌이 없으면
    // 실제 "미리보기 병합 커밋"을 만들어 refs/yobi/pull/{id}/merged를 갱신한 뒤 그 커밋의 부모/자신
    // 해시를 mergedCommitIdFrom/mergedCommitIdTo에 기록한다. processMergeCheck() 전용이며,
    // PullRequestViewController가 페이지 렌더링마다 호출하는 attemptMerge()는 이 메서드를 거치지 않는다
    // (부수효과 경계는 P1-52에서 이미 확립). yona가 이 미리보기 커밋의 작성자로 사이트 시스템 계정
    // (Config.getSiteName()/getSystemEmailAddress())을 쓰는 것과 동일하게, 실제 push한 sender가 아니라
    // 사이트 이름으로 커밋한다.
    private fun updateMerge(pullRequestId: Long): PullRequestMergeResult {
        val pullRequest = pullRequestRepository.findById(pullRequestId)
            .orElseThrow { IllegalArgumentException("PullRequest with ID $pullRequestId not found") }

        val playRepo = repositoryService.getRepository(pullRequest.toProject)
        val gitDir = playRepo.getDirectory()

        return FileRepositoryBuilder().setGitDir(gitDir).build().use { repo ->
            val fromGitDir = repositoryService.getRepository(pullRequest.fromProject).getDirectory().absolutePath
            val fetchedSourceRef = "refs/yobi/pull/${pullRequest.id}/head"

            Git(repo).fetch()
                .setRemote(fromGitDir)
                .setRefSpecs(
                    RefSpec()
                        .setSource(pullRequest.fromBranch)
                        .setDestination(fetchedSourceRef)
                        .setForceUpdate(true)
                )
                .call()

            val merger = MergeStrategy.RECURSIVE.newMerger(repo, true) as ThreeWayMerger
            val leftParent = repo.resolve(pullRequest.toBranch)
                ?: throw IllegalArgumentException("Target branch '${pullRequest.toBranch}' not found")
            val rightParent = repo.resolve(fetchedSourceRef)
                ?: throw IllegalArgumentException("Fetched source branch not found")

            val success = merger.merge(leftParent, rightParent)
            val result = PullRequestMergeResult(pullRequest = pullRequest)
            val diff = diffCommits(repo, leftParent, rightParent)

            if (success) {
                val whoMerges = PersonIdent(siteName, "yuna@yuna.io")
                val mergeCommitId = createMergeCommitAndUpdateRef(repo, pullRequest, leftParent, rightParent, merger, whoMerges, diff)
                result.setResolvedStateOfPullRequest()
                pullRequest.mergedCommitIdFrom = leftParent.name
                pullRequest.mergedCommitIdTo = mergeCommitId.name
            } else {
                result.setConflictStateOfPullRequest()
            }

            result.gitCommits = diff
            pullRequest.lastCommitId = rightParent.name
            pullRequestRepository.save(pullRequest)

            result
        }
    }

    private fun diffCommits(repo: Repository, from: ObjectId, to: ObjectId): List<GitCommit> {
        val git = Git(repo)
        val commits = git.log().addRange(from, to).call().toList()
        val userResolver: (String?, String?) -> User? = { _, email ->
            if (email != null) userRepository.findByEmail(email).orElse(null) else null
        }
        return commits.map { GitCommit(it, userResolver) }
    }

    private fun getMergedTreeIfReusable(
        repo: Repository,
        leftParent: ObjectId,
        rightParent: ObjectId,
        pullRequest: PullRequest
    ): ObjectId? {
        val refName = "refs/yobi/pull/${pullRequest.id}/merged"
        var commit: RevCommit? = null
        try {
            val ref = repo.findRef(refName)
            if (ref != null && ref.objectId != null) {
                commit = RevWalk(repo).parseCommit(ref.objectId)
            }
        } catch (e: Exception) {
            // Ignore log
        }
        if (commit != null && commit.parentCount == 2 &&
            commit.getParent(0) == leftParent &&
            commit.getParent(1) == rightParent
        ) {
            return commit.tree.toObjectId()
        }
        return null
    }

    // yona PullRequestMergeResult.saveCommits()/findNewCommits()/updatePriorCommits() 대응.
    // 반환값(새로 저장된 커밋)은 P1-52의 processMergeCheck()가 PullRequestEvent/알림 생성에 사용한다.
    private fun updatePullRequestCommits(pullRequest: PullRequest, gitCommits: List<GitCommit>): List<PullRequestCommit> {
        val priorCommits = pullRequestCommitRepository.findByPullRequestAndState(
            pullRequest, PullRequestCommit.State.CURRENT
        )
        val priorCommitIds = priorCommits.map { it.commitId }.toSet()

        val newCommits = mutableListOf<PullRequestCommit>()
        for (commit in gitCommits) {
            val commitId = commit.getId()
            if (!priorCommitIds.contains(commitId)) {
                newCommits.add(PullRequestCommit.bindPullRequestCommit(commit, pullRequest))
            }
        }
        val savedNewCommits = pullRequestCommitRepository.saveAll(newCommits)

        val gitCommitIds = gitCommits.map { it.getId() }.toSet()
        val updatedCommits = mutableListOf<PullRequestCommit>()
        for (priorCommit in priorCommits) {
            if (!gitCommitIds.contains(priorCommit.commitId)) {
                priorCommit.state = PullRequestCommit.State.PRIOR
                updatedCommits.add(priorCommit)
            }
        }
        pullRequestCommitRepository.saveAll(updatedCommits)

        return savedNewCommits
    }

    private fun makeMergeCommitMessage(pullRequest: PullRequest, commits: List<GitCommit>): String {
        val builder = StringBuilder()
        val shortenedFrom = Repository.shortenRefName(pullRequest.fromBranch)
        builder.append("Merge branch '$shortenedFrom'")

        if (pullRequest.fromProject != pullRequest.toProject) {
            builder.append(" of ${pullRequest.fromProject.owner}/${pullRequest.fromProject.name}")
        }

        val shortenedTo = Repository.shortenRefName(pullRequest.toBranch)
        if (shortenedTo == "master" || shortenedTo == "heads/master" || pullRequest.toBranch == "refs/heads/master") {
            builder.append("\n\n")
        } else {
            builder.append(" into '$shortenedTo'\n\n")
        }
        builder.append("from pull-request ${pullRequest.number}\n\n")

        builder.append("* $shortenedFrom:\n")
        for (gitCommit in commits) {
            builder.append("  ${gitCommit.getShortMessage()}\n")
        }
        builder.append("\n")

        for (user in pullRequest.reviewers) {
            builder.append("Reviewed-by: ${user.name} <${user.email ?: ""}>\n")
        }

        return builder.toString()
    }

    @Transactional(readOnly = true)
    override fun getPullRequests(toProjectId: Long, state: State?): List<PullRequest> {
        val project = projectRepository.findById(toProjectId)
            .orElseThrow { IllegalArgumentException("Project not found: $toProjectId") }
        return if (state != null) {
            pullRequestRepository.findByToProjectAndState(project, state)
        } else {
            pullRequestRepository.findByToProject(project)
        }
    }

    @Transactional(readOnly = true)
    override fun getPullRequest(toProjectId: Long, number: Long): PullRequest? {
        val project = projectRepository.findById(toProjectId)
            .orElseThrow { IllegalArgumentException("Project not found: $toProjectId") }
        return pullRequestRepository.findByToProjectAndNumber(project, number)
    }

    @Transactional
    override fun createPullRequest(
        title: String,
        body: String?,
        fromProjectId: Long,
        toProjectId: Long,
        fromBranch: String,
        toBranch: String,
        contributor: User
    ): PullRequest {
        val fromProject = projectRepository.findById(fromProjectId)
            .orElseThrow { IllegalArgumentException("Source project not found: $fromProjectId") }
        val toProject = projectRepository.findById(toProjectId)
            .orElseThrow { IllegalArgumentException("Target project not found: $toProjectId") }

        val lastPr = pullRequestRepository.findFirstByToProjectOrderByNumberDesc(toProject)
        val nextNumber = (lastPr?.number ?: 0L) + 1L

        val pullRequest = PullRequest(
            title = title,
            body = body,
            fromProject = fromProject,
            toProject = toProject,
            fromBranch = fromBranch,
            toBranch = toBranch,
            contributor = contributor,
            state = State.OPEN,
            created = Instant.now(),
            updated = Instant.now(),
            number = nextNumber
        )

        val saved = pullRequestRepository.save(pullRequest)

        try {
            // yona PullRequestMergingActor(PR 생성 시 트리거)도 processPullRequestMerging()을 거치므로
            // 최초 커밋 목록의 PullRequestCommit 영속화/PullRequestEvent 기록까지 여기서 함께 이뤄진다
            // (알림만 isNewPullRequest=true라 생략됨, P1-52).
            processMergeCheck(saved.id!!, contributor, isNewPullRequest = true)
        } catch (e: Exception) {
            // JGit merge 예외가 발생하더라도 PR 생성 자체는 허용
        }

        // yona NotificationEvent.afterNewPullRequest 대응 (P1-39).
        val title = "[${toProject.name}] 새 풀 리퀘스트: #${saved.number} ${saved.title}"
        val notificationEvent = NotificationEvent(
            title = title,
            senderId = contributor.id,
            created = Instant.now(),
            resourceType = ResourceType.PULL_REQUEST,
            resourceId = saved.id.toString(),
            eventType = EventType.NEW_PULL_REQUEST,
            newValue = saved.body
        )
        val receivers = watchService.findActualWatchers(
            baseWatchers = setOf(contributor),
            resourceType = ResourceType.PULL_REQUEST,
            resourceId = saved.id.toString(),
            projectId = toProject.id,
            eventType = notificationEvent.eventType
        ).toMutableSet()
        // yona NotificationEvent.java:1425-1428 getDefaultReceivers(pullRequest)의
        // getMentionedUsers(body) 대응 (P1-127). 신규 PR 본문의 @멘션도 수신자에 포함한다. [GL-models_NotificationEvent-098]
        receivers.addAll(commentService.extractMentionedUsers(saved.body ?: ""))
        receivers.removeIf { it.id == contributor.id }
        notificationEvent.receivers = receivers

        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }

        recordPullRequestEvent(saved, EventType.NEW_PULL_REQUEST, contributor.loginId, null, saved.body)

        return saved
    }

    @Transactional
    override fun updatePullRequest(
        pullRequestId: Long,
        title: String,
        body: String?,
        fromBranch: String,
        toBranch: String
    ): PullRequest {
        val pr = pullRequestRepository.findById(pullRequestId)
            .orElseThrow { IllegalArgumentException("PullRequest not found: $pullRequestId") }

        // yona hasSameBranchesWith()/findDuplicatedPullRequest() 대응 — 브랜치가 바뀌는 경우에만
        // 동일한 from/to 프로젝트·브랜치 조합의 OPEN PR이 이미 있는지 검사한다.
        if (pr.fromBranch != fromBranch || pr.toBranch != toBranch) {
            val duplicated = pullRequestRepository.findByFromBranchAndToBranchAndFromProjectAndToProjectAndState(
                fromBranch, toBranch, pr.fromProject, pr.toProject, State.OPEN
            )
            if (duplicated != null) {
                throw DuplicatedPullRequestException(
                    "동일한 브랜치 조합(from=$fromBranch, to=$toBranch)의 풀 리퀘스트가 이미 열려 있습니다: ${duplicated.id}"
                )
            }
        }

        // yona PullRequest.updateWith() 대응 — deleteIssueEvents() -> 필드 갱신 -> addNewIssueEvents()
        deleteIssueReferenceEvents(pr)

        pr.toBranch = toBranch
        pr.fromBranch = fromBranch
        pr.title = title
        pr.body = body
        pr.updated = Instant.now()
        val updated = pullRequestRepository.save(pr)

        addIssueReferenceEvents(updated)

        return updated
    }

    // yona PullRequest.deleteIssueEvents() 대응 (P1-68).
    private fun deleteIssueReferenceEvents(pullRequest: PullRequest) {
        val newValue = pullRequest.id.toString()
        val oldEvents = issueEventRepository.findByNewValueAndSenderLoginIdAndEventType(
            newValue, pullRequest.contributor.loginId, EventType.ISSUE_REFERRED_FROM_PULL_REQUEST
        )
        if (oldEvents.isNotEmpty()) {
            issueEventRepository.deleteAll(oldEvents)
        }
    }

    // yona PullRequest.addNewIssueEvents() 대응 (P1-68). title+body에서 "#숫자" 형태로 참조된
    // 이슈들을 toProject에서 찾아 ISSUE_REFERRED_FROM_PULL_REQUEST 이벤트를 새로 만든다.
    private fun addIssueReferenceEvents(pullRequest: PullRequest) {
        val issueNumbers = IssueReferenceParser.findReferredIssueNumbers(pullRequest.title + (pullRequest.body ?: ""))
        val newValue = pullRequest.id.toString()
        for (number in issueNumbers) {
            val issue = issueRepository.findByProjectAndNumber(pullRequest.toProject, number) ?: continue
            issueEventRepository.save(
                IssueEvent(
                    issue = issue,
                    senderLoginId = pullRequest.contributor.loginId,
                    newValue = newValue,
                    created = Instant.now(),
                    eventType = EventType.ISSUE_REFERRED_FROM_PULL_REQUEST
                )
            )
        }
    }

    @Transactional
    override fun changeState(
        pullRequestId: Long,
        state: State,
        updaterLoginId: String
    ): PullRequest {
        val pr = pullRequestRepository.findById(pullRequestId)
            .orElseThrow { IllegalArgumentException("PullRequest not found: $pullRequestId") }
        val oldState = pr.state
        if (oldState == state) {
            return pr
        }

        pr.state = state
        pr.updated = Instant.now()
        val saved = pullRequestRepository.save(pr)

        val updater = userRepository.findByLoginId(updaterLoginId).orElse(null)
        val title = "[${saved.toProject.name}] PR #${saved.number} 상태 변경: $oldState -> $state"
        val notificationEvent = NotificationEvent(
            title = title,
            senderId = updater?.id,
            created = Instant.now(),
            resourceType = ResourceType.PULL_REQUEST,
            resourceId = saved.id.toString(),
            eventType = EventType.PULL_REQUEST_STATE_CHANGED,
            oldValue = oldState.toString(),
            newValue = state.toString()
        )
        // yona NotificationEvent.getReceivers(sender, pullRequest)(=pullRequest.getWatchers() - sender) 대응.
        // 기존에는 contributor 한 명만 고정 수신자였는데, PR을 실제로 감시 중인 다른 사용자에게는
        // 전혀 알림이 가지 않는 누락이 있었다.
        val receivers = watchService.findActualWatchers(
            baseWatchers = setOf(saved.contributor),
            resourceType = ResourceType.PULL_REQUEST,
            resourceId = saved.id.toString(),
            projectId = saved.toProject.id,
            eventType = notificationEvent.eventType
        ).toMutableSet()
        if (updater != null) {
            receivers.removeIf { it.id == updater.id }
        }
        notificationEvent.receivers = receivers
        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }

        recordPullRequestEvent(saved, EventType.PULL_REQUEST_STATE_CHANGED, updaterLoginId, oldState.toString(), state.toString())

        return saved
    }

    // yona models/PullRequestEvent.java 대응(draft-time 병합/취소 최적화는 recordWithDraftMerge에서 처리, P1-40).
    private fun recordPullRequestEvent(
        pullRequest: PullRequest,
        eventType: EventType,
        senderLoginId: String?,
        oldValue: String?,
        newValue: String?
    ) {
        val event = PullRequestEvent(
            pullRequest = pullRequest,
            senderLoginId = senderLoginId,
            eventType = eventType,
            oldValue = oldValue,
            newValue = newValue,
            created = Instant.now()
        )
        pullRequestEventRepository.recordWithDraftMerge(event)
    }

    @Transactional
    override fun addReviewer(pullRequestId: Long, reviewer: User) {
        val pr = pullRequestRepository.findById(pullRequestId)
            .orElseThrow { IllegalArgumentException("PullRequest not found: $pullRequestId") }
        val user = userRepository.findById(reviewer.id!!)
            .orElseThrow { IllegalArgumentException("User not found: ${reviewer.id}") }

        if (pr.reviewers.add(user)) {
            pullRequestRepository.save(pr)
            notifyReviewerChanged(pr, user, "DONE")
        }
    }

    @Transactional
    override fun removeReviewer(pullRequestId: Long, reviewer: User) {
        val pr = pullRequestRepository.findById(pullRequestId)
            .orElseThrow { IllegalArgumentException("PullRequest not found: $pullRequestId") }
        val user = userRepository.findById(reviewer.id!!)
            .orElseThrow { IllegalArgumentException("User not found: ${reviewer.id}") }

        if (pr.reviewers.remove(user)) {
            pullRequestRepository.save(pr)
            notifyReviewerChanged(pr, user, "CANCEL")
        }
    }

    // yona CodeReviewServiceImpl.addReviewer/removeReviewer와 동일한 알림/타임라인 기록 (P1-49).
    // PullRequestController(REST)가 이 서비스를, ReviewApiController가 CodeReviewService를 각각 사용하는
    // 중복 구현 구조는 그대로 남아있지만(별도 정리 과제), 최소한 두 경로 모두 알림이 발송되도록 맞춘다.
    // yona NotificationEvent.afterReviewed()의 title = formatReplyTitle(pullRequest) 대응 (P1-63).
    // 리뷰어 참여/취소를 구분하는 임의의 문장 대신, 다른 PR 알림들과 동일한 "Re: [project] title (#number)"
    // 범용 포맷을 그대로 재현한다.
    private fun formatReplyTitle(pullRequest: PullRequest): String =
        "Re: [${pullRequest.toProject.name}] ${pullRequest.title} (#${pullRequest.number})"

    private fun notifyReviewerChanged(pullRequest: PullRequest, reviewer: User, newValue: String) {
        val notificationEvent = NotificationEvent(
            title = formatReplyTitle(pullRequest),
            senderId = reviewer.id,
            created = Instant.now(),
            resourceType = ResourceType.PULL_REQUEST,
            resourceId = pullRequest.id.toString(),
            eventType = EventType.PULL_REQUEST_REVIEW_STATE_CHANGED,
            // yona NotificationEvent.afterReviewed()의 oldValue = reviewAction.getOppositAction().name() 대응.
            oldValue = if (newValue == "DONE") "CANCEL" else "DONE",
            newValue = newValue
        )
        val receivers = mutableSetOf(pullRequest.contributor)
        receivers.addAll(pullRequest.reviewers)
        receivers.removeIf { it.id == reviewer.id }
        notificationEvent.receivers = receivers

        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }

        recordPullRequestEvent(pullRequest, EventType.PULL_REQUEST_REVIEW_STATE_CHANGED, reviewer.loginId, null, newValue)
    }

    override fun getDiff(pullRequest: PullRequest): List<FileDiff> {
        val playRepoA = repositoryService.getRepository(pullRequest.toProject)

        if (pullRequest.mergedCommitIdFrom != null && pullRequest.mergedCommitIdTo != null) {
            @Suppress("UNCHECKED_CAST")
            return playRepoA.getDiff(pullRequest.mergedCommitIdFrom!!, pullRequest.mergedCommitIdTo!!) as List<FileDiff>
        }
        
        val playRepoB = repositoryService.getRepository(pullRequest.fromProject)
        if (playRepoA is GitRepository && playRepoB is GitRepository) {
            val revA = pullRequest.toBranch
            val revB = pullRequest.lastCommitId ?: pullRequest.fromBranch
            return playRepoA.getDiff(revA, playRepoB, revB)
        }
        
        val revA = pullRequest.mergedCommitIdFrom ?: pullRequest.toBranch
        val revB = pullRequest.mergedCommitIdTo ?: pullRequest.lastCommitId ?: pullRequest.fromBranch
        @Suppress("UNCHECKED_CAST")
        return playRepoA.getDiff(revA, revB) as List<FileDiff>
    }

    override fun getDiff(pullRequest: PullRequest, commitId: String): List<FileDiff> {
        val project = if (pullRequest.state == State.MERGED) pullRequest.toProject else pullRequest.fromProject
        val playRepo = repositoryService.getRepository(project)
        @Suppress("UNCHECKED_CAST")
        return playRepo.getDiff(commitId) as List<FileDiff>
    }

    @Transactional
    override fun deleteFromBranch(pullRequestId: Long): PullRequest {
        val pullRequest = pullRequestRepository.findById(pullRequestId)
            .orElseThrow { IllegalArgumentException("PullRequest with ID $pullRequestId not found") }

        if (pullRequest.state != State.MERGED) {
            throw InvalidBranchOperationException("병합된 PR만 원본 브랜치를 삭제할 수 있습니다.")
        }

        val playRepo = repositoryService.getRepository(pullRequest.fromProject)
        val branch = playRepo.getBranches().firstOrNull { isSameBranch(it.name, pullRequest.fromBranch) }
            ?: throw InvalidBranchOperationException("원본 브랜치를 찾을 수 없습니다: ${pullRequest.fromBranch}")

        playRepo.deleteBranch(pullRequest.fromBranch)
        pullRequest.lastCommitId = branch.headCommit.getId()

        return pullRequestRepository.save(pullRequest)
    }

    @Transactional
    override fun restoreFromBranch(pullRequestId: Long): PullRequest {
        val pullRequest = pullRequestRepository.findById(pullRequestId)
            .orElseThrow { IllegalArgumentException("PullRequest with ID $pullRequestId not found") }

        val lastCommitId = pullRequest.lastCommitId
            ?: throw InvalidBranchOperationException("복원할 브랜치의 커밋 정보가 없습니다.")

        val playRepo = repositoryService.getRepository(pullRequest.fromProject)
        val alreadyExists = playRepo.getBranches().any { isSameBranch(it.name, pullRequest.fromBranch) }
        if (alreadyExists) {
            throw InvalidBranchOperationException("이미 존재하는 브랜치입니다: ${pullRequest.fromBranch}")
        }

        playRepo.createBranch(pullRequest.fromBranch, lastCommitId)

        return pullRequest
    }

    private fun isSameBranch(refName: String, branchName: String): Boolean {
        val shortRefName = refName.removePrefix("refs/heads/")
        val shortBranchName = branchName.removePrefix("refs/heads/")
        return shortRefName == shortBranchName
    }
}
