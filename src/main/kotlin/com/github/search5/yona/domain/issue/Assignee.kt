package com.github.search5.yona.domain.issue

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.user.User
import jakarta.persistence.*

@Entity
@Table(name = "assignee")
class Assignee(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    var project: Project
)
