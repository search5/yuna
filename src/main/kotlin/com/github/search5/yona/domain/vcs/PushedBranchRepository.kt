package com.github.search5.yona.domain.vcs

import com.github.search5.yona.domain.project.Project
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.Optional

@Repository
interface PushedBranchRepository : JpaRepository<PushedBranch, Long> {
    fun findByProjectAndName(project: Project, name: String): Optional<PushedBranch>
    fun findByProjectAndPushedDateAfter(project: Project, cutoff: Instant): List<PushedBranch>
    fun findByProjectAndPushedDateBefore(project: Project, cutoff: Instant): List<PushedBranch>
}
