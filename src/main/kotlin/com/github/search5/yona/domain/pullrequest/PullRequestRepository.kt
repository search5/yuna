package com.github.search5.yona.domain.pullrequest

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.user.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface PullRequestRepository : JpaRepository<PullRequest, Long>, JpaSpecificationExecutor<PullRequest> {

    fun findByToProjectAndState(toProject: Project, state: State): List<PullRequest>
    fun countByToProjectAndState(toProject: Project, state: State): Long
    fun findByToProjectAndState(toProject: Project, state: State, pageable: Pageable): Page<PullRequest>
    fun findByToProject(toProject: Project): List<PullRequest>
    fun findByToProject(toProject: Project, pageable: Pageable): Page<PullRequest>
    fun findByToProjectAndNumber(toProject: Project, number: Long): PullRequest?
    fun findByToProjectInAndState(toProjects: List<Project>, state: State, pageable: Pageable): Page<PullRequest>

    fun findFirstByToProjectOrderByNumberDesc(toProject: Project): PullRequest?
    fun findByContributor(contributor: User): List<PullRequest>

    // yona PullRequest.java:219-225 findOpendPullRequestsByDaysAgo(user, days) 대응 (P2-38) —
    // (legacy 메서드명과 달리 실제로는 state 필터가 없다, 원문 그대로 이식) 최근 daysAgo일 안에
    // 갱신된 PR만 updated desc/state asc 순으로 반환한다.
    fun findByContributorAndUpdatedGreaterThanEqualOrderByUpdatedDescStateAsc(
        contributor: User,
        since: Instant
    ): List<PullRequest>

    // yona PullRequestApp.closedPullRequests 대응 — CLOSED와 MERGED를 모두 "닫힌 PR"로 취급한다.
    fun findByToProjectAndStateIn(
        toProject: Project,
        states: List<State>,
        pageable: Pageable
    ): Page<PullRequest>

    // yona PullRequestApp.sentPullRequests 대응 — 이 프로젝트가 출발지(fromProject)인 PR 목록.
    fun findByFromProject(
        fromProject: Project,
        pageable: Pageable
    ): Page<PullRequest>

    // yona Project.deletePullRequests()의 PullRequest.findSentPullRequests(this) 대응 (P0-19).
    fun findByFromProject(fromProject: Project): List<PullRequest>

    // yona git/partial_search.scala.html의 PullRequest.count(conditionForAccepted/conditionForSent)
    // 대응(그룹11 #167/#182) — sent 탭 뱃지에 쓰는 "보낸 PR 중 병합된 것" / "보낸 PR 전체" 카운트.
    fun countByFromProject(fromProject: Project): Long
    fun countByFromProjectAndState(fromProject: Project, state: State): Long

    // yona User.findPullRequestContributorsByProjectId(project.id) 대응(그룹11 #167/#182) —
    // partial_search의 "보낸이" 상세검색 드롭다운에 쓰는, 이 프로젝트에 PR을 보낸 적 있는 사용자 목록.
    @Query("SELECT DISTINCT pr.contributor FROM PullRequest pr WHERE pr.toProject = :project")
    fun findDistinctContributorsByToProject(@Param("project") project: Project): List<User>

    // yona PullRequest.findByFromProjectAndBranch() 대응 (P1-24) — 이미 PR로 추적 중인
    // 브랜치는 별도 PushedBranch 레코드를 만들지 않기 위한 존재 확인.
    fun existsByFromProjectAndFromBranch(fromProject: Project, fromBranch: String): Boolean

    // yona PullRequest.findDuplicatedPullRequest() 대응 (P1-68) — 동일한 from/to 프로젝트·브랜치
    // 조합으로 이미 열려있는 PR이 있는지 확인한다(PR 수정 시 브랜치를 재할당할 때 사용).
    fun findByFromBranchAndToBranchAndFromProjectAndToProjectAndState(
        fromBranch: String,
        toBranch: String,
        fromProject: Project,
        toProject: Project,
        state: State
    ): PullRequest?


    @Query("""
        SELECT pr FROM PullRequest pr
        WHERE ((pr.fromProject = :project AND pr.fromBranch = :branch)
           OR (pr.toProject = :project AND pr.toBranch = :branch))
           AND pr.state NOT IN ('CLOSED', 'MERGED')
    """)
    fun findRelatedPullRequests(
        @Param("project") project: Project,
        @Param("branch") branch: String
    ): List<PullRequest>
}

