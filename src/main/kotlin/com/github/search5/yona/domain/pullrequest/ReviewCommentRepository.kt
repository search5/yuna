package com.github.search5.yona.domain.pullrequest

import com.github.search5.yona.domain.project.Project
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ReviewCommentRepository : JpaRepository<ReviewComment, Long> {
    fun findByThreadIdOrderByCreatedDateAsc(threadId: Long): List<ReviewComment>

    @Query("""
        SELECT rc FROM ReviewComment rc 
        WHERE rc.thread.project.id IN :projectIds 
          AND rc.contents LIKE :keyword
     """)
    fun searchReviewComments(@Param("projectIds") projectIds: List<Long>, @Param("keyword") keyword: String, pageable: Pageable): Page<ReviewComment>

    @Query("""
        SELECT COUNT(rc) FROM ReviewComment rc 
        WHERE rc.thread.project.id IN :projectIds 
          AND rc.contents LIKE :keyword
    """)
    fun countSearchReviewComments(@Param("projectIds") projectIds: List<Long>, @Param("keyword") keyword: String): Int

    @Query("""
        SELECT rc FROM ReviewComment rc 
        WHERE rc.thread.project = :project 
          AND rc.contents LIKE :keyword
    """)
    fun searchReviewCommentsInProject(@Param("project") project: Project, @Param("keyword") keyword: String, pageable: Pageable): Page<ReviewComment>

    @Query("""
        SELECT COUNT(rc) FROM ReviewComment rc 
        WHERE rc.thread.project = :project 
          AND rc.contents LIKE :keyword
    """)
    fun countSearchReviewCommentsInProject(@Param("project") project: Project, @Param("keyword") keyword: String): Int
}
