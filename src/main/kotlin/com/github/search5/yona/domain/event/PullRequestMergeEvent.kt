package com.github.search5.yona.domain.event

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.user.User

data class PullRequestMergeEvent(
    val pullRequestId: Long,
    val sender: User,
    val isNewPullRequest: Boolean
)

data class RelatedPullRequestMergeEvent(
    val project: Project,
    val branch: String,
    val sender: User
)
