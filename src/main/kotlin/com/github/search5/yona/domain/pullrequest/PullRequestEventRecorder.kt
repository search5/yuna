package com.github.search5.yona.domain.pullrequest

import com.github.search5.yona.domain.enumeration.EventType
import java.time.Duration
import java.time.Instant

private val DRAFT_WINDOW: Duration = Duration.ofSeconds(30)

/**
 * yona `models/PullRequestEvent.java`의 `add()` 대응(P1-40).
 * `IssueEvent`(P1-38)와 달리 값을 병합(A→B→C ⇒ A→C)하지 않고, PULL_REQUEST_REVIEW_STATE_CHANGED
 * 타입에 한해 같은 사용자가 30초 이내에 연속으로 리뷰 상태를 바꾸면 직전 이벤트를 삭제하고
 * 새 이벤트도 저장하지 않는다(둘 다 사라짐 — legacy `needToDeleteEvent`/`add()`의 실제 동작 그대로).
 * 그 외 이벤트 타입은 병합/취소 대상이 아니라 항상 그대로 저장한다.
 *
 * 저장되면 저장된 [PullRequestEvent]를, 상쇄되어 저장되지 않았으면 null을 반환한다.
 */
fun PullRequestEventRepository.recordWithDraftMerge(event: PullRequestEvent): PullRequestEvent? {
    val draftSince = Instant.now().minus(DRAFT_WINDOW)
    val lastEvent = findFirstByPullRequestAndCreatedAfterOrderByCreatedDesc(event.pullRequest, draftSince)

    val needToDelete = lastEvent != null &&
        event.eventType == EventType.PULL_REQUEST_REVIEW_STATE_CHANGED &&
        lastEvent.eventType == EventType.PULL_REQUEST_REVIEW_STATE_CHANGED &&
        lastEvent.senderLoginId == event.senderLoginId

    return if (needToDelete) {
        delete(lastEvent!!)
        null
    } else {
        save(event)
    }
}
