package com.github.search5.yona.domain.milestone

import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.project.Project
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface MilestoneRepository : JpaRepository<Milestone, Long> {
    fun findByProject(project: Project): List<Milestone>
    fun countByProject(project: Project): Long
    fun findByProjectAndState(project: Project, state: State): List<Milestone>
    fun findByProjectAndTitle(project: Project, title: String): Milestone?

    @Query("""
        SELECT m FROM Milestone m 
        WHERE m.project.id IN :projectIds 
          AND (m.title LIKE :keyword 
               OR m.contents LIKE :keyword)
     """)
    fun searchMilestones(@Param("projectIds") projectIds: List<Long>, @Param("keyword") keyword: String, pageable: Pageable): Page<Milestone>

    @Query("""
        SELECT COUNT(m) FROM Milestone m 
        WHERE m.project.id IN :projectIds 
          AND (m.title LIKE :keyword 
               OR m.contents LIKE :keyword)
    """)
    fun countSearchMilestones(@Param("projectIds") projectIds: List<Long>, @Param("keyword") keyword: String): Int

    @Query("""
        SELECT m FROM Milestone m 
        WHERE m.project = :project 
          AND (m.title LIKE :keyword 
               OR m.contents LIKE :keyword)
    """)
    fun searchMilestonesInProject(@Param("project") project: Project, @Param("keyword") keyword: String, pageable: Pageable): Page<Milestone>

    @Query("""
        SELECT COUNT(m) FROM Milestone m 
        WHERE m.project = :project 
          AND (m.title LIKE :keyword 
               OR m.contents LIKE :keyword)
    """)
    fun countSearchMilestonesInProject(@Param("project") project: Project, @Param("keyword") keyword: String): Int
}
