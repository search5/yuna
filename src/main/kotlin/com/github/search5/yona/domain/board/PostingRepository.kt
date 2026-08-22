package com.github.search5.yona.domain.board

import com.github.search5.yona.domain.project.Project
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PostingRepository : JpaRepository<Posting, Long> {
    fun findByProject(project: Project): List<Posting>
    fun countByProject(project: Project): Long
    fun findByProject(project: Project, pageable: Pageable): Page<Posting>
    fun findByProjectAndNotice(project: Project, notice: Boolean): List<Posting>
    fun findByProjectAndNotice(project: Project, notice: Boolean, pageable: Pageable): Page<Posting>
    fun findByProjectAndNumber(project: Project, number: Long): Posting?
    fun findByProjectIn(projects: List<Project>, pageable: Pageable): Page<Posting>
    fun findByProjectAndReadme(project: Project, readme: Boolean): List<Posting>

    // yona organization/group_board_list.scala.html:65-71 notices 대응 (조직 그룹, TASK-0244) —
    // 조직에서 보이는 프로젝트들의 공지 게시글 전체(페이지 무관, 1페이지에서만 상단에 노출됨).
    fun findByProjectInAndNotice(projects: List<Project>, notice: Boolean): List<Posting>

    // yona organization/group_board_list.scala.html의 param.filter(검색어) + projectNames[](프로젝트
    // 좁히기) 대응 — keyword가 빈 문자열이면 전체를 대상으로 한다.
    @Query("""
        SELECT p FROM Posting p
        WHERE p.project IN :projects
          AND p.notice = false
          AND (:keyword = '' OR p.title LIKE CONCAT('%', :keyword, '%') OR p.body LIKE CONCAT('%', :keyword, '%'))
    """)
    fun findByProjectInAndKeyword(
        @Param("projects") projects: List<Project>,
        @Param("keyword") keyword: String,
        pageable: Pageable
    ): Page<Posting>

    @Query("""
        SELECT p FROM Posting p 
        WHERE (p.project.id IN :projectIds
               AND (p.title LIKE :keyword 
                    OR p.body LIKE :keyword))
           OR (:userId IS NOT NULL AND p.authorId = :userId
               AND (p.title LIKE :keyword OR p.body LIKE :keyword))
    """)
    fun searchPostings(@Param("projectIds") projectIds: List<Long>, @Param("keyword") keyword: String, @Param("userId") userId: Long?, pageable: Pageable): Page<Posting>

    @Query("""
        SELECT COUNT(p) FROM Posting p 
        WHERE (p.project.id IN :projectIds
               AND (p.title LIKE :keyword 
                    OR p.body LIKE :keyword))
           OR (:userId IS NOT NULL AND p.authorId = :userId
               AND (p.title LIKE :keyword OR p.body LIKE :keyword))
    """)
    fun countSearchPostings(@Param("projectIds") projectIds: List<Long>, @Param("keyword") keyword: String, @Param("userId") userId: Long?): Int

    @Query("""
        SELECT p FROM Posting p
        WHERE p.project = :project
          AND p.notice = false
          AND (p.title LIKE :keyword
               OR p.body LIKE :keyword)
    """)
    fun searchPostingsInProject(@Param("project") project: Project, @Param("keyword") keyword: String, pageable: Pageable): Page<Posting>

    @Query("""
        SELECT COUNT(p) FROM Posting p
        WHERE p.project = :project
          AND p.notice = false
          AND (p.title LIKE :keyword
               OR p.body LIKE :keyword)
    """)
    fun countSearchPostingsInProject(@Param("project") project: Project, @Param("keyword") keyword: String): Int

    fun findAllByOrderByCreatedDateDesc(pageable: Pageable): Page<Posting>

    // yona BoardApp.SearchCondition.asExpressionList()의 labelIdSet 필터 대응 (P1-19)
    @Query("""
        SELECT DISTINCT p FROM Posting p
        JOIN p.labels l
        WHERE p.project = :project
          AND p.notice = false
          AND l.id IN :labelIds
          AND (:keyword IS NULL OR p.title LIKE :keyword OR p.body LIKE :keyword)
    """)
    fun findByProjectAndLabelIdsIn(
        @Param("project") project: Project,
        @Param("labelIds") labelIds: List<Long>,
        @Param("keyword") keyword: String?,
        pageable: Pageable
    ): Page<Posting>
}
