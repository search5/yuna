package com.github.search5.yona.domain.issue

import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.user.User

interface IssueService {
    fun createIssue(
        issue: Issue,
        author: User,
        assigneeUser: User? = null,
        milestoneId: Long? = null,
        labelIds: List<Long>? = null
    ): Issue

    fun updateIssue(
        issueId: Long,
        title: String,
        body: String,
        updater: User,
        assigneeUser: User? = null,
        milestoneId: Long? = null,
        labelIds: List<Long>? = null
    ): Issue

    fun changeState(issueId: Long, newState: State, updaterLoginId: String): Issue

    fun changeAssignee(issueId: Long, newAssigneeUser: User?, updaterLoginId: String): Issue

    fun changeMilestone(issueId: Long, newMilestoneId: Long?, updaterLoginId: String): Issue

    // yona IssueApp.editIssue()의 hasTargetProject()/moveIssueToOtherProject()/addIssueMovedNotification()
    // 대응 (P1-48). 이슈(및 서브태스크)를 다른 프로젝트로 옮기고, 번호를 새로 매기고, 라벨을 이전하며,
    // ISSUE_MOVED + NEW_ISSUE 알림을 발행한다.
    fun moveIssue(issueId: Long, targetProjectId: Long, mover: User): Issue

    fun voteIssue(issueId: Long, user: User)
    fun unvoteIssue(issueId: Long, user: User)
    fun voteComment(commentId: Long, user: User)
    fun unvoteComment(commentId: Long, user: User)
}
