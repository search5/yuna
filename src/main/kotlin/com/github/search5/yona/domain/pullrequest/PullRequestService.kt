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

    fun updatePullRequest(
        pullRequestId: Long,
        title: String,
        body: String?
    ): PullRequest

    fun changeState(
        pullRequestId: Long,
        state: State
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
