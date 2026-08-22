# 템플릿(뷰) 이식 백로그

legacy yona(`/home/jiho/yona-convert/legacy-yona/app/views/**/*.scala.html`, 총 **242개**)의 모든 뷰 템플릿을
yuna(`/home/jiho/yona-convert/yuna/src/main/resources/templates/**/*.html`, Thymeleaf, 현재 104개 존재하나
"대부분 미착수/미검증" 상태)로 **그대로** 이식하기 위한 파일별 작업 순서표.

## 작업 원칙 (반드시 준수)

- **yuna식 독자 구현 금지.** 이 백로그의 모든 항목은 "새 화면을 설계"하는 게 아니라 **legacy 템플릿을 그대로 옮기는** 작업이다.
  레이아웃 구조, 조건 분기, 반복문, 표시되는 필드와 그 순서, 텍스트/메시지 키, CSS 클래스명, JS 훅(`id`/`data-*`/`class`)까지
  전부 legacy 원본을 따른다. "더 낫다고 생각되는" 구조 변경, 필드 생략, 위계 단순화는 금지.
- **Play/Scala 템플릿 문법 → Thymeleaf 문법 치환만 아키텍처적으로 허용되는 변경이다.** 예:
  - `@if(cond) { ... } else { ... }` → `th:if` / `th:unless` (또는 `th:if`+`th:if="${not ...}"` 쌍)
  - `@for(x <- list) { ... }` → `th:each="x : ${list}"`
  - `@helper.form(...)`, `@play20.compat.*` → `th:action`/`method`/CSRF는 Spring Security의 표준 방식으로 치환
  - `@Messages("key", arg)` → `#{key(${arg})}` (기존 `templates/*.html`이 이미 이 컨벤션을 씀, `messages*.properties` 재사용)
  - Ebean 엔티티의 필드/메서드 접근(`project.name`, `issue.state.state()` 등)은 대응하는 JPA 엔티티(`Project`, `Issue` Kotlin 클래스)의
    동일 이름 필드/메서드로 1:1 치환 — 필드가 없으면 백엔드부터 먼저 이식됐는지 `docs/PARITY_BACKLOG.md`에서 확인 후 진행
  - Play 라우트 헬�다(`@routes.IssueApp.issue(...)`) → Thymeleaf `@{...}` URL 표현식, 대응 컨트롤러의 실제 `@GetMapping` 경로 사용
  - legacy가 AJAX로 부분갱신하던 `partial_*.scala.html` 조각은, 대응하는 컨트롤러 엔드포인트가 yuna에 없다면
    **그 엔드포인트 자체도 이식 대상**이다(뷰만 옮기고 끝나지 않음) — 없으면 `docs/PARITY_BACKLOG.md`에 새 항목으로 등록 후 진행
- **"상태 [~]" (yuna에 같은 이름/역할의 파일이 이미 있음)라고 해서 완료로 간주하지 말 것.** 이 세션 이전까지 템플릿 작업은
  거의 검증되지 않았다 — 반드시 legacy 원본과 **줄 단위로 대조**해서 누락된 분기/필드/조각이 없는지 확인하고, 있으면 채워 넣는다.
  대조 결과 완전히 일치하면 그때 백로그 상태를 `[x]`로 바꾸고 완료 로그를 남긴다.
- **"상태 [i]" (legacy의 별도 `partial_*` 파일이 yuna에서는 부모 템플릿에 인라인된 것으로 보임)**: 부모 템플릿이 실제로 그
  조각의 내용을 **전부** 포함하는지 확인 필요. 일부만 들어있거나 아예 없으면 `[ ]`로 취급하고 채워 넣는다(인라인이냐 별도
  파일이냐는 아키텍처 선택으로 허용되지만, 아예 빠진 것은 허용되지 않는다).
- UI는 나중에 실제 서비스에 연결한다는 방침이지만, 이 백로그는 화면 자체를 **지금** 채워 넣는 작업이다 — 컨트롤러가 이미
  해당 뷰 이름을 반환하도록 이식되어 있는 경우가 많으므로(`docs/PARITY_BACKLOG.md` P0~P2 항목들), 뷰만 채우면 바로 붙는다.
- 작업 순서는 **의존성 우선**(레이아웃/공용 파샬 → 화면 도메인)이며, 각 그룹 내부는 대체로 legacy 파일시스템 순서를 따른다.
  番호는 전체 작업 순서(1~242)를 나타낸다 — 반드시 순서대로 할 필요는 없지만, 앞 번호가 뒤 번호에 의존(레이아웃/공용 파샬을
  include)하는 경우가 많아 순서를 지키는 편이 재작업을 줄인다. 단, 레이아웃(그룹1)이 공용 파샬(그룹2, 예: navbar/footer/
  scripts)을 include하는 **역방향 의존**이 실제로 있으므로, 그 경우엔 필요한 공용 파샬을 먼저(또는 함께) 처리한다.
