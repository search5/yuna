package com.github.search5.yona.domain.board

import com.github.search5.yona.domain.project.Project
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PostingCommentRepository : JpaRepository<PostingComment, Long> {
    fun findByPostingIdOrderByCreatedDateAsc(postingId: Long): List<PostingComment>
    fun countByPostingId(postingId: Long): Int

    @Query("""
        SELECT pc FROM PostingComment pc 
        WHERE pc.posting.project.id IN :projectIds 
          AND pc.contents LIKE :keyword
    """)
    fun searchPostingComments(@Param("projectIds") projectIds: List<Long>, @Param("keyword") keyword: String, pageable: Pageable): Page<PostingComment>

    @Query("""
        SELECT COUNT(pc) FROM PostingComment pc 
        WHERE pc.posting.project.id IN :projectIds 
          AND pc.contents LIKE :keyword
    """)
    fun countSearchPostingComments(@Param("projectIds") projectIds: List<Long>, @Param("keyword") keyword: String): Int

    @Query("""
        SELECT pc FROM PostingComment pc 
        WHERE pc.posting.project = :project 
          AND pc.contents LIKE :keyword
    """)
    fun searchPostingCommentsInProject(@Param("project") project: Project, @Param("keyword") keyword: String, pageable: Pageable): Page<PostingComment>

    @Query("""
        SELECT COUNT(pc) FROM PostingComment pc 
        WHERE pc.posting.project = :project 
          AND pc.contents LIKE :keyword
    """)
    fun countSearchPostingCommentsInProject(@Param("project") project: Project, @Param("keyword") keyword: String): Int
}
