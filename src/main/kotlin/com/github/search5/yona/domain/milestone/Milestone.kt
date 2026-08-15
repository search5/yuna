package com.github.search5.yona.domain.milestone

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.enumeration.State
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "milestone",
    uniqueConstraints = [UniqueConstraint(columnNames = ["project_id", "title"])]
)
class Milestone(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var title: String = "",

    var dueDate: Instant? = null,

    @Lob
    @Column(columnDefinition = "TEXT")
    var contents: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var state: State = State.OPEN,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    var project: Project
)