- **TDD로 진행한다.** 파일마다 다음 순서를 지킨다:
  1. legacy `.scala.html` 원본을 읽고, yuna 쪽에 검증 가능한(=CSS 선택자/텍스트/속성으로 단언 가능한) 구체적 차이점을
     찾는다(예: "로그인 시에만 댓글 폼이 보인다", "코드 비공개 프로젝트에서는 CODE 탭이 숨겨진다").
  2. `src/test/kotlin/com/github/search5/yona/web/TemplateEquivalenceSpec.kt`(이미 존재하는 템플릿 렌더링 검증
     하네스 — `AbstractIntegrationTest` 확장, 실제 Spring 컨텍스트+MockMvc로 실제 페이지를 렌더링해 Jsoup으로
     파싱 후 CSS 선택자 단언)에 새 `describe` 블록으로 그 차이점을 검증하는 테스트를 먼저 추가한다. 파일이 아직
     없는 화면이면 컨트롤러가 그 뷰 이름을 반환하는지부터 확인(없으면 `docs/PARITY_BACKLOG.md` 확인 후 컨트롤러도
     함께 이식). 도메인이 크게 다르면(레이아웃, PR/코드리뷰 등) `XxxTemplateEquivalenceSpec.kt`처럼 별도 스펙
     파일을 새로 만들어도 된다(같은 하네스 패턴 재사용).
  3. `./gradlew test --tests "..."`로 레드 확인(템플릿 미비로 실패해야 정상).
  4. 템플릿을 legacy 그대로 채워 넣어 그린 전환.
  5. 영향받는 인접 템플릿/기존 테스트도 함께 재실행해 회귀 확인, 전체 스위트로 마무리 확인.
  6. 이 문서의 상태(`[ ]`/`[~]`/`[i]` → `[x]`)와 진행 로그, 필요시 `docs/PARITY_BACKLOG.md`(새 컨트롤러 엔드포인트를
     이식한 경우)를 갱신하고 커밋한다.

## 상태 기호

| 기호 | 의미 |
|---|---|
| `[ ]` | yuna에 대응 파일 없음 — 신규 작성 |
| `[~]` | yuna에 같은 이름/역할로 추정되는 파일이 있음 — legacy와 줄 단위 대조 + 재작업 필요 |
| `[i]` | legacy의 별도 파일이지만 yuna에서는 다른(상위) 템플릿에 인라인 통합된 것으로 보임 — 포함 여부 확인 필요 |
| `[x]` | legacy와 대조 완료, 완전히 이식됨(이 백로그 작성 시점 기준 0건) |

---

## 그룹 1 — 레이아웃 & 전역 뼈대 (9개, #1~9)

다른 모든 템플릿이 extends/include 하므로 최우선.

