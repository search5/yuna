package com.github.search5.yona.web

import com.github.search5.yona.domain.enumeration.SearchType
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.support.SearchService
import com.github.search5.yona.domain.user.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// yona-wiki P3-02 4라운드(Step8.5 서버 보강) — `yona search issues/projects`. web/SearchController.kt
// (`/search`, `/{owner}/{projectName}/search` 등)는 Thymeleaf 뷰(`search/list`)를 렌더링하는
// 세션 기반 컨트롤러라 위임 대상으로 쓸 수 없어(응답이 뷰 이름 String), 그 컨트롤러가 이미 쓰는
// SearchService(searchInAll)를 직접 호출해 JSON으로 노출하는 신규 얇은 컨트롤러를 뒀다(신규
// 서비스 로직 없음).
//
// **범위 조정**: yona SearchType enum엔 PROJECT/ISSUE/USER/POST/MILESTONE/ISSUE_COMMENT/
// POST_COMMENT/REVIEW만 있고 "PULL_REQUEST"에 대응하는 값이 없다(PR 자체를 색인하는 통합검색
// 기능이 서버에 아직 없음) - `yona search prs`는 서버에 대응 기능이 없어 이번 라운드 구현 대상에서
// 제외하고 계획 문서에 다음 라운드 이월로 남긴다.
//
// **스코프 인가 갭(계획 문서 리스크 표에 기록)**: 이 엔드포인트는 여러 프로젝트를 가로지르는 전역
// 검색이라 `/api/v1/projects/{owner}/{project}/{resource}` 3세그먼트 모델(저장소 단위 스코프)에
// 자연스럽게 맞지 않는다. `/api/v1/search/**`는 ApiTokenAuthenticationFilter의 어떤 스코프 패턴과도
// 매칭되지 않아 세션 로그인/레거시 전권 토큰으로만 인증되고, Fine-grained 스코프 토큰은 이 경로에서
// 인증되지 않는다(레거시 findByToken 조회가 스코프 토큰의 원문값을 모르므로 자연히 비로그인 취급 -
// 구멍이 아니라 기능 제한, ProjectRestApiController 목록/조회 API의 3라운드 이전 갭과 동일한 성격).
@RestController
@RequestMapping("/api/v1/search")
class SearchRestApiController(
    private val searchService: SearchService,
    private val userRepository: UserRepository
) {

    private fun getLoginUser(authentication: Authentication?) =
        authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }

    @GetMapping("/issues")
    fun searchIssues(
        @RequestParam q: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        authentication: Authentication?
    ): ResponseEntity<Page<Issue>> {
        if (q.isBlank()) return ResponseEntity.badRequest().build()
        val user = getLoginUser(authentication)
        val result = searchService.searchInAll(q, SearchType.ISSUE, user, PageRequest.of(page, size))
        return ResponseEntity.ok(result.issues)
    }

    @GetMapping("/projects")
    fun searchProjects(
        @RequestParam q: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        authentication: Authentication?
    ): ResponseEntity<Page<Project>> {
        if (q.isBlank()) return ResponseEntity.badRequest().build()
        val user = getLoginUser(authentication)
        val result = searchService.searchInAll(q, SearchType.PROJECT, user, PageRequest.of(page, size))
        return ResponseEntity.ok(result.projects)
    }
}
