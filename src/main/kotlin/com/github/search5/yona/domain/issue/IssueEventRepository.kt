package com.github.search5.yona.domain.issue

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface IssueEventRepository : JpaRepository<IssueEvent, Long> {
    fun findByIssueOrderByCreatedAsc(issue: Issue): List<IssueEvent>

    fun findFirstByIssueAndCreatedAfterOrderByIdDesc(issue: Issue, created: Instant): IssueEvent?
}