| # | 상태 | legacy 경로 | yuna 대상 경로 | 비고 |
|---|---|---|---|---|
| 1 | [~] | `layout.scala.html` | `site/layout.html` | 메인 사이트 레이아웃(navbar/footer/scripts 포함) 대응 여부 확인. siteLayout과 혼동 주의 |
| 2 | [~] | `layout_framed.scala.html` | `site/layout_framed.html` | 상동, "framed"(iframe/팝업용 미니 레이아웃) 버전 |
| 3 | [ ] | `siteLayout.scala.html` | (없음, 신규) | **사이트 관리자(admin)** 전용 레이아웃 — `site/*` 관리자 화면들이 extends. `layout.scala.html`과 다른 파일임에 주의 |
| 4 | [ ] | `siteLayout_framed.scala.html` | (없음, 신규) | 상동 framed 버전 |
| 5 | [ ] | `projectLayout.scala.html` | (없음, 신규) | 프로젝트 컨텍스트 공통 뼈대(project/header 포함해 project/* 전체가 extends) |
| 6 | [ ] | `organizationLayout.scala.html` | (없음, 신규) | 조직 컨텍스트 공통 뼈대 |
| 7 | [ ] | `sidebar.scala.html` | (없음, 신규) | 메인 사이드바(최상위, index/sidebar와 다름) |
| 8 | [~] | `projectMenu.scala.html` | `project/menu.html` | 프로젝트 상단 탭 메뉴(코드/이슈/PR/게시판/마일스톤) |
| 9 | [ ] | `restricted.scala.html` | (없음, 신규) | 접근 제한/게스트 안내 화면 |

## 그룹 2 — `common/*` 공용 파샬 (35개, #10~44)

거의 모든 화면이 하나 이상 include. 레이아웃 다음으로 최우선.

| # | 상태 | legacy 경로 | yuna 대상 경로 | 비고 |
|---|---|---|---|---|
| 10 | [ ] | `common/navbar.scala.html` | `common/navbar.html` | 상단 내비게이션 — layout.html이 include |
| 11 | [ ] | `common/footer.scala.html` | `common/footer.html` | |
| 12 | [ ] | `common/scripts.scala.html` | `common/scripts.html` | 전역 JS 로딩 블록 |
| 13 | [ ] | `common/usermenu.scala.html` | `common/usermenu.html` | 우상단 사용자 드롭다운 |
| 14 | [~] | `common/usermenu_tab_content_list.scala.html` | `common/usermenu_tab_content_list.html` | |
| 15 | [ ] | `common/loginDialog.scala.html` | `common/loginDialog.html` | 로그인 모달 |
| 16 | [~] | `common/select2.scala.html` | `common/select2.html` | select2 위젯 초기화 스니펫 |
| 17 | [~] | `common/calendar.scala.html` | `common/calendar.html` | 날짜선택기 위젯 |
| 18 | [~] | `common/mySeriesMenuTab.scala.html` | `common/mySeriesMenuTab.html` | "내 이슈/PR/마일스톤" 탭 메뉴 |
| 19 | [ ] | `common/markdown.scala.html` | `common/markdown.html` | 마크다운 렌더 영역 공통 래퍼 |
| 20 | [ ] | `common/editor.scala.html` | `common/editor.html` | 마크다운 에디터(툴바 포함) |
| 21 | [ ] | `common/fileUploader.scala.html` | `common/fileUploader.html` | 첨부파일 업로더 위젯 |
| 22 | [ ] | `common/uploadForm.scala.html` | `common/uploadForm.html` | |
| 23 | [ ] | `common/attachmentFile.scala.html` | `common/attachmentFile.html` | 첨부파일 1건 표시 |
| 24 | [ ] | `common/commentForm.scala.html` | `common/commentForm.html` | 댓글 작성 폼(이슈/게시글 공용) |
| 25 | [ ] | `common/commentUpdateForm.scala.html` | `common/commentUpdateForm.html` | 댓글 수정 폼 |
| 26 | [ ] | `common/commentDeleteModal.scala.html` | `common/commentDeleteModal.html` | 댓글 삭제 확인 모달 |
| 27 | [ ] | `common/commentCount.scala.html` | `common/commentCount.html` | |
| 28 | [ ] | `common/commentAndVoterPairDisplay.scala.html` | `common/commentAndVoterPairDisplay.html` | |
| 29 | [ ] | `common/child_commentForm.scala.html` | `common/child_commentForm.html` | 대댓글 작성 폼 |
| 30 | [ ] | `common/childComments.scala.html` | `common/childComments.html` | 대댓글 목록 |
| 31 | [ ] | `common/childCommentsAnchorDiv.scala.html` | `common/childCommentsAnchorDiv.html` | |
| 32 | [ ] | `common/voteCount.scala.html` | `common/voteCount.html` | 이슈 추천수 |
| 33 | [ ] | `common/sharerCount.scala.html` | `common/sharerCount.html` | 이슈 공유대상 수 |
| 34 | [ ] | `common/showSubtasksCheckbox.scala.html` | `common/showSubtasksCheckbox.html` | |
| 35 | [ ] | `common/tasklistBar.scala.html` | `common/tasklistBar.html` | 마크다운 체크리스트 진행률 바 |
| 36 | [ ] | `common/twoColumnModeCheckboxArea.scala.html` | `common/twoColumnModeCheckboxArea.html` | 에디터 2단 모드 토글 |
| 37 | [ ] | `common/issueLabelColor.scala.html` | `common/issueLabelColor.html` | 라벨 색상 표시 |
| 38 | [ ] | `common/commitMsg.scala.html` | `common/commitMsg.html` | 커밋 메시지 표시(이슈 참조 링크화 포함) |
| 39 | [ ] | `common/branchItem.scala.html` | `common/branchItem.html` | 브랜치 1건 표시 |
| 40 | [ ] | `common/reviewForm.scala.html` | `common/reviewForm.html` | 코드리뷰 댓글 폼 |
| 41 | [ ] | `common/partial_history.scala.html` | `common/partial_history.html` | 최근 활동 히스토리 목록(공용) |
| 42 | [ ] | `common/notificationMail.scala.html` | `common/notificationMail.html` | 알림메일 본문 템플릿(HTML 메일) |
| 43 | [ ] | `common/uservoice.scala.html` | `common/uservoice.html` | UserVoice 위젯 스니펫 |
| 44 | [ ] | `common/debug.scala.html` | `common/debug.html` | 디버그용(운영 비노출 가능성 — 그래도 legacy에 있으니 이식) |

## 그룹 3 — `error/*` 에러 페이지 (9개, #45~53)

작고 독립적, 레이아웃만 확정되면 바로 가능.

| # | 상태 | legacy 경로 | yuna 대상 경로 | 비고 |
|---|---|---|---|---|
| 45 | [~] | `error/notfound.scala.html` | `error/404.html` | 파일명 규칙(HTTP 코드 vs 의미명) 차이 — 내용 대조 필수 |
| 46 | [ ] | `error/notfound_default.scala.html` | (없음) | 레이아웃 없는 최소 404(예: framed 컨텍스트) |
| 47 | [~] | `error/forbidden.scala.html` | `error/403.html` | |
| 48 | [ ] | `error/forbidden_default.scala.html` | (없음) | |
| 49 | [ ] | `error/forbidden_organization.scala.html` | (없음) | 조직 컨텍스트 전용 403 변형 |
| 50 | [~] | `error/badrequest.scala.html` | `error/400.html` | |
| 51 | [ ] | `error/badrequest_default.scala.html` | (없음) | |
| 52 | [~] | `error/internalServerError_default.scala.html` | `error/500.html` | |
| 53 | [ ] | `error/requestTextEntityTooLarge.scala.html` | (없음) | 업로드 용량 초과 에러 페이지 |

> yuna `error/401.html`은 legacy에 직접 대응 파일이 없음(legacy는 401을 별도 페이지로 안 두는 것으로 보임) — 이식 작업 중
> legacy 라우팅/에러 핸들러에서 401이 실제로 어떻게 처리되는지(302 로그인 리다이렉트인지) 재확인 필요.

## 그룹 4 — `index/*` 홈/대시보드 (16개, #54~69)

로그인 후 첫 화면.

| # | 상태 | legacy 경로 | yuna 대상 경로 | 비고 |
|---|---|---|---|---|
| 54 | [~] | `index/index.scala.html` | `index.html` | 최상위 위치(yuna는 `index/` 하위가 아니라 루트) — 기존 배치 유지 |
| 55 | [ ] | `index/sidebar.scala.html` | `index/sidebar.html` | 홈 전용 사이드바(그룹1의 최상위 `sidebar.scala.html`과 다른 파일) |
| 56 | [ ] | `index/partial_intro.scala.html` | `index/partial_intro.html` | 비로그인 방문자용 소개 영역 |
| 57 | [ ] | `index/displayProjects.scala.html` | `index/displayProjects.html` | 프로젝트 카드 그리드 공용 렌더러 |
| 58 | [ ] | `index/myProjectList.scala.html` | `index/myProjectList.html` | |
| 59 | [ ] | `index/myProjectList_partial.scala.html` | `index/myProjectList_partial.html` | AJAX 갱신용 |
| 60 | [ ] | `index/myOrganizationList.scala.html` | `index/myOrganizationList.html` | |
| 61 | [ ] | `index/myOrganizationList_partial.scala.html` | `index/myOrganizationList_partial.html` | |
| 62 | [ ] | `index/myRecentIssueList.scala.html` | `index/myRecentIssueList.html` | |
| 63 | [ ] | `index/myRecentIssueList_partial.scala.html` | `index/myRecentIssueList_partial.html` | |
| 64 | [ ] | `index/allProjectList.scala.html` | `index/allProjectList.html` | |
| 65 | [ ] | `index/allProjectList_partial.scala.html` | `index/allProjectList_partial.html` | |
| 66 | [ ] | `index/allOrganizationList.scala.html` | `index/allOrganizationList.html` | |
| 67 | [ ] | `index/allOrganizationList_partial.scala.html` | `index/allOrganizationList_partial.html` | |
| 68 | [~] | `index/notifications.scala.html` | `index/notifications.html` | |
| 69 | [~] | `index/partial_notifications.scala.html` | `index/partial_notifications.html` | |

## 그룹 5 — `user/*` 인증/프로필 (17개, #70~86)

| # | 상태 | legacy 경로 | yuna 대상 경로 | 비고 |
|---|---|---|---|---|
| 70 | [~] | `user/login.scala.html` | `login.html` | 루트 배치는 기존 유지, 내용 대조 |
| 71 | [~] | `user/signup.scala.html` | `signup.html` | |
| 72 | [ ] | `user/verified.scala.html` | `user/verified.html` | 이메일 인증 완료 안내 |
| 73 | [~] | `user/resetPassword.scala.html` | `user/resetPassword.html` | |
| 74 | [~] | `user/edit.scala.html` | `user/edit.html` | |
| 75 | [~] | `user/edit_password.scala.html` | `user/edit_password.html` | |
| 76 | [~] | `user/edit_emails.scala.html` | `user/edit_emails.html` | |
| 77 | [~] | `user/edit_token.scala.html` | `user/edit_token.html` | |
| 78 | [~] | `user/edit_notifications.scala.html` | `user/edit_notifications.html` | |
| 79 | [~] | `user/partial_edit_tabmenu.scala.html` | `user/partial_edit_tabmenu.html` | |
| 80 | [~] | `user/view.scala.html` | `user/view.html` | 프로필 화면 본체 — 아래 5개 partial이 실제로 인라인되어 있는지 확인 |
| 81 | [i] | `user/partial_issues.scala.html` | (view.html에 인라인 추정) | 프로필 "이슈" 탭 |
| 82 | [i] | `user/partial_milestones.scala.html` | (view.html에 인라인 추정) | 프로필 "마일스톤" 탭 |
| 83 | [i] | `user/partial_postings.scala.html` | (view.html에 인라인 추정) | 프로필 "게시글" 탭 |
| 84 | [i] | `user/partial_pullRequests.scala.html` | (view.html에 인라인 추정) | 프로필 "PR" 탭 |
| 85 | [i] | `user/partial_projectlist.scala.html` | (view.html에 인라인 추정) | 프로필 "소속 프로젝트" 탭 |
| 86 | [~] | `user/userFiles.scala.html` | `user/userFiles.html` | 내가 올린 첨부파일 목록 |

> `site/lostPassword.scala.html`은 legacy상 `site/` 밑에 있지만 인증 플로우라 이 그룹에서 함께 처리(#153 참고, yuna는 이미
> `user/lostPassword.html`로 위치 이동해 둠 — 그대로 유지).

## 그룹 6 — `project/*` 프로젝트 (26개, #87~112)

| # | 상태 | legacy 경로 | yuna 대상 경로 | 비고 |
|---|---|---|---|---|
| 87 | [~] | `project/create.scala.html` | `project/create.html` | |
| 88 | [~] | `project/importing.scala.html` | `project/importing.html` | |
| 89 | [~] | `project/list.scala.html` | `project/list.html` | |
| 90 | [ ] | `project/header.scala.html` | `project/header.html` | 프로젝트 상단 헤더(이름/설명/워치버튼 등) — projectLayout이 include |
| 91 | [~] | `project/home.scala.html` | `project/home.html` | 아래 readme/dashboard* 파샬이 실제로 다 들어있는지 줄 단위 대조 |
| 92 | [i] | `project/partial_readme.scala.html` | (home.html에 인라인, P2-42로 로직은 이식됨) | 뷰 마크업 자체가 legacy와 일치하는지 확인 |
| 93 | [i] | `project/partial_dashboard.scala.html` | (home.html에 인라인 추정) | |
| 94 | [i] | `project/partial_dashboard_issuesbyassignee.scala.html` | (home.html에 인라인 추정) | |
| 95 | [i] | `project/partial_dashboard_issuesbylabel.scala.html` | (home.html에 인라인 추정) | |
| 96 | [i] | `project/partial_dashboard_issuesbymilestone.scala.html` | (home.html에 인라인 추정) | |
| 97 | [i] | `project/partial_dashboard_pullrequests.scala.html` | (home.html에 인라인 추정) | |
| 98 | [ ] | `project/partial_history.scala.html` | `project/partial_history.html` | 최근 활동(공용 common/partial_history 재사용 가능성 확인) |
| 99 | [~] | `project/members.scala.html` | `project/members.html` | |
| 100 | [~] | `project/setting.scala.html` | `project/setting.html` | |
| 101 | [~] | `project/partial_settingmenu.scala.html` | `project/setting_menu.html` | 파일명 다름 — 내용 대조 |
| 102 | [~] | `project/change_vcs.scala.html` | `project/change_vcs.html` | |
| 103 | [~] | `project/transfer.scala.html` | `project/transfer.html` | |
| 104 | [~] | `project/delete.scala.html` | `project/delete.html` | |
| 105 | [~] | `project/watchers.scala.html` | `project/watchers.html` | |
| 106 | [~] | `project/webhooks.scala.html` | `project/setting_webhook.html` | 파일명 다름 |
| 107 | [ ] | `project/partial_webhooks_list.scala.html` | (setting_webhook.html에 인라인 추정) | |
| 108 | [~] | `project/issuelabels.scala.html` | `project/issuelabels.html` | |
| 109 | [i] | `project/partial_issuelabels_list.scala.html` | (issuelabels.html에 인라인 추정) | |
| 110 | [i] | `project/partial_issuelabels_editcategory.scala.html` | (issuelabels.html에 인라인 추정) | |
| 111 | [i] | `project/partial_issuelabels_editlabel.scala.html` | (issuelabels.html에 인라인 추정) | |
| 112 | [~] | `project/statistics.scala.html` | `project/statistics.html` | |

## 그룹 7 — `issue/*` 이슈 (30개, #113~142)

| # | 상태 | legacy 경로 | yuna 대상 경로 | 비고 |
|---|---|---|---|---|
| 113 | [~] | `issue/create.scala.html` | `issue/create.html` | |
| 114 | [~] | `issue/edit.scala.html` | `issue/edit.html` | |
| 115 | [~] | `issue/list.scala.html` | `issue/list.html` | |
| 116 | [ ] | `issue/partial_searchform.scala.html` | `issue/partial_searchform.html` | 목록 상단 검색/필터 폼 |
| 117 | [ ] | `issue/partial_list.scala.html` | `issue/partial_list.html` | AJAX 목록 갱신용 |
| 118 | [ ] | `issue/partial_list_wrap.scala.html` | `issue/partial_list_wrap.html` | |
| 119 | [ ] | `issue/partial_list_draft.scala.html` | `issue/partial_list_draft.html` | 초안 이슈 표시 |
| 120 | [ ] | `issue/partial_list_subtask.scala.html` | `issue/partial_list_subtask.html` | |
| 121 | [ ] | `issue/partial_list_quicksearch.scala.html` | `issue/partial_list_quicksearch.html` | |
| 122 | [~] | `issue/partial_massupdate.scala.html` | `issue/partial_massupdate.html` | |
| 123 | [~] | `issue/partial_select_label.scala.html` | `issue/partial_select_label.html` | |
| 124 | [~] | `issue/partial_show_selected_label.scala.html` | `issue/partial_show_selected_label.html` | |
| 125 | [ ] | `issue/partial_select_subtask.scala.html` | `issue/partial_select_subtask.html` | |
| 126 | [~] | `issue/view.scala.html` | `issue/view.html` | 아래 partial들이 실제로 다 들어있는지 대조 |
| 127 | [i] | `issue/partial_assignee.scala.html` | (view.html에 인라인 추정) | |
| 128 | [i] | `issue/partial_comment.scala.html` | (view.html에 인라인 추정) | |
| 129 | [i] | `issue/partial_comments.scala.html` | (view.html에 인라인 추정) | |
| 130 | [i] | `issue/partial_event_timeline.scala.html` | (view.html에 인라인 추정) | 상태변경 이벤트 타임라인 |
| 131 | [ ] | `issue/partial_index_comment.scala.html` | `issue/partial_index_comment.html` | (홈/검색 등에서 쓰는 축약형) |
| 132 | [ ] | `issue/partial_index_comments.scala.html` | `issue/partial_index_comments.html` | |
| 133 | [ ] | `issue/partial_index_event_timeline.scala.html` | `issue/partial_index_event_timeline.html` | |
| 134 | [i] | `issue/partial_view_child.scala.html` | (view.html에 인라인 추정) | 하위 이슈 1건 |
| 135 | [i] | `issue/partial_view_childIssueList.scala.html` | (view.html에 인라인 추정) | 하위 이슈 목록 |
| 136 | [i] | `issue/partial_view_childIssueListOnly.scala.html` | (view.html에 인라인 추정) | AJAX 갱신용 |
| 137 | [i] | `issue/partial_voters.scala.html` | (view.html에 인라인 추정) | |
| 138 | [i] | `issue/partial_voter_list.scala.html` | (view.html에 인라인 추정) | |
| 139 | [~] | `issue/my_list.scala.html` | `issue/my_list.html` | |
| 140 | [~] | `issue/my_partial_list.scala.html` | `issue/my_partial_list.html` | |
| 141 | [~] | `issue/my_partial_list_quicksearch.scala.html` | `issue/my_partial_list_quicksearch.html` | |
| 142 | [~] | `issue/my_partial_search.scala.html` | `issue/my_partial_search.html` | |

## 그룹 8 — `board/*` 게시판 (6개, #143~148)

| # | 상태 | legacy 경로 | yuna 대상 경로 | 비고 |
|---|---|---|---|---|
| 143 | [~] | `board/list.scala.html` | `board/list.html` | |
| 144 | [ ] | `board/partial_list.scala.html` | `board/partial_list.html` | |
| 145 | [~] | `board/create.scala.html` | `board/create.html` | |
| 146 | [~] | `board/edit.scala.html` | `board/edit.html` | |
| 147 | [~] | `board/view.scala.html` | `board/view.html` | |
| 148 | [i] | `board/partial_comments.scala.html` | (view.html에 인라인 추정) | |

## 그룹 9 — `milestone/*` 마일스톤 (5개, #149~153 중 4개 + 사이 여백)

| # | 상태 | legacy 경로 | yuna 대상 경로 | 비고 |
|---|---|---|---|---|
| 149 | [~] | `milestone/list.scala.html` | `milestone/list.html` | |
| 150 | [~] | `milestone/create.scala.html` | `milestone/create.html` | |
| 151 | [~] | `milestone/edit.scala.html` | `milestone/edit.html` | |
| 152 | [~] | `milestone/view.scala.html` | `milestone/view.html` | |
| 153 | [ ] | `milestone/partial_status.scala.html` | `milestone/partial_status.html` | 진행률 표시 |

## 그룹 10 — `code/*` 코드브라우저 (13개, #154~166)

| # | 상태 | legacy 경로 | yuna 대상 경로 | 비고 |
|---|---|---|---|---|
| 154 | [~] | `code/view.scala.html` | `code/view.html` | |
| 155 | [i] | `code/partial_view_file.scala.html` | (view.html에 인라인 추정) | |
| 156 | [i] | `code/partial_view_folder.scala.html` | (view.html에 인라인 추정) | |
| 157 | [~] | `code/branches.scala.html` | `code/branches.html` | |
| 158 | [i] | `code/partial_branchrow.scala.html` | (branches.html에 인라인 추정) | |
| 159 | [~] | `code/history.scala.html` | `code/history.html` | |
| 160 | [~] | `code/diff.scala.html` | `code/diff.html` | |
| 161 | [~] | `code/svnDiff.scala.html` | `code/svnDiff.html` | |
| 162 | [~] | `code/compare.scala.html` | `code/compare.html` | |
| 163 | [~] | `code/compare_svn.scala.html` | `code/compare_svn.html` | |
| 164 | [~] | `code/nohead.scala.html` | `code/nohead.html` | |
| 165 | [~] | `code/nohead_svn.scala.html` | `code/nohead_svn.html` | |
| 166 | [ ] | `code/partial_nonrange_codecomment_thread.scala.html` | `code/partial_nonrange_codecomment_thread.html` | 코드리뷰 스레드(범위 없는 커밋 댓글) |

## 그룹 11 — Pull Request(legacy `git/*`) + 코드리뷰 diff 파샬 + `reviewthread/*` (26개, #167~192)

legacy는 PR/코드리뷰를 `git/` 디렉터리에 둔다(Git 저장소 조작과 PR이 강하게 결합돼 있던 legacy 아키텍처 흔적) — yuna는
`pullrequest/`로 이름을 옮겨 이미 정착시켰으므로 그 배치를 유지한다(경로 이동은 허용된 아키텍처 차이, 내용은 그대로).

| # | 상태 | legacy 경로 | yuna 대상 경로 | 비고 |
|---|---|---|---|---|
| 167 | [~] | `git/list.scala.html` | `pullrequest/list.html` | |
| 168 | [~] | `git/create.scala.html` | `pullrequest/create.html` | |
| 169 | [~] | `git/edit.scala.html` | `pullrequest/edit.html` | |
| 170 | [~] | `git/view.scala.html` | `pullrequest/view.html` | P2-39에서 conversation 탭 로직은 되돌렸음 — 마크업 자체는 legacy와 줄 단위 대조 필요 |
| 171 | [i] | `git/viewChanges.scala.html` | (view.html에 "changes" 탭으로 인라인 추정) | |
| 172 | [ ] | `git/clone.scala.html` | `pullrequest/clone.html` | 클론 방법 안내(HTTP/SSH URL) |
| 173 | [~] | `git/fork.scala.html` | `project/fork.html` | 경로 다름(project 밑) — 내용 대조 |
| 174 | [ ] | `git/partial_branch.scala.html` | `pullrequest/partial_branch.html` | |
| 175 | [ ] | `git/partial_forklist.scala.html` | `pullrequest/partial_forklist.html` | |
| 176 | [ ] | `git/partial_info.scala.html` | `pullrequest/partial_info.html` | |
| 177 | [ ] | `git/partial_list.scala.html` | `pullrequest/partial_list.html` | AJAX 목록 갱신용 |
| 178 | [ ] | `git/partial_merge_result.scala.html` | `pullrequest/partial_merge_result.html` | |
| 179 | [i] | `git/partial_pull_request_event.scala.html` | (view.html에 인라인, P2-39/P1-106 관련) | conversation 탭 되돌림과 함께 재검증 |
| 180 | [ ] | `git/partial_recently_pushed_branches.scala.html` | `pullrequest/partial_recently_pushed_branches.html` | |
| 181 | [ ] | `git/partial_reviewlist.scala.html` | `pullrequest/partial_reviewlist.html` | |
| 182 | [ ] | `git/partial_search.scala.html` | `pullrequest/partial_search.html` | |
| 183 | [ ] | `git/partial_state.scala.html` | `pullrequest/partial_state.html` | PR 상태 뱃지 |
| 184 | [~] | `reviewthread/list.scala.html` | `reviewthread/list.html` | |
| 185 | [ ] | `reviewthread/partial_list.scala.html` | `reviewthread/partial_list.html` | |
| 186 | [ ] | `partial_comment_thread.scala.html`(최상위) | `pullrequest/partial_comment_thread.html` | 코드리뷰 스레드 렌더러(diff 인라인) |
| 187 | [ ] | `partial_comment_form_on_thread.scala.html`(최상위) | `pullrequest/partial_comment_form_on_thread.html` | |
| 188 | [ ] | `partial_diff.scala.html`(최상위) | `pullrequest/partial_diff.html` | |
| 189 | [ ] | `partial_diff_line.scala.html`(최상위) | `pullrequest/partial_diff_line.html` | |
| 190 | [ ] | `partial_diff_comment_on_line.scala.html`(최상위) | `pullrequest/partial_diff_comment_on_line.html` | |
| 191 | [ ] | `partial_filediff.scala.html`(최상위) | `pullrequest/partial_filediff.html` | |
| 192 | [ ] | `partial_update_notification.scala.html`(최상위) | `pullrequest/partial_update_notification.html` | 실시간(폴링) 갱신 알림 배너 |

## 그룹 12 — `organization/*` 조직 (17개, #193~209)

| # | 상태 | legacy 경로 | yuna 대상 경로 | 비고 |
|---|---|---|---|---|
| 193 | [~] | `organization/list.scala.html` | `organization/list.html` | |
| 194 | [~] | `organization/create.scala.html` | `organization/create.html` | |
| 195 | [~] | `organization/view.scala.html` | `organization/view.html` | |
| 196 | [ ] | `organization/header.scala.html` | `organization/header.html` | (view.html 인라인 여부 확인) |
| 197 | [ ] | `organization/menu.scala.html` | `organization/menu.html` | 조직 상단 탭 메뉴 |
| 198 | [~] | `organization/members.scala.html` | `organization/members.html` | |
| 199 | [~] | `organization/setting.scala.html` | `organization/setting.html` | |
| 200 | [~] | `organization/partial_settingmenu.scala.html` | `organization/partial_settingmenu.html` | |
| 201 | [~] | `organization/deleteForm.scala.html` | `organization/delete.html` | 파일명 다름 |
| 202 | [~] | `organization/group_board_list.scala.html` | `organization/boardList.html` | 파일명 다름 |
| 203 | [ ] | `organization/group_board_list_partial.scala.html` | `organization/boardList_partial.html` | |
| 204 | [~] | `organization/group_issue_list.scala.html` | `organization/issueList.html` | |
| 205 | [ ] | `organization/group_issue_list_partial.scala.html` | `organization/issueList_partial.html` | |
| 206 | [ ] | `organization/group_issue_list_quicksearch.scala.html` | `organization/issueList_quicksearch.html` | |
| 207 | [ ] | `organization/group_issue_search_partial.scala.html` | `organization/issueSearch_partial.html` | |
| 208 | [~] | `organization/group_pullrequest_list.scala.html` | `organization/pullRequestList.html` | |
| 209 | [ ] | `organization/group_pullrequest_list_partial.scala.html` | `organization/pullRequestList_partial.html` | |

## 그룹 13 — `site/*` 사이트 관리자 (14개, #210~223)

| # | 상태 | legacy 경로 | yuna 대상 경로 | 비고 |
|---|---|---|---|---|
| 210 | [ ] | `site/siteMngLayout.scala.html` | (없음, 신규) | 관리자 화면 공통 레이아웃(그룹1의 siteLayout과의 관계 확인) |
| 211 | [~] | `site/data.scala.html` | `site/data.html` | |
| 212 | [~] | `site/diagnostic.scala.html` | `site/diagnostic.html` | |
| 213 | [ ] | `site/setting.scala.html` | `site/setting.html` | yuna에 파일 자체가 없음 |
| 214 | [~] | `site/update.scala.html` | `site/update.html` | |
| 215 | [~] | `site/mail.scala.html` | `site/mail.html` | |
| 216 | [~] | `site/massMail.scala.html` | `site/massMail.html` | |
| 217 | [~] | `site/userList.scala.html` | `site/userList.html` | |
| 218 | [~] | `site/projectList.scala.html` | `site/projectList.html` | |
| 219 | [~] | `site/postList.scala.html` | `site/postList.html` | |
| 220 | [~] | `site/issueList.scala.html` | `site/issueList.html` | |
| 221 | [~] | `site/lostPassword.scala.html` | `user/lostPassword.html` | 위치 이동됨(그룹5와 중복 체크 — 여기서는 표만) |
| 222 | [ ] | `site/partial_pagination.scala.html` | `site/partial_pagination.html` | 공용 페이지네이션 컨트롤 |
| 223 | [ ] | `site/partial_paginationForUserList.scala.html` | `site/partial_paginationForUserList.html` | |

## 그룹 14 — `search/*` 통합검색 (10개, #224~233)

| # | 상태 | legacy 경로 | yuna 대상 경로 | 비고 |
|---|---|---|---|---|
| 224 | [~] | `search/result.scala.html` | `search/list.html` | 파일명 다름 — legacy는 탭별 partial을 아래서 include, yuna 412줄 단일 파일이 다 흡수했는지 대조 |
| 225 | [ ] | `search/partial_search.scala.html` | `search/partial_search.html` | 검색창/탭 헤더 |
| 226 | [i] | `search/partial_projects.scala.html` | (list.html에 인라인 추정) | |
| 227 | [i] | `search/partial_issues.scala.html` | (list.html에 인라인 추정) | |
| 228 | [i] | `search/partial_issue_comments.scala.html` | (list.html에 인라인 추정) | |
| 229 | [i] | `search/partial_posts.scala.html` | (list.html에 인라인 추정) | |
| 230 | [i] | `search/partial_post_comments.scala.html` | (list.html에 인라인 추정) | |
| 231 | [i] | `search/partial_milestones.scala.html` | (list.html에 인라인 추정) | |
| 232 | [i] | `search/partial_reviews.scala.html` | (list.html에 인라인 추정) | |
| 233 | [i] | `search/partial_users.scala.html` | (list.html에 인라인 추정) | |

## 그룹 15 — `help/*` 도움말 (5개, #234~238)

정적 콘텐츠 위주, 우선순위 낮지만 작업량도 적음.

| # | 상태 | legacy 경로 | yuna 대상 경로 | 비고 |
|---|---|---|---|---|
| 234 | [~] | `help/toc.scala.html` | `help/toc.html` | |
| 235 | [~] | `help/markdown.scala.html` | `help/markdown.html` | |
| 236 | [~] | `help/keymap.scala.html` | `help/keymap.html` | |
| 237 | [~] | `help/UIKit.scala.html` | `help/UIKit.html` | |
| 238 | [~] | `help/experimental.scala.html` | `help/experimental.html` | |

## 그룹 16 — `migration/*` (2개, #239~240)

| # | 상태 | legacy 경로 | yuna 대상 경로 | 비고 |
|---|---|---|---|---|
| 239 | [ ] | `migration/migrationPageLayout.scala.html` | (없음, 신규) | migration 화면 전용 레이아웃 |
| 240 | [~] | `migration/home.scala.html` | `migration/home.html` | |

## 그룹 17 — `welcome/*` 초기 설치 (2개, #241~242)

| # | 상태 | legacy 경로 | yuna 대상 경로 | 비고 |
|---|---|---|---|---|
| 241 | [~] | `welcome/secret.scala.html` | `bootstrap-setup.html` | 최초 관리자 계정 설정 화면으로 추정 — 정확한 대응 여부 확인 |
| 242 | [~] | `welcome/restart.scala.html` | `bootstrap-restart.html` | |

---

## 진행 로그

작업을 진행하면서 이 섹션에 그룹/파일 단위로 완료 기록을 남긴다(형식은 `docs/PARITY_BACKLOG.md`의 완료 로그와 동일한
톤 — 원인/구현 내용/legacy와 다르게 처리한 지점과 근거/검증 방법을 명시).

(아직 없음)
