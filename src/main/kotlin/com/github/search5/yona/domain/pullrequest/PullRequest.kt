package com.github.search5.yona.domain.pullrequest

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.enumeration.State
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "pull_request")
class PullRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var title: String = "",

    @Column(columnDefinition = "TEXT")
    var body: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_project_id", nullable = false)
    var toProject: Project,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_project_id", nullable = false)
    var fromProject: Project,

    @Column(nullable = false)
    var toBranch: String = "",

    @Column(nullable = false)
    var fromBranch: String = "",

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contributor_id", nullable = false)
    var contributor: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id")
    var receiver: User? = null,

    var created: Instant? = null,
    var updated: Instant? = null,
    var received: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var state: State = State.OPEN,

    var isConflict: Boolean? = false,
    var isMerging: Boolean? = false,

    var lastCommitId: String? = null,
    var mergedCommitIdFrom: String? = null,
    var mergedCommitIdTo: String? = null,

    var number: Long? = null,

    @ManyToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @JoinTable(
        name = "pull_request_reviewers",
        joinColumns = [JoinColumn(name = "pull_request_id")],
        inverseJoinColumns = [JoinColumn(name = "user_id")],
        uniqueConstraints = [UniqueConstraint(columnNames = ["pull_request_id", "user_id"])]
    )
    var reviewers: MutableSet<User> = mutableSetOf()
)
