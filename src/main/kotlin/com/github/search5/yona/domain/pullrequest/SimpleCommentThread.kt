package com.github.search5.yona.domain.pullrequest

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.user.UserIdent
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import java.time.Instant

@Entity
@DiscriminatorValue("simple")
class SimpleCommentThread(
    id: Long? = null,
    author: UserIdent? = null,
    state: ThreadState = ThreadState.OPEN,
    createdDate: Instant = Instant.now(),
    pullRequest: PullRequest? = null,
    project: Project? = null,
    reviewComments: MutableList<ReviewComment> = mutableListOf(),
    prevCommitId: String = "",
    commitId: String? = null
) : CommentThread(
    id, author, state, createdDate, pullRequest, project, reviewComments, prevCommitId, commitId
)
