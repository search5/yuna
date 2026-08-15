package com.github.search5.yona.domain.support

import com.github.search5.yona.domain.project.Project
import jakarta.persistence.*
import java.time.Instant

@MappedSuperclass
abstract class AbstractPosting(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var title: String = "",

    @Lob
    @Column(columnDefinition = "TEXT")
    var body: String? = null,

    @Lob
    @Column(columnDefinition = "TEXT")
    var history: String? = null,

    var createdDate: Instant? = null,
    var updatedDate: Instant? = null,

    var authorId: Long? = null,
    var authorLoginId: String? = null,
    var authorName: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    var project: Project,

    var number: Long? = null,

    var numOfComments: Int = 0
)
