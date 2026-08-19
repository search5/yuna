package com.github.search5.yona.domain.pullrequest

import com.github.search5.yona.domain.vcs.GitCommit
import com.github.search5.yona.domain.vcs.RepositoryService
import com.github.search5.yona.domain.vcs.GitRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.event.PullRequestMergeEvent
import com.github.search5.yona.domain.notification.NotificationEvent
import com.github.search5.yona.domain.notification.NotificationEventRepository
import com.github.search5.yona.domain.watch.WatchService
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.*
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.merge.ThreeWayMerger
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.RefSpec
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
    private val notificationEventRepository: NotificationEventRepository,
    private val pullRequestEventRepository: PullRequestEventRepository,
    private val watchService: WatchService
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
            val mergeCommit = CommitBuilder().apply {
                val reusableTreeId = getMergedTreeIfReusable(repo, leftParent, rightParent, pullRequest)
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

            // refs/yobi/pull/{id}/merged 레프 업데이트
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

    private fun updatePullRequestCommits(pullRequest: PullRequest, gitCommits: List<GitCommit>) {
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
        pullRequestCommitRepository.saveAll(newCommits)

        val gitCommitIds = gitCommits.map { it.getId() }.toSet()
        val updatedCommits = mutableListOf<PullRequestCommit>()
        for (priorCommit in priorCommits) {
            if (!gitCommitIds.contains(priorCommit.commitId)) {
                priorCommit.state = PullRequestCommit.State.PRIOR
                updatedCommits.add(priorCommit)
            }
        }
        pullRequestCommitRepository.saveAll(updatedCommits)
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
            attemptMerge(saved.id!!)
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
        receivers.removeIf { it.id == contributor.id }
        notificationEvent.receivers = receivers

        notificationEventRepository.save(notificationEvent)
        eventPublisher.publishEvent(notificationEvent)

        recordPullRequestEvent(saved, EventType.NEW_PULL_REQUEST, contributor.loginId, null, saved.body)

        return saved
    }

    @Transactional
    override fun updatePullRequest(
        pullRequestId: Long,
        title: String,
        body: String?
    ): PullRequest {
        val pr = pullRequestRepository.findById(pullRequestId)
            .orElseThrow { IllegalArgumentException("PullRequest not found: $pullRequestId") }
        pr.title = title
        pr.body = body
        pr.updated = Instant.now()
        return pullRequestRepository.save(pr)
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
            newValue = state.toString(),
            receivers = mutableSetOf(saved.contributor).apply { removeIf { it.id == updater?.id } }
        )
        notificationEventRepository.save(notificationEvent)
        eventPublisher.publishEvent(notificationEvent)

        recordPullRequestEvent(saved, EventType.PULL_REQUEST_STATE_CHANGED, updaterLoginId, oldState.toString(), state.toString())

        return saved
    }

    // yona models/PullRequestEvent.java 대응(간소화 - draft-time 병합/취소 최적화는 제외, P1-08).
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
        pullRequestEventRepository.save(event)
    }

    @Transactional
    override fun addReviewer(pullRequestId: Long, reviewer: User) {
        val pr = pullRequestRepository.findById(pullRequestId)
            .orElseThrow { IllegalArgumentException("PullRequest not found: $pullRequestId") }
        val user = userRepository.findById(reviewer.id!!)
            .orElseThrow { IllegalArgumentException("User not found: ${reviewer.id}") }
        
        pr.reviewers.add(user)
        pullRequestRepository.save(pr)
    }

    @Transactional
    override fun removeReviewer(pullRequestId: Long, reviewer: User) {
        val pr = pullRequestRepository.findById(pullRequestId)
            .orElseThrow { IllegalArgumentException("PullRequest not found: $pullRequestId") }
        val user = userRepository.findById(reviewer.id!!)
            .orElseThrow { IllegalArgumentException("User not found: ${reviewer.id}") }

        pr.reviewers.remove(user)
        pullRequestRepository.save(pr)
    }

    override fun getDiff(pullRequest: PullRequest): List<com.github.search5.yona.domain.vcs.FileDiff> {
        val playRepoA = repositoryService.getRepository(pullRequest.toProject)
        
        if (pullRequest.mergedCommitIdFrom != null && pullRequest.mergedCommitIdTo != null) {
            @Suppress("UNCHECKED_CAST")
            return playRepoA.getDiff(pullRequest.mergedCommitIdFrom!!, pullRequest.mergedCommitIdTo!!) as List<com.github.search5.yona.domain.vcs.FileDiff>
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
        return playRepoA.getDiff(revA, revB) as List<com.github.search5.yona.domain.vcs.FileDiff>
    }

    override fun getDiff(pullRequest: PullRequest, commitId: String): List<com.github.search5.yona.domain.vcs.FileDiff> {
        val project = if (pullRequest.state == com.github.search5.yona.domain.enumeration.State.MERGED) pullRequest.toProject else pullRequest.fromProject
        val playRepo = repositoryService.getRepository(project)
        @Suppress("UNCHECKED_CAST")
        return playRepo.getDiff(commitId) as List<com.github.search5.yona.domain.vcs.FileDiff>
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
