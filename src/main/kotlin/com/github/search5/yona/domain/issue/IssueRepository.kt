package com.github.search5.yona.domain.issue

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.milestone.Milestone
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

import org.springframework.data.jpa.repository.JpaSpecificationExecutor

@Repository
interface IssueRepository : JpaRepository<Issue, Long>, JpaSpecificationExecutor<Issue> {
    fun findByProject(project: Project): List<Issue>
    fun countByProject(project: Project): Long
    fun findByProject(project: Project, pageable: Pageable): Page<Issue>
    fun findByProjectAndState(project: Project, state: State, pageable: Pageable): Page<Issue>
    fun findByProjectAndState(project: Project, state: State): List<Issue>
    fun countByProjectAndState(project: Project, state: State): Long
    fun findByProjectIn(projects: List<Project>, pageable: Pageable): Page<Issue>
    fun findByProjectInAndState(projects: List<Project>, state: State, pageable: Pageable): Page<Issue>
    fun findByProjectAndNumber(project: Project, number: Long): Issue?
    fun findByMilestone(milestone: Milestone): List<Issue>
    fun findByMilestoneAndState(milestone: Milestone, state: State): List<Issue>
    fun findByAuthorId(authorId: Long): List<Issue>

    @Query("""
        SELECT i FROM Issue i 
        WHERE i.project.id IN :projectIds 
          AND (i.title LIKE :keyword 
               OR i.body LIKE :keyword)
    """)
    fun searchIssues(@Param("projectIds") projectIds: List<Long>, @Param("keyword") keyword: String, pageable: Pageable): Page<Issue>

    @Query("""
        SELECT COUNT(i) FROM Issue i 
        WHERE i.project.id IN :projectIds 
          AND (i.title LIKE :keyword 
               OR i.body LIKE :keyword)
    """)
    fun countSearchIssues(@Param("projectIds") projectIds: List<Long>, @Param("keyword") keyword: String): Int

    @Query("""
        SELECT i FROM Issue i 
        WHERE i.project = :project 
          AND (i.title LIKE :keyword 
               OR i.body LIKE :keyword)
    """)
    fun searchIssuesInProject(@Param("project") project: Project, @Param("keyword") keyword: String, pageable: Pageable): Page<Issue>

    @Query("""
        SELECT i FROM Issue i 
        WHERE i.project = :project 
          AND i.state = :state
          AND (i.title LIKE :keyword 
               OR i.body LIKE :keyword)
    """)
    fun searchIssuesInProjectAndState(
        @Param("project") project: Project,
        @Param("state") state: State,
        @Param("keyword") keyword: String,
        pageable: Pageable
    ): Page<Issue>

    @Query("""
        SELECT COUNT(i) FROM Issue i 
        WHERE i.project = :project 
          AND (i.title LIKE :keyword 
               OR i.body LIKE :keyword)
    """)
    fun countSearchIssuesInProject(@Param("project") project: Project, @Param("keyword") keyword: String): Int

    @Modifying
    @Query("update Issue i set i.milestone = null where i.milestone = :milestone")
    fun removeMilestoneFromIssues(@Param("milestone") milestone: Milestone)

    fun findByState(state: State, pageable: Pageable): Page<Issue>

    // 1. 내가 담당자인 이슈
    @Query("""
        SELECT i FROM Issue i 
        WHERE i.assignee.user.id = :assigneeId 
          AND i.state = :state
          AND (:keyword IS NULL OR i.title LIKE :keyword OR i.body LIKE :keyword)
    """)
    fun findByAssigneeAndState(
        @Param("assigneeId") assigneeId: Long,
        @Param("state") state: State,
        @Param("keyword") keyword: String?,
        pageable: Pageable
    ): Page<Issue>

    @Query("""
        SELECT COUNT(i) FROM Issue i 
        WHERE i.assignee.user.id = :assigneeId 
          AND i.state = :state
    """)
    fun countByAssigneeAndState(
        @Param("assigneeId") assigneeId: Long,
        @Param("state") state: State
    ): Long

    // 2. 내가 작성자인 이슈
    @Query("""
        SELECT i FROM Issue i 
        WHERE i.authorId = :authorId 
          AND i.state = :state
          AND (:keyword IS NULL OR i.title LIKE :keyword OR i.body LIKE :keyword)
    """)
    fun findByAuthorIdAndState(
        @Param("authorId") authorId: Long,
        @Param("state") state: State,
        @Param("keyword") keyword: String?,
        pageable: Pageable
    ): Page<Issue>

