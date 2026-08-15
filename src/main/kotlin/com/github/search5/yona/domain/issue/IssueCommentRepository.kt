package com.github.search5.yona.domain.issue

import com.github.search5.yona.domain.project.Project
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface IssueCommentRepository : JpaRepository<IssueComment, Long> {
    fun findByIssueIdOrderByCreatedDateAsc(issueId: Long): List<IssueComment>

    @Query("""
        SELECT ic FROM IssueComment ic 
        WHERE ic.issue.project.id IN :projectIds 
          AND ic.contents LIKE :keyword
    """)
    fun searchIssueComments(@Param("projectIds") projectIds: List<Long>, @Param("keyword") keyword: String, pageable: Pageable): Page<IssueComment>

    @Query("""
        SELECT COUNT(ic) FROM IssueComment ic 
        WHERE ic.issue.project.id IN :projectIds 
          AND ic.contents LIKE :keyword
    """)
    fun countSearchIssueComments(@Param("projectIds") projectIds: List<Long>, @Param("keyword") keyword: String): Int

    @Query("""
        SELECT ic FROM IssueComment ic 
        WHERE ic.issue.project = :project 
          AND ic.contents LIKE :keyword
    """)
    fun searchIssueCommentsInProject(@Param("project") project: Project, @Param("keyword") keyword: String, pageable: Pageable): Page<IssueComment>

    @Query("""
        SELECT COUNT(ic) FROM IssueComment ic 
        WHERE ic.issue.project = :project 
          AND ic.contents LIKE :keyword
    """)
    fun countSearchIssueCommentsInProject(@Param("project") project: Project, @Param("keyword") keyword: String): Int
}
