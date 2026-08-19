package com.github.search5.yona.domain.issue

import java.time.Duration
import java.time.Instant

private val DRAFT_WINDOW: Duration = Duration.ofSeconds(30)

/**
 * yona `models/IssueEvent.java`의 `add()`/`addWithoutSkipEvent()` 대응(P1-38).
 * 같은 이슈에 대해 같은 사용자가 [DRAFT_WINDOW] 이내에 같은 타입의 이벤트를 연속으로 남기면
 * 타임라인 잡음을 줄이기 위해 병합하거나 상쇄한다.
 *
 * - [skipWaypoint]=true(`add()`): A→B, B→C ⇒ A→C로 병합(중간 지점 B는 생략).
 *   A→B, B→A처럼 값이 되돌아오면 두 이벤트 모두 남기지 않는다.
 * - [skipWaypoint]=false(`addWithoutSkipEvent()`): 중간 지점은 그대로 남기되,
 *   정확히 값이 되돌아오는 경우(A→B, B→A)만 두 이벤트 모두 상쇄한다.
 *
 * 저장되면 저장된 [IssueEvent]를, 상쇄되어 저장되지 않았으면 null을 반환한다.
 */
fun IssueEventRepository.recordWithDraftMerge(event: IssueEvent, skipWaypoint: Boolean): IssueEvent? {
    val draftSince = Instant.now().minus(DRAFT_WINDOW)
    val lastEvent = findFirstByIssueAndCreatedAfterOrderByIdDesc(event.issue, draftSince)

    if (lastEvent != null &&
        lastEvent.eventType == event.eventType &&
        lastEvent.senderLoginId == event.senderLoginId
    ) {
        if (skipWaypoint) {
            event.oldValue = lastEvent.oldValue
            delete(lastEvent)
            if (event.oldValue == event.newValue) {
                return null
            }
        } else if (event.oldValue == lastEvent.newValue && event.newValue == lastEvent.oldValue) {
            delete(lastEvent)
            return null
        }
    }

    return save(event)
}
