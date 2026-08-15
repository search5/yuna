package com.github.search5.yona.domain.project

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface ProjectRepository : JpaRepository<Project, Long> {
    fun findByOwnerAndName(owner: String, name: String): Optional<Project>
    fun existsByOwnerAndName(owner: String, name: String): Boolean
    fun findByOwner(owner: String): List<Project>

    @Query("""
        SELECT DISTINCT p.id FROM Project p 
        LEFT JOIN p.projectUsers pu 
        LEFT JOIN p.organization o 
        LEFT JOIN o.organizationUsers ou 
        WHERE p.projectScope = com.github.search5.yona.domain.project.ProjectScope.PUBLIC 
           OR (pu.user.id = :userId) 
           OR (ou.user.id = :userId AND p.projectScope = com.github.search5.yona.domain.project.ProjectScope.PROTECTED)
    """)
    fun findAllowedProjectIdsForUser(@Param("userId") userId: Long): List<Long>

    @Query("""
        SELECT p.id FROM Project p 
        WHERE p.projectScope = com.github.search5.yona.domain.project.ProjectScope.PUBLIC
    """)
    fun findPublicProjectIds(): List<Long>

    @Query("""
        SELECT p FROM Project p 
        WHERE p.id IN :projectIds 
          AND (p.name LIKE :keyword 
               OR p.overview LIKE :keyword)
    """)
    fun searchProjects(@Param("projectIds") projectIds: List<Long>, @Param("keyword") keyword: String, pageable: Pageable): Page<Project>

    @Query("""
        SELECT COUNT(p) FROM Project p 
        WHERE p.id IN :projectIds 
          AND (p.name LIKE :keyword 
               OR p.overview LIKE :keyword)
    """)
    fun countSearchProjects(@Param("projectIds") projectIds: List<Long>, @Param("keyword") keyword: String): Int

    @Query("""
        SELECT p FROM Project p 
        WHERE (p.name LIKE :query 
               OR p.owner LIKE :query)
    """)
    fun findProjectsForAdmin(@Param("query") query: String, pageable: Pageable): Page<Project>

    @Query("""
        SELECT COUNT(p) FROM Project p 
        WHERE (p.name LIKE :query 
               OR p.owner LIKE :query)
    """)
    fun countProjectsForAdmin(@Param("query") query: String): Int
}
