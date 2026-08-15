package com.github.search5.yona.domain.issue

import com.github.search5.yona.domain.user.User
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "issue_sharer")
class IssueSharer(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    var created: Instant = Instant.now(),

    @Column(nullable = false)
    var loginId: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id", nullable = false)
    var issue: Issue
)
