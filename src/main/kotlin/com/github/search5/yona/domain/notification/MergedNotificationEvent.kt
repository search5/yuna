package com.github.search5.yona.domain.notification

import com.github.search5.yona.domain.user.User

/**
 * yona notification/MergedNotificationEvent.java + notification/INotificationEvent.java 대응 (P1-27).
 * yona는 인터페이스(INotificationEvent)로 단일 이벤트와 병합 이벤트를 다형적으로 다루지만, yuna는
 * NotificationEvent가 유일한 알림 엔티티라 그 다형성이 불필요해 단순 래퍼 클래스로 축약한다.
 *
 * [main]은 title/sender/resourceType/resourceId/eventType/createdDate 등 "대표 속성"의 출처이고,
 * [messageSources]는 실제 메일 본문에 합쳐질 개별 이벤트들의 순서 목록이다(legacy와 동일하게
 * 렌더링 시 "\n\n---\n\n"로 join). [receivers]는 병합 과정에서 재계산된 값이 있으면 그것을,
 * 없으면 main의 원래 수신자 집합을 그대로 쓴다(legacy MergedNotificationEvent.findReceivers 대응).
 */
class MergedNotificationEvent private constructor(
    val main: NotificationEvent,
    val messageSources: MutableList<NotificationEvent>,
    private var receiversOverride: Set<User>?
) {
    constructor(main: NotificationEvent) : this(main, mutableListOf(main), null)
    constructor(main: NotificationEvent, messageSources: List<NotificationEvent>) : this(main, messageSources.toMutableList(), null)

    val receivers: Set<User>
        get() = receiversOverride ?: main.receivers

    fun setReceivers(receivers: Set<User>) {
        receiversOverride = receivers
    }
}
