package com.github.search5.yona.domain.mail

sealed class IncomingMailOutcome {
    data class IssueCreated(val issueId: Long, val owner: String, val projectName: String) : IncomingMailOutcome()
    data class IssueCommentCreated(val commentId: Long, val issueId: Long) : IncomingMailOutcome()
    data class PostingCommentCreated(val commentId: Long, val postingId: Long) : IncomingMailOutcome()
    data class Rejected(val reason: String) : IncomingMailOutcome()
    object Duplicate : IncomingMailOutcome()
    object UnknownSender : IncomingMailOutcome()
}
