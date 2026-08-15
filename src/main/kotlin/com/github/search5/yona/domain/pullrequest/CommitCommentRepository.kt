package com.github.search5.yona.domain.pullrequest

import com.github.search5.yona.domain.project.Project
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CommitCommentRepository : JpaRepository<CommitComment, Long> {
    fun findByProjectAndCommitIdOrderByCreatedDateAsc(project: Project, commitId: String): List<CommitComment>
}
