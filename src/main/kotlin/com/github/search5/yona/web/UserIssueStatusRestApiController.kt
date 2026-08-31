package com.github.search5.yona.web

import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.mention.MentionService
import com.github.search5.yona.domain.user.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// yona-wiki P3-02 4라운드(Step8.5 서버 보강) — `gh issue status`(내게 배정된/내가 만든 이슈 개요)
// 대응. UserViewController.userIssues()(세션 기반 대시보드 뷰, `issue/my_list` 템플릿 렌더링)가
// 이미 쓰는 것과 동일한 리포지토리 메서드(findByAssigneeAndState/findByAuthorIdAndState + count
// 쌍)만 재사용해 JSON으로 노출한다(신규 서비스 로직 없음).
//
// yona-wiki P3-02 Step8.6 항목2(2026-09-01, 우선순위 2위) — 5라운드가 "최소 버전"으로 남겨둔
// mentioned/favorite/shared/commenter 필터와 페이지네이션/정렬 파라미터를 전부 노출하도록 확장.
//
// **스코프 인가 갭**: `/api/v1/user/**`는 특정 저장소에 속한 리소스가 아니라(사용자 전역 대시보드)
// `/api/v1/projects/{owner}/{project}/{resource}` 저장소 단위 스코프 모델과 맞지 않는다 - 다른
// 신규 전역 엔드포인트(search/organizations)와 동일하게 세션 로그인/레거시 전권 토큰으로만
// 인증되고 Fine-grained 스코프 토큰은 인증되지 않는다(계획 문서 리스크 표에 기록).
@RestController
@RequestMapping("/api/v1/user/issues")
class UserIssueStatusRestApiController(
    private val issueRepository: IssueRepository,
    private val userRepository: UserRepository,
    private val mentionService: MentionService
) {

    // yona-wiki P3-02 Step8.6 항목2(2026-09-01, 우선순위 2위) — `gh issue status` 필터/페이지네이션
    // 전체 노출. UserViewController.userIssues()가 이미 구현해둔 mentioned/favorite/shared/commenter
    // 필터와 pageNum/state/filter/orderBy/orderDir 파라미터를, 신규 백엔드 로직 없이 동일한
    // IssueRepository 메서드 호출에 그대로 전달하는 방식으로 확장했다(5라운드가 최소 버전으로 남겨둔
    // assigned/created 두 섹션은 하위호환을 위해 응답에 그대로 유지하고, commented/mentioned/
    // favorite/shared 4개 섹션을 추가했다).
    //
    // **자기 자신 기준 기본값**: 이 엔드포인트는 "내 이슈 현황" 대시보드라 commenterId/mentionId/
    // sharerId/favoriteId를 명시하지 않으면(웹 UI 대시보드와 달리 다른 사용자를 조회하는 화면이
    // 아니므로) 전부 로그인 사용자 자신으로 기본값을 채운다. userIssues()처럼 파라미터를 명시하면
    // 다른 사용자 id로도 조회 가능하다(웹 UI와 동일한 유연성 유지).
    @GetMapping("/status")
    fun status(
        @RequestParam(required = false, defaultValue = "1") pageNum: Int,
        @RequestParam(required = false, defaultValue = "open") state: String,
        @RequestParam(required = false) filter: String?,
        @RequestParam(required = false, defaultValue = "updatedDate") orderBy: String,
        @RequestParam(required = false, defaultValue = "desc") orderDir: String,
        @RequestParam(required = false) commenterId: Long?,
        @RequestParam(required = false) mentionId: Long?,
        @RequestParam(required = false) sharerId: Long?,
        @RequestParam(required = false) favoriteId: Long?,
        authentication: Authentication?
    ): ResponseEntity<Map<String, Any?>> {
        val user = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val userId = user.id!!
        val page = if (pageNum < 1) 0 else pageNum - 1
        val sort = if (orderDir.equals("asc", ignoreCase = true)) {
            Sort.by(Sort.Direction.ASC, orderBy)
        } else {
            Sort.by(Sort.Direction.DESC, orderBy)
        }
        val pageable = PageRequest.of(page, 20, sort)
        val currentState = State.getValue(state.lowercase())
        val searchKeyword = if (!filter.isNullOrBlank()) "%$filter%" else null

        val effectiveCommenterId = commenterId ?: userId
        val effectiveMentionId = mentionId ?: userId
        val effectiveSharerId = sharerId ?: userId
        val effectiveFavoriteId = favoriteId ?: userId
        val mentionedIssueIds = mentionService.getMentioningIssueIds(effectiveMentionId)

        val assignedPage = issueRepository.findByAssigneeAndState(userId, currentState, searchKeyword, pageable)
        val createdPage = issueRepository.findByAuthorIdAndState(userId, currentState, searchKeyword, pageable)
        val commentedPage = issueRepository.findCommentedByState(effectiveCommenterId, currentState, searchKeyword, pageable)
        val mentionedPage = if (mentionedIssueIds.isEmpty()) {
            Page.empty(pageable)
        } else {
            issueRepository.findMentionedByState(mentionedIssueIds, currentState, searchKeyword, pageable)
        }
        val favoritePage = issueRepository.findFavoriteByState(effectiveFavoriteId, currentState, searchKeyword, pageable)
        val sharedPage = issueRepository.findSharedByState(effectiveSharerId, currentState, searchKeyword, pageable)

        return ResponseEntity.ok(
            mapOf(
                "assigned" to sectionNode(
                    assignedPage,
                    issueRepository.countByAssigneeAndState(userId, State.OPEN),
                    issueRepository.countByAssigneeAndState(userId, State.CLOSED)
                ),
                "created" to sectionNode(
                    createdPage,
                    issueRepository.countByAuthorIdAndState(userId, State.OPEN),
                    issueRepository.countByAuthorIdAndState(userId, State.CLOSED)
                ),
                "commented" to sectionNode(
                    commentedPage,
                    issueRepository.countCommentedByState(effectiveCommenterId, State.OPEN),
                    issueRepository.countCommentedByState(effectiveCommenterId, State.CLOSED)
                ),
                "mentioned" to sectionNode(
                    mentionedPage,
                    if (mentionedIssueIds.isEmpty()) 0L else issueRepository.countMentionedByState(mentionedIssueIds, State.OPEN),
                    if (mentionedIssueIds.isEmpty()) 0L else issueRepository.countMentionedByState(mentionedIssueIds, State.CLOSED)
                ),
                "favorite" to sectionNode(
                    favoritePage,
                    issueRepository.countFavoriteByState(effectiveFavoriteId, State.OPEN),
                    issueRepository.countFavoriteByState(effectiveFavoriteId, State.CLOSED)
                ),
                "shared" to sectionNode(
                    sharedPage,
                    issueRepository.countSharedByState(effectiveSharerId, State.OPEN),
                    issueRepository.countSharedByState(effectiveSharerId, State.CLOSED)
                )
            )
        )
    }

    private fun sectionNode(page: Page<Issue>, openCount: Long, closedCount: Long): Map<String, Any?> {
        return mapOf(
            "openCount" to openCount,
            "closedCount" to closedCount,
            "items" to page.content,
            "totalElements" to page.totalElements,
            "totalPages" to page.totalPages,
            "page" to page.number + 1
        )
    }
}