    @Query("""
        SELECT COUNT(i) FROM Issue i 
        WHERE i.authorId = :authorId 
          AND i.state = :state
    """)
    fun countByAuthorIdAndState(
        @Param("authorId") authorId: Long,
        @Param("state") state: State
    ): Long

    // 3. 내가 댓글을 단 이슈
    @Query("""
        SELECT DISTINCT i FROM Issue i 
        JOIN IssueComment c ON c.issue = i
        WHERE c.authorId = :commenterId 
          AND i.state = :state
          AND (:keyword IS NULL OR i.title LIKE :keyword OR i.body LIKE :keyword)
    """)
    fun findCommentedByState(
        @Param("commenterId") commenterId: Long,
        @Param("state") state: State,
        @Param("keyword") keyword: String?,
        pageable: Pageable
    ): Page<Issue>

    @Query("""
        SELECT COUNT(DISTINCT i) FROM Issue i 
        JOIN IssueComment c ON c.issue = i
        WHERE c.authorId = :commenterId 
          AND i.state = :state
    """)
    fun countCommentedByState(
        @Param("commenterId") commenterId: Long,
        @Param("state") state: State
    ): Long

    // 4. 나를 언급한 이슈 (제목/본문에 @username 또는 댓글에 @username 언급)
    @Query("""
        SELECT DISTINCT i FROM Issue i
        LEFT JOIN IssueComment c ON c.issue = i
        WHERE i.state = :state
          AND (i.title LIKE :mentionKeyword 
               OR i.body LIKE :mentionKeyword 
               OR c.contents LIKE :mentionKeyword)
          AND (:keyword IS NULL OR i.title LIKE :keyword OR i.body LIKE :keyword)
    """)
    fun findMentionedByState(
        @Param("mentionKeyword") mentionKeyword: String,
        @Param("state") state: State,
        @Param("keyword") keyword: String?,
        pageable: Pageable
    ): Page<Issue>

    @Query("""
        SELECT COUNT(DISTINCT i) FROM Issue i
        LEFT JOIN IssueComment c ON c.issue = i
        WHERE i.state = :state
          AND (i.title LIKE :mentionKeyword 
               OR i.body LIKE :mentionKeyword 
               OR c.contents LIKE :mentionKeyword)
    """)
    fun countMentionedByState(
        @Param("mentionKeyword") mentionKeyword: String,
        @Param("state") state: State
    ): Long

    // 5. 내가 보관(favorite)한 이슈
    @Query("""
        SELECT i FROM Issue i
        JOIN i.voters v
        WHERE v.id = :voterId
          AND i.state = :state
          AND (:keyword IS NULL OR i.title LIKE :keyword OR i.body LIKE :keyword)
    """)
    fun findFavoriteByState(
        @Param("voterId") voterId: Long,
        @Param("state") state: State,
        @Param("keyword") keyword: String?,
        pageable: Pageable
    ): Page<Issue>

    @Query("""
        SELECT COUNT(i) FROM Issue i
        JOIN i.voters v
        WHERE v.id = :voterId
          AND i.state = :state
    """)
    fun countFavoriteByState(
        @Param("voterId") voterId: Long,
        @Param("state") state: State
    ): Long

    // 6. 내가 공유받은 이슈
    @Query("""
        SELECT DISTINCT i FROM Issue i
        JOIN i.sharers s
        WHERE s.user.id = :userId
          AND i.state = :state
          AND (:keyword IS NULL OR i.title LIKE :keyword OR i.body LIKE :keyword)
    """)
    fun findSharedByState(
        @Param("userId") userId: Long,
        @Param("state") state: State,
        @Param("keyword") keyword: String?,
        pageable: Pageable
    ): Page<Issue>

    @Query("""
        SELECT COUNT(DISTINCT i) FROM Issue i
        JOIN i.sharers s
        WHERE s.user.id = :userId
          AND i.state = :state
    """)
    fun countSharedByState(
        @Param("userId") userId: Long,
        @Param("state") state: State
    ): Long

    fun countByParentId(parentId: Long): Long
    fun countByParentIdAndState(parentId: Long, state: State): Long
    fun findByParentId(parentId: Long): List<Issue>
}


