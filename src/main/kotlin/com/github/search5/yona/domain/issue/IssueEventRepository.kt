package com.github.search5.yona.domain.issue

import com.github.search5.yona.domain.enumeration.EventType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface IssueEventRepository : JpaRepository<IssueEvent, Long> {
    fun findByIssueOrderByCreatedAsc(issue: Issue): List<IssueEvent>

    fun findFirstByIssueAndCreatedAfterOrderByIdDesc(issue: Issue, created: Instant): IssueEvent?

    // yona PullRequest.deleteIssueEvents() 대응 (P1-68). 특정 PR이 이전에 남긴
    // ISSUE_REFERRED_FROM_PULL_REQUEST 이벤트들을 찾는다(newValue=PR id, senderLoginId=기여자).
    fun findByNewValueAndSenderLoginIdAndEventType(
        newValue: String,
        senderLoginId: String,
        eventType: EventType
    ): List<IssueEvent>
}
