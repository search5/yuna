package com.github.search5.yona.domain.pullrequest

import java.time.Instant

// yona git/partial_pull_request_event.scala.html이 commentThreads와 함께 PullRequestEvent를
// 병합해 보여주는 것 대응 (P1-106). issue/IssueTimelineItem.kt와 동일한 kind 판별 패턴.
data class PullRequestTimelineItem(
    val kind: String,
    val date: Instant,
    val thread: CommentThread? = null,
    val event: PullRequestEvent? = null
)
