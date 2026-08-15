package com.github.search5.yona.domain.project

import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.user.User
import jakarta.persistence.*

@Entity
@Table(name = "project_user")
class ProjectUser(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    var project: Project,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    var role: Role
)
