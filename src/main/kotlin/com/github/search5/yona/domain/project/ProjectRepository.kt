package com.github.search5.yona.domain.project

import com.github.search5.yona.domain.support.toSnakeCaseSort
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
    fun countByLabelsId(labelId: Long): Long

    // legacy Project.findByOwnerAndOriginalProject(destination, project) 대응 (그룹11 #173) —
    // 특정 소유자(destination) 밑에 이미 이 프로젝트를 원본으로 포크한 프로젝트가 있는지 조회.
    // git/fork.scala.html의 "이미 포크한 프로젝트가 있습니다" 안내 목록에 쓰인다.
    fun findByOwnerAndOriginalProject(owner: String, originalProject: Project): List<Project>

    // yona Project.projectNameChangeable(id, userName, projectName) 대응 (P1-144) — 대소문자 무시
    // 비교(`.ieq(...)`) + 본인(id) 제외(`.ne("id", id)`)로 같은 소유자 내 이름 중복 여부를 검사한다.
    fun existsByOwnerIgnoreCaseAndNameIgnoreCaseAndIdNot(owner: String, name: String, id: Long): Boolean

    // yona Project.findByPreviousPlaceOf(previousOwnerLoginId, previousName) 대응 (P1-76) —
    // 대소문자 무시 비교(yona `.ieq(...)`) + 가장 최근 변경 건 우선(`previousNameChangedTime desc`).
    fun findFirstByPreviousOwnerLoginIdIgnoreCaseAndPreviousNameIgnoreCaseOrderByPreviousNameChangedTimeDesc(
        previousOwnerLoginId: String,
        previousName: String
    ): Optional<Project>

    // yona Project.findByOwnerAndProjectName()의 "현재 이름으로 못 찾으면 예전 이름으로 재시도" 폴백
    // 대응 (P1-76). Kotlin 인터페이스 default 메서드로 둬 모든 호출부가 공용으로 재사용한다.
    fun findByOwnerAndNameOrPreviousPlace(owner: String, name: String): Optional<Project> {
        val direct = findByOwnerAndName(owner, name)
        if (direct.isPresent) {
            return direct
        }
        return findFirstByPreviousOwnerLoginIdIgnoreCaseAndPreviousNameIgnoreCaseOrderByPreviousNameChangedTimeDesc(
            owner, name
        )
    }

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

    // yona Search.projectsEL()의 Application.HIDE_PROJECT_LISTING=true 분기 대응 (P0-23).
    // PUBLIC 프로젝트를 제외하고 "이 사용자가 직접 멤버이거나(모든 scope) 소속 조직이 PROTECTED로
    // 공개한" 프로젝트만 반환한다 — 사이트 전역으로 프로젝트 존재 자체를 숨기는 모드에서 쓰인다.
    @Query("""
        SELECT DISTINCT p.id FROM Project p 
        LEFT JOIN p.projectUsers pu 
        LEFT JOIN p.organization o 
        LEFT JOIN o.organizationUsers ou 
        WHERE (pu.user.id = :userId) 
           OR (ou.user.id = :userId AND p.projectScope = com.github.search5.yona.domain.project.ProjectScope.PROTECTED)
    """)
    fun findAllowedProjectIdsForUserExcludingPublic(@Param("userId") userId: Long): List<Long>

    @Query("""
        SELECT p.id FROM Project p 
        WHERE p.projectScope = com.github.search5.yona.domain.project.ProjectScope.PUBLIC
    """)
    fun findPublicProjectIds(): List<Long>

    // JPQL 대신 네이티브 쿼리를 쓰는 이유는 IssueRepository.searchIssues() 주석 참고 (Postgres
    // Hibernate 7.2.x LIKE 2개 이상 버그 회피 — name/overview 2개 컬럼이라 1개로 인수분해 불가).
    @Query(
        value = "SELECT * FROM project WHERE id IN :projectIds AND (name LIKE :keyword OR overview LIKE :keyword)",
        countQuery = "SELECT COUNT(*) FROM project WHERE id IN :projectIds AND (name LIKE :keyword OR overview LIKE :keyword)",
        nativeQuery = true
    )
    fun searchProjectsQuery(@Param("projectIds") projectIds: List<Long>, @Param("keyword") keyword: String, pageable: Pageable): Page<Project>

    fun searchProjects(projectIds: List<Long>, keyword: String, pageable: Pageable): Page<Project> =
        searchProjectsQuery(projectIds.ifEmpty { listOf(-1L) }, keyword, pageable.toSnakeCaseSort())

    @Query(
        value = "SELECT COUNT(*) FROM project WHERE id IN :projectIds AND (name LIKE :keyword OR overview LIKE :keyword)",
        nativeQuery = true
    )
    fun countSearchProjectsQuery(@Param("projectIds") projectIds: List<Long>, @Param("keyword") keyword: String): Int

    fun countSearchProjects(projectIds: List<Long>, keyword: String): Int =
        countSearchProjectsQuery(projectIds.ifEmpty { listOf(-1L) }, keyword)

    @Query(
        value = "SELECT * FROM project WHERE (name LIKE :query OR owner LIKE :query)",
        countQuery = "SELECT COUNT(*) FROM project WHERE (name LIKE :query OR owner LIKE :query)",
        nativeQuery = true
    )
    fun findProjectsForAdminQuery(@Param("query") query: String, pageable: Pageable): Page<Project>

    fun findProjectsForAdmin(query: String, pageable: Pageable): Page<Project> =
        findProjectsForAdminQuery(query, pageable.toSnakeCaseSort())

    @Query(
        value = "SELECT COUNT(*) FROM project WHERE (name LIKE :query OR owner LIKE :query)",
        nativeQuery = true
    )
    fun countProjectsForAdmin(@Param("query") query: String): Int
}
