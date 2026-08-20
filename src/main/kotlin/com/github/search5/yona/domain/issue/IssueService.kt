package com.github.search5.yona.domain.issue

import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.user.User

interface IssueService {
    // yona AbstractPosting.isPublish(transient)/Issue.isDraft(영속) 대응 (P1-65).
    // isDraft=true면 State.DRAFT로 생성되고(신규 이슈 알림 미발행), 그 외엔 기존과 동일.
    fun createIssue(
        issue: Issue,
        author: User,
        assigneeUser: User? = null,
        milestoneId: Long? = null,
        labelIds: List<Long>? = null,
        isDraft: Boolean = false
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

    // yona IssueApp.editIssue()의 "if (issue.isPublish) { ... }" 발행 전환 대응 (P1-65). 초안을
    // 정식 이슈로 발행한다 — createdDate 재설정, (DRAFT였다면) state DRAFT→OPEN, 프로젝트
    // lastIssueNumber 기준 재채번(생성 시 받았던 번호를 대체), 편집 이력 초기화, NEW_ISSUE 알림 발행.
    fun publishIssue(issueId: Long, publisher: User): Issue

    fun voteIssue(issueId: Long, user: User)
    fun unvoteIssue(issueId: Long, user: User)
    fun voteComment(commentId: Long, user: User)
    fun unvoteComment(commentId: Long, user: User)
}
