package com.github.search5.yona.domain.pullrequest

import com.github.search5.yona.domain.project.Project
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CommentThreadRepository : JpaRepository<CommentThread, Long> {
    fun findByCommitIdOrderByCreatedDateDesc(commitId: String): List<CommentThread>
    fun findByProjectAndCommitIdOrderByCreatedDateDesc(project: Project, commitId: String): List<CommentThread>
    fun findByCommitIdAndStateOrderByCreatedDateDesc(commitId: String, state: CommentThread.ThreadState): List<CommentThread>
    fun findByPullRequest(pullRequest: PullRequest): List<CommentThread>
    fun findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(project: Project, commitId: String): List<CommentThread>
}
