package com.github.search5.yona.domain.notification

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

// yona models/NotificationEvent.scheduleDeleteOldNotifications() 대응 (P1-27).
class NotificationCleanupSchedulerSpec : DescribeSpec({
    val notificationEventRepository = mockk<NotificationEventRepository>()

    describe("deleteOldNotifications") {
        it("keepDays가 0 이하(기본값 -1)이면 삭제 쿼리를 실행하지 않는다(legacy KEEP_TIME_IN_DAYS 기본값과 동일)") {
            val scheduler = NotificationCleanupScheduler(notificationEventRepository, -1L)

            scheduler.deleteOldNotifications()

            verify(exactly = 0) { notificationEventRepository.deleteByCreatedBefore(any()) }
        }

        it("keepDays가 양수면 그만큼 이전에 생성된 이벤트를 삭제한다") {
            every { notificationEventRepository.deleteByCreatedBefore(any()) } returns 3L
            val scheduler = NotificationCleanupScheduler(notificationEventRepository, 30L)

            scheduler.deleteOldNotifications()

            verify(exactly = 1) { notificationEventRepository.deleteByCreatedBefore(any()) }
        }
    }
})
