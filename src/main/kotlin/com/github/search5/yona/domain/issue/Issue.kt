package com.github.search5.yona.domain.issue

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.milestone.Milestone
import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.support.AbstractPosting
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "issue",
    uniqueConstraints = [UniqueConstraint(columnNames = ["project_id", "number"])]
)
class Issue(
    id: Long? = null,
    title: String = "",
    body: String? = null,
    history: String? = null,
    createdDate: Instant? = null,
    updatedDate: Instant? = null,
    authorId: Long? = null,
    authorLoginId: String? = null,
    authorName: String? = null,
    project: Project,
    number: Long? = null,
    numOfComments: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var state: State = State.OPEN,

    var dueDate: Instant? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "milestone_id")
    var milestone: Milestone? = null,

    @ManyToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
    @JoinColumn(name = "assignee_id")
    var assignee: Assignee? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    var parent: Issue? = null,

    var weight: Int = 0,
    var isDraft: Boolean = false,

    @ManyToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @JoinTable(
        name = "issue_issue_label",
        joinColumns = [JoinColumn(name = "issue_id")],
        inverseJoinColumns = [JoinColumn(name = "issue_label_id")]
    )
    var labels: MutableSet<IssueLabel> = mutableSetOf(),

    @ManyToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @JoinTable(
        name = "issue_voter",
        joinColumns = [JoinColumn(name = "issue_id")],
        inverseJoinColumns = [JoinColumn(name = "user_id")]
    )
    var voters: MutableSet<User> = mutableSetOf(),

    @OneToMany(mappedBy = "issue", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var sharers: MutableSet<IssueSharer> = mutableSetOf()
) : AbstractPosting(
    id = id,
    title = title,
    body = body,
    history = history,
    createdDate = createdDate,
    updatedDate = updatedDate,
    authorId = authorId,
    authorLoginId = authorLoginId,
    authorName = authorName,
    project = project,
    number = number,
    numOfComments = numOfComments
)
