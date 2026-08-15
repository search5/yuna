package com.github.search5.yona.domain.issue

import com.github.search5.yona.domain.project.Project
import jakarta.persistence.*

@Entity
@Table(name = "issue_label")
class IssueLabel(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    var category: IssueLabelCategory,

    @Column(nullable = false)
    var color: String = "",

    @Column(nullable = false)
    var name: String = "",

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    var project: Project
)
