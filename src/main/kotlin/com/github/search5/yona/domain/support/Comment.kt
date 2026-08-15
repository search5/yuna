package com.github.search5.yona.domain.support

import jakarta.persistence.*
import java.time.Instant

@MappedSuperclass
abstract class Comment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    var contents: String = "",

    var createdDate: Instant? = null,

    var authorId: Long? = null,
    var authorLoginId: String? = null,
    var authorName: String? = null,

    var projectId: Long? = null
)
