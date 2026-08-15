package com.github.search5.yona.domain.project

import com.github.search5.yona.domain.organization.Organization
import com.github.search5.yona.domain.user.User
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "project")
class Project(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var name: String = "",

    @Column(columnDefinition = "TEXT")
    var overview: String? = null,

    var vcs: String? = null,
    var siteurl: String? = null,
    var owner: String? = null,

    var createdDate: Instant? = null,

    var lastIssueNumber: Long = 0,
    var lastPostingNumber: Long = 0,

    var isCodeAccessibleMemberOnly: Boolean = false,

    var lastPushedDate: Instant? = null,

    var defaultReviewerCount: Int = 1,
    var isUsingReviewerCount: Boolean = false,

    var isCodeEnabled: Boolean = true,
    var isIssueEnabled: Boolean = true,
    var isPullRequestEnabled: Boolean = true,
    var isReviewEnabled: Boolean = true,
    var isMilestoneEnabled: Boolean = true,
    var isBoardEnabled: Boolean = true,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    var organization: Organization? = null,

    @Enumerated(EnumType.STRING)
    var projectScope: ProjectScope = ProjectScope.PRIVATE,

    @OneToMany(mappedBy = "project", cascade = [CascadeType.ALL], orphanRemoval = true)
    var projectUsers: MutableList<ProjectUser> = mutableListOf(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_project_id")
    var originalProject: Project? = null,

    @OneToMany(mappedBy = "originalProject", cascade = [CascadeType.ALL])
    var forkingProjects: MutableList<Project> = mutableListOf(),

    @ManyToMany(mappedBy = "enrolledProjects")
    var enrolledUsers: MutableList<User> = mutableListOf(),

    @ManyToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @JoinTable(
        name = "project_label",
        joinColumns = [JoinColumn(name = "project_id")],
        inverseJoinColumns = [JoinColumn(name = "label_id")]
    )
    var labels: MutableSet<Label> = mutableSetOf()
) {
    val isPrivate: Boolean
        get() = projectScope == ProjectScope.PRIVATE

    val isPublic: Boolean
        get() = projectScope == ProjectScope.PUBLIC

    val isProtected: Boolean
        get() = projectScope == ProjectScope.PROTECTED
}
