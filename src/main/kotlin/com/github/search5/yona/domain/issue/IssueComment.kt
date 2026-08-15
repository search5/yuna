package com.github.search5.yona.domain.issue

import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.support.Comment
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "issue_comment")
class IssueComment(
    id: Long? = null,
    contents: String = "",
    createdDate: Instant? = null,
    authorId: Long? = null,
    authorLoginId: String? = null,
    authorName: String? = null,
    projectId: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id", nullable = false)
    var issue: Issue,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    var parentComment: IssueComment? = null,

    @ManyToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @JoinTable(
        name = "issue_comment_voter",
        joinColumns = [JoinColumn(name = "issue_comment_id")],
        inverseJoinColumns = [JoinColumn(name = "user_id")]
    )
    var voters: MutableSet<User> = mutableSetOf()
) : Comment(
    id = id,
    contents = contents,
    createdDate = createdDate,
    authorId = authorId,
    authorLoginId = authorLoginId,
    authorName = authorName,
    projectId = projectId
)
