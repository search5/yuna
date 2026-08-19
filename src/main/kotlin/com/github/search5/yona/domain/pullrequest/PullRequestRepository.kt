package com.github.search5.yona.domain.pullrequest

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.user.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PullRequestRepository : JpaRepository<PullRequest, Long> {

    fun findByToProjectAndState(toProject: Project, state: State): List<PullRequest>
    fun countByToProjectAndState(toProject: Project, state: State): Long
    fun findByToProjectAndState(toProject: Project, state: State, pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<PullRequest>
    fun findByToProject(toProject: Project): List<PullRequest>
    fun findByToProject(toProject: Project, pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<PullRequest>
    fun findByToProjectAndNumber(toProject: Project, number: Long): PullRequest?
    fun findByToProjectInAndState(toProjects: List<Project>, state: State, pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<PullRequest>

    fun findFirstByToProjectOrderByNumberDesc(toProject: Project): PullRequest?
    fun findByContributor(contributor: User): List<PullRequest>

    // yona PullRequestApp.closedPullRequests 대응 — CLOSED와 MERGED를 모두 "닫힌 PR"로 취급한다.
    fun findByToProjectAndStateIn(
        toProject: Project,
        states: List<State>,
        pageable: org.springframework.data.domain.Pageable
    ): org.springframework.data.domain.Page<PullRequest>

    // yona PullRequestApp.sentPullRequests 대응 — 이 프로젝트가 출발지(fromProject)인 PR 목록.
    fun findByFromProject(
        fromProject: Project,
        pageable: org.springframework.data.domain.Pageable
    ): org.springframework.data.domain.Page<PullRequest>

    // yona PullRequest.findByFromProjectAndBranch() 대응 (P1-24) — 이미 PR로 추적 중인
    // 브랜치는 별도 PushedBranch 레코드를 만들지 않기 위한 존재 확인.
    fun existsByFromProjectAndFromBranch(fromProject: Project, fromBranch: String): Boolean


    @Query("""
        SELECT pr FROM PullRequest pr 
        WHERE (pr.fromProject = :project AND pr.fromBranch = :branch) 
           OR (pr.toProject = :project AND pr.toBranch = :branch)
           AND pr.state NOT IN ('CLOSED', 'MERGED')
    """)
    fun findRelatedPullRequests(
        @Param("project") project: Project,
        @Param("branch") branch: String
    ): List<PullRequest>
}

