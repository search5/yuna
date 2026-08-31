package com.github.search5.yona.web

import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.user.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// yona-wiki P3-02 4라운드(Step8.5 서버 보강) — `gh issue status`(내게 배정된/내가 만든 이슈 개요)
// 대응. UserViewController.userIssues()(세션 기반 대시보드 뷰, `issue/my_list` 템플릿 렌더링)가
// 이미 쓰는 것과 동일한 리포지토리 메서드(findByAssigneeAndState/findByAuthorIdAndState + count
// 쌍)만 재사용해 JSON으로 노출한다(신규 서비스 로직 없음).
//
// **범위 조정(계획 문서 지시대로 최소 버전)**: 이번 라운드는 "담당자인 이슈/내가 만든 이슈
// 개수·목록"만 구현한다. UserViewController.userIssues()가 지원하는 나머지 필터
// (mentioned/favorite/shared/commenter)와 페이지네이션·정렬 파라미터 확장은 범위가 커질 수 있어
// 계획 문서에 다음 라운드 이월로 남긴다.
//
// **스코프 인가 갭**: `/api/v1/user/**`는 특정 저장소에 속한 리소스가 아니라(사용자 전역 대시보드)
// `/api/v1/projects/{owner}/{project}/{resource}` 저장소 단위 스코프 모델과 맞지 않는다 - 다른
// 신규 전역 엔드포인트(search/organizations)와 동일하게 세션 로그인/레거시 전권 토큰으로만
// 인증되고 Fine-grained 스코프 토큰은 인증되지 않는다(계획 문서 리스크 표에 기록).
@RestController
@RequestMapping("/api/v1/user/issues")
class UserIssueStatusRestApiController(
    private val issueRepository: IssueRepository,
    private val userRepository: UserRepository
) {

    @GetMapping("/status")
    fun status(authentication: Authentication?): ResponseEntity<Map<String, Any?>> {
        val user = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val userId = user.id!!
        val pageable = PageRequest.of(0, 20)
        val assignedOpen = issueRepository.findByAssigneeAndState(userId, State.OPEN, null, pageable)
        val createdOpen = issueRepository.findByAuthorIdAndState(userId, State.OPEN, null, pageable)

        return ResponseEntity.ok(
            mapOf(
                "assigned" to mapOf(
                    "openCount" to issueRepository.countByAssigneeAndState(userId, State.OPEN),
                    "closedCount" to issueRepository.countByAssigneeAndState(userId, State.CLOSED),
                    "items" to assignedOpen.content
                ),
                "created" to mapOf(
                    "openCount" to issueRepository.countByAuthorIdAndState(userId, State.OPEN),
                    "closedCount" to issueRepository.countByAuthorIdAndState(userId, State.CLOSED),
                    "items" to createdOpen.content
                )
            )
        )
    }
}
