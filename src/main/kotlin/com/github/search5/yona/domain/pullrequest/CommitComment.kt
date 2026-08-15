package com.github.search5.yona.domain.pullrequest

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.support.CodeRange
import com.github.search5.yona.domain.user.UserIdent
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "commit_comment")
class CommitComment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    var project: Project? = null,

    var path: String? = null,
    var line: Int? = null,

    @Enumerated(EnumType.STRING)
    var side: CodeRange.Side? = null,

    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    var contents: String = "",

    var createdDate: Instant = Instant.now(),

    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "id", column = Column(name = "author_id")),
        AttributeOverride(name = "loginId", column = Column(name = "author_login_id")),
        AttributeOverride(name = "name", column = Column(name = "author_name"))
    )
    var author: UserIdent? = null,

    var commitId: String = ""
) {
    fun hasLocation(): Boolean = !path.isNullOrBlank() && line != null
}
