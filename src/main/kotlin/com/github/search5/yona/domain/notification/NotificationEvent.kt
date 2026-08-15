package com.github.search5.yona.domain.notification

import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.enumeration.EventType
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "notification_event")
class NotificationEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var title: String = "",

    var senderId: Long? = null,

    @ManyToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @JoinTable(
        name = "notification_event_n4user",
        joinColumns = [JoinColumn(name = "notification_event_id")],
        inverseJoinColumns = [JoinColumn(name = "n4user_id")]
    )
    var receivers: MutableSet<User> = mutableSetOf(),

    var created: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var resourceType: ResourceType = ResourceType.NOT_A_RESOURCE,

    @Column(nullable = false)
    var resourceId: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var eventType: EventType = EventType.NEW_ISSUE,

    @Lob
    @Column(columnDefinition = "TEXT")
    var oldValue: String? = null,

    @Lob
    @Column(columnDefinition = "TEXT")
    var newValue: String? = null,

    @OneToOne(mappedBy = "notificationEvent", cascade = [CascadeType.ALL])
    var notificationMail: NotificationMail? = null
)
