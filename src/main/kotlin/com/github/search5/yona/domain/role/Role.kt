package com.github.search5.yona.domain.role

import jakarta.persistence.*

@Entity
@Table(name = "role")
class Role(
    @Id
    var id: Long? = null,

    @Column(nullable = false)
    var name: String = "",

    var active: Boolean = true
)
