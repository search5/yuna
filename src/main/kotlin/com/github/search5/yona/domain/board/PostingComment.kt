package com.github.search5.yona.domain.board

import com.github.search5.yona.domain.support.Comment
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "posting_comment")
class PostingComment(
    id: Long? = null,
    contents: String = "",
    createdDate: Instant? = null,
    authorId: Long? = null,
    authorLoginId: String? = null,
    authorName: String? = null,
    projectId: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posting_id", nullable = false)
    var posting: Posting,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    var parentComment: PostingComment? = null
) : Comment(
    id = id,
    contents = contents,
    createdDate = createdDate,
    authorId = authorId,
    authorLoginId = authorLoginId,
    authorName = authorName,
    projectId = projectId
)
