package com.github.search5.yona.domain.pullrequest

import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.user.User
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.http.HttpStatus

import com.github.search5.yona.domain.vcs.FileDiff

interface PullRequestService {
    /**
     * 풀 리퀘스트의 충돌 가능성을 판단하고, 임시 머지 결과를 반환합니다.
     */
    fun attemptMerge(pullRequestId: Long): PullRequestMergeResult

    /**
     * yona actors/PullRequestActor.processPullRequestMerging() 대응 (P1-52). attemptMerge()의
     * 부수효과 없는 미리보기와 달리, 새 커밋이 발견되면 PullRequestCommit 영속화·PullRequestEvent
     * 기록·리뷰어 초기화를 수행하고([isNewPullRequest]가 false일 때만 알림도 발행), diff가 완전히
     * 사라지면 자동으로 MERGED 상태로 전환한다. createPullRequest()(PR 생성)와 관련 PR 재검사
     * (RelatedPullRequestMergeEvent) 양쪽에서 공유한다 — legacy도 PullRequestMergingActor/
     * RelatedPullRequestMergingActor 둘 다 이 메서드 하나로 수렴한다.
     */
    fun processMergeCheck(pullRequestId: Long, sender: User, isNewPullRequest: Boolean): PullRequestMergeResult

    /**
     * 풀 리퀘스트를 실제로 머지하고 커밋을 기록하며, 상태를 업데이트합니다.
     * @param pullRequestId 풀 리퀘스트 ID
     * @param updater 머지 처리를 수행하는 유저
     */
    fun merge(pullRequestId: Long, updater: User): PullRequestMergeResult

    fun getPullRequests(toProjectId: Long, state: State?): List<PullRequest>

    fun getPullRequest(toProjectId: Long, number: Long): PullRequest?

    fun createPullRequest(
        title: String,
        body: String?,
        fromProjectId: Long,
        toProjectId: Long,
        fromBranch: String,
        toBranch: String,
        contributor: User
    ): PullRequest

    // yona PullRequest.updateWith()/hasSameBranchesWith()/findDuplicatedPullRequest() +
    // PullRequestApp.editPullRequest() 대응 (P1-68). title/body뿐 아니라 from/toBranch까지
    // 재할당할 수 있으며, 브랜치가 바뀌면 동일한 from/to 프로젝트·브랜치 조합의 OPEN PR이
    // 이미 있는지 검사해 있으면 [DuplicatedPullRequestException]을 던진다. 브랜치 변경 여부와
    // 무관하게 항상 기존 ISSUE_REFERRED_FROM_PULL_REQUEST 이벤트를 지우고 새 title/body를
    // 다시 스캔해 재생성한다(deleteIssueEvents()/addNewIssueEvents()).
    fun updatePullRequest(
        pullRequestId: Long,
        title: String,
        body: String?,
        fromBranch: String,
        toBranch: String
    ): PullRequest

    fun changeState(
        pullRequestId: Long,
        state: State,
        updaterLoginId: String
    ): PullRequest

    fun addReviewer(pullRequestId: Long, reviewer: User)
    fun removeReviewer(pullRequestId: Long, reviewer: User)

    fun getDiff(pullRequest: PullRequest): List<FileDiff>
    fun getDiff(pullRequest: PullRequest, commitId: String): List<FileDiff>

    /**
     * 병합된 PR의 원본(from) 브랜치를 삭제한다. 이후 복원할 수 있도록 삭제 직전
     * 브랜치의 head 커밋을 pullRequest.lastCommitId에 기록한다.
     */
    fun deleteFromBranch(pullRequestId: Long): PullRequest

    /**
     * deleteFromBranch()로 지워진 브랜치를 pullRequest.lastCommitId 지점으로 복원한다.
     */
    fun restoreFromBranch(pullRequestId: Long): PullRequest
}

@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "리뷰어 수가 부족하여 머지할 수 없습니다.")
class LackingReviewerException(message: String) : RuntimeException(message)

@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "병합되지 않은 PR은 브랜치를 삭제/복원할 수 없습니다.")
class InvalidBranchOperationException(message: String) : RuntimeException(message)

// yona PullRequestApp.editPullRequest()의 findDuplicatedPullRequest() != null 분기 대응 (P1-68).
@ResponseStatus(value = HttpStatus.CONFLICT, reason = "동일한 브랜치 조합의 풀 리퀘스트가 이미 열려 있습니다.")
class DuplicatedPullRequestException(message: String) : RuntimeException(message)
