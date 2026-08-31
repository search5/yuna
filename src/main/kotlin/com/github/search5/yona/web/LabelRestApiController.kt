package com.github.search5.yona.web

import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// yona-wiki P3-02 4라운드(Step8.5 서버 보강) — Go CLI `yona label list/create/edit/delete`용
// 신규 범용 REST API(`/api/v1/projects/{owner}/{project}/labels`).
//
// **재검증으로 바로잡은 지점**: 계획 문서 최초 감사표는 이 기능이 web/LabelController.kt에 있다고
// 안내했지만, 실제로 그 파일은 프로젝트와 무관한 전역 라벨/카테고리 자동완성(`/labels`,
// `/categories`, 라벨 입력 위젯용)만 제공한다. 실제 "프로젝트 하나에 속한 라벨" CRUD는
// - 목록: ProjectController.getProjectLabels() (`/api/{owner}/{projectName}/labels`)
// - 생성/수정/삭제: ProjectViewController.newLabel()/updateLabelForm()/deleteLabelForm()
//   (`/{owner}/{projectName}/issue/label(s)/...`, ISSUE_LABEL 기준 AccessControl 체크)
// 에 이미 구현돼 있어(세션/폼 기반이지만 응답은 @ResponseBody로 JSON) 이 컨트롤러는 새 서비스
// 로직 없이 그 메서드들에 위임하는 얇은 어댑터로만 구현한다(Issue/PR REST API와 동일한 패턴).
//
// ApiTokenAuthenticationFilter의 resourceSegmentToResourceType에 "labels" ->
// ResourceType.ISSUE_LABEL(ISSUES 그룹) 매핑을 추가해뒀다 - 위임 대상 메서드들이 실제로 요구하는
// AccessControl 권한(ISSUE_LABEL)과 필터가 부여하는 스코프 그룹이 일치한다.
@RestController
@RequestMapping("/api/v1/projects/{owner}/{project}/labels")
class LabelRestApiController(
    private val projectController: ProjectController,
    private val projectViewController: ProjectViewController
) {

    @GetMapping
    fun list(
        @PathVariable owner: String,
        @PathVariable project: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        return projectController.getProjectLabels(owner, project, authentication)
    }

    @PostMapping
    fun create(
        @PathVariable owner: String,
        @PathVariable project: String,
        @RequestBody request: CreateLabelRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        return projectViewController.newLabel(
            owner, project, request.name, request.color, request.category, request.categoryIsExclusive, authentication
        )
    }

    @PatchMapping("/{id}")
    fun update(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable id: Long,
        @RequestBody request: UpdateLabelRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        return projectViewController.updateLabelForm(owner, project, id, request.name, request.color, request.categoryId, authentication)
    }

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable owner: String,
        @PathVariable project: String,
        @PathVariable id: Long,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        // ProjectViewController.deleteLabelForm()의 _method=delete 파라미터는 HTML Form이 DELETE
        // 메소드를 못 써서 생긴 legacy 오버라이드 관례 - 이 REST API는 실제 HTTP DELETE를 그대로
        // 쓰므로 항상 "delete" 값을 고정으로 넘긴다.
        return projectViewController.deleteLabelForm(owner, project, id, "delete", authentication)
    }

    data class CreateLabelRequest(
        val name: String,
        val color: String,
        val category: String,
        val categoryIsExclusive: Boolean = false
    )

    data class UpdateLabelRequest(
        val name: String,
        val color: String,
        val categoryId: Long
    )
}
