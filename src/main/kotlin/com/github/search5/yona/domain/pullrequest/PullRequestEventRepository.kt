package com.github.search5.yona.domain.pullrequest

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PullRequestEventRepository : JpaRepository<PullRequestEvent, Long> {
    fun findByPullRequestOrderByCreatedAsc(pullRequest: PullRequest): List<PullRequestEvent>
}
