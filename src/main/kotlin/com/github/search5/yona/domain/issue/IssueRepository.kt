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

    // yona Search.java:112-127 issuesEL()의 "(Project && Keyword) || (Author && Keyword) ||
    // (Assignee && Keyword)" 대응 (P1-81) — 프로젝트 접근권한과 무관하게 본인이 작성했거나
    // 담당자로 지정된 이슈는 항상 검색에 노출된다(equalsUserTemplate()가 익명 사용자는 건너뛰므로
    // userId가 null이면 이 두 분기는 자연히 무효화된다). assignee는 LEFT JOIN으로 명시해야 한다 —
    // 암묵적 경로 탐색(i.assignee.user.id)은 Hibernate가 INNER JOIN으로 컴파일해, OR로 묶은
    // 다른 분기가 매치돼야 할 담당자 없는 이슈까지 통째로 결과에서 사라지게 만든다.
    @Query("""
        SELECT i FROM Issue i
        LEFT JOIN i.assignee a
        LEFT JOIN a.user au
        WHERE (i.project.id IN :projectIds
               AND (i.title LIKE :keyword OR i.body LIKE :keyword))
           OR (:userId IS NOT NULL AND i.authorId = :userId
               AND (i.title LIKE :keyword OR i.body LIKE :keyword))
           OR (:userId IS NOT NULL AND au.id = :userId
               AND (i.title LIKE :keyword OR i.body LIKE :keyword))
    """)
    fun searchIssues(
        @Param("projectIds") projectIds: List<Long>,
        @Param("keyword") keyword: String,
        @Param("userId") userId: Long?,
        pageable: Pageable
    ): Page<Issue>

    @Query("""
        SELECT COUNT(i) FROM Issue i
        LEFT JOIN i.assignee a
        LEFT JOIN a.user au
        WHERE (i.project.id IN :projectIds
               AND (i.title LIKE :keyword OR i.body LIKE :keyword))
           OR (:userId IS NOT NULL AND i.authorId = :userId
               AND (i.title LIKE :keyword OR i.body LIKE :keyword))
           OR (:userId IS NOT NULL AND au.id = :userId
               AND (i.title LIKE :keyword OR i.body LIKE :keyword))
    """)
    fun countSearchIssues(
        @Param("projectIds") projectIds: List<Long>,
        @Param("keyword") keyword: String,
        @Param("userId") userId: Long?
    ): Int

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

    // 4. 나를 언급한 이슈 (yona Mention.getMentioningIssueIds() 대응, P2-41 — 조직/프로젝트 그룹
    // 멘션까지 포함한 실제 멘션 인덱스 테이블 기반. 이전에는 title/body/댓글 LIKE 텍스트 검색으로만
    // 근사해 그룹 멘션(@orgname, @owner/project)으로 간접 멘션된 이슈를 놓쳤다.)
    @Query("""
        SELECT i FROM Issue i
        WHERE i.id IN :mentionedIssueIds
          AND i.state = :state
          AND (:keyword IS NULL OR i.title LIKE :keyword OR i.body LIKE :keyword)
    """)
    fun findMentionedByState(
        @Param("mentionedIssueIds") mentionedIssueIds: List<Long>,
        @Param("state") state: State,
        @Param("keyword") keyword: String?,
        pageable: Pageable
    ): Page<Issue>

    @Query("""
        SELECT COUNT(i) FROM Issue i
        WHERE i.id IN :mentionedIssueIds AND i.state = :state
    """)
    fun countMentionedByState(
        @Param("mentionedIssueIds") mentionedIssueIds: List<Long>,
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

    // yona ProjectApp.getMentionIssueList() 대응 (P1-14): @이슈번호 멘션 자동완성용 최근 이슈 검색
    @Query("""
        SELECT i FROM Issue i
        WHERE i.project = :project
          AND (:query = ''
               OR LOWER(i.title) LIKE LOWER(CONCAT('%', :query, '%'))
               OR CAST(i.number AS string) LIKE CONCAT(:query, '%'))
        ORDER BY i.createdDate DESC
    """)
    fun findForMention(@Param("project") project: Project, @Param("query") query: String, pageable: Pageable): List<Issue>
}


