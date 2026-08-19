package com.github.search5.yona.domain.notification

import com.github.search5.yona.AbstractIntegrationTest
import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

// yona models/NotificationEvent.java의 add()/addWithoutSkipEvent() 대응 (P1-27).
@Transactional
class NotificationEventRecorderSpec @Autowired constructor(
    private val recorder: NotificationEventRecorder,
    private val notificationEventRepository: NotificationEventRepository,
    private val notificationMailRepository: NotificationMailRepository,
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    init {
        describe("NotificationEventRecorder.record") {
            beforeEach {
                notificationMailRepository.deleteAll()
                notificationEventRepository.deleteAll()
                userRepository.deleteAll()
            }

            fun eventOf(sender: User, receiver: User, resourceId: String, oldValue: String?, newValue: String?) =
                NotificationEvent(
                    title = "이슈 상태 변경",
                    senderId = sender.id,
                    created = Instant.now(),
                    resourceType = ResourceType.ISSUE_POST,
                    resourceId = resourceId,
                    eventType = EventType.ISSUE_STATE_CHANGED,
                    oldValue = oldValue,
                    newValue = newValue,
                    receivers = mutableSetOf(receiver)
                )

            it("저장되면 NotificationMail 마커도 함께 생성해야 한다") {
                val sender = userRepository.save(User(loginId = "sender1", name = "발신자1", email = "s1@yona.io"))
                val receiver = userRepository.save(User(loginId = "receiver1", name = "수신자1", email = "r1@yona.io"))

                val saved = recorder.record(eventOf(sender, receiver, "1", "OPEN", "CLOSED"))

                saved shouldBe notificationEventRepository.findById(saved!!.id!!).orElse(null)
                notificationMailRepository.findByNotificationEvent(saved!!) shouldBe notificationMailRepository.findAll().first()
            }

            it("수신자가 없으면 저장하지 않아야 한다") {
                val sender = userRepository.save(User(loginId = "sender2", name = "발신자2", email = "s2@yona.io"))
                val event = NotificationEvent(
                    title = "제목", senderId = sender.id, created = Instant.now(),
                    resourceType = ResourceType.ISSUE_POST, resourceId = "2",
                    eventType = EventType.ISSUE_STATE_CHANGED, newValue = "CLOSED"
                )

                val saved = recorder.record(event)

                saved shouldBe null
                notificationEventRepository.count() shouldBe 0
            }

            it("30초 내 같은 사용자가 같은 리소스에 같은 타입 이벤트를 연속 발생시키면 A→C로 병합해야 한다") {
                val sender = userRepository.save(User(loginId = "sender3", name = "발신자3", email = "s3@yona.io"))
                val receiver = userRepository.save(User(loginId = "receiver3", name = "수신자3", email = "r3@yona.io"))

                recorder.record(eventOf(sender, receiver, "3", "OPEN", "CLOSED"))
                val second = recorder.record(eventOf(sender, receiver, "3", "CLOSED", "REJECTED"))

                notificationEventRepository.count() shouldBe 1
                second!!.oldValue shouldBe "OPEN"
                second.newValue shouldBe "REJECTED"
                notificationMailRepository.count() shouldBe 1
            }

            it("30초 내 정확히 원상복구되면(A→B→A) 두 이벤트 모두 상쇄해야 한다") {
                val sender = userRepository.save(User(loginId = "sender4", name = "발신자4", email = "s4@yona.io"))
                val receiver = userRepository.save(User(loginId = "receiver4", name = "수신자4", email = "r4@yona.io"))

                recorder.record(eventOf(sender, receiver, "4", "OPEN", "CLOSED"))
                val second = recorder.record(eventOf(sender, receiver, "4", "CLOSED", "OPEN"))

                second shouldBe null
                notificationEventRepository.count() shouldBe 0
                notificationMailRepository.count() shouldBe 0
            }

            it("skipWaypoint=false면 중간 지점은 남기고 정확히 되돌아오는 경우만 상쇄해야 한다") {
                val sender = userRepository.save(User(loginId = "sender5", name = "발신자5", email = "s5@yona.io"))
                val receiver = userRepository.save(User(loginId = "receiver5", name = "수신자5", email = "r5@yona.io"))

                recorder.record(eventOf(sender, receiver, "5", "", "sharer1"), skipWaypoint = false)
                val second = recorder.record(eventOf(sender, receiver, "5", "sharer1", ""), skipWaypoint = false)

                second shouldBe null
                notificationEventRepository.count() shouldBe 0
            }
        }
    }
}
