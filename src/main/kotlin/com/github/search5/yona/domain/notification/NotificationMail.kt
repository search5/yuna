package com.github.search5.yona.domain.notification

import jakarta.persistence.*

@Entity
@Table(name = "notification_mail")
class NotificationMail(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_event_id", nullable = false)
    var notificationEvent: NotificationEvent
)
