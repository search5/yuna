package com.github.search5.yona.domain.comment

import com.github.search5.yona.domain.issue.IssueComment
import com.github.search5.yona.domain.board.PostingComment
import com.github.search5.yona.domain.user.User

interface CommentService {
    fun createIssueComment(
        issueId: Long,
        contents: String,
        author: User,
        parentCommentId: Long? = null
    ): IssueComment

    fun createPostingComment(
        postingId: Long,
        contents: String,
        author: User,
        parentCommentId: Long? = null
    ): PostingComment

    fun extractMentionedUsers(contents: String): Set<User>
}
