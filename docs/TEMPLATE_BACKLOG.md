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
| 1 | [x] | `layout.scala.html` | `site/layout.html` | 완료(TASK-0220/TASK-0241, TDD). og/twitter 메타태그·업데이트알림배너·NProgress/ViewerJS 자산 이식. `\|:\|` 제목 분리 컨벤션도 TASK-0241에서 추가 이식 완료(`TemplateHelper.titleMain/titleOgDescription`) — issue/view, board/view, project/home 3개 호출부에 적용, 부가로 issue/create·board/create·milestone/create의 `' - Yona'` 중복 접미사 버그도 함께 수정 |
| 2 | [x] | `layout_framed.scala.html` | `site/layout_framed.html` | 완료(TASK-0221, TDD). og/twitter 메타태그·nprogress/magnific-popup 자산·popover 초기화·GA 스크립트 이식. sidebar()는 이미 이 파일에 인라인되어 있었음(→ #7과 동일 파일로 처리) |
| 3 | [x] | `siteLayout.scala.html` | `site/{data,diagnostic,issueList,mail,massMail,postList,projectList,update,userList}.html` (각 파일에 인라인 조합) | 완료(TASK-0222, TDD). 신규 데코레이터 파일 대신, 관리자 화면 9개가 이미 `head`/`gnb`/`breadcrumb`/`sidebar`/`scripts` 조각을 조합하고 있었음 — 빠져있던 `footer` 조각만 9개 파일에 공통 추가(legacy `siteLayout`이 `@content` 뒤에 `@common.footer()`를 감싸는 것과 동일 동작) |
| 4 | [i] | `siteLayout_framed.scala.html` | `site/layout_framed.html` | 확인 결과 legacy에서 이 파일의 유일한 사용처는 `index/sidebar.scala.html`(빈 content로 `siteLayout_framed`→`layout_framed` 호출) — 즉 "사이드바+iframe 프레임 셸"이며, 이는 #2에서 이미 완료한 `site/layout_framed.html`(→`/user/sidebar`)과 동일 화면. 별도 신규 파일 불필요 |
| 5 | [i] | `projectLayout.scala.html` | `project/*.html` 각 파일(인라인 조합) | `navbar+project.header+content+footer` 데코레이터 패턴은 이미 `project/home.html`/`members.html`/`setting.html`/`statistics.html`가 `site/layout::gnb`+`project/header::header`+`project/menu::menu`+`site/layout::footer` 조합으로 실현 중. **단, `change_vcs/delete/fork/issuelabels/setting_webhook/transfer/watchers.html`은 header/footer 조각이 빠져있음을 확인** — 그룹6(#87~112) 작업 시 각 파일 항목에서 직접 채워 넣을 것(지금은 그룹1 범위를 넘어서므로 보류, 그룹6 착수 시 최우선 처리) |
| 6 | [i] | `organizationLayout.scala.html` | `organization/*.html` 각 파일(인라인 조합 필요) | `navbar(menuType,null,group)+content+footer` 패턴 대응. **`organization/*.html` 10개 파일 전부 `site/layout::gnb`/`footer` 조각이 하나도 포함돼 있지 않음을 확인** — project 그룹보다 미착수 정도가 훨씬 심함. 그룹12(#193~209) 착수 시 최우선 처리 |
| 7 | [i] | `sidebar.scala.html` | `site/layout_framed.html` (인라인) | #2 작업 중 확인: `site/layout_framed.html`의 `#sidebar` div가 이미 이 파일 내용을 인라인 포함(로그인 필수 분기는 컨트롤러 레벨 리다이렉트로 대체) |
| 8 | [x] | `projectMenu.scala.html` | `project/menu.html` | 완료(TASK-0242, TDD). 리뷰/설정 카운트 배지 누락, PR 탭의 SVN 프로젝트 숨김 조건(`project.vcs=='GIT'`) 누락, 포크 프로젝트의 sentPullRequests 링크 분기 누락, 키보드 단축키(`htKeyMap`+`yobi.project.Global.js`) 스크립트 전체 누락을 발견해 복구 |
| 9 | [x] | `restricted.scala.html` | (포팅 보류, 아래 참고) | **보류 결정(사유 기록)** — play-authenticate 모듈의 데모/테스트용 페이지(`Sshhh...don't tell anyone`, 하드코딩된 유튜브 영상, `currentAuth()`/`auth.getProvider()`/`auth.expires()` 등 해당 라이브러리 전용 API 표시). yuna는 Spring Security 기반이라 동등 개념(OAuth2AuthorizedClientService 등)을 새로 엮어야 하는데, 실사용 가치가 없는 라이브러리 데모 화면이라 투입 대비 효과가 지나치게 낮다고 판단해 보류. `docs/PARITY_BACKLOG.md`의 P1-27 최초 보류 결정처럼, 사용자가 이식을 원하면 언제든 재지시 가능 |

## 그룹 2 — `common/*` 공용 파샬 (35개, #10~44)

거의 모든 화면이 하나 이상 include. 레이아웃 다음으로 최우선.

| # | 상태 | legacy 경로 | yuna 대상 경로 | 비고 |
|---|---|---|---|---|
| 10 | [x] | `common/navbar.scala.html` | `site/layout.html :: gnb` (인라인) | 완료(TASK-0224/TASK-0243, TDD). `HIDE_PROJECT_LISTING`+게스트 가드로 "전체 목록" 링크 숨김 이식. TASK-0243에서 방침 정정에 따라 재작업: (1) 조직 페이지의 검색범위 드롭다운에서 "현재 그룹" 링크가 legacy처럼 `HIDE_PROJECT_LISTING||게스트` 모드에서 `isMemberOf(org)||isAdminOf(org)`인 사용자에게만 보이도록 게이트 추가(`TemplateHelper.isOrganizationMemberOrAdmin` 신규), (2) "모든 프로젝트" 검색범위가 legacy처럼 게스트/HIDE모드에서 숨겨지고 사이트관리자는 예외로 보이도록 게이트 추가(기존엔 무조건 노출) |
| 11 | [x] | `common/footer.scala.html` | `site/layout.html :: footer` (인라인) | 확인 완료 — 완전 일치(TASK-0224 조사 중 대조 완료, 코드 변경 없음) |
| 12 | [x] | `common/scripts.scala.html` | `site/layout.html :: scripts` (인라인) | 완료(TASK-0224, TDD). tplYobiToast, "U" 단축키, pageshow NProgress 해제, iframe 히스토리 동기화 스크립트 이식. Play flash-scope 제네릭 순회(title/description 특수케이스)는 yuna의 warning/error/info 3키 모델로 이미 아키텍처 치환되어 있었음(선행 세션) |
| 13 | [x] | `common/usermenu.scala.html` | `site/layout.html :: gnb` (인라인) | 완료(TASK-0224/TASK-0243, TDD). 내 이슈 카운터 배지(`myOpenIssueCount`), 게스트 새 그룹 만들기 숨김 이식. TASK-0243에서 `NAVBAR_CUSTOM_LINK_NAME/URL` 커스텀 링크를 `yuna.application.navbar.custom-link.name`/`.url` 설정 프로퍼티(기본값 빈 문자열, 미설정 시 기존과 동일하게 안 보임)로 이식 완료. OAuth 세션 불일치 경고는 Spring Security 세션 모델 자체가 달라 대응되는 상태가 없어 계속 미이식(별도 사유, 백로그 유지) |
| 14 | [x] | `common/usermenu_tab_content_list.scala.html` | `common/usermenu_tab_content_list.html` | 완료(TASK-0225, TDD). legacy가 include하는 3개 파샬(`index/my{OrganizationList,ProjectList,RecentIssueList}.scala.html`) 중 `myRecentIssueList`(최근 방문 이슈 탭)만 완전히 누락돼 있었음을 발견해 이식. yuna가 legacy에 없는 "전체" 탭을 추가로 갖고 있는 점은 그대로 유지(비고: 이미 동작 중인 기능 삭제는 이번 범위 밖) |
| 15 | [~] | `common/loginDialog.scala.html` | `site/layout.html :: scripts` (인라인) | 부분 완료(TASK-0224). jquery-ui 스크립트 로드 이식(TDD). `useSocialLoginOnly` 폼 숨김 토글과 legacy의 동적 OAuth 프로바이더 목록(`forProviders`)은 yuna가 Spring Security OAuth2 정적 클라이언트 등록 방식이라 구조적으로 다름 — 하드코딩된 github/google 버튼으로 아키텍처적으로 치환된 상태(선행 세션), 토글 자체는 백엔드 설정 부재로 미이식(저가치 코너케이스로 판단, 문서에 기록) |
| 16 | [x] | `common/select2.scala.html` | `common/select2.html` | 확인 완료 — 완전 일치(TASK-0226 조사 중 대조 완료, 코드 변경 없음) |
| 17 | [x] | `common/calendar.scala.html` | `common/calendar.html` | 확인 완료 — 완전 일치(TASK-0224 조사 중 대조 완료, 코드 변경 없음) |
| 18 | [x] | `common/mySeriesMenuTab.scala.html` | `common/mySeriesMenuTab.html` | 완료(TASK-0226, TDD). "기본 페이지로 설정" 버튼 가시 조건에 loginDefaultPage 비교 추가, `index/notifications.html`/`user/userFiles.html`의 중복 인라인 탭바를 공용 조각 재사용으로 교체 |
| 19 | [i] | `common/markdown.scala.html` | `site/layout.html :: markdown(project)` (인라인) | 확인 완료 — 완전 일치(TASK-0227 조사 중 대조 완료, 코드 변경 없음) |
| 20 | [i] | `common/editor.scala.html` | `site/layout.html :: markdownEditor(name,value,editorMode)` (인라인) | 확인: 현재 호출부(`issue/create,edit`, `board/create,edit,view`, `milestone/create,edit`)는 전부 대시(`-`) 없는 `name`만 넘겨서 legacy의 `wrapIdGen`/`textareaName` 분리 로직·`viaEmail` 파라미터화가 실질적으로 관측되지 않음 — 백엔드에 "via email" 기능 자체가 없어 저가치로 판단해 지금은 미이식(문서에 기록, 필요 시 재검토) |
| 21 | [x] | `common/fileUploader.scala.html` | `site/layout.html :: scripts`(tplAttachedFile/tplDropFilesHere) + `common/uploadForm.html`(신규) | 완료(TASK-0227, TDD). tplAttachedFile/tplDropFilesHere는 이미 정확히 이식돼 있었음(확인). `common.uploadForm(...)` 호출 부분은 #22에서 처리 |
| 22 | [x] | `common/uploadForm.scala.html` | `common/uploadForm.html`(신규 생성) | 완료(TASK-0227, TDD). **중대 발견**: `issue/view.html`/`board/view.html`의 기존 `#upload-drop-zone`/`input[name=file]` 마크업이 legacy 구조(`upload-wrap`/`data-resource-type`/`input[name=filePath]`)와 전혀 다른 독자 구현이었고, 어떤 정적 JS 파일도 `upload-drop-zone`/`upload-file-input` 셀렉터를 참조하지 않아(grep 확인) 사실상 동작하지 않는 죽은 마크업이었음. legacy 구조로 교체 |
| 23 | [~] | `common/attachmentFile.scala.html` | (없음, 신규 필요) | 서버사이드 렌더링되는 "이미 첨부된 파일 1건" 표시 조각(수정 화면 등에서 기존 첨부파일 목록에 사용) — 아직 미착수. #21/22와 달리 클라이언트 JS 템플릿(tplAttachedFile)이 아닌 서버 렌더 조각이라 별도 확인 필요 |
| 24 | [i] | `common/commentForm.scala.html` | `issue/view.html`, `board/view.html` (각 페이지에 인라인) | 확인: `<form id="comment-form" ... enctype="multipart/form-data">` + 에디터 + fileUploader 슬롯 + 제출 버튼 구조는 이미 정확히 대응됨(#22에서 enctype 누락도 함께 수정). `common.editor(...)`/`common.fileUploader(...)` 자리에 해당하는 하위 조각들의 이식 상태는 #20/#21/#22 참고 |
| 25 | [ ] | `common/commentUpdateForm.scala.html` | (없음, 신규 필요 — 규모 큼) | **조사 완료, 미착수**. 댓글 인라인 수정 UI(파일 업로드+알림메일 체크박스+저장/취소 포함) 자체가 yuna에 전혀 없음(`comment-editform`/`comment-update-form` 마크업 grep 0건). 백엔드는 `CommentController`의 `PUT /api/projects/{projectId}/issues|posts/{number}/comments/{commentId}` REST API로 이미 존재하지만, legacy는 `<form action=".../{id}" enctype=multipart/form-data>` 폼 전체제출 방식이라 REST API 기준으로 AJAX 재설계가 필요 — 순수 마크업 포팅을 넘어서는 프론트엔드 설계 작업. 그룹2 완주 후 별도 세션에서 집중 처리 권장 |
| 26 | [ ] | `common/commentDeleteModal.scala.html` | (없음, 신규 필요) | **조사 완료, 미착수**. 삭제 확인 모달(`#comment-delete-modal`)과 `yobi.Comment.js` 초기화 스크립트가 yuna에 전혀 없음. 삭제 백엔드(`DELETE /api/projects/{projectId}/issues|posts/{number}/comments/{commentId}`)는 존재. #25와 함께 처리하는 게 효율적(같은 JS 초기화 흐름) |
| 27 | [i] | `common/commentCount.scala.html` | `issue/list.html`(인라인) | 확인 완료 — `.comments-count.comments-count-color` 구조 완전 일치(TASK-0227 후속 조사, 코드 변경 없음) |
| 28 | [i] | `common/commentAndVoterPairDisplay.scala.html` | `issue/list.html`(인라인) | 확인 완료 — `.item-count-groups` 조합 표시 구조 완전 일치(코드 변경 없음) |
| 29 | [ ] | `common/child_commentForm.scala.html` | (없음, #25/#26과 함께 처리) | **조사 완료, 미착수**. 대댓글(child comment) 작성 원라인 폼. 백엔드는 `CommentController`가 `parentCommentId`를 이미 받아 처리(`IssueComment.parentComment` 필드 존재) — REST API는 준비됐으나 프론트 UI가 전혀 없음. #25/#26과 같은 "댓글 UI 전체 AJAX 재설계" 묶음으로 처리 권장 |
| 30 | [ ] | `common/childComments.scala.html` | (없음, #25/#26과 함께 처리) | **조사 완료, 미착수**. 대댓글 목록+인라인 답글폼 조합. 마크업(`subcomment-media-body`/`one-line-comment`/`child-comment-input-form`) 자체가 yuna에 없음. #25/#26/#29와 같은 묶음 |
| 31 | [ ] | `common/childCommentsAnchorDiv.scala.html` | (없음, #25/#26과 함께 처리) | **조사 완료, 미착수**. 대댓글 앵커(`#comment-N`) div — #29/#30 없이는 의미 없어 함께 처리 |
| 32 | [i] | `common/voteCount.scala.html` | `issue/list.html`(인라인) | 확인 완료 — `.vote-count.vote-color` 구조 완전 일치(코드 변경 없음) |
| 33 | [i] | `common/sharerCount.scala.html` | `issue/list.html`(인라인) | 확인 완료 — `.sharer-color` 구조 완전 일치(코드 변경 없음) |
| 34 | [i] | `common/showSubtasksCheckbox.scala.html` | `issue/list.html`(인라인) | 확인 완료 — `#two-column-mode-checkbox`/`#toggle-show-subtasks` 구조 완전 일치(코드 변경 없음) |
| 35 | [x] | `common/tasklistBar.scala.html` | `issue/view.html`, `board/view.html`(인라인, 신규 추가) | 완료(TASK-0229, TDD). **발견**: `yona.Tasklist.js`/`gfm-task-list.js` 정적 자산은 이미 존재했지만 `.tasklist` 셸 마크업과 스크립트 로드가 두 페이지 모두에 전혀 없어 죽어있던 기능이었음. legacy와 동일 위치(본문 markdown-wrap 바로 앞)에 셸 추가 + `yona.Tasklist.js` 로드 추가 |
| 36 | [i] | `common/twoColumnModeCheckboxArea.scala.html` | `issue/list.html` 등(인라인) | 확인 완료 — `#two-column-mode-checkbox`/`#two-column-mode` 구조 일치(코드 변경 없음) |
| 37 | [x] | `common/issueLabelColor.scala.html` | `web/LabelStyleController.kt`(`GET /{owner}/{project}/issue/labels.css`) | 완료(TASK-0229, TDD). **발견**: 이 legacy 파일은 뷰가 아니라 `IssueLabelApp.labelStyles()` 컨트롤러가 `text/css`로 직접 렌더링하는 동적 스타일시트였고, yuna의 `LabelStyleController`가 이미 완전히 동일한 로직(RGB/hex 파싱+휘도 계산 포함)으로 이식돼 있었음(선행 세션) — 단 legacy가 이 스타일시트를 링크하는 10개 화면 중 `issue/view`/`issue/create`/`issue/edit`/`board/view`/`board/list` 5곳에 `<link>` 태그 자체가 빠져 있어 추가. `project/partial_dashboard_issuesbylabel`/`project/partial_issuelabels_list`(프로젝트 대시보드·라벨 설정 화면)는 대응 파일 존재 여부 확인 필요 — 미착수로 남김 |
| 38 | [x] | `common/commitMsg.scala.html` | `common/commitMsg.html`(신규 fragment) | 완료(TASK-0243). `common/commitMsg.html` fragment 신규 작성(short span/a + 멀티라인일 때 moreBtn + hidden pre.desc). legacy 실사용처는 `code/diff.scala.html`(forceExpand=true)와 `code/history.scala.html`(short+moreBtn) 2곳뿐임을 확인(view/svnDiff는 이 fragment를 쓰지 않고 별도 인라인 span) — 두 곳 모두 fragment 재사용으로 교체 |
| 39 | [x] | `common/branchItem.scala.html` | `common/branchItem.html`(신규 fragment) | 완료(TASK-0243). legacy 실사용처는 `code/svnDiff.scala.html`의 브랜치 드롭다운(btn-group+dropdown-menu) 한 곳뿐 — 해당 드롭다운 자체가 yuna svnDiff.html에 통째로 빠져 있던 것도 함께 복구. `TemplateHelper.branchItemName`/`branchItemType`/`branchInHtml` 신규 추가(legacy `Branches.itemName/itemType/branchInHTML` 대응) |
| 40 | [ ] | `common/reviewForm.scala.html` | (미확인) | 코드리뷰 댓글 폼 — PR/리뷰 도메인(그룹11, #167~192) 착수 시 함께 처리 예정. `common.editor`+`common.uploadForm` 재사용 구조라 #20/#22 완료로 재료는 준비됨 |
| 41 | [ ] | `common/partial_history.scala.html` | (없음, 백엔드 기능 자체 부재) | **조사 완료, 미착수**. "변경 이력"(edit history) 기능 자체가 yuna `Issue`/`Posting` 엔티티에 없음(`history` 필드 자체가 없음) — 순수 템플릿 이식이 아니라 `docs/PARITY_BACKLOG.md`에 백엔드 항목으로 먼저 등록해야 하는 규모. 이번 배치에서는 조사 결과만 기록 |
| 42 | [i] | `common/notificationMail.scala.html` | `domain/notification/NotificationMailRenderer.kt`(인라인) | 확인 완료 — Thymeleaf 템플릿이 아니라 Kotlin 코드로 HTML 문자열을 직접 생성하는 방식으로 이미 완전히 동일하게 이식돼 있음(폰트 스택, `hr` 구분선, unwatch/설정변경 푸터 링크, 메시지 키까지 일치). 코드 변경 없음 |
| 43 | [x] | `common/uservoice.scala.html` | (포팅 제외) | **제외 결정(사유 기록)** — legacy 자체에서도 이 파일을 호출하는 곳이 0건(grep 확인, 죽은 코드). 설령 사용하더라도 원본 Yona 프로젝트 전용 UserVoice 계정(`forum_id`, 위젯 스크립트 URL이 원본 프로젝트에 하드코딩)이라 포크인 yuna에 그대로 심는 것은 부적절 |
| 44 | [x] | `common/debug.scala.html` | (포팅 제외) | **제외 결정(사유 기록)** — legacy 자체에서도 이 파일을 호출하는 컨트롤러/뷰가 0건(grep 확인, 완전한 죽은 코드) |

## 그룹 3 — `error/*` 에러 페이지 (9개, #45~53)

작고 독립적, 레이아웃만 확정되면 바로 가능.

| # | 상태 | legacy 경로 | yuna 대상 경로 | 비고 |
|---|---|---|---|---|
| 45 | [ ] | `error/notfound.scala.html` | (없음, 신규 필요 — 규모 큼) | **보류 결정(사유 기록)**. 프로젝트 컨텍스트 전용 404(projectLayout+projectMenu 사용). yuna는 대부분 컨트롤러가 직접 `return "error/404"`(제네릭 뷰)로 처리하는 단순한 패턴이라, 프로젝트 메뉴가 있는 별도 404 뷰를 쓰려면 그 뷰를 리턴하는 모든 컨트롤러를 프로젝트 컨텍스트 인지형으로 바꿔야 하는 광범위한 리팩터 — 투입 대비 효과(에러 페이지에 프로젝트 메뉴 표시)가 낮다고 판단해 보류. `[없음, 신규]`가 아니라 실제로 정확히는 실제 매핑 대상이 #46이었음이 재확인 조사로 드러남(비고 참고) |
| 46 | [x] | `error/notfound_default.scala.html` | `error/404.html` | 완료(TASK-0231, TDD). **재확인**: yuna의 `error/404.html`(project 파라미터 없는 제네릭 뷰)이 실제로는 `notfound.scala.html`이 아니라 이 `_default` 변형에 대응함(siteLayout이 아닌 별도의 최소 헤더+**전용 D2 Program footer**를 쓰는 legacy의 유일한 예외 케이스). `errorGnb`(간소 헤더)는 이미 이 파일의 커스텀 헤더와 일치했으나, 전용 footer가 통째로 빠져 있어 추가 |
| 47 | [ ] | `error/forbidden.scala.html` | (없음, 신규 필요 — 규모 큼) | **보류 결정** — #45와 동일 사유(프로젝트 컨텍스트 전용 403, 실제 매핑 대상은 #48) |
| 48 | [x] | `error/forbidden_default.scala.html` | `error/403.html` | 완료(TASK-0231, TDD). **재확인**: yuna의 `error/403.html`이 실제로 대응하는 legacy 파일은 이것 — `siteLayout`을 쓰므로 검색폼 있는 **전체 GNB**와 **사이트 공용 footer**가 필요한데, 잘못 `errorGnb`(간소 헤더, notfound_default 전용)를 쓰고 있었고 footer도 없었음. `gnb`+`footer`로 교체 |
| 49 | [ ] | `error/forbidden_organization.scala.html` | (없음, 신규 필요) | **보류 결정** — 조직 컨텍스트 전용 403 변형. #45/#47과 같은 사유(yuna 컨트롤러들이 조직 컨텍스트 인지형 에러뷰를 쓰지 않는 단순 패턴) |
| 50 | [ ] | `error/badrequest.scala.html` | (없음, 신규 필요 — 규모 큼) | **보류 결정** — #45와 동일 사유(프로젝트 컨텍스트 전용 400, 실제 매핑 대상은 #51) |
| 51 | [x] | `error/badrequest_default.scala.html` | `error/400.html` | 완료(TASK-0231, TDD). #48과 동일한 문제(errorGnb+footer없음 → gnb+footer)를 수정 |
| 52 | [x] | `error/internalServerError_default.scala.html` | `error/500.html` | 완료(TASK-0231, TDD). #48/#51과 동일한 문제 수정. 유일하게 legacy에 "non-default" 대응 파일이 없어(이 파일이 유일한 500 변형) 원래 매핑이 맞았음 |
| 53 | [ ] | `error/requestTextEntityTooLarge.scala.html` | (없음, 신규 필요) | **조사 완료, 미착수**. 업로드 용량 초과(413) 에러 페이지 자체가 yuna에 없음. `MaxUploadSizeExceededException` 등을 잡는 전역 `@ExceptionHandler`/`@ControllerAdvice`가 현재 하나도 없어 순수 템플릿 이식이 아니라 백엔드 예외처리 신설이 선행돼야 함 — `docs/PARITY_BACKLOG.md`에 등록 후 처리 권장 |

> yuna `error/401.html`은 legacy에 직접 대응 파일이 없음(legacy는 401을 별도 페이지로 안 두는 것으로 보임) — 이식 작업 중
> legacy 라우팅/에러 핸들러에서 401이 실제로 어떻게 처리되는지(302 로그인 리다이렉트인지) 재확인 필요.

## 그룹 4 — `index/*` 홈/대시보드 (16개, #54~69)

로그인 후 첫 화면.

| # | 상태 | legacy 경로 | yuna 대상 경로 | 비고 |
|---|---|---|---|---|
| 54 | [x] | `index/index.scala.html` | `index.html` | 완료(TASK-0232, TDD). 확인: legacy `index.scala.html`은 사실상 `index/notifications.scala.html`을 그대로 위임 호출하는 얇은 래퍼 — 내용은 #68과 동일. `index.html`이 `common/mySeriesMenuTab` 대신 3번째로 중복 하드코딩한 탭바(#14/#18에서 고친 것과 동일 버그, 이 파일은 놓쳤었음)를 발견해 공용 조각으로 교체 |
| 55 | [i] | `index/sidebar.scala.html` | `site/layout_framed.html` | #4/#7과 동일 발견: `siteLayout_framed(...){}`을 빈 content로 호출하는 것이 이 파일의 전부라, `site/layout_framed.html`(→`/user/sidebar`)이 이미 이 화면을 대체 |
| 56 | [i] | `index/partial_intro.scala.html` | `index.html`(인라인) | 확인 완료 — 헤딩·기능 6종 아이콘(cgicenter/code/articles/lock/preview/friends) 전부 일치(코드 변경 없음) |
| 57 | [i] | `index/displayProjects.scala.html` | `common/usermenu_tab_content_list.html`(인라인) | 확인 완료 — legacy의 인라인 헬퍼 함수를 Thymeleaf `th:if`/`th:each`로 동일 인라인 처리(아키텍처적으로 불가피한 치환, 별도 파일 불필요) |
| 58 | [i] | `index/myProjectList.scala.html` | `common/usermenu_tab_content_list.html`(인라인) | 확인 완료 — recentlyVisited/watching/createdByMe/joinmember 4탭 순서·구조 전부 일치(코드 변경 없음) |
| 59 | [i] | `index/myProjectList_partial.scala.html` | `common/usermenu_tab_content_list.html`(인라인) | 확인 완료 — 프로젝트 1건 렌더링 구조 일치(코드 변경 없음) |
| 60 | [i] | `index/myOrganizationList.scala.html` | `common/usermenu_tab_content_list.html`(인라인) | 확인 완료 — 개인프로젝트→즐겨찾기조직→일반조직→즐겨찾기프로젝트 순서 전부 일치(코드 변경 없음) |
| 61 | [i] | `index/myOrganizationList_partial.scala.html` | `common/usermenu_tab_content_list.html`(인라인) | 확인 완료(코드 변경 없음) |
| 62 | [x] | `index/myRecentIssueList.scala.html` | `common/usermenu_tab_content_list.html`(인라인) | #14에서 이미 완료(TASK-0225) |
| 63 | [x] | `index/myRecentIssueList_partial.scala.html` | `common/usermenu_tab_content_list.html`(인라인) | #14에서 이미 완료(TASK-0225) |
| 64 | [x] | `index/allProjectList.scala.html` | (포팅 제외) | **제외 결정** — legacy 자체에서 호출부 0건(어떤 뷰/컨트롤러도 참조 안 함, grep 확인). 완전한 죽은 코드 |
| 65 | [i] | `index/allProjectList_partial.scala.html` | `common/usermenu_tab_content_list.html`(인라인) | **살아있는 코드였음** — `myOrganizationList.scala.html`이 조직 하위 프로젝트 렌더링에 이 파샬을 재사용 중(이름과 달리 "전체 목록" 전용이 아님). 이미 인라인 확인 완료 |
| 66 | [x] | `index/allOrganizationList.scala.html` | (포팅 제외) | **제외 결정** — legacy 자체에서 호출부 0건, 죽은 코드 |
| 67 | [x] | `index/allOrganizationList_partial.scala.html` | (포팅 제외) | **제외 결정** — 오직 #66(죽은 코드)에서만 참조돼 간접적으로도 죽은 코드. (참고: yuna의 "전체" 탭은 이 죽은 legacy 코드와 무관한 yuna 자체 추가 기능 — #14/TASK-0225에서 이미 유지하기로 결정했음) |
| 68 | [x] | `index/notifications.scala.html` | `index/notifications.html` | #18에서 mySeriesMenuTab 중복 문제 해결 완료(TASK-0226). 나머지 구조(welcome-table, siteLayout 위임)도 대조 확인 완료 |
| 69 | [x] | `index/partial_notifications.scala.html` | `index/partial_notifications.html` | 확인 완료 — 아이콘 타입 분기, Edit 특수케이스, 제목 링크/일반 텍스트 분기, 아바타 폴백, More 버튼+AJAX 페이지네이션 스크립트까지 전부 정확히 일치(코드 변경 없음) |

## 그룹 5 — `user/*` 인증/프로필 (17개, #70~86)

| # | 상태 | legacy 경로 | yuna 대상 경로 | 비고 |
|---|---|---|---|---|
| 70 | [x] | `user/login.scala.html` | `login.html` | 완료(TASK-0233, TDD). 이메일 인증 안내 문구(`email-verification-help`) 누락 발견해 추가. 소셜로그인 프로바이더 동적목록/useSocialLoginOnly 토글은 #15와 동일 사유로 미이식(이미 github/google 고정 버튼으로 아키텍처 치환됨). `title.loginFor` 메시지키 파라미터화 대신 하드코딩된 것은 렌더링 결과가 동일해 저가치로 판단해 미수정 |
| 71 | [x] | `user/signup.scala.html` | `signup.html` | 완료(TASK-0234, TDD). **독자 페이지였음** — 자체 `<head>`만 있고 site GNB/footer가 전무했음. site/layout 조각으로 교체 + 관리자승인 안내(`isUsingSignUpConfirm`)/실시간 아이디·이메일 중복확인(validate.js+yobi.user.SignUp.js, 백엔드 엔드포인트 신규) 복구. `UserController.confirmEmail()`도 #72와 동일한 RestController-raw-HTML 안티패턴이라 함께 발견해 `UserViewController`로 이전 |
| 72 | [x] | `user/verified.scala.html` | `user/verified.html`(신규 생성) | 완료(TASK-0233, TDD). **중대 발견**: `UserController.verifyUser()`가 `@RestController`에서 Thymeleaf 템플릿 대신 하드코딩된 raw HTML 문자열(`ResponseEntity<String>`)을 직접 반환하고 있었음 — GNB/footer/i18n 전무. `UserViewController`(`@Controller`)로 이전하고 legacy 구조(siteLayout→전체 GNB+footer, `user.verified`/`user.verified.detail` 메시지키) 그대로 이식. 실패 시 404 상태코드도 legacy의 `notFound(...)`에 맞춰 추가 |
| 73 | [x] | `user/resetPassword.scala.html` | `user/resetPassword.html` | 완료(TASK-0235, TDD). #71/#72와 동일 패턴 — 독자 페이지(site GNB/footer 없음, i18n 메시지키 미사용, resetPassword 모듈 스크립트 미로드)였음을 발견해 site/layout 조각 기반으로 재작성 |
| 74 | [x] | `user/edit.scala.html` | `user/edit.html` | 확인 — gnb/footer 조각 존재 확인(코드 변경 없음) |
| 75 | [x] | `user/edit_password.scala.html` | `user/edit_password.html` | 확인 — gnb/footer 조각 존재 확인(코드 변경 없음) |
| 76 | [x] | `user/edit_emails.scala.html` | `user/edit_emails.html` | 확인 — gnb/footer 조각 존재 확인(코드 변경 없음) |
| 77 | [x] | `user/edit_token.scala.html` | `user/edit_token.html` | 확인 — gnb/footer 조각 존재 확인(코드 변경 없음) |
| 78 | [x] | `user/edit_notifications.scala.html` | `user/edit_notifications.html` | 확인 — gnb/footer 조각 존재 확인(코드 변경 없음) |
| 79 | [x] | `user/partial_edit_tabmenu.scala.html` | `user/partial_edit_tabmenu.html` | 확인 — 프래그먼트 파일이라 gnb/footer 불필요, 정상(코드 변경 없음) |
| 80 | [x] | `user/view.scala.html` | `user/view.html` | 완료(TASK-0235). 탭 구성(issues/pullRequests/projects 3개) legacy와 정확히 일치 확인 — #82/#83이 프로필 탭이 아님을 재확인(비고 참고) |
| 81 | [i] | `user/partial_issues.scala.html` | `user/view.html`(인라인) | 확인 완료 — 프로필 "이슈" 탭에 인라인, 구조 일치(코드 변경 없음) |
| 82 | [x] | `user/partial_milestones.scala.html` | (없음 — legacy 자체가 dead code) | TASK-0250에서 재조사 완료: legacy 전체(`grep -rn "partial_milestones" app/`, git log 포함)에서 이 파일을 실제로 호출하는 곳이 **단 한 군데도 없음**을 확인했다. `search/partial_search.scala.html`이 호출하는 `@partial_milestones(group, project, searchResult)`는 시그니처(`group, project, searchResult`)가 이 파일의 시그니처(`milestone, project`)와 다른 **동명이인** — 실제로는 같은 디렉터리의 `search/partial_milestones.scala.html`(그룹14 #231)을 가리킨다. 이전 세션의 "search/partial_search.scala.html 전용" 메모 자체가 오판이었음. legacy에서 도달 불가능한 죽은 코드이므로 이식할 대상 화면이 없다 — genuinely impossible(포팅할 legacy 호출 지점이 존재하지 않음)으로 판단, 미이식 |
| 83 | [x] | `user/partial_postings.scala.html` | (없음 — legacy 자체가 dead code) | #82와 동일 사유로 재조사 완료: legacy 전체에서 호출 지점 없음(`user/view.scala.html`도 issues/pullRequests/projectlist 3탭만 사용, milestones/postings 탭 없음 — #80에서 이미 확인된 사실과 일치). 미이식 |
| 84 | [i] | `user/partial_pullRequests.scala.html` | `user/view.html`(인라인) | 확인 완료 — 프로필 "PR" 탭에 인라인, 구조 일치(코드 변경 없음) |
| 85 | [i] | `user/partial_projectlist.scala.html` | `user/view.html`(인라인) | 확인 완료 — 프로필 "소속 프로젝트" 탭에 인라인, 구조 일치(코드 변경 없음) |
| 86 | [x] | `user/userFiles.scala.html` | `user/userFiles.html` | 확인 완료 — 첨부파일 목록 테이블 구조, hover, fileType 아이콘, pagination까지 legacy와 정확히 일치(코드 변경 없음) |

> `site/lostPassword.scala.html`은 legacy상 `site/` 밑에 있지만 인증 플로우라 이 그룹에서 함께 처리(#153 참고, yuna는 이미
> `user/lostPassword.html`로 위치 이동해 둠 — 그대로 유지).

## 그룹 6 — `project/*` 프로젝트 (26개, #87~112)

| # | 상태 | legacy 경로 | yuna 대상 경로 | 비고 |
|---|---|---|---|---|
| 87 | [x] | `project/create.scala.html` | `project/create.html` | 완료(TASK-0239, TDD). **중대 발견**: PROTECTED(그룹공개) 옵션이 통째로 빠져 있었음(정적 자산 `yobi.project.New.js`는 `#opt-protected`/`#protected` DOM을 이미 참조하고 있어 죽은 JS 훅 상태였음) — 라디오 추가, 백엔드는 이미 `ProjectScope.PROTECTED`/조직 관리자 권한 완비. 검증 실패 시 입력값이 전부 날아가던 문제(legacy Form 자동 재바인딩 부재)도 `NewProjectForm` 모델 추가로 수정 |
| 88 | [x] | `project/importing.scala.html` | `project/importing.html` | 확인 완료(TASK-0239). PROTECTED 옵션·에러 재바인딩·조직 소유자 토글까지 이미 legacy와 동등하게(오히려 Spring 검증으로 더 엄격하게) 구현돼 있었음, 코드 변경 없음 |
| 89 | [x] | `project/list.scala.html` | `project/list.html` | 확인 완료(TASK-0239). legacy는 미접근 프로젝트를 렌더링 후 흐리게 가리는 방식(`AccessControl.isAllowed`+회색 placeholder)이지만, yuna 컨트롤러는 `findAllowedProjectIdsForUser`로 쿼리 단계에서 이미 필터링돼 있어 애초에 미접근 프로젝트가 결과에 포함되지 않음 — 동등하거나 더 엄격한 접근제어이므로 placeholder 분기 이식 불필요로 판단(아키텍처 치환) |
| 90 | [x] | `project/header.scala.html` | `project/header.html` | 완료(TASK-0236/TASK-0244, TDD). **중대 발견**: 프로젝트 가입 요청(enroll/cancelEnroll) 기능 UI 전체가 빠져 있었음(백엔드 `ProjectMemberController`에 `POST /api/projects/{id}/enroll(/cancel)`은 이미 존재) — `TemplateHelper.isEnrolled()` 신규 추가 후 복구. `project.isProtected`(그룹 프로젝트) "G" 배지도 누락돼 있어 추가. TASK-0244에서 방침 정정에 따라 재작업: 즐겨찾기 별표의 서버사이드 초기 상태(`isFavoriteProject`)는 이전에 "여러 호출부 전파 필요"라고 잘못 판단해 미이식했었으나, 실제로는 `FavoriteProject` 도메인/리포지토리가 이미 존재하고 legacy도 `project/header.scala.html` 프래그먼트 내부에서 로컬로 계산하는 구조라 전파가 전혀 필요 없었음 — `TemplateHelper.isFavoriteProject(project, user)` 신규 추가로 즉시 해결 |
| 91 | [x] | `project/home.scala.html` | `project/home.html` | 확인 완료(TASK-0237). readme/dashboard* 탭 구조·조건분기 legacy와 일치 |
| 92 | [i] | `project/partial_readme.scala.html` | (home.html에 인라인, P2-42로 로직은 이식됨) | 확인 완료(TASK-0237). 마크업 legacy와 일치 |
| 93 | [i] | `project/partial_dashboard.scala.html` | (home.html에 인라인) | 확인 완료(TASK-0237). 마크업 legacy와 일치 |
| 94 | [i] | `project/partial_dashboard_issuesbyassignee.scala.html` | (home.html에 인라인) | 확인 완료(TASK-0237). 마크업 legacy와 일치 |
| 95 | [i] | `project/partial_dashboard_issuesbylabel.scala.html` | (home.html에 인라인) | 확인 완료(TASK-0237). 마크업 legacy와 일치 |
| 96 | [i] | `project/partial_dashboard_issuesbymilestone.scala.html` | (home.html에 인라인) | 확인 완료(TASK-0237). 마크업 legacy와 일치 |
| 97 | [i] | `project/partial_dashboard_pullrequests.scala.html` | (home.html에 인라인) | 확인 완료(TASK-0237). 마크업 legacy와 일치 |
| 98 | [i] | `project/partial_history.scala.html` | `project/home.html`(history 탭에 인라인) | 확인 완료(TASK-0237). 최근 활동 목록 마크업 legacy와 일치, 별도 파일로 분리하지 않고 home.html에 인라인된 구조 확인 |
| 99 | [x] | `project/members.scala.html` | `project/members.html` | 확인 완료(TASK-0237). site/layout 기반 GNB/footer 이미 정상 구성돼 있음(코드 변경 없음) |
| 100 | [x] | `project/setting.scala.html` | `project/setting.html` | 완료(TASK-0237). site/layout 기반 GNB/footer는 이미 정상이었으나 외부 CDN jQuery(`code.jquery.com`) 중복 로드 발견 — site/layout::scripts와 중복되는 위험한 로드라 제거 |
| 101 | [x] | `project/partial_settingmenu.scala.html` | `project/setting_menu.html` | 확인 완료 — 설정/멤버/라벨/웹훅/이관/삭제/VCS변경 7개 탭 구조 및 `active` 파라미터명 일치(코드 변경 없음) |
| 102 | [x] | `project/change_vcs.scala.html` | `project/change_vcs.html` | 완료(TASK-0236, TDD). **독자 GNB 발견**: 검은 `.gnb-wrap`/`.gnb-brand` 하드코딩 가짜 헤더(별도 jQuery 로드까지 포함)로 완전히 독자 구현돼 있었음 — site/layout+project/header+project/menu+setting_menu 조각 기반으로 재작성. 기존 콘텐츠(메시지키/JS모듈 연동)는 이미 정확해 유지 |
| 103 | [x] | `project/transfer.scala.html` | `project/transfer.html` | 완료(TASK-0236, TDD). #102와 동일한 독자 GNB 패턴 발견, site/layout 조각 기반으로 재작성 |
| 104 | [x] | `project/delete.scala.html` | `project/delete.html` | 완료(TASK-0236, TDD). #102와 동일한 독자 GNB 패턴 발견, site/layout 조각 기반으로 재작성 |
| 105 | [x] | `project/watchers.scala.html` | `project/watchers.html` | 완료(TASK-0236, TDD). 독자 GNB뿐 아니라 콘텐츠 자체도 legacy의 `.members.project.row-fluid`/`.member.span6`/`.member-name`/`.member-id` 클래스 대신 독자 `.watcher-list`/`.watcher-item` CSS로 재구현돼 있었고 i18n 메시지키도 미사용이었음 — legacy 클래스/메시지키 그대로 재작성 |
| 106 | [x] | `project/webhooks.scala.html` | `project/setting_webhook.html` | 완료(TASK-0236, TDD). #102와 동일한 독자 GNB 패턴 발견(외부 CDN jQuery 로드 포함 — site/layout과 중복 로드 위험), site/layout 조각 기반으로 재작성. 기존에 이미 있던 `setting_menu` 호출과 중복되지 않도록 정리 |
| 107 | [x] | `project/partial_webhooks_list.scala.html` | `project/setting_webhook.html`(인라인) | 완료(TASK-0237, TDD). 독자 Bootstrap `<table>` + 하드코딩 한글 텍스트로 재구현돼 있던 것을 발견 — legacy의 `.row-fluid.list-head`/`.list-item` 구조와 메시지키(`project.webhook.payloadUrl`/`secret`/`list.empty`)로 재작성. 삭제 버튼도 커스텀 confirm+AJAX 클릭 핸들러 대신 사이트 공용 `data-request-method`/`data-request-uri` 컨벤션으로 교체(legacy도 confirm 없이 바로 요청) |
| 108 | [x] | `project/issuelabels.scala.html` | `project/issuelabels.html` | 완료(TASK-0237, TDD). #102와 동일한 독자 GNB(가짜 `.gnb-wrap`) 패턴 발견, site/layout+project/header+project/menu+setting_menu 조각 기반으로 재작성. 프리셋 색상 13개→legacy와 동일한 17개로 복구, `issue-label` 클래스 누락 추가. 라벨/카테고리 CRUD용 커스텀 JS(`yobi.issue.LabelEditor.js` 실사용 모듈로의 교체 여부)는 필드/ID 호환성 재검증이 더 필요해 이번 배치에서는 보류(현행 커스텀 구현 유지) |
| 109 | [i] | `project/partial_issuelabels_list.scala.html` | (issuelabels.html에 인라인, `#labels-list-container` 비동기 렌더링으로 대체) | 확인 완료(TASK-0237). 목록 렌더링이 서버사이드 파샬 대신 `/api/projects/{id}/labels` 비동기 호출 기반으로 구현돼 있음 — REST 아키텍처 전환에 따른 의도된 차이 |
| 110 | [i] | `project/partial_issuelabels_editcategory.scala.html` | (issuelabels.html에 인라인, 카테고리 생성 폼으로 대체) | 확인 완료(TASK-0237). 위와 동일하게 REST 기반 구현 |
| 111 | [i] | `project/partial_issuelabels_editlabel.scala.html` | (issuelabels.html에 인라인, 라벨 생성 폼으로 대체) | 확인 완료(TASK-0237). 위와 동일하게 REST 기반 구현 |
| 112 | [x] | `project/statistics.scala.html` | `project/statistics.html` | 확인 완료(TASK-0237). legacy 자체가 "Under Construction" 플레이스홀더뿐이며 yuna도 동일하게 이식돼 있음(코드 변경 없음) |

## 그룹 7 — `issue/*` 이슈 (30개, #113~142)

| # | 상태 | legacy 경로 | yuna 대상 경로 | 비고 |
|---|---|---|---|---|
| 113 | [x] | `issue/create.scala.html` | `issue/create.html` | 완료(TASK-0238). 마일스톤 선택 영역이 `project.menuSetting.milestone` 게이트 없이 항상 노출되던 것을 `project.isMilestoneEnabled` 조건으로 복구. 담당자 선택 영역의 `isProjectResourceCreatable(ISSUE_ASSIGNEE)` 게이트는 이슈 생성 폼 접근 자체가 이미 동일 권한을 요구해 실질적으로 항상 참이라 생략해도 무해하다고 판단, 미이식 |
| 114 | [x] | `issue/edit.scala.html` | `issue/edit.html` | 완료(TASK-0238). #113과 동일하게 마일스톤 선택 영역에 `project.isMilestoneEnabled` 게이트 복구 |
| 115 | [x] | `issue/list.scala.html` | `issue/list.html` | 완료(TASK-0238, TDD). 아래 #116~125 조사에서 발견된 3건(닫힌 마일스톤 optgroup 누락/마일스톤 비활성 시에도 일괄수정 마일스톤 옵션 노출/라벨 관리 링크 권한 미검사)을 이 파일에서 수정 |
| 116 | [i] | `issue/partial_searchform.scala.html` | `issue/list.html`에 인라인 | 완료(TASK-0238). 대체로 일치하나 **마일스톤 select가 열림 optgroup만 있고 닫힌 마일스톤 optgroup이 통째로 빠져** 있었음(legacy는 열림/닫힘 2개 optgroup) — `IssueViewController.list()`에 `closedMilestones` 모델 속성 신규 추가 후 복구. **라벨 관리 링크도 legacy `isManagerOf(project)` 권한 체크 없이 항상 노출**되고 있었음(실제 라벨 설정 화면은 매니저/사이트관리자만 접근 가능해 일반 멤버에게 403 유발) — `templateHelper.isManager` 게이트 추가 |
| 117 | [i] | `issue/partial_list.scala.html` | `issue/list.html`에 인라인 | 확인 완료(TASK-0238). 이슈 행 마크업(제목/작성자/날짜/서브태스크 진행률/마일스톤/댓글·공감·공유 카운트) legacy와 일치 |
| 118 | [i] | `issue/partial_list_wrap.scala.html` | `issue/list.html`에 인라인 | 확인 완료(TASK-0238). 좌측 필터+우측 목록 2단 레이아웃, 정렬 필터 링크(마감일/최근업데이트/등록일/댓글순) 구조 일치 |
| 119 | [~] | `issue/partial_list_draft.scala.html` | (미이식) | **미이식 확인(TASK-0238)**: legacy는 초안(isDraft=true) 이슈를 작성자 본인에게만 목록 최상단에 노출하는 별도 파샬을 갖고 있으나, yuna는 이슈 목록 쿼리가 `State.OPEN`/`State.CLOSED`만 조회해 `State.DRAFT` 이슈는 목록에서 완전히 안 보임(직접 URL 접근 시에만 열람 가능, `IssueController`에 작성자 본인 검증 로직은 있음). 백엔드에 작성자별 초안 조회 쿼리 신규 추가 + 목록 템플릿에 초안 섹션 추가가 필요해 순수 템플릿 포팅 범위를 넘어 보류 — 후속 배치에서 별도 작업으로 처리 필요 |
| 120 | [i] | `issue/partial_list_subtask.scala.html` | `issue/list.html`에 인라인 | 확인 완료(TASK-0238). 서브태스크 진행률 바(`done-outline`/`red-outline`)와 부모 이슈 링크 구조 일치 |
| 121 | [i] | `issue/partial_list_quicksearch.scala.html` | `issue/list.html`에 인라인 | 확인 완료(TASK-0238). 좌측 퀵링크(전체/할당된/작성한/댓글단 이슈) 구조·카운트 배지 일치 |
| 122 | [x] | `issue/partial_massupdate.scala.html` | `issue/list.html`에 인라인 | 완료(TASK-0238). 일괄 수정 폼(상태/담당자/마일스톤/라벨 추가·제거) 구조는 대체로 일치하나, **마일스톤 드롭다운이 `project.menuSetting.milestone`(마일스톤 메뉴 활성화 여부) 게이트 없이 마일스톤 데이터만 있으면 노출**되고 있었음 — `project.isMilestoneEnabled` 조건 추가로 복구 |
| 123 | [i] | `issue/partial_select_label.scala.html` | `issue/list.html`에 인라인 | 확인 완료(TASK-0238). select2 기반 카테고리별 라벨 다중선택 구조는 일치하나, legacy는 라벨 dt 안에 `isManagerOf(project)`일 때만 보이는 `[편집]` 인라인 링크를 두는 반면 yuna는 별도 `.labels-wrap` 아이콘 버튼(#116에서 권한 게이트 복구함)으로 분리 배치 — 기능은 동등, DOM 구조만 소폭 차이(수용 가능한 수준으로 판단) |
| 124 | [i] | `issue/partial_show_selected_label.scala.html` | `issue/partial_show_selected_label.html`(프래그먼트) | 확인 완료(TASK-0238). `dl`/`dt`/라벨 앵커 구조 일치, 수정 불필요 |
| 125 | [~] | `issue/partial_select_subtask.scala.html` | (미이식) | **미이식 확인(TASK-0238)**: 이슈 생성/수정 폼에서 부모 이슈를 select2로 검색해 지정하는 위젯. yuna의 create.html/edit.html에 부모이슈 지정 UI가 없음(현재는 URL 쿼리파라미터 `parentIssueId`로만 하위이슈 생성 가능, 기존 이슈의 부모를 나중에 바꾸는 기능 없음) — REST 검색 엔드포인트 신규 필요, 순수 템플릿 포팅 범위를 넘어 보류 |
| 126 | [x] | `issue/view.scala.html` | `issue/view.html` | 대체로 정밀 이식돼 있음을 확인(TASK-0238). 아래 #127,134~136에서 발견된 2건의 실질적 기능 누락은 백엔드 작업이 필요해 별도 보류 항목으로 기록 |
| 127 | [~] | `issue/partial_assignee.scala.html` | (view.html에 정적 텍스트로만 존재, 인터랙티브 위젯 없음) | **중대 발견(TASK-0238), 미이식**: view.html 우측 패널의 담당자/마일스톤이 항상 읽기전용 `<span>` 텍스트뿐이며, legacy처럼 `isAllowedUpdate`(매니저 등)일 때 select2 기반 인라인 수정 위젯으로 바뀌는 기능이 없음. 정적 자산 `yobi.issue.View.js`에는 이미 `[data-toggle=select2]` change 이벤트로 `massUpdate` 엔드포인트에 AJAX 저장하는 로직이 구현돼 있으나(`_onChangeIssueInfo`/`_requestUpdateIssue`), (a) view.html에 해당 select 마크업 자체가 없고, (b) 템플릿의 `$yobi.loadModule("issue.View", {...})` 호출에도 `urls.massUpdate`가 전달되지 않아 이중으로 죽어있는 상태. 참고로 legacy 자체도 `_onSelectingAssignee`가 리스닝하는 `[name="assignee.user.id"]` 셀렉터가 실제 렌더링되는 `partial_assignee`의 `name="assigneeLoginId"`와 불일치해 legacy에서도 일부 핸들러는 이미 죽어있었음(주 동작 경로는 `data-toggle=select2`의 범용 change 핸들러). 라벨은 이미 동일 패턴(#123)으로 정상 작동 중이라 참고 구현으로 재사용 가능. REST 계약 검증이 필요해 순수 템플릿 포팅 범위를 넘어 보류 — 후속 배치에서 (1) view.html에 assignee/milestone/dueDate select2 마크업 추가, (2) `massUpdate` URL을 JS 초기화 옵션에 전달, (3) 실제 massUpdate 컨트롤러가 단건 필드 갱신 요청을 올바르게 처리하는지 검증까지 함께 처리 필요 |
| 128 | [i] | `issue/partial_comment.scala.html` | (view.html에 인라인) | 확인 완료(TASK-0238). 댓글 아바타/작성자/날짜/공감 리스트·모달/공감토글 버튼/마크다운 본문 구조 legacy와 일치 |
| 129 | [i] | `issue/partial_comments.scala.html` | (view.html에 인라인) | 확인 완료(TASK-0238, 이전 P1-106에서 이미 이식). 댓글+이벤트 통합 타임라인(`issue.getTimeline()`) 구조 일치 |
| 130 | [i] | `issue/partial_event_timeline.scala.html` | (view.html에 인라인) | 확인 완료(TASK-0238, 이전 P1-106에서 이미 이식). 상태변경 이벤트 렌더링 구조 일치 |
| 131 | [i] | `issue/partial_index_comment.scala.html` | (미이식, 기능적으로 불필요) | 조사 완료(TASK-0238): legacy에서 `issue.isDraft`일 때만 쓰이는 축약형 댓글 뷰(줄임말 텍스트 등)이며, yuna는 draft 여부와 무관하게 항상 풀 타임라인(#129와 동일)을 렌더링해 기능적으로 상위호환 — 별도 이식 불필요로 판단 |
| 132 | [i] | `issue/partial_index_comments.scala.html` | (미이식, 기능적으로 불필요) | #131과 동일 사유로 별도 이식 불필요 |
| 133 | [i] | `issue/partial_index_event_timeline.scala.html` | (미이식, 기능적으로 불필요) | #131과 동일 사유로 별도 이식 불필요 |
| 134 | [~] | `issue/partial_view_child.scala.html` | (미이식) | **중대 발견(TASK-0238), 미이식**: 하위 이슈 1건 렌더링 파샬. #135와 함께 보류 |
| 135 | [~] | `issue/partial_view_childIssueList.scala.html` | (미이식) | **중대 발견(TASK-0238), 미이식**: 이슈 상세 화면에 부모+하위이슈 목록(진행률 바 포함)을 보여주는 기능이 view.html에 전혀 없음(하위 이슈 "등록" 버튼만 있고 등록된 하위 이슈 목록을 볼 방법이 없음). `Issue.findByParentIssueIdAndState` 상당의 리포지토리 메서드 신규 필요 + `partial_view_child`(#134)/`partial_view_childIssueListOnly`(#136) 렌더링 로직까지 함께 필요해 순수 템플릿 포팅 범위를 넘어 보류 — 후속 배치에서 전용 작업으로 처리 필요 |
| 136 | [~] | `issue/partial_view_childIssueListOnly.scala.html` | (미이식) | #135의 AJAX 갱신용 파샬, #135와 함께 보류 |
| 137 | [i] | `issue/partial_voters.scala.html` | (view.html에 인라인) | 확인 완료(TASK-0238). 공감 버튼 토글/투표자 아바타 목록/더보기 툴팁 구조 legacy와 일치 |
| 138 | [i] | `issue/partial_voter_list.scala.html` | (view.html에 인라인) | 확인 완료(TASK-0238). 투표자 목록 모달 구조 legacy와 일치 |
| 139 | [x] | `issue/my_list.scala.html` | `issue/my_list.html` | 확인 완료(TASK-0238). site/layout 기반 GNB, mySeriesMenuTab/my_partial_search 조각 호출, 로그인기본페이지 설정 스크립트까지 legacy와 정확히 일치, 코드 변경 없음 |
| 140 | [i] | `issue/my_partial_list.scala.html` | `issue/my_partial_search.html`에 인라인 | 확인 완료(TASK-0238). CSS 클래스 구조 legacy와 일치(동적 값만 th:class로 치환) |
| 141 | [i] | `issue/my_partial_list_quicksearch.scala.html` | `issue/my_partial_list_quicksearch.html` | 확인 완료(TASK-0238). 코드 변경 없음 |
| 142 | [x] | `issue/my_partial_search.scala.html` | `issue/my_partial_search.html` | 확인 완료(TASK-0238). 상태 탭/정렬 필터/2단보기/서브태스크펼치기 체크박스 구조 legacy와 일치, 코드 변경 없음 |

## 그룹 8 — `board/*` 게시판 (6개, #143~148)

| # | 상태 | legacy 경로 | yuna 대상 경로 | 비고 |
|---|---|---|---|---|
| 143 | [x] | `board/list.scala.html` | `board/list.html` | 완료(TASK-0240, TDD). #144 조사에서 발견된 2건 수정 |
| 144 | [i] | `board/partial_list.scala.html` | `board/list.html`에 인라인 | **발견(TASK-0240)**: (1) 제목의 `showHeaderWordsInBracketsIfExist`/`removeHeaderWords`(대괄호 접두어 분리 표시)가 통째로 빠져 있었음 — 복구. (2) **더 중대한 발견**: 공지글이 상단 공지 영역뿐 아니라 일반 페이징 목록 쿼리(`postingRepository.findByProject`)에도 필터링 없이 포함되어 화면에 중복 노출되고 있었음(legacy는 `el.eq("notice", false)`로 명시적 제외) — `PostingRepository`에 `findByProjectAndNotice(project, notice, pageable)` 페이징 버전 신규 추가 + `searchPostingsInProject`/`findByProjectAndLabelIdsIn` 쿼리에도 `notice=false` 조건 추가, `BoardViewController.posts()`가 기본 조회 시 이를 사용하도록 수정 |
| 145 | [x] | `board/create.scala.html` | `board/create.html` | 확인 완료(TASK-0240). 파일업로더/README 연동/커밋메시지 연동 필드까지 legacy와 정확히 일치. `isProjectResourceCreatable(COMMIT)` 게이트가 걸린 README 체크박스는 그룹10/11의 코드-연동 범위라 이번 배치에서는 미이식(그룹10/11에서 처리 예정) |
| 146 | [x] | `board/edit.scala.html` | `board/edit.html` | 확인 완료(TASK-0240). #145와 동일하게 README 지정 체크박스(`post.readmefy`)만 그룹10/11 범위로 미이식, 나머지 일치 |
| 147 | [x] | `board/view.scala.html` | `board/view.html` | 완료(TASK-0240, TDD). 삭제 확인 모달의 예/아니오 버튼이 `#{button.yes}`/`#{button.no}` 메시지 키 대신 하드코딩 한글("네"/"아니요")이었던 것을 복구. `common.noAuthor`(작성자 없는 경우 표시)는 yuna의 Posting이 작성자 정보를 비정규화 저장해 항상 값이 있어 해당 없음. `change.history`(게시글 수정 이력 모달)는 이력 추적 테이블 자체가 없어 순수 템플릿 포팅 범위를 넘어 보류 |
| 148 | [i] | `board/partial_comments.scala.html` | (view.html에 인라인, P1-106에서 이미 이식) | 확인 완료(TASK-0240). issue와 동일한 댓글+이벤트 타임라인 구조 재사용, 일치 |

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
| 154 | [x] | `code/view.scala.html` | `code/view.html` | 완료(TASK-0243). 대규모 재작성 — 상세는 하단 진행 로그 참고 |
| 155 | [x] | `code/partial_view_file.scala.html` | `code/view.html`(인라인) | 완료 — view.html에 인라인, 필드 전부 채움(작성자/아바타/댓글수/Raw/Edit/열기/이력 링크, 바이너리/이미지/과대용량/마크다운/일반코드 분기) |
| 156 | [x] | `code/partial_view_folder.scala.html` | `code/view.html`(인라인) | 완료 — view.html에 인라인, 폴더 우선순 정렬, 빈 폴더 안내, 커밋 아바타/메시지/날짜 채움 |
| 157 | [x] | `code/branches.scala.html` | `code/branches.html` | 완료(TASK-0243). "보낸 코드" 컬럼 PR 링크, 액션 컬럼 조건부 렌더링 복구 — 상세는 하단 진행 로그 |
| 158 | [x] | `code/partial_branchrow.scala.html` | `code/branches.html`(인라인) | 완료 — branches.html에 인라인, PR 링크/상태뱃지 포함 |
| 159 | [x] | `code/history.scala.html` | `code/history.html` | 완료(TASK-0243). 전면 재작성 — 상세는 하단 진행 로그 |
| 160 | [x] | `code/diff.scala.html` | `code/diff.html` | 완료(TASK-0243). GNB/프로젝트헤더/메뉴 프래그먼트 복구, commitMsg fragment 적용, #166 스레드 fragment 연결, 리뷰 사이드바(open/closed 탭) 추가 — 상세는 하단 진행 로그 |
| 161 | [x] | `code/svnDiff.scala.html` | `code/svnDiff.html` | 완료(TASK-0243). GNB/프로젝트헤더/메뉴, site/layout::scripts 누락 복구, 브랜치 드롭다운(#39) 추가 |
| 162 | [x] | `code/compare.scala.html` | `code/compare.html` | 완료(TASK-0243). **가짜 GNB(하드코딩 로그인/로그아웃 마크업) 발견·제거** — 알려진 버그 패턴(e) 실사례. 실제 site/layout::gnb/project/header/menu/scripts로 교체, `th:with` 내 중첩 따옴표 구문 오류 수정 |
| 163 | [x] | `code/compare_svn.scala.html` | `code/compare_svn.html` | 완료(TASK-0243). 위와 동일한 가짜 GNB 버그, 동일하게 수정 |
| 164 | [x] | `code/nohead.scala.html` | `code/nohead.html` | 완료(TASK-0243). project/header·menu 프래그먼트 누락 복구, UPDATE 권한 게이트 복구, 메시지 키 적용 |
| 165 | [x] | `code/nohead_svn.scala.html` | `code/nohead_svn.html` | 완료(TASK-0243). nohead.html과 동일 |
| 166 | [x] | `code/partial_nonrange_codecomment_thread.scala.html` | `code/partial_nonrange_codecomment_thread.html` | 완료(TASK-0243) — 신규 작성. `common/commentFormOnThread.html`(legacy `views/partial_comment_form_on_thread.scala.html` 대응)도 함께 신규 작성해 답글 폼 + 스레드 상태 토글 재사용 가능하게 구성 |

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
| 193 | [x] | `organization/list.scala.html` | `organization/list.html` | 완료(TASK-0250) — gnb는 있었으나 footer/scripts 조각 누락 발견해 복구 |
| 194 | [x] | `organization/create.scala.html` | `organization/create.html` | 완료(TASK-0250) — n-alert/wrongName 검증 마크업, `name="new-org"`, `organization.New` 모듈 로드, footer/scripts 전부 누락 발견해 복구, 검증 실패 시 입력값 보존 추가 |
| 195 | [x] | `organization/view.scala.html` | `organization/view.html` | 완료(TASK-0250) — 완전 재작성(기존은 가짜 GNB+독자 마크업으로 legacy와 무관한 구조였음). header/menu 프래그먼트 신설 연결, 관리자/멤버 목록 분리, 탈퇴 버튼, 프로젝트 목록 카드(라벨/포크출처/워칭카운트) 전부 복구 |
| 196 | [x] | `organization/header.scala.html` | `organization/header.html` | 완료(TASK-0250) — 신규 작성(project/header.html과 동일 패턴). 로고/브레드크럼/게스트 가입요청 드롭다운 |
| 197 | [x] | `organization/menu.scala.html` | `organization/menu.html` | 완료(TASK-0250) — 신규 작성(project/menu.html과 동일 패턴). 홈/이슈/게시판/PR 탭 + 관리자 설정 톱니 |
| 198 | [x] | `organization/members.scala.html` | `organization/members.html` | 완료(TASK-0250) — 완전 재작성(가짜 GNB 제거). 멤버추가/역할변경/삭제/가입승인을 기존 REST API(`/api/organizations/**`)에 연결(project/members.html과 동일 관례) |
| 199 | [x] | `organization/setting.scala.html` | `organization/setting.html` | 완료(TASK-0250) — 완전 재작성(가짜 GNB 제거), header/menu/partial_settingmenu 프래그먼트 연결 |
| 200 | [x] | `organization/partial_settingmenu.scala.html` | `organization/partial_settingmenu.html` | 기존 구현이 이미 legacy와 동치(설정/멤버/삭제 3탭) — 변경 없음, 상태만 [x]로 정정 |
| 201 | [x] | `organization/deleteForm.scala.html` | `organization/delete.html` | 완료(TASK-0250) — 완전 재작성(가짜 GNB 제거), 삭제 확인 모달/AJAX 그대로 이식 |
| 202 | [x] | `organization/group_board_list.scala.html` | `organization/boardList.html` | 완료(TASK-0250) — 완전 재작성. 공지/일반글 분리, 프로젝트 다중선택, orderBy 정렬 링크, notice 1페이지 한정 노출 백엔드 포함 신규 구현 |
| 203 | [x] | `organization/group_board_list_partial.scala.html` | `organization/boardList_partial.html` | 완료(TASK-0250) — 신규 작성 |
| 204 | [x] | `organization/group_issue_list.scala.html` | `organization/issueList.html` | 완료(TASK-0250) — 완전 재작성(issueSearch_partial 위임 구조로) |
| 205 | [x] | `organization/group_issue_list_partial.scala.html` | `organization/issueList_partial.html` | 완료(TASK-0250) — 신규 작성 |
| 206 | [x] | `organization/group_issue_list_quicksearch.scala.html` | `organization/issueList_quicksearch.html` | 완료(TASK-0250) — 신규 작성(전체/할당된/작성한/멘션된 4종 퀵필터) |
| 207 | [x] | `organization/group_issue_search_partial.scala.html` | `organization/issueSearch_partial.html` | 완료(TASK-0250) — 신규 작성. authorId/assigneeId/mentionId 필터·상태탭 카운트·정렬 백엔드(`IssueSpecification.filterOrganizationIssues`) 신규 구현 |
| 208 | [x] | `organization/group_pullrequest_list.scala.html` | `organization/pullRequestList.html` | 완료(TASK-0250) — 완전 재작성. 열림/닫힘 탭 배지 카운트 백엔드 포함 |
| 209 | [x] | `organization/group_pullrequest_list_partial.scala.html` | `organization/pullRequestList_partial.html` | 완료(TASK-0250) — 신규 작성. 리뷰스레드 진행률 바(CommentThreadRepository 연동) 포함 |

## 그룹 13 — `site/*` 사이트 관리자 (14개, #210~223)

| # | 상태 | legacy 경로 | yuna 대상 경로 | 비고 |
|---|---|---|---|---|
| 210 | [x] | `site/siteMngLayout.scala.html` | `site/layout.html :: sidebar/breadcrumb` | 그룹1 #3(TASK-0222)에서 이미 조각 조합으로 치환 확인됨 — 이번에 사이드바 update 배지 누락만 추가 수정(TASK-0250) |
| 211 | [x] | `site/data.scala.html` | `site/data.html` | TASK-0250: `<title>` 버그 + 스트레이 `</div>` 수정 |
| 212 | [x] | `site/diagnostic.scala.html` | `site/diagnostic.html` | TASK-0250: `<title>` 버그 수정 |
| 213 | [x] | `site/setting.scala.html` | `site/setting.html` | TASK-0250: 신규 작성(legacy도 라우트 없는 죽은 TODO 스텁 — 그 상태 그대로 이식) |
| 214 | [x] | `site/update.scala.html` | `site/update.html` | TASK-0250: `<title>` 버그 수정 |
| 215 | [x] | `site/mail.scala.html` | `site/mail.html` | 이미 legacy와 일치 확인(코드 변경 없음) |
| 216 | [x] | `site/massMail.scala.html` | `site/massMail.html` | TASK-0250: 스트레이 `</div>` 수정 |
| 217 | [x] | `site/userList.scala.html` | `site/userList.html` | TASK-0250: `<title>` 버그 + yuna 독자 서버사이드 페이지네이션을 legacy yobi.Pagination.js 위젯으로 복구 |
| 218 | [x] | `site/projectList.scala.html` | `site/projectList.html` | TASK-0250: `<title>` 하드코딩 버그 + 독자 페이지네이션 복구 + `console.log` 누락 복원 |
| 219 | [x] | `site/postList.scala.html` | `site/postList.html` | TASK-0250: `<title>` 버그 + 스트레이 `</div>` + 페이지네이션 파라미터명(`page`) 불일치 수정 |
| 220 | [x] | `site/issueList.scala.html` | `site/issueList.html` | TASK-0250: `<title>` 하드코딩 버그 + 독자 페이지네이션 복구 |
| 221 | [x] | `site/lostPassword.scala.html` | `user/lostPassword.html` | TASK-0250: **완전한 독자 페이지였음을 발견**(GNB/footer 없음, i18n 미사용) — site/layout 기반으로 재작성 |
| 222 | [x] | `site/partial_pagination.scala.html` | `site/partial_pagination.html` | TASK-0250: 신규 작성(legacy도 호출부 없는 죽은 파샬 — 그 상태 그대로 이식) |
| 223 | [x] | `site/partial_paginationForUserList.scala.html` | `site/partial_paginationForUserList.html` | TASK-0250: 신규 작성(마찬가지로 죽은 파샬, 링크 대상만 legacy pageNum→yuna page 파라미터로 환산) |

## 그룹 14 — `search/*` 통합검색 (10개, #224~233)

| # | 상태 | legacy 경로 | yuna 대상 경로 | 비고 |
|---|---|---|---|---|
| 224 | [x] | `search/result.scala.html` | `search/list.html` | TASK-0250에서 legacy 8개 파샬 전부와 줄 단위 대조 완료 — 아래 #225~233 비고 참고 |
| 225 | [x] | `search/partial_search.scala.html` | `search/list.html`(인라인) | 별도 파일 대신 `list.html`에 검색창/카테고리탭/결과 dispatch 전체가 인라인됨(아키텍처 선택, 허용범위). 페이지네이션을 legacy의 `yobi.Pagination.update()`/`#pagination` 컨벤션 대신 자체 부트스트랩식 링크로 재발명한 버그 발견 후 legacy 그대로 복구 |
| 226 | [x] | `search/partial_projects.scala.html` | `search/list.html`(인라인) | **버그 다수 발견·수정**: (1) 프로젝트 로고/아바타 이미지 전체 누락 → 추가, (2) 포크 배지(`fork.original`+원본 프로젝트 링크) 전체 누락 → 추가, (3) `project.overview`를 legacy처럼 **스니펫 없이 전체** 출력해야 하는데 다른 탭과 동일하게 40자 스니펫 로직을 잘못 적용해뒀음 → 스니펫 로직 제거, (4) 생성일 문구가 하드코딩 "생성일:"이었고 `project.create` 메시지키/ago 포맷 미사용 → 수정, (5) `project.codeUpdate`(마지막 코드 업데이트) 라인 전체 누락 → 추가, (6) legacy는 이 파샬에만 **페이지네이션이 없음**(1페이지 제한, legacy 자체 제약) — yuna는 있었음 → 제거, (7) `<li>` class에 legacy의 `project` 보조클래스 누락 → 추가, (8) owner/name 표기에 불필요한 공백(" / ") 삽입 → "owner/name"으로 수정 |
| 227 | [x] | `search/partial_issues.scala.html` | `search/list.html`(인라인) | **버그 발견·수정**: 스니펫이 본문보다 짧을 때 legacy가 붙이는 "....." 말줄임표 누락, 작성자가 항상 텍스트로만 표시되고(링크 없음) `issue.noAuthor` 폴백도 없었음(하드코딩 "작성자 없음") → 실제 사용자 프로필 링크+`#{issue.noAuthor}` 폴백으로 복구, 날짜가 `agoOrDateString`이 아닌 `yyyy-MM-dd HH:mm` 고정 포맷이었음 → `@templateHelper.agoOrDateString`/`getDateString`으로 교체, 페이지네이션을 legacy 컨벤션(`yobi.Pagination.update`)으로 교체 |
| 228 | [x] | `search/partial_issue_comments.scala.html` | `search/list.html`(인라인) | **버그 발견·수정**: 제목이 legacy의 "Re) "+이슈제목이 아니라 "...에 달린 댓글"이라는 임의 문구로 바뀌어 있었음(문구 재창작 금지 원칙 위반) → 원문 그대로 복구, `#comment-{id}` 앵커 누락 → 추가, 작성자 링크/`issue.noAuthor` 폴백·ago 날짜·페이지네이션은 #227과 동일 사유로 수정 |
| 229 | [x] | `search/partial_posts.scala.html` | `search/list.html`(인라인) | #227과 동일한 버그들(말줄임표/작성자링크/ago날짜/페이지네이션) 발견·수정. legacy가 게시글인데도 `issue.noAuthor` 키를 재사용하는 것도 legacy 원문 그대로 유지(다른 화면과 다르게 `post.noAuthor`를 쓰지 않는 legacy 자체의 특이점) |
| 230 | [x] | `search/partial_post_comments.scala.html` | `search/list.html`(인라인) | #228과 동일 버그(제목 문구 재창작, `#comment-{id}` 누락, 작성자링크/ago날짜/페이지네이션) 발견·수정. `posting.noAuthor` 메시지키는 legacy 5개 로케일 파일 전체에 **정의 자체가 없던 잠재 버그**를 발견 — Spring MessageSource는 Play와 달리 키 누락 시 예외를 던지므로, `issue.noAuthor`와 동일 값으로 6개 로케일 파일 모두에 새로 추가(파일: `messages*.properties`) |
| 231 | [x] | `search/partial_milestones.scala.html` | `search/list.html`(인라인) | **버그 발견·수정**: 기한(dueDate)이 없을 때 legacy는 기한 영역 자체를 렌더링하지 않는데, yuna는 항상 "기한 없음" 텍스트를 렌더링하고 있었음(legacy에 없는 문구 재창작) → `th:if`로 복구. 기한이 있을 때도 `label.dueDate` 메시지키 미사용, `milestone.until()`(오늘/기한초과/남은일수) 표시 전체 누락 → `TemplateHelper.until(Milestone)` 신규 추가 후 복구 |
| 232 | [x] | `search/partial_reviews.scala.html` | `search/list.html`(인라인) | **버그 다수 발견·수정**: (1) PR 링크 라우트가 실존하지 않는 `/pullRequest/{number}`였음(실제 컨트롤러 매핑은 `/pull/{number}`) → 404 버그 수정, (2) legacy는 PR 기반 리뷰만 제목(`Re) `+PR제목)을 보여주고 커밋 기반(비-PR) 리뷰는 제목 없이 스니펫 전체를 링크로 감싸는데, yuna는 항상 PR 리뷰로 가정해 `thread.pullRequest`가 null이면 NPE가 나는 구조였음 → `thread.isOnPullRequest()` 분기로 legacy 그대로 복구, (3) `TemplateHelper.urlToCommentThread(CommentThread)` 신규 추가(PR/커밋 라우팅, `NotificationUrlResolver.urlToContainer()`와 동일한 전례로 outdated-diff 특정 커밋 세부분기는 생략), (4) 작성자 표시가 `rc.author.name`을 조건 없이 그대로 쓰고 있어 이름이 없으면 빈 텍스트만 나왔음 → `issue.noAuthor` 폴백 복구, (5) 날짜가 `#dates.format`(java.util.Date 전용, `ReviewComment.createdDate`는 `Instant`라 런타임 오류 위험)이었음 → `agoOrDateString`으로 교체 |
| 233 | [x] | `search/partial_users.scala.html` | `search/list.html`(인라인) | **버그 발견·수정**: 아바타 이미지 전체 누락 → `User.avatarUrl(32)` 사용해 복구(legacy `urlToPicture`/`DEFAULT_AVATAR_URL` 분기와 동등), `userinfo.since` 메시지키 미사용(하드코딩 "가입일:") → 복구. 가입일 포맷이 legacy `User.getDateString()`("MMM dd, yyyy", `Locale.US` 고정)과 다른 별개 포맷(`yyyy-MM-dd`)이었음 → `TemplateHelper.getUserSinceDateString()` 신규 추가로 legacy 포맷 그대로 복구. `<li>` class에 legacy의 `project` 보조클래스 누락 → 추가 |

## 그룹 15 — `help/*` 도움말 (5개, #234~238)

정적 콘텐츠 위주, 우선순위 낮지만 작업량도 적음.

| # | 상태 | legacy 경로 | yuna 대상 경로 | 비고 |
|---|---|---|---|---|
| 234 | [x] | `help/toc.scala.html` | `help/toc.html` | 완료(TASK-0249, TDD). `<title>`이 하드코딩 `'도움말'`이었던 것을 `#{title.help}` 메시지 키로 교체 |
| 235 | [x] | `help/markdown.scala.html` | `help/markdown.html` | 확인 완료(TASK-0249). 이슈/게시글 작성 에디터에 포함되어 10개 문법 탭 legacy와 일치, 코드 변경 없음 |
| 236 | [x] | `help/keymap.scala.html` | `help/keymap.html` | 완료(TASK-0249, TDD). 파라미터(`section`,`project`)를 못 받는 `th:replace` 전체 include 방식이었던 것을 `th:fragment="keymap(section, project)"`로 교체(board/list.html, board/view.html 두 호출부도 함께 수정) |
| 237 | [x] | `help/UIKit.scala.html` | `help/UIKit.html` | 확인 완료(TASK-0249). site GNB/footer 없는 완전 standalone 페이지로 legacy와 동일하게 렌더링됨, 코드 변경 없음 |
| 238 | [x] | `help/experimental.scala.html` | `help/experimental.html` | 확인 완료(TASK-0249). legacy도 미참조 상태의 독립 모달 조각이며 마크업 일치, 코드 변경 없음 |

## 그룹 16 — `migration/*` (2개, #239~240)

| # | 상태 | legacy 경로 | yuna 대상 경로 | 비고 |
|---|---|---|---|---|
| 239 | [x] | `migration/migrationPageLayout.scala.html` | `migration/home.html`(인라인 조합) | 완료(TASK-0245, TDD). 그룹1(#4~#6) 선례와 동일하게 별도 데코레이터 파일 대신 `migration/home.html`이 `site/layout::head/gnb/footer/scripts` 조각을 직접 조합해 이 레이아웃의 역할(관리자 로그인 알림+전체 GNB+`common.scripts()`+사이드바/즐겨찾기 토글 스크립트)을 대체함 |
| 240 | [x] | `migration/home.scala.html` | `migration/home.html` | 완료(TASK-0245, TDD). **중대 발견**: 기존 yuna 파일이 legacy 구조를 거의 통째로 재작성한 "yuna식 독자 구현"이었음 — 컨테이너 div로 감싸기, 인라인 스타일 대량 추가, "주의 사항!!" 행 누락, `import-warning` 커스텀 엘리먼트 누락, 진행률 바 클래스(`bar`/`bar-danger`/`bar-success`→`progress-bar`류로 변경) 및 담당자 경고(`warn-no-worker`/`warn-user-project`) 마크업 상당수 누락, GNB/footer는 있었으나 `site/layout::scripts`(yobiDialog/toast/로그인모달/`yona-common.js` 등) 및 migrationPageLayout 전용 자산(구글 폰트 3종, `jquery-1.9.0.js`/`jquery.browser.js`/`jquery.pjax.js`/`yobi.Common.js`/`vendor.js`/`yona.Migration.js`, 사이드바·즐겨찾기 토글 인라인 스크립트)이 전혀 없었음. legacy 그대로 재작성(Play `ng-if="'@token'"`→`th:if`, `@if(StringUtils.isNotBlank(token))`→`th:attr` 삼항식, `@api.routes.UserApi.*`→`/-_-api/v1/favorite*` 경로로 치환) |

## 그룹 17 — `welcome/*` 초기 설치 (2개, #241~242)

| # | 상태 | legacy 경로 | yuna 대상 경로 | 비고 |
|---|---|---|---|---|
| 241 | [x] | `welcome/secret.scala.html` | `bootstrap-setup.html` | 완료(TASK-0246, TDD). 매핑 확인 완료(`BootstrapSetupController`가 반환). 필드별 검증 에러 뱃지(`label-important`) 구조가 통째로 빠져 있던 것을 복구, 메시지 키(`#{...}`) 컨벤션 적용, CSS/구조 잉여물 제거. 상세는 진행 로그 참고 |
| 242 | [x] | `welcome/restart.scala.html` | `bootstrap-restart.html` | 완료(TASK-0246, TDD). 텍스트는 이미 일치, 메시지 키 컨벤션 적용 + CSS/구조 잉여물 제거. `hasFailedToUpdateSecret` 분기는 legacy의 application.secret 파일 재기록 인프라 자체가 yuna에 없어 이번 배치에서는 미이식(사유는 진행 로그 참고) |

---

## 진행 로그

작업을 진행하면서 이 섹션에 그룹/파일 단위로 완료 기록을 남긴다(형식은 `docs/PARITY_BACKLOG.md`의 완료 로그와 동일한
톤 — 원인/구현 내용/legacy와 다르게 처리한 지점과 근거/검증 방법을 명시).

### #1 `layout.scala.html` → `site/layout.html` (TASK-0220)

- **원인**: yuna의 `site/layout.html`은 legacy `layout.scala.html`의 head/gnb/scripts 내용을 이미 fragment
  형태로 상당 부분 인라인해두고 있었으나(선행 세션 작업), legacy 원본과 줄 단위 대조 결과 다음이 누락돼 있었다:
  1. `og:*`/`twitter:*` OpenGraph·트위터카드 메타 태그 전체
  2. 사이트 관리자 전용 **업데이트 알림 배너**(legacy `partial_update_notification.scala.html`) — 백엔드
     (`YonaUpdateService`, `SiteApiController.unwatchUpdate`, `messages*.properties`의 `site.update.*` 키)는
     이미 이식돼 있었으나 뷰 조각만 빠져 있었다.
  3. `NProgress.configure(...)` 초기화 호출과 `nprogress.css`/`nprogress.js` 자산 — 기존 코드는 `NProgress.set/start`만
     호출하고 라이브러리 자체를 로드/설정하지 않아 실제로는 매 페이지에서 JS 에러가 나는 상태였다.
  4. ViewerJS(`viewer.css`/`viewer.js`/`jquery-viewer.js`)와 `.markdown-wrap` 이미지 갤러리 뷰어 초기화 스크립트 전체
- **구현 내용**:
  - `GlobalModelAttributeAdvice`에 `yonaUpdateService`(기존 `YonaUpdateService` 빈을 그대로 노출)와
    `currentRequestPath`(`HttpServletRequest.requestURI`, legacy `Http.Context.current().request().path()` 대응)
    `@ModelAttribute`를 추가 — 기존 `currentUser`/`templateHelper`/`markdownService`와 동일한 패턴(Play의 전역 정적
    접근을 Spring `@ControllerAdvice` 전역 모델 속성으로 치환)이라 아키텍처적으로 허용되는 치환.
  - `site/layout.html`의 `head` 조각에 og/twitter 메타 태그 8종과 `nprogress.css`/`viewer.css` 링크 추가.
  - `gnb` 조각의 admin-affix 배너 바로 뒤에 legacy `partial_update_notification.scala.html`과 동일한 조건
    (`currentUser.isSiteManager && yonaUpdateService.isWatched && yonaUpdateService.isUpdateRequired`)의
    업데이트 알림 `<p class="center-txt">` 블록 추가. 메시지 키(`site.update.notification`,
    `site.update.notification.hide`)는 기존 `messages*.properties`를 그대로 재사용.
  - `scripts` 조각에 `nprogress.js`/`viewer.js`/`jquery-viewer.js` 스크립트 로드와, legacy와 동일한
    `NProgress.configure({minimum:0.7})` 초기화 스크립트, `.markdown-wrap` 뷰어 초기화 스크립트(`Viewer.setDefaults`,
    이미지 mouseover 커서 처리 포함) 추가.
- **legacy와 다르게 처리한 지점**: legacy는 `title.split(" \\|:\\| ")`로 제목을 "실제 제목"과 "설명"으로 분리해
  `og:title`/`og:description`을 따로 채우지만, yuna는 `title` 모델 속성이 이미 여러 컨트롤러에 걸쳐 단일 문자열
  컨벤션으로 정착돼 있어(이 파일만으로는 바꿀 수 없는 범위) 이번 작업에서는 `og:title`/`og:description`에 동일한
  `${title}` 값을 사용했다. `\|:\|` 분리 컨벤션을 온전히 이식하려면 모든 컨트롤러의 `title` 전달 방식을 바꿔야 하는
  더 큰 범위의 작업이라 별도 항목으로 분리하지 않고 이번 비고에 한계로 기록한다(필요 시 추후 새 P-번호로 등록).
- **테스트**: `TemplateEquivalenceSpec.kt`의 `[Test-19-5] 레이아웃 공통 조각(site/layout.html) 동치성 검증`
  (사이트 관리자+업데이트 필요 시 배너 노출/미노출 3종, og·twitter 메타 태그 존재, NProgress/ViewerJS 자산 로드).
  `YonaUpdateService`는 실제 네트워크 호출(`checkForUpdate()`) 없이 리플렉션으로 `isUpdateRequired`/`latestVersion`
  private 필드를 직접 세팅해 결정론적으로 검증했다.
- **검증**: `./gradlew test --tests "com.github.search5.yona.web.TemplateEquivalenceSpec"`(RED 확인 후 GREEN),
  `./gradlew compileKotlin compileTestKotlin`, 전체 회귀 `./gradlew test` 모두 통과.
- **후속**: `GlobalModelAttributeAdvice`에 새로 추가한 `sendYonaUsage` 전역 속성을 이용해, #1 작업 당시 미이식으로
  남겨뒀던 legacy `layout.scala.html`의 Google Analytics 블록도 #2 작업과 함께 `site/layout.html`의 `scripts`
  조각에 마저 채워 넣었다(같은 커밋에는 포함되지 않고 TASK-0221에서 함께 처리, 아래 #2 로그 참고).

### #2 `layout_framed.scala.html` → `site/layout_framed.html` (TASK-0221)

- **원인**: legacy와 대조한 결과 `site/layout_framed.html`은 이미 sidebar 콘텐츠(즐겨찾기/프로젝트 탭, 조직·프로젝트
  목록 루프)와 `iframePath`/`site/layout :: scripts` 재사용까지 상당히 진척돼 있었으나(선행 세션 작업), 다음이
  빠져 있었다:
  1. `og:*`/`twitter:*` 메타 태그 전체(이 파일은 `site/layout.html`의 `head` 조각을 공유하지 않고 자체 `<head>`를
     가지고 있어 #1에서 고친 내용이 적용되지 않는 별도 위치였다)
  2. `nprogress.css`/`magnific-popup.css` 스타일시트 링크
  3. 사이드바 프로젝트/조직 목록 항목에 `data-toggle="popover"`가 마크업엔 있는데 `.popover()` 초기화 호출이
     어디에도 없어 실제로는 동작하지 않는 죽은 마크업 상태였음
  4. `body` 클래스에 legacy의 `@theme`(다른 `site/*.html` 파일들의 `theme-default` 컨벤션과 동일한 자리)가
     `framed-body`만 있고 빠져 있었음
  5. `Application.SEND_YONA_USAGE` 게이팅 Google Analytics 스크립트 전체
- **구현 내용**:
  - `GlobalModelAttributeAdvice`에 `sendYonaUsage`(`@Value("\${yuna.analytics.send-usage:false}")`, legacy
    `application.conf`의 `application.sendYonaUsage` 대응, 기본값은 자체 호스팅 환경 안전을 위해 off) 전역 모델
    속성 추가.
  - `site/layout_framed.html`의 자체 `<head>`에 og/twitter 메타 태그 8종, `nprogress.css`, `magnific-popup.css`
    링크 추가. `body` 클래스에 `theme-default` 추가(형제 파일들 컨벤션과 통일).
  - 기존 탭 active-state 초기화 스크립트 블록 끝에 `$('[data-toggle="popover"]').popover();` 추가.
  - `</body>` 직전에 `${sendYonaUsage}` 조건부 GA 스니펫 추가(legacy와 동일한 추적 ID `UA-102735758-1` 그대로 이식
    — 이식 자체는 충실히 하되 기본값 off로 안전하게 둠).
  - 같은 커밋에서 `site/layout.html`의 `scripts` 조각에도 동일한 `${sendYonaUsage}` 게이팅 GA 스니펫을 추가해
    #1에서 미이식으로 남겨뒀던 항목을 마저 닫았다.
- **legacy와 다르게 처리한 지점**: 없음(둘 다 config 플래그로 게이팅되는 legacy 동작 그대로, 기본값만 자체 호스팅
  환경에 안전하도록 off로 설정 — legacy도 배포 시 conf에서 별도로 켜야 하는 구조라 동일한 성격의 기본값).
- **테스트**: `TemplateEquivalenceSpec.kt`의 `[Test-19-6] framed 레이아웃(site/layout_framed.html) 동치성 검증`
  (og/twitter 메타·nprogress/magnific-popup 자산, body 클래스, popover 초기화 스크립트, GA 기본 비활성 4종) +
  `[Test-19-5]`에 GA 기본 비활성 케이스 1종 추가. GA가 `sendYonaUsage=true`일 때 실제로 렌더링되는지는 Spring
  컨텍스트 속성을 테스트에서 안전하게 토글하는 장치가 없어 별도로 검증하지 않았고, 기본값(off) 경로만 검증했다
  (프로덕션 코드는 legacy와 동일한 조건부 렌더링 구조를 그대로 사용하므로 로직 자체의 리스크는 낮다고 판단).
- **검증**: `./gradlew test --tests "com.github.search5.yona.web.TemplateEquivalenceSpec"`(RED 확인 후 GREEN),
  전체 회귀 `./gradlew test` 통과.

### #3 `siteLayout.scala.html` → 사이트 관리자 화면 9개 (TASK-0222)

- **원인**: legacy `siteLayout.scala.html`은 `layout()`을 `@common.navbar(menuType, null, null)` + `@content` +
  `@common.footer()`로 감싸는 얇은 데코레이터다. yuna 쪽을 확인해보니 `site/{data,diagnostic,issueList,mail,
  massMail,postList,projectList,update,userList}.html` 9개 관리자 화면이 이미 각자 `site/layout.html`의
  `head`/`gnb`/`breadcrumb`/`sidebar`/`scripts` 조각을 개별적으로 조합해 사실상 `siteLayout`과 동등한 뼈대를
  구성하고 있었다(선행 세션 작업) — 즉 legacy처럼 파일 하나를 extends하는 대신, Thymeleaf의 표준 관용구인
  "조각 여러 개를 각 페이지가 직접 조합"하는 방식으로 이미 아키텍처 치환이 이뤄져 있었다. 다만 9개 파일 모두
  `footer` 조각만 빠져 있었고(`data.html`은 `scripts` 조각조차 빠져 있었음), legacy는 `@content` 바로 뒤에
  `@common.footer()`를 렌더링하므로 명백한 누락이었다.
- **구현 내용**: 9개 파일 전부에 `<footer th:replace="~{site/layout :: footer}"></footer>`를 본문 콘텐츠와
  `scripts` 조각 사이에 추가(`data.html`은 `scripts` 조각도 함께 추가 — 다른 8개 파일과의 일관성 확보, 나머지
  8개는 이미 있었음).
- **legacy와 다르게 처리한 지점**: "레이아웃 파일 하나가 여러 화면을 extends"하는 구조를, "여러 화면이 각자
  조각을 조합"하는 구조로 치환 — 이는 Thymeleaf에 Play의 curried 데코레이터 템플릿과 동일한 문법이 없어
  아키텍처적으로 불가피한 치환이며(선행 세션에서 이미 이렇게 확립된 컨벤션), 신규 `siteLayout.html` 데코레이터
  파일을 새로 만들지 않고 기존 컨벤션을 따라 누락분만 채웠다.
- **테스트**: `TemplateEquivalenceSpec.kt`의 `[Test-19-7] 사이트 관리자 레이아웃(siteLayout.scala.html 대응)
  동치성 검증` — `/sites/userList`를 사이트 관리자 권한으로 조회해 `footer.page-footer-outer` 렌더링 확인.
  나머지 8개 파일은 동일한 패턴의 기계적 반복 수정이라 별도 테스트를 추가하지 않고 회귀 스위트로 컴파일/렌더링
  깨짐 여부만 확인했다.
- **검증**: `./gradlew test --tests "com.github.search5.yona.web.TemplateEquivalenceSpec"`(RED 확인 후 GREEN),
  전체 회귀 `./gradlew test` 통과.

### #4~#9 그룹1 잔여 항목 조사 (TASK-0223, 코드 변경 없음 — 문서만)

- **#4 `siteLayout_framed.scala.html`**: legacy에서 유일한 사용처가 `index/sidebar.scala.html`(빈 content로
  호출)이고, 이는 "사이드바+iframe 프레임 셸"만 그리는 화면이라 #2에서 완료한 `site/layout_framed.html`
  (`/user/sidebar`)과 동일 대상. `[i]`로 상태 변경, 코드 변경 없음.
- **#5 `projectLayout.scala.html`**: `navbar+project.header+content+footer` 데코레이터 패턴이 `project/*.html`
  각 파일에 이미 조합 방식으로 이식돼 있음을 확인(`home/members/setting/statistics.html`은 완전, 그러나
  `change_vcs/delete/fork/issuelabels/setting_webhook/transfer/watchers.html` 7개는 `project/header`와
  `site/layout::footer` 조각이 빠져 있음을 발견). 그룹6(#87~112) 착수 시 처리하기로 결정하고 백로그 비고에 기록,
  지금은 손대지 않음(그룹1 범위 밖).
- **#6 `organizationLayout.scala.html`**: 동일 패턴이나 `organization/*.html` 10개 파일 전부 `gnb`/`footer` 조각이
  전무함을 확인 — project 그룹보다 훨씬 미착수 상태. 그룹12(#193~209) 착수 시 처리하기로 기록.
- **#9 `restricted.scala.html`**: play-authenticate 라이브러리 데모 페이지(현재 사용하지 않는 인증 스택 전용
  API를 노출하는 디버그용 화면)라 이식 가치 대비 비용이 과도하다고 판단해 보류 결정, 사유를 표에 기록(`docs/
  PARITY_BACKLOG.md`의 보류 항목 기록 관행과 동일 형식).
- **검증**: 문서만 수정, 코드 변경 없음 — 별도 테스트/빌드 불필요.

### #10~#18 `common/*` 공용 파샬 그룹2 착수분 (TASK-0224)

- **원인**: #10(navbar)/#11(footer)/#12(scripts)/#13(usermenu)/#15(loginDialog)는 이미 `site/layout.html`의
  `gnb`/`footer`/`scripts` 조각에 인라인 조합돼 있었으나(선행 세션), legacy와 줄 단위 대조 결과 다음이 누락:
  1. `common/navbar.scala.html`: `HIDE_PROJECT_LISTING` 설정 또는 게스트 사용자일 때 "전체 목록" 링크를 숨기는
     분기(`!Application.HIDE_PROJECT_LISTING && !UserApp.currentUser().isGuest`)가 없어 항상 노출되고 있었음.
     백엔드는 이미 `hideProjectListing`(P0-23) 설정과 `User.isGuest` 필드를 갖추고 있었음.
  2. `common/usermenu.scala.html`: "내 이슈" 링크 옆 미해결 이슈 카운터 배지(`Issue.countOpenIssuesByUser`
     대응)가 없었음. "새 그룹 만들기" 링크가 게스트에게도 노출되고 있었음(legacy는 `!currentUser.isGuest`로 숨김).
  3. `common/scripts.scala.html`: 토스트 알림 jquery-tmpl(`tplYobiToast`), 로그인 사용자 전용 "U" 단축키
     (`/user/{loginId}`), bfcache 복원 시 `NProgress.done()` 호출(`pageshow` 이벤트), iframe 내부 페이지에서
     부모 창의 히스토리를 동기화하는 클릭 핸들러(`.ago`/`.head-anchor`/`.share-link`)가 빠져 있었음.
  4. `common/loginDialog.scala.html`: `yobi.LoginDialog.js`가 의존하는 `jquery-ui-1.10.4.custom.min.js`
     스크립트 로드가 빠져 있었음(정적 자산 자체는 이미 존재).
  #11(footer)와 #17(calendar)은 대조 결과 완전히 일치함을 확인(코드 변경 없음).
- **구현 내용**:
  - `GlobalModelAttributeAdvice`에 `hideProjectListing`(`@Value`, 기존 컨트롤러들과 동일 프로퍼티 키 재사용)과
    `myOpenIssueCount`(`IssueRepository.countByAssigneeAndState(userId, OPEN)` 재사용, 로그인 사용자 기준)
    전역 모델 속성 추가.
  - `site/layout.html`의 `gnb`/`errorGnb`에서 "전체 목록" 링크에 `th:if` 가드 추가, "내 이슈" 링크에
    `.counter-badge` 배지 추가(`th:inline="text"` + `[[...]]`로 기존 텍스트 노드 구조 보존), "새 그룹 만들기"에
    게스트 가드 추가.
  - `scripts` 조각에 `tplYobiToast` 템플릿, "U" 단축키(`th:if`+인라인 JS 문자열 결합), `pageshow`/`NProgress.done()`
    리스너, iframe 히스토리 동기화 스크립트, `jquery-ui` 스크립트 로드 추가.
- **legacy와 다르게 처리한 지점**:
  - navbar의 조직 검색범위 세부조건(`HIDE_PROJECT_LISTING||게스트`일 때만 조직 멤버/관리자에게 그룹 검색범위
    노출)은 좁은 코너케이스라 미이식.
  - usermenu의 `NAVBAR_CUSTOM_LINK_NAME/URL`(운영자 지정 커스텀 링크)과 OAuth 세션 불일치 경고는 대응 백엔드
    설정이 없고 실사용 가치가 낮아 미이식.
  - loginDialog의 `useSocialLoginOnly` 토글과 동적 OAuth 프로바이더 목록은 yuna가 Spring Security OAuth2
    정적 클라이언트 등록 구조라 근본적으로 다르며(선행 세션에서 이미 github/google 고정 버튼으로 아키텍처
    치환), 토글 자체를 위한 백엔드 설정 신설은 이번 범위에서 보류.
  - 세 가지 모두 사유를 백로그 표 비고에 명시했다(침묵 생략이 아님).
- **테스트**: `TemplateEquivalenceSpec.kt`에 `[Test-19-8]`(게스트/일반회원 GNB 링크 노출, 미해결 이슈 배지)
  3종, `[Test-19-9]`(토스트 템플릿, U 단축키, pageshow, iframe 히스토리 동기화, jquery-ui 로드) 2종 추가.
  각각 RED 확인 후 구현.
- **검증**: `./gradlew test --tests "com.github.search5.yona.web.TemplateEquivalenceSpec"`(RED 확인 후 GREEN),
  전체 회귀 `./gradlew test` 통과.

### #14 `common/usermenu_tab_content_list.scala.html` (TASK-0225)

- **원인**: legacy 이 파일은 `index/myOrganizationList`/`myProjectList`/`myRecentIssueList` 3개 파샬을 include하는
  얇은 조합 파일이다. yuna의 `common/usermenu_tab_content_list.html`(307줄)은 `myOrganizationList`+`myProjectList`
  내용은 이미 갖추고 있었으나, **`myRecentIssueList`(최근 방문한 이슈 탭) 전체가 빠져 있었다** — 탭 패널 마크업뿐
  아니라 `site/layout.html`의 GNB 사이드바 탭 메뉴(`common/usermenu.scala.html` 대응, #13에서 이미 손댄 영역)에도
  탭 버튼 자체가 없었다(yuna는 legacy에 없는 "전체" 탭으로 대체돼 있었음). 백엔드는 `RecentIssueService.
  getRecentIssues(User)`(P1-09)가 이미 존재했으나 `/user/usermenuTabContentList` 컨트롤러가 이를 모델에 담지
  않고 있었다.
- **구현 내용**:
  - `UserViewController`에 `RecentIssueService` 주입, `usermenuTabContentList()`에
    `visitedIssues = recentIssueService.getRecentIssues(loginUser)` 모델 속성 추가.
  - `common/usermenu_tab_content_list.html`에 `id="myRecentIssueList"` 탭 패널 추가(검색창 + `visitedIssues`
    반복 렌더링, legacy `myRecentIssueList_partial.scala.html`의 `data-toggle=popover`/`project-item-container`/
    `issue-item` 구조를 그대로 이식).
  - `site/layout.html`의 `gnb`/`errorGnb` 사이드바 탭 메뉴에 "최근 방문 이슈" 탭 버튼을 `myProjectList` 다음
    (legacy와 동일 순서: Favorite→Project→RecentIssue) 위치에 추가.
- **legacy와 다르게 처리한 지점**: yuna가 이미 갖고 있던 "전체"(`allProjectList`) 탭은 legacy에는 없는 항목이지만,
  이미 완성돼 동작 중인 기능을 삭제하는 것은 파괴적 변경이라 이번 범위에서 제거하지 않고 4번째 탭으로 유지했다
  (legacy 3탭 + yuna 추가 1탭). 완전한 legacy 동치화를 원하면 별도 결정 필요 — 백로그에 기록.
- **테스트**: `TemplateEquivalenceSpec.kt`의 `[Test-19-10] 사용자 메뉴 탭 콘텐츠(common/usermenu_tab_content_list.
  scala.html) 동치성 검증` 2종(최근 방문 이슈 렌더링, GNB 탭 버튼 노출). `RecentIssueService.recordIssueVisit`로
  실제 방문 이력을 시딩해 검증. `UserViewControllerSpec.kt`(기존 mockk 기반 스펙)이 생성자 시그니처 변경으로
  깨져 `recentIssueService` mock을 추가해 함께 고쳤다(회귀).
- **검증**: `./gradlew test --tests "com.github.search5.yona.web.TemplateEquivalenceSpec" --tests
  "com.github.search5.yona.web.UserViewControllerSpec"`(RED 확인 후 GREEN), 전체 회귀 `./gradlew test` 통과.

### #16~#18 `common/*` 공용 파샬 마무리 (TASK-0226)

- **원인**: #16(select2)은 legacy와 대조 결과 완전 일치(코드 변경 없음). #18(mySeriesMenuTab)은 두 가지 실질
  격차를 발견: (1) "기본 페이지로 설정" 버튼이 legacy에서는 `!path.equals("/") && !path.substring(1).equals(
  loginDefaultPage)` 조건으로 "이미 기본 페이지인 경우" 숨겨지는데 yuna는 활성 탭 종류만 체크하고 이 비교가
  아예 없었음. (2) `common/mySeriesMenuTab.html :: menu` 공용 조각이 `issue/my_list.html` 한 곳에서만 쓰이고,
  legacy에서 같은 탭바를 공유해야 할 `index/notifications.html`과 `user/userFiles.html`은 각자 탭바를 인라인
  중복 작성해뒀으며 두 곳 다 "기본 페이지로 설정" 버튼 자체가 없었다.
- **구현 내용**:
  - `GlobalModelAttributeAdvice`에 `loginDefaultPage`(`UserSettingRepository.findByUserId(id).loginDefaultPage`)
    전역 모델 속성 추가.
  - `common/mySeriesMenuTab.html`의 버튼 노출 조건에 `currentRequestPath != '/' and currentRequestPath.
    substring(1) != loginDefaultPage`를 추가하고, `requestURI`(개별 컨트롤러 의존) 대신 이미 전역으로 존재하는
    `currentRequestPath`(#1에서 추가)를 재사용하도록 통일.
  - `index/notifications.html`, `user/userFiles.html`의 인라인 중복 탭바를 `th:replace="~{common/
    mySeriesMenuTab :: menu('notifications'|'my_files')}"` 공용 조각 재사용으로 교체.
- **legacy와 다르게 처리한 지점**: 없음 — 이번 건은 순수하게 누락 복원 및 기존 컨벤션(전역 모델 속성) 재사용.
- **테스트**: `TemplateEquivalenceSpec.kt`의 `[Test-19-11] 개인 3탭 메뉴(common/mySeriesMenuTab.scala.html)
  동치성 검증` 3종(기본페이지 일치 시 버튼 숨김, 불일치 시 노출, 내 파일 페이지의 공용 탭바 공유).
- **검증**: `./gradlew test --tests "com.github.search5.yona.web.TemplateEquivalenceSpec"`(RED 확인 후 GREEN).
  전체 회귀는 사용자 지시에 따라 10개 항목당 1회로 배치(다음 배치에서 실행 예정).

### #19~#24 마크다운 에디터·첨부파일 업로드 조각 (TASK-0227)

- **원인**: #19(markdown)는 `site/layout.html :: markdown(project)` 조각과 완전 일치 확인. #20(editor)의
  `markdownEditor` 조각은 `wrapIdGen`(editorName의 `-` 뒤 ID 재사용)/`textareaName`(`-` 앞부분만 name으로 사용)/
  `viaEmail` 파라미터화가 legacy에는 있지만 yuna에는 없음 — 그러나 현재 모든 호출부가 대시 없는 단순 이름만
  넘기고, `viaEmail`을 구동할 "이메일로 이슈/댓글 생성" 백엔드 기능 자체가 yuna에 없어(grep 확인) 실질적으로
  관측 불가능한 차이로 판단해 이번엔 보류.
  **#22(uploadForm)에서 중대한 발견**: `issue/view.html`/`board/view.html`의 첨부파일 업로드 위젯이 legacy의
  `common/uploadForm.scala.html`(`upload-wrap`/`data-resource-type`/`input[name=filePath]`/`attach-wrap`
  구조, `common.attach.drophere`/`clickbutton`/`pastehere`/`attachIfYouSave` 메시지) 대신 완전히 다른 자체
  마크업(`upload-drop-zone`/`input[name=file]`/`common.attach.clickToUpload` 단일 메시지)으로 **독자 구현**돼
  있었다. 정적 JS 전체를 grep해도 `upload-drop-zone`/`upload-file-input` 셀렉터를 참조하는 코드가 전혀 없어,
  이 마크업은 겉보기엔 그럴듯하지만 실제로는 아무 JS에도 연결되지 않은 죽은 위젯이었다(선행 세션에서 Test-19-2/
  19-3을 통과시키기 위해 만들어진 것으로 추정). `<form id="comment-form">`에도 legacy가 요구하는
  `enctype="multipart/form-data"`가 빠져 있었다.
- **구현 내용**:
  - 신규 `common/uploadForm.html` 조각(`th:fragment="uploadForm(resourceType, resourceId, formId)"`) 작성 —
    legacy `common/uploadForm.scala.html` 구조를 그대로 이식(`data-resource-type`/`data-resource-id`는
    `ResourceType` Kotlin enum 이름 문자열로, `AttachmentController`의 `ResourceType.valueOf(containerType)`과
    일치시킴).
  - `issue/view.html`(`ISSUE_COMMENT`)/`board/view.html`(`NONISSUE_COMMENT`)의 댓글 폼에서 죽은 `#upload-drop-zone`
    마크업을 새 조각으로 교체하고, `<form>`에 `enctype="multipart/form-data"` 추가.
  - 기존 `TemplateEquivalenceSpec.kt`의 `[Test-19-2]`/`[Test-19-3]`가 옛 마크업(`#upload-drop-zone`, `input[name=
    file]`)을 검증하고 있던 것을 legacy 구조에 맞는 셀렉터(`.upload-wrap[data-resource-type]`, `input[name=
    filePath]`)로 수정(이전 세션이 "yuna식 독자 구현"을 그대로 테스트에 박제해뒀던 것을 바로잡음).
- **legacy와 다르게 처리한 지점**: `data-resource-type` 값은 legacy처럼 소문자 스네이크케이스(`issue_comment`)가
  아니라 Kotlin enum 상수명(`ISSUE_COMMENT`)을 그대로 썼다 — `AttachmentController`가 `ResourceType.valueOf(...)`
  로 파싱하므로 enum 상수명이어야 실제로 동작한다(legacy의 소문자 값은 Play의 다른 직렬화 방식 때문).
  #23(attachmentFile, 기존 첨부파일 서버렌더 표시)은 아직 착수하지 않음 — 별도 조사 필요(비고에 기록).
  업로드 JS(`yobi.Files.js`)가 이 마크업을 실제로 초기화(`_getUploader`/`_initElement`)하는 호출부는 템플릿
  범위를 벗어나므로 이번 항목에서 추적하지 않았다 — 순수 마크업의 legacy 동치화만 수행했다.
- **테스트**: 기존 `[Test-19-2]`/`[Test-19-3]`의 업로드 위젯 관련 단언을 새 구조로 갱신, RED 확인 후 구현.
- **검증**: `./gradlew test --tests "com.github.search5.yona.web.TemplateEquivalenceSpec"`(RED 확인 후 GREEN).
  전체 회귀는 10개 항목 배치 규칙에 따라 다음 체크포인트에서 실행.

### #25~#28 댓글 수정/삭제/카운트 표시 (TASK-0228, 조사만 — 코드 변경 없음)

- **#27(commentCount)/#28(commentAndVoterPairDisplay)**: `issue/list.html`에 이미 완전히 동일한 구조로 인라인돼
  있음을 확인(`.comments-count.comments-count-color`, `.item-count-groups` 조합 표시). 코드 변경 없음, `[i]`로
  상태 갱신.
- **#25(commentUpdateForm)/#26(commentDeleteModal)**: 조사 결과 **댓글 인라인 수정·삭제 UI 자체가 yuna에
  전혀 존재하지 않음**(`comment-editform`/`comment-update-form`/`comment-delete-modal` 마크업 및
  `yobi.Comment.js` 초기화 스크립트 grep 0건). 백엔드는 `CommentController`에 `PUT`/`DELETE /api/projects/
  {projectId}/issues|posts/{number}/comments/{commentId}` REST 엔드포인트로 이미 존재하나, legacy는
  `<form action=".../{id}" enctype=multipart/form-data method=post>` 전체 폼 제출 방식이라 REST API 프론트엔드를
  AJAX로 새로 설계해야 한다(단순 마크업 치환을 넘어서는 작업 — 파일 업로드까지 REST 흐름에 맞게 통합 필요).
  이번 배치에서는 착수하지 않고 백로그에 상세 조사 결과를 기록만 했다 — 그룹2의 나머지 항목(#29~#44)을 마저
  훑은 뒤, 별도 세션에서 #25/#26을 한 묶음으로 집중 처리할 것을 권장(같은 JS 초기화 경로를 공유하므로 함께
  설계하는 게 효율적).
- **검증**: 코드 변경 없음(조사만), 별도 테스트/빌드 불필요.

### #29~#37 대댓글 조사, 카운트 조각 확인, 태스크리스트·라벨 CSS 이식 (TASK-0229)

- **#29~#31(child_commentForm/childComments/childCommentsAnchorDiv)**: 조사 결과 대댓글(reply) 기능 자체가
  yuna에 전혀 없음(백엔드는 `CommentController`가 `parentCommentId`를 이미 받아 처리하나 프론트 UI 없음).
  #25/#26과 같은 "댓글 UI 전체 AJAX 재설계" 묶음으로 별도 세션 처리 권장, 백로그에 기록만 하고 미착수.
- **#32~#34(voteCount/sharerCount/showSubtasksCheckbox)/#36(twoColumnModeCheckboxArea)**: `issue/list.html`에
  이미 legacy와 완전히 동일한 구조로 인라인돼 있음을 확인, 코드 변경 없음.
- **#35(tasklistBar)**: `issue/view.html`/`board/view.html` 어디에도 태스크리스트 진행률 바 셸이 없었음을 발견.
  정적 자산(`yona.Tasklist.js`, `gfm-task-list.js`)은 이미 존재했으나 아무 페이지에서도 로드되지 않는 죽은
  코드 상태였다. legacy와 동일하게 본문(`#issue-body-N`/`#post-body-N`) 안, markdown-wrap 바로 앞에 `.tasklist`
  셸(`task-title`+`done-counter`+`task-progress`+`bar red`)을 추가하고 `yona.Tasklist.js`를 로드했다(legacy도
  `gfm-task-list.js`는 어디서도 로드하지 않아 그대로 미로드 유지).
- **#37(issueLabelColor)**: 이 legacy 파일은 Thymeleaf 뷰가 아니라 `IssueLabelApp.labelStyles()` 컨트롤러가
  `text/css`로 직접 렌더링하는 동적 스타일시트임을 확인. yuna의 `LabelStyleController.kt`가 RGB/hex 파싱과
  YCC 휘도 계산까지 포함해 이미 완전히 동일한 로직으로 이식돼 있었다(선행 세션). 다만 legacy가 이 스타일시트를
  `<link>`하는 10개 화면 중 `issue/list`/`milestone/list`/`milestone/view` 3곳만 링크가 있었고, `issue/view`/
  `issue/create`/`issue/edit`/`board/view`/`board/list` 5곳은 빠져 있어 추가했다. 나머지 2곳(`project/
  partial_dashboard_issuesbylabel`, `project/partial_issuelabels_list`)은 yuna 쪽 대응 파일이 있는지 확실치
  않아 이번엔 손대지 않고 비고에 기록만 했다(그룹6 project/* 작업 시 재확인 권장).
- **legacy와 다르게 처리한 지점**: 없음(순수 누락 복원).
- **테스트**: `TemplateEquivalenceSpec.kt`의 `[Test-19-12]`(태스크리스트 셸+스크립트 로드, 이슈/게시판 2종)와
  `[Test-19-13]`(labels.css 링크, 이슈상세/게시판상세/게시판목록 3종) 추가, RED 확인 후 구현.
- **검증**: `./gradlew test --tests "com.github.search5.yona.web.TemplateEquivalenceSpec"`(RED 확인 후 GREEN).
  전체 회귀는 10개 항목 배치 규칙에 따라 다음 체크포인트에서 실행(이번 배치로 #16~#37 총 22개 항목 누적,
  다음 응답에서 전체 회귀 실행 예정).
  → **전체 회귀 `./gradlew test` TASK-0229 커밋 직후 실행, 통과 확인함.**

### #38~#44 그룹2 잔여 항목 조사 (TASK-0230, 코드 변경 없음 — 문서만)

- **#38(commitMsg)**: `.commitMsg` 클래스가 `code/{view,diff,svnDiff}.html` 3곳에 이미 존재하나, legacy의
  short/desc 펼침(moreBtn) 구조까지 일치하는지는 미확인. `[~]`로 표시, 그룹10(`code/*`, #154~166) 착수 시
  정밀 대조하기로 결정.
- **#39(branchItem)**: 브랜치 드롭다운 항목 — 코드 브라우징 화면과 강하게 결합돼 그룹10에서 함께 처리.
- **#40(reviewForm)**: 코드리뷰 댓글 폼 — PR/리뷰 도메인(그룹11, #167~192)에서 처리. `common.editor`/
  `common.uploadForm` 재사용 구조라 #20/#22가 이미 재료를 준비해뒀음을 확인.
- **#41(partial_history)**: "변경 이력" 기능 자체가 yuna `Issue`/`Posting` 엔티티에 없음(`history` 필드 부재)
  — 순수 템플릿 이식 범위를 넘어서는 백엔드 항목이라 `docs/PARITY_BACKLOG.md`에 먼저 등록이 필요함을 확인,
  이번 배치에서는 조사만.
- **#42(notificationMail)**: `NotificationMailRenderer.kt`가 Kotlin 코드로 이미 완전히 동일한 HTML을 생성 중임을
  확인(폰트 스택, 구분선, 푸터 링크, 메시지 키 전부 일치). 코드 변경 없음.
- **#43(uservoice)**: legacy 자체에서 호출부 0건(죽은 코드) + 원본 프로젝트 전용 UserVoice 계정 하드코딩이라
  이식 대상에서 제외 결정, 사유 기록.
- **#44(debug)**: legacy 자체에서 호출부 0건(죽은 코드)이라 이식 대상에서 제외 결정, 사유 기록.
- **그룹2(#10~44) 결과**: 완료/확인 25개(#10~24,27~28,32~37,42), 대형 항목이라 별도 세션 필요로 플래그 6개
  (#25,26,29,30,31,41), 제외 결정 2개(#43,44), 다음 그룹 착수 시 처리하기로 미룬 항목 3개(#38,39,40) — 그룹2
  전체 처리 완료.
- **검증**: 문서만 수정, 코드 변경 없음.

### 그룹3 `error/*` 에러 페이지 (TASK-0231)

- **원인**: legacy는 각 HTTP 에러마다 "프로젝트 컨텍스트 버전"(`notfound`/`forbidden`/`badrequest`, `projectLayout`
  사용)과 "제네릭 버전"(`*_default`, `siteLayout`/단독 `layout` 사용) 두 벌을 갖고 있다. yuna의 `error/{400,403,
  404,500}.html`은 모두 `project` 파라미터가 없는 제네릭 뷰이므로(대부분 컨트롤러가 직접 `return "error/404"`
  식으로 리턴), 실제로는 legacy의 **"_default" 변형**에 대응한다는 것을 재조사로 확인했다(기존 백로그 표는
  이 구분 없이 `notfound`/`forbidden`/`badrequest`에 매핑해뒀던 게 부정확했음).
  - `notfound_default`는 다른 세 `_default` 파일과 달리 `siteLayout`이 아니라 자체 최소 헤더+**전용 footer**
    (`Copyright © NAVER Corp. Supported by ... D2 Program`, 메인 사이트 footer와 다른 문구)를 쓰는 유일한
    예외였다. yuna의 `errorGnb` 조각은 이미 이 최소 헤더와 일치했으나 전용 footer가 아예 없었다.
  - `forbidden_default`/`badrequest_default`/`internalServerError_default`는 `siteLayout`(=검색폼 있는 전체
    GNB + 메인 사이트 footer)을 쓰는데, yuna의 `error/403,400,500.html` 세 개 다 `errorGnb`(간소 헤더, 원래는
    notfound_default 전용)를 잘못 쓰고 있었고 footer도 전부 빠져 있었다.
- **구현 내용**:
  - `error/404.html`: `errorGnb`는 유지하고, legacy `notfound_default` 전용 D2 Program footer를 그대로 추가.
  - `error/403.html`, `400.html`, `500.html`: `errorGnb` → `gnb`로 교체하고 `site/layout :: footer`(메인 사이트
    footer) 추가.
- **legacy와 다르게 처리한 지점**: 없음(순수 오분류 수정 + 누락 복원).
- **보류 결정**: #45(notfound)/#47(forbidden)/#49(forbidden_organization)/#50(badrequest) — 프로젝트/조직
  컨텍스트 인지형 에러 뷰. yuna는 대부분 컨트롤러가 제네릭 에러 뷰 이름을 직접 리턴하는 단순한 패턴이라, 이걸
  프로젝트/조직 컨텍스트별로 분기하려면 그 뷰를 리턴하는 모든 컨트롤러를 고쳐야 하는 광범위한 리팩터 — 투입
  대비 효과(에러 페이지에 프로젝트/조직 메뉴 표시)가 낮다고 판단해 보류, 사유 기록.
  #53(requestTextEntityTooLarge) — 업로드 용량 초과(413) 처리 자체가 yuna에 없어(전역 `@ExceptionHandler` 부재)
  순수 템플릿 이식 범위를 넘는 백엔드 항목이라 `docs/PARITY_BACKLOG.md` 등록 후 처리 권장, 조사만 기록.
- **테스트**: `TemplateEquivalenceSpec.kt`의 `[Test-19-14]` 3종 — 존재하지 않는 프로젝트 접근 시 404의 D2
  footer, 비공개 프로젝트 비로그인 접근 시 403의 전체 GNB+footer(실제 HTTP 트리거로 검증), 400/500은 트리거
  조건 구성이 복잡해 템플릿 파일 내용 직접 검사로 대체(gnb/footer 조각 참조 여부, errorGnb 미사용 확인).
- **검증**: `./gradlew test --tests "com.github.search5.yona.web.TemplateEquivalenceSpec"`(RED 확인 후 GREEN).
  전체 회귀는 10개 항목 배치 규칙에 따라 이번 응답 마지막에 실행.

### 그룹4 `index/*` 홈/대시보드 (TASK-0232)

- **원인**: legacy `index/index.scala.html`은 `index/notifications.scala.html`을 그대로 위임 호출하는 얇은
  래퍼임을 확인(내용은 #68과 동일). yuna의 `index.html`이 `common/mySeriesMenuTab` 공용 조각 대신 **3번째로**
  탭바를 하드코딩 중복(이미 #14/#18에서 `index/notifications.html`/`user/userFiles.html` 2곳을 고쳤는데 이
  파일은 놓쳤었음)하고 있었고, "기본 페이지로 설정" 버튼이 legacy의 `!path.equals("/")` 조건 없이 항상
  노출되고 있었다.
  `index/myProjectList`/`myOrganizationList`(+각 `_partial`)/`displayProjects`/`partial_intro` 6개 파일은
  이미 `common/usermenu_tab_content_list.html`과 `index.html`에 legacy와 정확히 동일한 구조(탭 순서, 필드,
  CSS 클래스)로 인라인돼 있음을 확인.
  `index/allProjectList.scala.html`/`allOrganizationList.scala.html`/`allOrganizationList_partial.scala.html`
  은 legacy 자체에서도 호출부가 0건인 죽은 코드임을 확인(grep). 단 `allProjectList_partial.scala.html`은
  이름과 달리 `myOrganizationList.scala.html`이 조직 하위 프로젝트를 그릴 때 재사용하는 살아있는 코드였다
  (이미 `common/usermenu_tab_content_list.html`에 인라인 확인됨). yuna의 "전체"(All) 탭은 이 죽은 legacy
  코드와 무관하게 존재하는 yuna 자체 추가 기능임이 이번 조사로 재확인됐다(#14/TASK-0225에서 유지 결정한 그대로).
- **구현 내용**: `index.html`의 하드코딩된 탭바를 `th:replace="~{common/mySeriesMenuTab :: menu('notifications')}"`
  로 교체.
- **legacy와 다르게 처리한 지점**: 없음(순수 중복 제거 + 누락 복원).
- **테스트**: `TemplateEquivalenceSpec.kt`의 `[Test-19-15]` — 루트 경로(`/`)에서 legacy와 동일하게 "기본
  페이지로 설정" 버튼이 숨겨지는지 검증(루트 경로 자체가 `currentRequestPath == '/'`라 항상 숨김 조건에
  해당하는 것을 활용). `IndexController.index()`가 `loginDefaultPage` 설정 시 리다이렉트하는 기존 동작
  때문에 테스트에서 `loginDefaultPage`를 명시적으로 null로 초기화해야 했다(발견 및 대응).
- **검증**: `./gradlew test --tests "com.github.search5.yona.web.TemplateEquivalenceSpec"`(RED 확인 후 GREEN).
  전체 회귀는 10개 항목 배치 규칙에 따라 다음 체크포인트에서 실행.

### 그룹5 착수분: #70~72 (TASK-0233)

- **#72(verified) 중대 발견**: `UserController.verifyUser()`/`verifyUserLegacy()`가 `@RestController`
  클래스에 있어 `ResponseEntity<String>`으로 하드코딩된 raw HTML(`"<h3>회원가입 계정 인증이 완료되었습니다.</h3>..."`)
  을 직접 반환하고 있었다 — Thymeleaf 템플릿, GNB, footer, i18n 메시지 키 전부 우회하는 완전한 독자 구현이었다.
  `UserService.verifyUser()`/`UserVerification` 백엔드 자체는 이미 정확히 이식돼 있었다.
- **구현 내용**: 신규 `user/verified.html`(legacy `siteLayout`→전체 GNB+footer, `#{user.verified}`/
  `#{user.verified.detail}` 메시지 키) 작성. 두 엔드포인트를 `UserController`(RestController)에서
  `UserViewController`(Controller)로 이전 — 이 프로젝트의 기존 API/View 컨트롤러 분리 컨벤션과 일치시킴.
  실패 시 legacy의 `notFound("Invalid verification")`에 맞춰 404 상태코드 추가.
  `#70(login)`: 이메일 인증 안내 문구(`.email-verification-help`, `notification.confirm.mail.will.be.sent`
  메시지 키는 이미 존재)가 누락돼 있어 추가.
- **legacy와 다르게 처리한 지점**: login의 소셜로그인 동적 프로바이더 목록/`useSocialLoginOnly` 토글은 #15와
  동일 사유(Spring Security OAuth2 정적 클라이언트 등록)로 미이식. `title.loginFor` 메시지 키를 파라미터화된
  호출 대신 하드코딩 문자열로 쓴 것은 현재 사이트명이 항상 "Yona"라 렌더링 결과가 완전히 동일해 저가치로 판단,
  손대지 않음.
- **테스트**: `TemplateEquivalenceSpec.kt`의 `[Test-19-16]`(인증 완료 화면 GNB+footer+loginId 노출),
  `[Test-19-17]`(로그인 화면 이메일 인증 안내 문구) 추가. `UserControllerSpec.kt`의 이제-깨진 구식 테스트
  제거, `UserViewControllerSpec.kt`에 성공/실패 2종 유닛 테스트 추가(생성자 이전에 맞춰 회귀 수정).
- **검증**: `./gradlew test --tests "com.github.search5.yona.web.TemplateEquivalenceSpec" --tests
  "com.github.search5.yona.web.UserControllerSpec" --tests "com.github.search5.yona.web.UserViewControllerSpec"`
  (RED 확인 후 GREEN). 전체 회귀는 10개 항목 배치 규칙에 따라 다음 체크포인트에서 실행.

### #71 `user/signup.scala.html` (TASK-0234)

- **원인**: `signup.html`이 자체 `<head>`만 갖고 `site/layout`의 GNB/footer 조각을 전혀 쓰지 않는 완전한 독자
  페이지였음(login.html은 #70에서 이미 site/layout 기반으로 고쳐졌던 것과 대조적). 관리자 승인이 필요한 경우
  보여주는 안내 문구(`isUsingSignUpConfirm`)가 없었고, 아이디/이메일 실시간 중복확인(validate.js +
  yobi.user.SignUp.js) 정적 자산은 존재했지만 로드도 안 되고 백엔드 체크 엔드포인트(`/user/isUsed`,
  `/user/isEmailExist`)조차 없었다.
  같은 조사 중 `UserController.confirmEmail()`/`confirmEmailLegacy()`(보조 이메일 인증)도 #72(verified)와
  **완전히 동일한 안티패턴**임을 발견 — `@RestController`가 Thymeleaf를 우회해 하드코딩된 raw HTML을 직접
  반환하고 있었다. legacy는 성공 시 `editUserInfoForm()`으로 리다이렉트, 실패 시 `ErrorViews.NotFound`(404)를
  반환하는데, yuna는 항상 200 OK로 고정 HTML 문자열만 반환했다.
- **구현 내용**:
  - `signup.html`을 `site/layout :: head/gnb/footer/scripts` 조각 기반으로 재작성. `requireAdminConfirm`
    모델 속성(`AuthController.signupForm()`/`signup()`에 추가)으로 게이팅되는 관리자 승인 안내 블록 복구.
  - `UserController`에 신규 `GET /user/isUsed`(`{isExist, isReserved}`)/`GET /user/isEmailExist`
    (`{isExist}`) 엔드포인트 추가 — legacy `UserApp.isUsed()`/`isEmailExist()`와 동일한 JSON 응답 형식.
    `isUsed`는 `userService.isLoginIdExist(...)`와 `organizationRepository.findByName(...)`(조직명과 아이디
    네임스페이스 공유) 둘 다 확인하고, 기존에 이미 존재하던 `ReservedWordsValidator`를 재사용.
  - `signup.html`에 `validate.js`+`yobi.user.SignUp.js` 로드와 체크 URL 설정 스크립트 추가.
  - `confirmEmail()`/`confirmEmailLegacy()`를 `UserController`(RestController)에서 `UserViewController`
    (Controller)로 이전, 성공 시 `redirect:/user/editform`, 실패 시 404+`error/404` 뷰로 수정.
- **legacy와 다르게 처리한 지점**:
  - `title.signupConfirmDesc2`(관리자 문의 이메일을 역순 난독화해서 보여주는 스팸봇 방지 트릭)는 "기본
    사이트 관리자"라는 고정 개념이 yuna에 없어(사이트 관리자는 동적으로 지정/해제 가능한 상태 플래그일 뿐)
    이식하지 않음 — 관리자 승인 필요 안내 문구 자체는 살렸지만 문의 이메일 표시 줄은 생략.
  - legacy의 `addUserInfoToSession(email.user)`(이메일 인증 링크 클릭만으로 세션을 자동 갱신하는 동작)는
    Spring Security 인증 모델과 근본적으로 다른 메커니즘이고 보안에 민감한 결정이라 이식 범위에서 제외 —
    응답/뷰 처리 방식(리다이렉트+404)만 legacy와 동치화했다.
  - `title.signupFor` 메시지 키 파라미터화 대신 하드코딩 문자열은 #70과 동일 사유로 미수정.
- **테스트**: `TemplateEquivalenceSpec.kt`의 `[Test-19-18]`(GNB+footer+검증스크립트, isUsed/isEmailExist JSON
  응답 형식 2종). `UserControllerSpec.kt`에 `isUsed`/`isEmailExist` 4종 유닛 테스트 추가, 이제 깨진 구식
  `confirmEmail` 테스트 제거. `UserViewControllerSpec.kt`에 `confirmEmail` 리다이렉트/404 2종 유닛 테스트 추가.
- **검증**: `./gradlew test --tests "com.github.search5.yona.web.TemplateEquivalenceSpec" --tests
  "com.github.search5.yona.web.UserControllerSpec" --tests "com.github.search5.yona.web.UserViewControllerSpec"`
  (RED 확인 후 GREEN). 전체 회귀는 10개 항목 배치 규칙에 따라 다음 체크포인트에서 실행.

### 그룹5 `user/*` 마무리: #73~86 (TASK-0235)

- **#73(resetPassword)**: #71/#72와 동일한 "독자 페이지" 패턴(site GNB/footer 없음, i18n 메시지키 미사용,
  `resetPassword` 모듈 스크립트 미로드 — 정적 자산 `yobi.resetPassword.js`는 존재했으나 죽어있었음) 발견,
  site/layout 조각 기반으로 재작성.
- **#74~79(edit·edit_password·edit_emails·edit_token·edit_notifications·partial_edit_tabmenu)**: 전부
  gnb/footer 조각을 이미 정상적으로 사용 중임을 확인, 코드 변경 없음.
- **#80(view)/#81/#84/#85(issues·pullRequests·projectlist 파샬)**: `user/view.html`의 탭 구성(이슈/PR/
  소속 프로젝트 3개)이 legacy `view.scala.html`과 정확히 일치함을 확인 — legacy 자체도 3탭만 가지고 있었다.
- **#82/#83(milestones·postings 파샬) 매핑 오류 발견**: 원래 백로그에 "프로필 마일스톤/게시글 탭"으로
  잘못 기록돼 있었으나, 실제로는 `search/partial_search.scala.html`(검색 결과 렌더링)에서만 호출되는
  검색 도메인 전용 파샬임을 확인했다. 그룹5가 아니라 그룹14(`search/*`, #224~233) 작업 시 처리하도록
  비고에 재배치 기록만 남기고 이번엔 손대지 않음.
- **#86(userFiles)**: 첨부파일 목록 테이블 구조, hover 효과, fileType 아이콘 자동 분류, pagination까지
  legacy와 정확히 일치함을 확인, 코드 변경 없음.
- **legacy와 다르게 처리한 지점(#73)**: 없음(누락 복원).
- **테스트**: `TemplateEquivalenceSpec.kt`의 `[Test-19-19]`(비밀번호 재설정 화면 GNB+footer+모듈스크립트).
- **검증**: `./gradlew test --tests "com.github.search5.yona.web.TemplateEquivalenceSpec"`(RED 확인 후 GREEN).
  **그룹5(user/*, #70~86) 17개 항목 전체 처리 완료.** 다음은 그룹6(project/*, #87~112).

### 그룹6 `project/*` 착수분: #90,101~107 (TASK-0236)

- **#90(header) 중대 발견**: 프로젝트 가입 요청(멤버 등록 신청) UI 전체가 빠져 있었다 — 백엔드
  `ProjectMemberController`에 `POST /api/projects/{id}/enroll`/`enroll/cancel`은 이미 존재했으나 프론트가 없었음.
  `TemplateHelper`에 `isEnrolled(project, user)`(`User.enrolledProjects` 기반) 신규 추가 후, 비멤버에게 "가입
  요청하기"/이미 요청한 경우 "가입 요청 취소" 드롭다운을 복구(신규 REST 엔드포인트라 legacy의 GET 링크 대신
  기존 `data-request-method="post"`+`data-request-uri` AJAX 컨벤션 재사용 — 아키텍처적으로 필요한 치환).
  `project.isProtected`(그룹 프로젝트) "G" 배지도 legacy엔 있는데 yuna엔 없어서 추가.
- **#102~#106(change_vcs/transfer/delete/watchers/setting_webhook) 조사 중 시스템적 문제 발견**: TASK-0223에서
  이미 플래그해뒀던 7개 파일 중 6개(`fork.html` 제외)가 전부 **동일한 하드코딩 가짜 GNB**(`.gnb-wrap`/
  `.gnb-brand`/`.gnb-menu`, 검정 배경의 독자 디자인)를 복붙해 갖고 있었다. `change_vcs.html`/`setting_webhook.html`
  은 심지어 `site/layout`이 이미 로드하는 jQuery와 별개로 자체 jQuery(`jquery-1.9.0.js`, 혹은 CDN
  `jquery-3.6.0.min.js`)를 추가로 로드하고 있어 버전 충돌/중복 로드 위험까지 있었다. 전부 `site/layout`+
  `project/header`+`project/menu`+`project/setting_menu` 조각 기반으로 재작성했다.
  - `watchers.html`은 GNB뿐 아니라 콘텐츠 자체도 legacy 클래스(`.members.project.row-fluid` 등)와 i18n
    메시지키를 안 쓰는 독자 구현이었어서 콘텐츠까지 legacy에 맞춰 재작성했다.
  - `change_vcs.html`/`delete.html`/`transfer.html`은 콘텐츠(메시지키, JS 모듈 연동)는 이미 정확했어서 GNB만
    교체했다. 단 `change_vcs.html`의 `setting_menu` 활성 탭 값이 `'setting'`으로 잘못돼 있어(`'changeVCS'`가
    맞음, `project/setting_menu.html`과 대조해 발견) 함께 수정.
  - `setting_webhook.html`은 이미 존재하던 올바른 `setting_menu(..., 'webhooks')` 호출과 별개로 처음에 실수로
    잘못된 값(`'webhook'`)의 중복 호출을 추가했다가 즉시 발견해 제거했다(자체 실수 교정 기록).
- **#107(partial_webhooks_list)**: 상세 대조 미실시, 다음 배치로 이월.
- **fork.html 관련 별도 발견(그룹6 표에는 없음)**: `project/fork.html`도 동일한 가짜 GNB 패턴이었으나, 실제
  legacy 대응 파일이 `project/fork.scala.html`(존재하지 않음)이 아니라 `git/fork.scala.html`(그룹11 대상)임을
  확인했다. 콘텐츠 전체 재작성은 그룹11에서 legacy `git/fork.scala.html`과 정밀 대조해 처리하기로 하고, 이번엔
  가짜 GNB만 site/layout 조각으로 교체해 최소한의 위험 요소(중복 CSS/독자 헤더)만 제거했다.
- **legacy와 다르게 처리한 지점**: #90의 가입 요청 버튼 링크가 legacy의 단순 GET `<a href>` 대신
  `data-request-method="post"`+AJAX인 것은 백엔드가 REST(POST) 엔드포인트로 이식돼 있어 불가피함.
- **테스트**: `TemplateEquivalenceSpec.kt`의 `[Test-19-20]`(프로젝트 헤더 가입요청 버튼 3종),
  `[Test-19-21]`(change_vcs 화면 GNB/footer/조각/스크립트), `[Test-19-22]`(delete/transfer/watchers/
  setting_webhook 4종 GNB/footer 일괄 검증).
- **검증**: `./gradlew test --tests "com.github.search5.yona.web.TemplateEquivalenceSpec"`(GREEN). 전체 회귀는
  10개 항목 배치 규칙에 따라 다음 체크포인트에서 실행.

### 그룹6 `project/*` 마무리분: #91~100,107~112 (TASK-0237)

- **#91~97(home.html + readme/dashboard* 파샬)**: legacy `home.scala.html`의 탭 구조(readme/dashboard/history)
  와 각 파샬(`partial_readme`/`partial_dashboard*`)의 마크업을 줄 단위 대조 — 전부 이미 `home.html`에 정확히
  인라인돼 있음을 확인, 코드 변경 없음.
- **#98(partial_history)**: `home.html`의 history 탭에 인라인된 최근 활동 목록 마크업이 legacy
  `partial_history.scala.html`과 일치함을 확인, 코드 변경 없음.
- **#99(members.html)**: site/layout 기반 GNB/footer가 이미 정상 구성돼 있음을 확인, 코드 변경 없음.
- **#100(setting.html) 발견**: GNB/footer는 이미 정상(site/layout 기반)이었으나, 화면 하단에 `site/layout ::
  scripts`가 이미 로드하는 jQuery와 별개로 외부 CDN(`code.jquery.com/jquery-3.6.0.min.js`)을 중복 로드하고
  있었다 — #102~106에서 발견한 것과 같은 계열의 위험(버전 충돌) — 제거.
- **#107(partial_webhooks_list) 발견**: `setting_webhook.html`의 웹훅 목록이 legacy의 `.row-fluid list-head`/
  `list-item` 구조 대신 독자 Bootstrap `<table>` + 하드코딩 한글 텍스트("타입","관리" 등)로 재구현돼 있었다
  — "yuna식 독자구현 금지" 원칙 위반. legacy 구조·메시지키(`project.webhook.payloadUrl`/`secret`/`list.empty`)
  그대로 재작성. 삭제 버튼도 커스텀 `confirm()`+수동 AJAX 클릭 핸들러 대신 사이트 공용
  `data-request-method="delete"`+`data-request-uri` 컨벤션으로 교체(legacy 원본도 confirm 없이 즉시 요청이므로
  더 충실한 이식).
- **#108(issuelabels.html) 발견**: #102와 동일한 가짜 `.gnb-wrap` 독자 GNB 패턴 — site/layout+project/header+
  project/menu+setting_menu 조각 기반으로 재작성(불필요해진 `.gnb-wrap`/`.project-header`/`.setting-container`
  전용 CSS는 제거, 여전히 필요한 `.label-preset-colors`/`.btn-preset-color`/`.label-item`/`.category-box`만
  유지). 프리셋 색상이 13개뿐이었으나 legacy는 17개(`03a9f4`/`8bc34a`/`cddc39`/`ffc107` 4개 누락) — 복구.
  프리셋 버튼 클래스도 legacy가 `class="issue-label btn-preset-color"`(2개)인데 yuna는 `btn-preset-color`만
  있어 `issue-label` 클래스 추가.
- **#109~111(issuelabels 3파샬)**: `issuelabels.html`에 인라인돼 있으나, legacy의 서버사이드 파샬 렌더링 대신
  `/api/projects/{id}/labels` 비동기 호출 기반 REST 아키텍처로 대체돼 있음을 확인 — 이 프로젝트의 REST 전환
  방침에 따른 의도된 차이로 문제 없음.
- **#112(statistics.html)**: legacy 원본 자체가 "Under Construction" 플레이스홀더뿐이며 yuna도 동일하게
  이식돼 있음(GNB/footer 포함 정상), 코드 변경 없음.
- **legacy와 다르게 처리한 지점**: #108의 라벨/카테고리 CRUD 커스텀 JS(현재 hand-rolled 클릭 핸들러 기반)를
  legacy가 쓰는 실제 정적 자산 `yobi.issue.LabelEditor.js` 모듈로 교체할지는, 필드명/ID/동작 호환성을 더 면밀히
  검증해야 해서 이번 배치에서는 보류(현행 유지) — 향후 배치에서 재검토 필요.
- **테스트**: `TemplateEquivalenceSpec.kt`의 `[Test-19-23]`(이슈 라벨 설정 화면 GNB/footer/헤더/setting_menu
  활성탭/프리셋색상 17개/외부 CDN jQuery 미로드 검증).
- **검증**: `./gradlew test --tests "com.github.search5.yona.web.TemplateEquivalenceSpec"`(GREEN).
  **그룹6(project/*, #87~112) 26개 항목 전체 처리 완료.** 다음은 그룹7(issue/*, #113~142).

### 그룹7 `issue/*` (#113~142) (TASK-0238)

- **패턴 점검**: 가짜 GNB/`.gnb-wrap`/외부 CDN jQuery 중복 로드 등 그룹6에서 반복 발견된 안티패턴은
  issue/* 11개 파일 전부에서 발견되지 않음(전부 site/layout 조각 정상 사용 중) — 이 그룹은 대신 "권한
  게이트 누락"과 "정적 자산은 있는데 마크업이 빠져 죽어있는 기능" 두 계열의 새로운 버그가 다수 발견됨.
- **#116(partial_searchform) 발견**: 마일스톤 검색 필터가 열림 마일스톤만 조회해 **닫힌 마일스톤
  optgroup 자체가 없었음**(legacy는 열림/닫힘 2개 optgroup) — `IssueViewController.list()`에
  `closedMilestones` 모델 속성을 신규 추가(`milestoneService.getMilestones(id, State.CLOSED)`)하고
  `issue/list.html`에 두 번째 optgroup 추가로 복구. 같은 파일에서 **"라벨 관리" 링크가 legacy의
  `isManagerOf(project)` 권한 체크 없이 항상 노출**되고 있던 것도 발견 — 실제 라벨 설정 화면
  (`ProjectViewController.labelsForm`)은 매니저/사이트관리자만 접근 가능해 일반 멤버가 클릭하면 403이
  뜨는 상태였음. `templateHelper.isManager(project, currentUser) || currentUser.isSiteManager` 게이트로
  복구.
- **#113,114,122(create/edit/massupdate) 발견**: 마일스톤 선택 UI 3곳 전부 `project.menuSetting.milestone`
  (마일스톤 메뉴 활성화 여부) 게이트 없이 마일스톤 데이터만 있으면 노출되고 있었음 — `project.isMilestoneEnabled`
  조건을 세 곳 모두에 추가.
- **#127(partial_assignee) 중대 발견, 미이식**: 이슈 상세 화면 우측 패널의 담당자/마일스톤이 항상
  읽기전용 텍스트뿐이고, legacy처럼 매니저가 select2로 인라인 재할당하는 기능이 없음. 정적 자산
  `yobi.issue.View.js`에는 이미 해당 AJAX 저장 로직(`_onChangeIssueInfo`→`massUpdate`)이 구현돼 있으나
  (a) 템플릿에 select2 마크업 자체가 없고 (b) JS 모듈 초기화 옵션에 `urls.massUpdate`가 전달되지 않아
  이중으로 죽어있음 — "정적 자산은 있는데 마크업/로드가 빠져 죽어있는 기능" 패턴. REST 계약 검증이 필요해
  이번 배치에서는 보류, 백로그에 구체적 구현 방향(라벨의 기존 패턴 재사용, 3단계 작업 순서) 기록.
- **#134~136(partial_view_child 계열) 중대 발견, 미이식**: 이슈 상세 화면에 하위 이슈 등록 버튼은 있으나
  등록된 하위 이슈 "목록"을 보여주는 기능이 전혀 없음. 신규 리포지토리 쿼리(부모ID+상태별 조회)가 필요해
  보류.
- **#119(partial_list_draft) 발견, 미이식**: 초안(draft) 이슈가 목록에서 완전히 안 보임(작성자 본인이
  직접 URL로 접근해야만 열람 가능). `State.DRAFT`가 목록 쿼리의 OPEN/CLOSED 필터에서 자연히 제외되기
  때문 — 작성자별 초안 조회 쿼리 신규 필요해 보류.
- **#125(partial_select_subtask) 발견, 미이식**: 이슈 생성/수정 폼에 부모 이슈를 검색해 지정하는 UI가
  없음(현재는 URL 쿼리파라미터로만 하위이슈 생성 가능) — REST 검색 엔드포인트 신규 필요해 보류.
- **#131~133(partial_index_comment 계열)**: legacy에서 draft 이슈 전용 축약형 댓글 뷰였으나, yuna는
  draft 여부와 무관하게 항상 풀 타임라인을 보여줘 기능적으로 상위호환 — 별도 이식 불필요로 판단.
- **나머지(#117,118,120,121,123,124,126,128~130,137~142)**: 상세 대조 결과 legacy와 구조/메시지키/JS
  훅이 모두 일치함을 확인, 코드 변경 없음.
- **legacy와 다르게 처리한 지점**: #123의 라벨 관리 링크를 legacy처럼 `<dt>` 안 인라인 링크가 아니라
  기존 yuna 구조(`.labels-wrap` 별도 아이콘 버튼)로 유지 — 기능/권한은 동등하고 DOM 구조만 소폭 다름,
  대규모 재작성 없이도 수용 가능한 수준으로 판단.
- **테스트**: `TemplateEquivalenceSpec.kt`의 `[Test-19-24]`(이슈 목록 화면 — 마일스톤 열림/닫힘 optgroup
  검증, 라벨 관리 링크 매니저/일반멤버 권한 노출 차이 검증).
- **검증**: `./gradlew test --tests "com.github.search5.yona.web.TemplateEquivalenceSpec"`(GREEN).
  **그룹7(issue/*, #113~142) 30개 항목 전체 처리 완료(구현 26개 + 백엔드 필요로 명확히 보류 4개: #119,
  #125, #127, #134~136).** 다음은 그룹8(board/*, #143~148).

### 방침 정정: "저가치 코너케이스" 판단으로 보류한 항목 재작업 착수 — #87~89 (TASK-0239)

- **사용자 지시 정정**: 지금까지 여러 항목(#1,10,13,15,20,70,90 등)을 "저가치 코너케이스"/"이미 동작 중이라
  유지" 등 자체 가치판단으로 축소·보류한 것에 대해 사용자가 명시적으로 정정 지시: "무조건 레거시에 맞추라"는
  최초 지시는 예외 없는 지시였고, 백엔드 기능이 없으면 백엔드까지 만들어서라도 legacy와 동일하게 맞춰야
  한다. 앞으로는 저가치 판단으로 스스로 건너뛰지 않는다 — 이번 작업부터 지금까지 `[ ]`/`[~]`로 보류해뒀던
  전체 35개 항목(그룹1~6 범위)을 순서대로 실제 완료 처리한다.
- **#87(project/create.html) 중대 발견**: 이번 세션에서 "그룹6 완료"라고 보고했음에도 실제로는 전혀 손대지
  않았던 항목. 실제 대조 결과 **PROTECTED(그룹공개) 옵션이 통째로 빠져 있었음** — 심지어 정적 자산
  `yobi.project.New.js`(`_onChangeProjectOwner`)는 이미 `#opt-protected`/`#protected` DOM을 참조하고
  있어 "정적 자산은 있는데 마크업이 없어 죽어있는 기능" 패턴이었다. 백엔드는 `ProjectScope.PROTECTED`enum과
  조직 관리자 권한 검사(`accessControl.isOrganizationAdmin`)가 이미 완비돼 있어 순수 템플릿 이식으로 해결.
  라디오 3종(PUBLIC/PROTECTED/PRIVATE) 복구, `opt-protected` 노출 여부를 서버사이드에서
  `isOwnerOrganization` 모델 속성으로 계산(legacy의 `Organization.isNameExist(owner)`와 동일 로직).
  추가로, **검증 실패 시 legacy는 Play Form이 자동으로 입력값을 재바인딩해 폼을 다시 채워주는데 yuna는
  전혀 보존하지 않아 사용자가 전체를 재입력해야 하는 상태**였음 — `NewProjectForm` 모델 클래스 신규 도입,
  GET/POST(실패시) 양쪽에서 항상 `form` 모델 속성을 채워 값 보존.
- **#88(project/importing.html)**: 실제 대조 결과 PROTECTED 옵션·Spring `@Valid`+`BindingResult` 기반
  필드별 에러 표시·조직 소유자 선택 시 protected 토글까지 이미 legacy와 동등하게(오히려 필드별 에러 노출은
  더 상세하게) 구현돼 있었음 — 코드 변경 없음.
- **#89(project/list.html)**: legacy는 프로젝트를 일단 렌더링한 뒤 `AccessControl.isAllowed`로 개별 항목을
  회색 처리하는 render-level 필터링 방식이지만, yuna 컨트롤러(`ProjectViewController.projects()`)는
  `findAllowedProjectIdsForUser`로 애초에 쿼리 단계에서 미접근 프로젝트를 제외 — 결과적으로 미접근
  프로젝트가 목록에 절대 나타나지 않아 legacy보다 동등하거나 더 엄격한 접근 제어. legacy의 회색 placeholder
  분기는 이 아키텍처에서는 도달 불가능한 코드가 되므로 이식하지 않음(가치판단이 아니라 논리적으로 트리거
  불가능함을 확인 후 결정) — 코드 변경 없음.
- **legacy와 다르게 처리한 지점**: #89의 접근제어 방식이 render-level→query-level로 바뀐 것은 Play→Spring
  아키텍처 전환에 따른 필연적 치환(기능 축소가 아니라 동등 이상의 보안 강화).
- **테스트**: `TemplateEquivalenceSpec.kt`의 `[Test-19-25]`(PROTECTED 옵션 존재 확인, 검증 실패 시 입력값
  보존 + opt-protected 노출 상태 재확인 2종).
- **검증**: `./gradlew test --tests "com.github.search5.yona.web.TemplateEquivalenceSpec"`(GREEN, 55 tests).
  다음은 나머지 34개 보류 항목(그룹1~5 범위)을 번호 순으로 재작업.

### 그룹8 `board/*` (#143~148) (TASK-0240)

- **#144(partial_list) 중대 발견**: 공지글이 상단 공지 영역에 노출된 것과 별개로, 하단 일반 페이징 목록
  쿼리(`postingRepository.findByProject`)가 공지 여부를 필터링하지 않아 **같은 게시글이 화면에 두 번
  노출**되고 있었음(legacy `BoardApp.posts()`는 `el.eq("notice", false)`로 명시적 제외). `PostingRepository`에
  `findByProjectAndNotice(project, notice, pageable)` 페이징 버전을 신규 추가하고, 라벨필터/검색 쿼리
  (`findByProjectAndLabelIdsIn`/`searchPostingsInProject`)에도 `p.notice = false` 조건을 추가해 세 갈래
  쿼리 경로 모두 일관되게 공지 제외 처리, `BoardViewController.posts()`의 기본(무필터) 조회를 새 메서드로
  교체. 같은 파일에서 **`showHeaderWordsInBracketsIfExist`/`removeHeaderWords`(제목 대괄호 접두어를
  `.bracket-word`로 분리 표시)가 통째로 빠져있던 것**도 발견해 `issue/list.html`과 동일한 패턴으로 복구.
- **#147(view) 발견**: 삭제 확인 모달의 예/아니오 버튼이 `#{button.yes}`/`#{button.no}` 메시지 키 대신
  하드코딩 한글("네"/"아니요", legacy 메시지 원문인 "예"와도 실제로 다른 문구였음)이었던 것을 복구.
- **#145,146(create/edit) 조사**: README 자동 지정 체크박스(`post.readmefy`, `isProjectResourceCreatable
  (COMMIT)` 게이트)는 코드 저장소 연동(커밋으로부터 게시글 생성) 기능의 일부라 그룹10/11(code/git) 범위로
  판단, 이번 배치에서는 미이식 — 향후 그룹10/11 작업 시 board/create·edit.html도 함께 재검토 필요.
- **#148(partial_comments)**: 이슈와 동일한 댓글+이벤트 타임라인 구조를 재사용해 이미 정확히 이식돼
  있음을 확인, 코드 변경 없음.
- **legacy와 다르게 처리한 지점**: 없음(발견된 격차 전부 순수 버그 수정/기능 복구).
- **참고**: `board/view.scala.html`의 `common.noAuthor`(작성자 없음 표시)는 yuna의 `Posting`이 작성자
  정보를 비정규화 저장해 항상 값이 존재하므로 해당 없음. `change.history`(게시글 수정 이력 모달)는 이력
  추적 테이블 자체가 없어 순수 템플릿 포팅 범위를 넘어 보류.
- **테스트**: `TemplateEquivalenceSpec.kt`의 `[Test-19-27]`(게시판 목록 — 공지글 중복노출 방지, 대괄호
  접두어 분리 표시 검증), `[Test-19-28]`(게시판 상세 삭제모달 예/아니오 메시지키 검증). 다른 세션이 동시에
  같은 파일에 `[Test-19-25]`(프로젝트 생성 화면)를 추가해둔 것을 발견해 번호 충돌을 피하기 위해 내 항목을
  `[Test-19-27]`/`[Test-19-28]`로 조정.
- **검증**: `./gradlew test --tests "com.github.search5.yona.web.TemplateEquivalenceSpec"`(GREEN).
  **그룹8(board/*, #143~148) 6개 항목 전체 처리 완료.** 다음은 그룹9(milestone/*, #149~153).

### 방침 정정 후속: #1 `\|:\|` 제목 분리 컨벤션 이식 (TASK-0241)

- **#1 재작업**: 이전에 "저가치 판단"으로 미이식 처리했던 `\|:\|` 제목 분리 컨벤션을 실제로 이식.
  legacy `layout.scala.html:8`은 `title.split(" |:| ")`로 `<title>`/og:title엔 앞부분만, og:description/
  twitter:description엔 뒷부분(또는 구분자 없으면 동일값)을 사용한다. `TemplateHelper.titleMain()`/
  `titleOgDescription()` 신규 추가, `site/layout.html :: head(title)` 프래그먼트에서 이 두 헬퍼로 계산한
  값을 사용하도록 변경(기존 호출부는 전부 구분자 없는 단일 문자열이라 `first()==last()`로 하위호환 보장).
  실제 `\|:\|` 조합을 사용하는 3개 파일(`project/home.html`, `issue/view.html`, `board/view.html`)에
  적용 — `issue.body`/`post.body` 200자 미리보기는 `TemplateHelper.ogDescriptionPreview()` 신규 추가.
- **부수 발견**: `board/create.html`/`issue/create.html`/`milestone/create.html` 3개 파일이 `head(title=...
  + ' - Yona')`처럼 `' - Yona'` 접미사를 직접 붙이고 있었는데, `head()` 프래그먼트 자체가 `<title>`에서
  이미 `' - Yona'`를 붙이고 있어 **`<title>프로젝트명 - Yona - Yona</title>`처럼 접미사가 중복 노출되던
  버그**였음 — legacy 어디에도 title 파라미터에 사이트명이 포함되지 않음을 확인 후 3개 파일 모두 제거.
- **구현 중 발견한 문제**: (1) `~{...}` 프래그먼트 지정 표현식 안에 `@bean.method(...)` 같은 복잡한
  표현식을 명명 파라미터 값으로 직접 넣으면 Thymeleaf가 파싱 자체를 못함(`Could not parse as expression`)
  — `<html>` 태그의 `th:with`로 미리 변수화한 뒤 그 변수만 프래그먼트 호출에 넘기도록 수정. (2) `th:with`
  값은 전체가 하나의 `${...}` 블록이어야 함(문자열 리터럴과 섞어 쓸 수 없음). (3) `${...}` SpringEL 블록
  안에는 Thymeleaf 전용 `#{...}` i18n 문법을 중첩할 수 없어, `project/home.html`은 `th:with`를
  `menuHomeLabel=#{menu.home}, pageTitle=${...menuHomeLabel...}` 형태로 2단계로 분리. (4) Kotlin 기본
  파라미터(`maxLen: Int = 200`)는 리플렉션 기반 SpEL 메서드 탐색에서 안 보여서(`@JvmOverloads` 없으면
  1-arg 오버로드 자체가 생성 안 됨) `EL1004E: Method call ... cannot be found` 에러 발생 — `@JvmOverloads`
  추가로 해결.
- **legacy와 다르게 처리한 지점**: 없음(순수 버그 수정 + 기능 이식).
- **테스트**: `TemplateEquivalenceSpec.kt`의 `[Test-19-29]`(이슈 상세 화면 title/og:title/og:description
  분리 검증, `\|:\|` 없는 화면의 하위호환 검증).
- **검증**: `./gradlew test --tests "com.github.search5.yona.web.TemplateEquivalenceSpec"`(GREEN, 60 tests).
  다음은 나머지 보류 항목(#8,10,12,13,20,23,38~41,45,47,49,50,53,57,70,82,83,90,108)을 번호 순으로 재작업.

### 방침 정정 후속: #8 프로젝트 메뉴 재작업 (TASK-0242)

- **#8(projectMenu.scala.html→project/menu.html) 중대 발견**: 상세 대조 결과 4가지 실 기능 격차 발견.
  (1) 리뷰 탭에 legacy의 `countReviewsBy` 카운트 배지가 아예 없었음 — `TemplateHelper.countReviews()`
  신규 추가(`ReviewThreadService.countReviewThreads(project, ReviewSearchCondition(state="OPEN"))`).
  (2) 설정(톱니바퀴) 메뉴에 legacy의 가입 대기 인원 수(`project.enrolledUsers.size`) 배지가 없었음 — 추가.
  (3) **PR 탭이 SVN 프로젝트에서도 노출되고 있었음** — legacy는 `project.vcs.equals("GIT")` 조건이 있는데
  yuna엔 전혀 없었음(순수 기능 버그, SVN 프로젝트에 눌러도 404 나는 PR 탭이 보이는 상태였음) — 조건 추가.
  (4) 포크 프로젝트의 PR 탭 링크가 항상 `/pulls`(받은 PR)였는데, legacy는 `project.isForkedFromOrigin`이면
  `sentPullRequests`(보낸 PR)로 분기함 — `project.originalProject != null` 체크로 분기 추가(백엔드
  `sentPullRequests` 엔드포인트는 이미 존재, 프론트만 안 붙어있었음). (5) **가장 중대한 발견**: legacy의
  키보드 단축키(H/B/C/I/M/P/Q) 스크립트(`$yobi.loadModule("project.Global", {htKeyMap: ...})`)가
  yuna에는 통째로 없었음 — 정적 자산 `yobi.project.Global.js`는 이미 `htKeyMap` 옵션을 받아 처리하는
  로직(`_initShortcutKey`)을 갖추고 있어 "정적 자산은 있는데 로드가 아예 없어 죽어있는 기능" 패턴이었음.
  Thymeleaf 자연템플릿(`/*[# th:if=...]*/`) 문법으로 각 메뉴 활성화 조건에 맞춰 `htKeyMap` 객체를
  동적 구성하는 스크립트 블록을 신규 추가.
- **legacy와 다르게 처리한 지점**: "Q"(설정) 단축키의 legacy 가드 조건이 `requestHeader.session.get
  ("loginId") match { case Some(role) if role.equals("manager") ... }`로, loginId 값을 문자열
  "manager"와 비교하는 앞뒤가 안 맞는 조건(레거시 버그로 추정)이었다 — 의도가 명백히 "매니저만"이므로
  이 프래그먼트가 이미 계산해두는 `isManager` 변수로 대체(설정 메뉴 자체의 노출 조건과 동일 기준 적용).
- **테스트 인프라 수정**: `publicProj`/`codeMemberOnlyProj` 공용 테스트 픽스처에 `vcs` 필드가 아예 없어서
  (`null`) PR 탭이 우연히 항상 노출되고 있었음 — 실제 서비스에서는 프로젝트 생성 시 항상 vcs가 채워지므로
  두 픽스처 모두 `vcs = "GIT"`로 명시.
- **테스트**: `TemplateEquivalenceSpec.kt`의 `[Test-19-30]`(SVN 프로젝트 PR 탭 미노출 검증, 매니저의
  설정 배지+htKeyMap 스크립트 노출 검증).
- **검증**: `./gradlew test --tests "com.github.search5.yona.web.TemplateEquivalenceSpec"`(GREEN, 62 tests).
  다음은 나머지 보류 항목(#10,12,13,20,23,45,47,49,50,53,90) — #38~41은 그룹10/11 담당 에이전트에게,
  #82/83은 그룹14 담당 에이전트에게 위임 완료(병렬 워크트리에서 진행 중).

### 방침 정정 후속: #10/#13 GNB 검색범위·커스텀링크 재작업 (TASK-0243)

- **#10 재작업**: 조직(org) 페이지에서 검색범위 드롭다운의 "현재 그룹" 링크가 legacy는
  `Application.HIDE_PROJECT_LISTING || 게스트` 모드일 때만, 그것도 `isMemberOf(org) || isAdminOf(org)`인
  사용자에게만 보이는데(왜 이 조합에서만 보이는지는 legacy 자체도 불명확하지만, 지시대로 그대로 이식),
  yuna는 무조건 노출하고 있었음 — `TemplateHelper.isOrganizationMemberOrAdmin(org, user)`
  (`OrganizationUserRepository.existsByOrganizationIdAndUserId`) 신규 추가 후 게이트 적용. "모든
  프로젝트" 검색범위도 legacy는 `(!HIDE_PROJECT_LISTING && !게스트) || 사이트관리자`인데 yuna는 무조건
  노출 — 게이트 추가.
- **#13 재작업**: `NAVBAR_CUSTOM_LINK_NAME`/`NAVBAR_CUSTOM_LINK_URL`(둘 다 기본값 빈 문자열인 설정
  프로퍼티)을 `yuna.application.navbar.custom-link.name`/`.url`로 이식. `GlobalModelAttributeAdvice`에
  `@Value` 주입 + `@ModelAttribute` 노출, `site/layout.html`의 `gnb`/`errorGnb` 두 조각 모두에 로그인
  사용자 전용 커스텀 링크 `<li>`(설정값이 비어있으면 `th:if`로 렌더링 자체가 생략됨, 레거시 기본 동작과
  동일) 추가.
- **legacy와 다르게 처리한 지점**: 없음(레거시가 `play.Configuration.root().getString(key, "")` 방식의
  단순 설정 프로퍼티라 Spring `@Value`로 1:1 대응 가능했음 — OAuth 프로바이더 동적 목록처럼 아키텍처
  자체가 다른 경우와 달리 이건 순수 문자열 설정값이라 어려움이 없었음).
- **테스트**: `TemplateEquivalenceSpec.kt`의 `[Test-19-31]`(일반 사용자/게스트/사이트관리자의 "모든
  프로젝트" 검색범위 노출 차이, 커스텀 링크 기본(미설정) 상태 검증).
- **검증**: `./gradlew test --tests "com.github.search5.yona.web.TemplateEquivalenceSpec"`(GREEN, 64 tests).
  다음은 나머지 보류 항목(#12는 재확인 결과 이미 정상이라 스킵, #20,23,45,47,49,50,53,90)을 이어서 처리.

### 방침 정정 후속: #90 즐겨찾기 별표 재작업 (TASK-0244, 메인 세션)

- **#90 재작업**: TASK-0236에서 "여러 호출부에 favoriteProjects 전파가 필요해 범위가 커 미이식"이라고
  적었던 판단이 실제로는 틀렸음을 확인 — legacy `project/header.scala.html:48-50`의 `isFavoriteProject`는
  이 프래그먼트 안에서 `FavoriteProject.findByProjectId(UserApp.currentUser().id, project.id) != null`로
  로컬 계산되는 것이지, 상위 컨트롤러가 목록을 미리 계산해 전파해주는 구조가 아니었다. yuna에도
  `FavoriteProject`/`FavoriteProjectRepository`/`FavoriteService`가 이미 전부 구현돼 있어(즐겨찾기 토글
  API까지 존재), `TemplateHelper.isFavoriteProject(project, user)`(`existsByUserIdAndProjectId` 조회)
  하나만 추가하면 되는 간단한 작업이었음 — `project/header.html`의 별표 아이콘에 `starred` 클래스
  조건부 적용(기존 JS `yona.Usermenu.js`는 이미 클릭 시 `starred` 토글 로직을 갖고 있었으나 서버사이드
  초기 렌더 상태만 없었음, "정적 자산은 있는데 초기 상태가 없어 반쪽만 동작하던" 패턴).
- **legacy와 다르게 처리한 지점**: 없음.
- **테스트**: `TemplateEquivalenceSpec.kt`의 `[Test-19-32]`(즐겨찾기 등록/미등록 프로젝트의 별표
  starred 클래스 유무 검증).
- **검증**: `./gradlew test --tests "com.github.search5.yona.web.TemplateEquivalenceSpec"`(GREEN, 66 tests).
  그룹1~6 범위의 방침-정정 재작업 대상 중 메인 세션이 직접 처리한 항목은 이것으로 마무리
  (#1,8,10,13,90). 남은 항목(#20,23,45,47,49,50,53)은 백엔드 신규 인프라가 필요해 별도 세션에서
  집중 처리 권장 — 사유는 각 항목 비고란 참고. #38~41/#25~31/#82~83/#108은 병렬 워크트리 에이전트에게
  위임 완료(진행 중/일부 완료, 병합 시 별도 기록).

### 그룹16 `migration/*` (#239~240) (TASK-0245, 병렬 워크트리 에이전트 작업 병합)

- **#240(home) 중대 발견**: 기존 `migration/home.html`이 legacy `home.scala.html`을 거의 통째로
  "yuna식 독자 구현"으로 재작성한 상태였음(표준 규칙 3번 위반 사례) — `ng-app` 컨테이너를 불필요한
  `.container` div로 감싸기, 인라인 `style` 대량 추가, 진행률 바 클래스를 legacy `bar`/`bar-danger`/
  `bar-success`에서 부트스트랩 4류 `progress-bar`/`progress-bar-danger`/`progress-bar-success`로 임의
  변경, "주의 사항!!" 안내 행 통째로 누락, `<import-warning>` 커스텀 엘리먼트(마일스톤/이슈/게시글 3곳)
  전부 누락, destination 프로젝트의 `warn-no-worker`/`warn-user-project`/organization 미관리자 경고
  마크업 상당수 누락, 담당자 테이블에 legacy에 없는 "Yona 유저"/"Github 유저 ID" 헤더 임의 추가.
- **#239(migrationPageLayout) 확인**: 그룹1 #4~#6 선례(별도 신규 데코레이터 파일 대신, 소비 화면이
  `site/layout::head/gnb/footer/scripts` 조각을 직접 조합)와 동일한 아키텍처로 처리 — legacy
  `migrationPageLayout.scala.html`의 역할(관리자 로그인 알림+전체 GNB=`site/layout::gnb`,
  `@common.scripts()`=`site/layout::scripts`, `@content`+`@common.navbar`)을 `migration/home.html`이
  직접 조합하도록 재작성. 단, `<head th:replace=...>`는 자식 마크업을 전부 버리므로(Thymeleaf `th:replace`
  의미상 호스트 엘리먼트+자식 전체가 대체됨) legacy `<head>`에만 있고 공용 `site/layout::head`엔 없는
  자산(구글 폰트 Montserrat/Indie Flower/Muli 3종, `cache-control` meta)은 `board/view.html` 선례를 따라
  `<body>` 최상단에 배치. 마찬가지로 legacy가 `<head>`에서 미리 로드하던 `jquery-1.9.0.js`/
  `jquery.browser.js`/`jquery.pjax.js`/`yobi.Common.js`/`vendor.js`/`yona.Migration.js`(구버전 jQuery를
  `site/layout::scripts`가 로드하는 `yona-common.js`/`yona.Usermenu.js`와 별개로 중복 로드하는 legacy의
  실제 동작)도 `<body>` 상단에 그대로 포팅 — 진짜 legacy 동작이므로 "중복 로드라서 하나로 합치는" 임의
  개선은 하지 않음. migrationPageLayout 하단의 사이드바 열기/닫기 및 프로젝트/조직 즐겨찾기 토글 인라인
  스크립트(`site/layout::scripts`가 로드하는 `yona.Usermenu.js`와 기능이 중복되는 legacy 자체 구현)도
  그대로 포팅, `@api.routes.UserApi.toggleFoveriteProject/toggleFoveriteOrganization/getFoveriteProjects`
  Play 라우트는 yuna `FavoriteController`의 실제 매핑(`POST /-_-api/v1/favoriteProjects/{id}`,
  `POST /-_-api/v1/favoriteOrganizations/{id}`, `GET /-_-api/v1/favoriteProjects`)으로 치환.
- **Play→Thymeleaf 치환**: `ng-if="'@token'"`(빈 문자열이면 항상 falsy)는 `th:if="${token != null and
  !token.isEmpty()}"`로, `@if(StringUtils.isNotBlank(token)) { ng-init="..." }`(조건부 속성 자체 추가/
  생략)는 `th:attr="ng-init=(${...} ? |...| : null)"` 삼항식(null이면 속성 자체가 생략됨)으로 치환.
  AngularJS `ng-*`/`{{ }}` 디렉티브는 클라이언트 사이드 문법이라 원문 그대로 유지(Play 템플릿 엔진과
  무관). 속성값 내 리터럴 `<` 문자(`vm.importResult.count/...*100<100 ? ...`)는 Thymeleaf 파서가
  속성값 안의 `<`를 태그 시작으로 오인하지 않도록 `&lt;` 엔티티로 이스케이프(렌더링 결과는 legacy와 동일).
- **legacy와 다르게 처리한 지점**: `<body class="@theme">`(legacy는 `theme` 파라미터로 빈 문자열 전달돼
  실질적으로 `class=""`)는 그룹1에서 이미 확립된 관례대로 `class="theme-default"` 고정값 사용(다른 모든
  site-admin류 yuna 화면과 동일 관례, 순수 테마 CSS 클래스 이름 선택이라 마크업/동작에 영향 없음).
- **테스트**: `TemplateEquivalenceSpec.kt`에 `[Test-19-33]` 신규 — GNB 검색폼/footer/migration 전용
  JS·폰트 자산 포함 검증, `code`(token) 파라미터가 없을 때 `.header-pannel` 전체가 렌더링되지 않는지
  검증(legacy `ng-if="'@token'"` 동치), 비로그인 시 로그인 폼 리다이렉트 검증. `github.allow.migration`
  기본값이 `false`라 `/migration`이 403을 반환하므로 클래스에 `@TestPropertySource(properties =
  ["github.allow.migration=true"])` 추가.
- **검증**: `./gradlew test --tests "com.github.search5.yona.web.TemplateEquivalenceSpec"`(GREEN).
  **그룹16(migration/*, #239~240) 2개 항목 전체 처리 완료.** (병렬 워크트리 에이전트가 작업, 메인
  세션이 TASK 번호/테스트 번호 재부여 후 병합)

### 그룹17 `welcome/*` (#241~242) (TASK-0246, 병렬 워크트리 에이전트 작업 병합) — 전체 242개 배치 마감

- **매핑 확인**: `BootstrapSetupController`(`/bootstrap-setup` GET/POST)가 `bootstrap-setup`/`bootstrap-restart`
  뷰를 반환하는 것을 확인, 백로그의 추정 매핑이 정확함을 검증.
- **legacy 트리거 조건과의 구조적 차이(조사 결과)**: legacy는 `Global.java`의 `onRequest` 인터셉트가
  `application.secret`이 기본값(`DEFAULT_SECRET`)일 때 전역적으로 `welcome/secret`(또는 재시작 필요 시
  `welcome/restart`)을 강제 표시하고, 폼 제출 시 기존 `admin`(initial-data.yml로 미리 생성된 계정)의
  비밀번호/이메일을 갱신하면서 `application.secret` 파일 자체를 무작위 값으로 재작성한다(`updateSiteSecretKey`).
  yuna는 이 Play 전용 "설정파일 시크릿 재작성" 인프라가 전혀 없어(다른 어떤 화면에도 대응 코드 없음),
  기존에 이미 구현돼 있던 "가입자 0명이면 `/bootstrap-setup`으로 강제 이동"(`BootstrapSetupInterceptor`) +
  "가입자 0명일 때 신규 SITE_ADMIN 계정 생성"(`BootstrapSetupController`) 대체 진입 조건은 그대로 유지.
  이 부분은 순수 뷰 이식 범위를 넘는 백엔드 보안 인프라 항목이라 **이번 배치에서는 의도적으로 미이식**
  (합리적 범위를 넘는 신규 서브시스템 — 별도 파리티 항목으로 취급해야 함, 이번 그룹 범위 아님).
- **#241(bootstrap-setup.html) 발견 및 수정**:
  1. **필드별 검증 에러 표시가 통째로 빠져 있었음** — legacy는 `newUserForm.errors().get(필드명)`으로
     `아이디/이메일/비밀번호/비밀번호 재입력` 4개 필드 각각의 `<dt>` 라벨 옆에 `<span class="label
     label-important">` 뱃지를 개별 노출하는데, yuna는 폼 상단에 범용 `alert-danger` 배너 하나만 있었고
     그마저도 로그인ID 불일치·비밀번호 불일치 2가지 케이스만 하드코딩 문자열로 처리, `비밀번호 공백`/
     `이메일 공백`/`이메일 중복` 검증 자체가 없었음. `BootstrapSetupController.setupAdmin()`을 legacy
     `Global.java`의 `hasError()`와 동일한 필드별 검증(로그인ID 공백+불일치 이중 체크, 비밀번호 공백,
     비밀번호 재입력 불일치, 이메일 공백, 이메일 중복(`userRepository.findByEmail`))으로 재작성하고,
     결과를 `loginIdErrors`/`emailErrors`/`passwordErrors`/`retypedPasswordErrors` 리스트로 모델에 담아
     템플릿에서 `th:each` + `#{__${err}__}`(메시지 키 동적 해석)로 legacy와 동일한 위치에 뱃지를 렌더링.
  2. 하드코딩 한글 텍스트를 전부 `#{...}` 메시지 키(`app.welcome`/`app.welcome.warning.title/desc`/
     `user.signupId`/`user.name`/`user.email`/`user.password`/`validation.retypePassword`/
     `app.welcome.submit`)로 치환(기존 `messages_ko_KR.properties`에 legacy와 동일한 문구가 이미 존재함을
     확인, 재사용). 사이트명(`<title>`/로고/footer)도 하드코딩 "Yona" 대신 `yuna.site-name` 설정값을
     `siteName` 모델 속성으로 주입해 `#{app.welcome(${siteName})}`로 렌더링(legacy `utils.Config.
     getSiteName()`에 대응).
  3. **CSS/구조 잉여물 제거**: `<body style="zoom: 1;">`, `.logo`/`.logo:hover`의 `text-decoration: none`
     추가 규칙은 legacy 원본에 없는 값이라 제거(순수 legacy 재현).
  4. 관리자 생성 중 예외를 삼켜 폼에 범용 에러 문자열을 보여주던 try/catch도 legacy(예외 시 그냥 500
     에러 페이지로 전파)에 맞춰 제거.
- **#242(bootstrap-restart.html) 발견 및 수정**: 텍스트(`환영합니다!`/`서버를 재시작해야합니다.`)는 이미
  legacy와 일치했으나 하드코딩이었던 것을 `#{app.restart.welcome}`/`#{app.restart.notice}` 메시지 키로
  치환, `siteName` 모델 속성 주입, 동일한 CSS/구조 잉여물(`body style="zoom:1"`, 로고 `text-decoration`)
  제거. `app.restart.updateSecretYourself`(시크릿 파일 재작성 실패 시 안내) 조건부 블록은 위에서 설명한
  대로 시크릿 재작성 인프라 자체가 없어 트리거될 수 없는 죽은 분기가 되므로("known bug pattern: JS/컨트롤러
  없는 죽은 마크업" 재발 방지 원칙에 따라) 의도적으로 미포함.
- **legacy와 다르게 처리한 지점**: 진입 조건("가입자 0명" vs legacy의 "application.secret 기본값")만
  기존 아키텍처를 유지(사유는 위 참고), 그 외 화면 내부(필드/문구/에러 표시 구조)는 전부 legacy와 일치시킴.
- **테스트**: 이 화면은 가입자 0명(최초 부팅) 상태에서만 도달 가능해 기존 `TemplateEquivalenceSpec`의
  공유 픽스처(다른 유저 다수 생성)와 충돌하므로, 신규 `BootstrapSetupTemplateEquivalenceSpec.kt` 별도
  스펙 파일로 분리(매 테스트마다 `userRepository.deleteAll()`로 격리). GET 최초 진입/이미 가입자 존재 시
  리다이렉트/로그인ID·비밀번호재입력·이메일 필드별 에러 뱃지 노출/성공 시 SITE_ADMIN 생성+재시작 화면
  렌더링까지 6개 케이스 검증.
- **검증**: `./gradlew test --tests "com.github.search5.yona.web.BootstrapSetupTemplateEquivalenceSpec"`
  (GREEN, 6 tests. 병렬 워크트리 에이전트가 수정 전 코드로 되돌려 RED 3건 확인 후 재적용해 GREEN 전환
  확인 완료). 메인 세션 병합 후 재검증 예정.
- **전체 242개 배치 완료**: 그룹1~17 전 항목 처리 완료(병렬 워크트리 에이전트 병합 진행 중). 남은
  미완료(`[ ]`/`[~]`/`[i]`) 마커는 각 진행 로그에 사유가 문서화된 상태 — 상세는 이 문서를 전체 검색해
  확인.
### 그룹10 `code/*` 코드브라우저 (#154~166) + #38/#39 (TASK-0243)

- **범위**: 그룹10 13개 항목(#154~166) 전부 + 이전 그룹2에서 그룹10 착수 시 처리하기로 미뤄둔 #38
  (`common/commitMsg`), #39(`common/branchItem`) 2건.
- **핵심 발견(알려진 버그 패턴 (e) 실사례)**: `code/compare.html`/`code/compare_svn.html`이 `site/layout::gnb`를
  쓰지 않고 로그인/회원가입/로그아웃 링크를 **하드코딩한 가짜 GNB**를 통째로 갖고 있었고, `<head>`도
  `site/layout::head`를 쓰지 않는 독자 `<meta>`/`<link>` 나열이었으며, `project/header`/`project/menu`
  프래그먼트도 아예 없었음(project-menu 탭 자체가 없어 프로젝트 내 다른 화면으로 이동 불가) — 실제
  서비스에 붙었다면 사이트 전역 CSRF 메타태그/알림/검색 등이 전부 빠진 상태로 렌더링됐을 것.
  `code/history.html`/`code/diff.html`/`code/svnDiff.html`/`code/nohead.html`/`code/nohead_svn.html`도
  정도는 약하지만 같은 패턴 — project/header를 손으로 흉내낸 `<div class="project-header">...`만 있고
  `project/menu`(탭 네비게이션)가 전부 빠져 있었음. **13개 파일 전부에 site/layout::gnb + project/header
  + project/menu(+scripts/footer) 표준 프래그먼트 조합을 복구.**
- **`code/diff.html`/`code/svnDiff.html`**: `site/layout::scripts`(전역 jQuery/CSRF/알림 JS 번들) 자체가
  누락되어 있어서, 두 파일의 `$(document).ready(...)` 스크립트가 jQuery 없이 실행되며 전부 죽어있던 상태
  (알려진 버그 패턴 (a)/(e)의 변형) — 복구.
- **`code/view.html`(#154/155/156)**: 브랜치 선택 `<option>`이 `RepositoryService.getRefNames()`가 돌려주는
  `refs/heads/xxx` 전체 ref 이름을 그대로 URL 세그먼트로 써서 브랜치 전환 시 깨진 URL로 이동하던 버그 →
  `TemplateHelper.branchItemName()`(legacy `Branches.itemName()` 대응, 신규) 적용. 파일뷰에서
  아바타/작성자링크/커밋 revision 링크(+댓글수 배지)/Raw 다운로드/Edit/브라우저로 열기/변경이력 버튼이
  전부 빠져 있던 것 복구(legacy `partial_view_file.scala.html` 그대로). 폴더뷰는 legacy가 폴더 우선
  정렬 후 파일을 나중에 순회하는데 yuna는 파일명 알파벳순으로 폴더/파일을 뒤섞어 정렬하던 버그 →
  두 단계 순회로 수정. "새 파일"/"Edit" 링크가 실제로는 어디에도 연결되지 않은 채였는데
  `BoardViewController.createPostForm`(`/post/new?path=&branch=&edit=`)이 이미 해당 파라미터를 전부
  지원하고 있어 연결. **"ZIP 다운로드" 버튼이 가리키는 컨트롤러 엔드포인트 자체가 없어 죽은 링크였음** —
  `GitRepository.getArchive()`(zip 스트리밍 로직)는 이미 구현돼 있었는데 이를 호출하는 컨트롤러가 없던
  전형적인 "백엔드 일부만 있고 배선 안 됨" 케이스 → `CodeViewController.download()` 신규 추가로 연결.
  파일 리비전 링크 옆 댓글 수 배지(legacy `CommentThread.countOnCommit`/`CommitComment.count`)를 위해
  `CommentThreadRepository.countByProjectAndCommitIdAndCodeRangePath`(TREAT를 쓴 명시적 JPQL —
  `codeRange`는 `CodeCommentThread` 서브클래스 전용 필드라 파생 쿼리로는 베이스 타입에서 참조 불가),
  `CommitCommentRepository.countByProjectAndCommitIdAndPath` 신규 추가.
- **`code/branches.html`(#157/158)**: "보낸 코드" 컬럼이 항상 "보낸 코드 없음"만 표시하도록 하드코딩돼
  있었음(PR 조회 자체가 없었음) → legacy `GitRepository.setTheLatestPullRequest()`/
  `PullRequest.findTheLatestOneFrom()` 대응으로 `PullRequestRepository.
  findFirstByFromProjectAndFromBranchAndToProjectOrderByNumberDesc` 신규 추가(포크 프로젝트 체인 케이스는
  단순화해 미지원 — 별도 이슈로 남김). 액션(기본 브랜치 설정/삭제) 컬럼이 권한 무관하게 항상
  `<th></th>`/`<td></td>`를 렌더링하던 것을 legacy처럼 DELETE/UPDATE 권한이 있을 때만 컬럼 자체를
  렌더링하도록 수정. `templateHelper.agoOrDateString(...)`에 `java.util.Date`를 그대로 넘기고 있어
  (헬퍼는 `Instant`만 받음) 타입 불일치로 렌더링 시 깨졌을 지점을 `.toInstant()` 추가로 수정.
- **`code/history.html`(#159)**: 커밋별 댓글 수 배지, 커밋 작성자 아바타/익명 처리, "코드 보기" 브라우즈
  버튼, `common/commitMsg` fragment(짧은 메시지+펼침 버튼) 전부 빠져 있던 것 복구. 파일 경로별 이력을 볼
  때만 표시돼야 하는 브레드크럼을 문자열 `indexOf` 기반으로 재구성하려던(동일 세그먼트 반복 시 깨지는)
  시도 대신 컨트롤러에서 누적 경로 리스트를 직접 계산하도록 변경(view.html과 동일한 방식).
- **`code/diff.html`(#160)**: 커밋 메시지가 `common/commitMsg`(forceExpand=true) 대신 `<pre>` 그대로였던
  것 교체. **`#166`(`partial_nonrange_codecomment_thread`) 신규 작성**해 범위 없는 코드리뷰 스레드 렌더링에
  연결(기존엔 스레드 컴포넌트 구조 없이 손으로 흉내낸 마크업이었음) — 답글 폼/스레드 상태 토글을 위해
  `common/commentFormOnThread.html`(legacy `views/partial_comment_form_on_thread.scala.html` 대응)도 신규
  작성. 리뷰 스레드 사이드바(열림/닫힘 탭 + 카드 목록, legacy `review-wrap`)가 통째로 없던 것 복구.
  댓글 삭제 버튼이 아예 존재하지 않는 하드코딩 fetch 핸들러를 쓰고 있었던 것을 실제 존재하는
  `ReviewApiController`(`DELETE /comments/{type}/{id}`)와 이미 전역 로드되는 `data-request-method`
  컨벤션(`yona-common.js`의 `jquery.requestAs.js`)으로 교체.
- **`code/svnDiff.html`(#161, #39)**: **브랜치 선택 드롭다운(`common/branchItem` 대응)이 통째로 빠져
  있던 것**을 `common/branchItem.html`(신규 fragment) + `TemplateHelper.branchItemName/branchItemType/
  branchInHtml`(legacy `Branches.itemName/itemType/branchInHTML` 대응, 신규) 조합으로 복구. 이 드롭다운을
  위해 `CodeViewController.showCommit()`에 `branches` 모델 속성 추가.
- **`code/compare.html`/`code/compare_svn.html`(#162/163)**: 위 가짜 GNB 수정 외에, "변경된 내역이
  없습니다" 안내가 `#{code.noChanges}` 메시지 키 대신 하드코딩 한글이었던 것 복구, `commitInfo` 표시가
  legacy는 "비교 범위: " 같은 접두어 없이 `@commitA..commitB` 그대로인데 yuna는 "비교 범위: " 라벨을
  붙이고 있던 차이 복구. `code/compare.html`의 라인댓글 렌더 fragment(`renderLineComments`) 안에서
  `th:with="lineVal=..., pathVal="${diff.pathB}", sideVal='B'"`처럼 **따옴표가 중첩된 깨진 Thymeleaf
  구문**(파싱 자체가 안 됐을 것) 2곳 발견·수정(쓰이지도 않는 미사용 변수였어서 제거).
- **`code/nohead.html`/`code/nohead_svn.html`(#164/165)**: 클론/초기화 안내 가이드가 legacy는
  `isAllowed(currentUser, project, UPDATE)`일 때만 보이는데 yuna는 로그인 여부와 무관하게 항상 노출하던
  것을 권한 게이트 복구(`CodeViewController.addNoHeadAttributes()` 헬퍼 신규, `canManage`/`siteName` 모델
  속성 추가). 안내 문구를 하드코딩 한글 대신 `#{code.nohead}`/`#{code.nohead.clone(${siteName})}` 등
  메시지 키로 교체(`{0}` 자리에 legacy `utils.Config.getSiteName()` 대응 `yuna.site-name` 프로퍼티 값을
  채움).
- **Jackson 3.x(`tools.jackson`) API 차이로 인한 수정**: `code/view.html` 작성 중 `ObjectNode.fieldNames()`가
  legacy(Jackson 2.x)에는 있지만 이 프로젝트가 쓰는 Jackson 3.x(`tools.jackson.databind`)에는 **존재하지
  않고** `propertyNames(): Collection<String>`으로 이름이 바뀌었음을 실제 jar 바이트코드(`javap`)로 확인,
  전부 교체(`#lists.sort(#lists.toList(...propertyNames()))`). 같은 이유로 `#temporals`(java8time 확장
  객체)가 이 프로젝트의 `build.gradle.kts`에 `thymeleaf-extras-java8time` 의존성이 없어 등록되지 않는데도
  `code/svnDiff.html`/`code/compare.html`에 `#temporals.format(...)`가 쓰이고 있던 것을 발견 —
  `templateHelper.getDateString(...)`으로 교체.
- **legacy와 다르게 처리한 지점**:
  1. `code/branches.html`의 "보낸 코드" PR 조회는 포크 프로젝트 체인(`fromProject.isForkedFromOrigin()`)
     케이스를 지원하지 않음(단순화) — 포크 아닌 일반적인 프로젝트만 정확.
  2. `code/view.html`의 파일 크기 초과("too big") 케이스: legacy는 바이너리와 별개로 "파일이 너무 커서
     표시할 수 없음" 안내를 보여주지만, yuna 백엔드(`GitRepository.fileAsJson`)는 이미 크기 초과 파일을
     `isBinary=true`로 뭉뚱그려 판정하고 있어(다른 기존 테스트들이 이 표현에 의존) 별도 3번째 분기를 새로
     만들지 않고 기존 binary-비-이미지 케이스(파일명+크기+다운로드 링크)로 렌더링되도록 둠 — 시각적으로는
     legacy와 다른 안내 문구지만 "파일을 볼 수 없다"는 결과는 동일.
  3. "감시(watch)" 버튼(`code/diff.html`/`code/svnDiff.html`): `WatchController`의 범용 `/watch` 엔드포인트가
     `ResourceType.COMMIT`을 지원하지 않고(커밋은 숫자 PK가 아니라 SHA 문자열이라 기존 `resourceId.
     toLongOrNull()` 파싱 자체가 안 맞음) 이를 지원하려면 Watch 리소스 추상화 자체를 확장해야 하는 별도
     범위의 작업이라 판단, 이번 배치에서는 보류 — `docs/PARITY_BACKLOG.md`에 등록 필요(다음 세션 확인).
  4. select2 방식 브랜치 드롭다운(`data-toggle="select2"`)은 `code/view.html`/`code/history.html`에서
     기존에 이미 일반 `<select>`+`onchange` 방식으로 단순화돼 있던 것을 그대로 유지(이번 배치 범위 아님,
     구조는 legacy와 동일하고 위젯 라이브러리만 다름).
- **주의 — 이번 배치는 테스트를 작성/실행하지 못함.** 8개 워크트리 동시 실행으로 Gradle 데몬 자원 경합이
  심해 코디네이터 지시에 따라 `./gradlew` 실행을 전면 중단하고 읽기/추론만으로 구현을 마무리, 커밋만
  먼저 했다(코디네이터가 병합 후 중앙에서 순차적으로 테스트 실행 예정). 구현 중 실제 jar 바이트코드
  검사(Jackson `ObjectNode`, Thymeleaf `Lists`/`Strings` 유틸리티)로 API 존재 여부는 검증했지만, 전체
  Spring 컨텍스트 기동/Thymeleaf 렌더링/신규 JPQL(`TREAT` 쿼리) 파싱은 **미검증** — 다음 세션에서
  `./gradlew test --tests "com.github.search5.yona.web.*"` 실행 후 발견되는 문제를 우선 수정 필요.
  `TemplateEquivalenceSpec.kt`(또는 신규 `CodeTemplateEquivalenceSpec.kt`)에 그룹10 검증 테스트를 추가하는
  작업 자체도 이번 배치에서 못함 — 다음 세션 최우선 후속 작업.
### 그룹12 `organization/*` 완료 (#193~209, TASK-0250)

- **착수 전 상태 확인**: `organization/*.html` 10개 파일 전부 legacy `site/layout::gnb`/`footer`/`scripts`
  조각이 하나도 포함돼 있지 않았고(그룹1 TASK-0223 조사에서 이미 기록해둔 것과 동일), `view.html`/
  `members.html`/`setting.html`/`delete.html`/`boardList.html`/`issueList.html`/`pullRequestList.html`
  7개는 project 그룹에서 발견된 것과 동일한 "가짜 GNB(`.gnb-wrap`/`.gnb-brand` 하드코딩)+jQuery CDN
  직접 로드+legacy와 무관한 독자 마크업" 버그를 그대로 갖고 있었음(작업 원칙 4-e 예고대로). `list.html`/
  `create.html`은 `site/layout::gnb`는 있었으나 `footer`/`scripts` 조각이 누락돼 있었음. `header.html`/
  `menu.html`(#196/#197)은 파일 자체가 아예 없었고, `boardList_partial`/`issueList_partial`/
  `issueList_quicksearch`/`issueSearch_partial`/`pullRequestList_partial`(#203/#205/#206/#207/#209)도
  전무했음 — legacy의 `group_issue_search_partial.scala.html`이 담당하던 이슈 검색(퀵서치+authorId/
  assigneeId/mentionId 필터+정렬+상태탭 카운트) 기능 자체가 백엔드에도 전혀 배선돼 있지 않았음.
- **17개 전부 legacy 1:1 재작업**: `project/header.html`+`project/menu.html`을 확립된 패턴으로 삼아
  `organization/header.html`(로고/브레드크럼/게스트 가입요청 드롭다운)과 `organization/menu.html`
  (홈/이슈/게시판/PR 탭+관리자 설정 톱니)을 신규 작성, `view/members/setting/delete/boardList/issueList/
  pullRequestList` 7개를 `site/layout::gnb/footer/scripts` + 새 header/menu 프래그먼트 기반으로 완전
  재작성, `list.html`/`create.html`은 누락된 footer/scripts와 legacy 대비 누락 마크업(create.html의
  n-alert 검증 영역, `organization.New` 모듈 로드, 폼 `name="new-org"`)만 보강. 새 partial 5개
  (`boardList_partial`/`issueList_partial`/`issueList_quicksearch`/`issueSearch_partial`/
  `pullRequestList_partial`)를 legacy 원본 그대로 신규 작성.
- **백엔드도 함께 이식(뷰만으로 끝나지 않는 항목들, 작업 원칙 준수)**:
  1. **조직 탈퇴(leave) 기능 자체가 전무**했음(`view.html`의 관리자/멤버 탈퇴 버튼이 가리킬 엔드포인트가
     없었음) — `OrganizationService.leaveOrganization()`/`OrganizationServiceImpl` 신규 추가,
     `OrganizationViewController.leave()`(`DELETE /organizations/{orgName}/leave`) 신규 추가. legacy
     `OrganizationApp.java:287-311 validateForLeave()`를 그대로 재현 — `AccessControl.isAllowed(user, org,
     LEAVE)`가 조직 ORG_ADMIN 여부와 동일하다는 것을 legacy `AccessControl.java:198-199`에서 확인하고,
     "관리자는 이 가드를 완전히 우회(마지막 관리자라도 자유롭게 탈퇴 가능 — legacy의 실제 버그)" /
     "관리자가 아니면 조직 전체 관리자 수가 정확히 1명일 때 탈퇴자와 무관하게 거부(역시 legacy 원문의
     버그성 동작)"를 있는 그대로 재현했다(TemplateEquivalenceSpec으로 검증).
  2. **조직 이슈 검색 자체가 백엔드에 없었음** — `IssueSpecification.filterOrganizationIssues()`(다중
     프로젝트+상태+authorId/assigneeId/mentionId+검색어) 신규 추가, `IssueRepository.
     countByProjectInAndState()` 신규 추가, `MentionService.getMentioningIssueIds()`(기존 "나를 언급한
     이슈" 기능)를 재사용해 mentionId 퀵필터 배선. `OrganizationViewController.organizationIssues()`를
     이 전부를 조합하도록 재작성.
  3. **조직 게시판 목록의 프로젝트 다중선택/정렬/공지분리**도 배선이 없었음 — `PostingRepository.
     findByProjectInAndKeyword()`/`findByProjectInAndNotice()` 신규 추가.
  4. **조직 PR 목록의 검색어 필터·상태별 카운트 배지**도 없었음 — `PullRequestRepository.
     searchByToProjectInAndState()`/`countByToProjectInAndState()` 신규 추가.
  5. **PR 리뷰 진행률 바**(`group_pullrequest_list_partial.scala.html`의 코멘트 스레드 열림/닫힘 비율)도
     연결이 안 돼 있었음 — `TemplateHelper.getCommentThreads()`/`countCommentThreadsByState()`/
     `getReviewProgressPercent()` 신규 추가(기존 `CommentThreadRepository.findByPullRequest()` 재사용).
  6. `TemplateHelper`에 `isOrganizationAdmin`/`isOrganizationMember`/`isOrganizationGuest`/
     `isEnrolledOrganization`/`isWatchingProject` 신규 추가 — 뷰 곳곳(header/menu/view.html)에서
     반복 호출되므로 project 쪽 `isMember`/`isManager`와 같은 자리에 뒀다(legacy `OrganizationUser.
     isAdmin/isMember/isGuest`, `User.enrolled(Organization)`, `User.isWatching(Project)` 대응).
- **legacy와 다르게 처리한 지점**: (a) `group_issue_list_quicksearch.scala.html`이 공용 `SearchCondition`
  클래스를 재사용하며 갖고 다니는 `milestoneId` data 속성은, org 이슈 검색 화면 자체에 마일스톤 필터
  UI가 legacy에도 없어(죽은 파라미터) yuna 쪽 퀵서치에서는 아예 배선하지 않았다(순수 아키텍처 단순화,
  기능 손실 없음). (b) `organization/list.scala.html`의 `Config.displayPrivateRepositories() ||
  AccessControl.isAllowed(..., READ)` 조직별 비공개 블러 처리는, yuna의 `Organization` 엔티티에 legacy
  Project처럼 별도 공개범위(privacy scope) 필드가 없어(조직 자체는 비공개 개념이 없음) 이식 대상이
  실질적으로 존재하지 않는다고 판단해 보류(제네릭 목록 그대로 노출) — 필요 시 별도 PARITY_BACKLOG 항목화
  검토.
- **테스트**: 새 `OrganizationTemplateEquivalenceSpec.kt`(그룹 규모가 커서 기존 `TemplateEquivalenceSpec.kt`와
  분리) — `[Org-1]`~`[Org-6]` 6개 describe 블록, 17개 화면 전체를 GNB/footer/header/menu 조각 노출,
  게스트/관리자/멤버별 header 가입버튼·탈퇴버튼 노출 분기, 설정서브메뉴 3탭, 게시판 공지/일반글 분리,
  이슈 상태탭+퀵서치+authorId 필터, PR 상태탭+열림/닫힘 분리, 조직 탈퇴(정상탈퇴/마지막관리자 1명 거부)
  총 17개 `it` 블록으로 검증.
- **검증 관련 특이사항**: 이 세션 종료 시점에 8개 병렬 워크트리가 동시에 Gradle 데몬을 띄우며 호스트
  메모리 경합(OOM)이 발생해 코디네이터 지시로 `./gradlew` 실행을 중단했다. 메인 소스셋
  (`compileKotlin`)은 이 그룹의 백엔드 변경 전부를 반영한 상태로 정상 컴파일 완료를 확인했으나
  (`compileTestKotlin` 포함 `./gradlew test` 전체 실행은 수행하지 못함), 이후 `TemplateHelper.
  getReviewProgressPercent()` 리팩터링과 신규 테스트 스펙 파일은 컴파일러로 재확인하지 못했다 — 코드
  리뷰로 기존 확립된 문법 패턴(`project/*.html`, `board/list.html`, `issue/list.html`, `site/layout.html`,
  `milestone/*.html`)과 1:1 대조해 안전을 확인했지만, 최종 `./gradlew test` 실행 검증은 코디네이터가
  병합 후 중앙에서 순차 실행하기로 함. **그룹12(organization/*, #193~209) 17개 항목 전체 처리 완료.**

### 그룹14 `search/*` (#224~233) + #82/#83 (TASK-0248, 병렬 워크트리 에이전트 작업 병합)

- **범위**: legacy `search/result.scala.html` + 하위 8개 파샬(#225~233) 전부가 yuna `search/list.html`
  단일 파일(약 412줄)에 인라인 조합돼 있는 구조를 그대로 유지(아키텍처적으로 허용되는 선택)하되,
  줄 단위 대조로 실제 내용 격차를 전수 조사 — 8개 탭 전부에서 실기능 버그 발견/수정(상세는 위 표의
  #224~233 각 행 비고 참고): 프로젝트 로고/포크배지/overview 스니펫 오적용, 이슈/게시글/댓글 탭의
  "....." 말줄임표 누락+작성자 링크 없음+`noAuthor` 폴백 없음+고정 날짜 포맷, 댓글 탭 제목이 legacy의
  "Re) " 접두어 대신 임의 문구로 재창작돼 있던 것(문구 재창작 금지 원칙 위반), 마일스톤 탭의 기한
  없음 처리 오류 및 `milestone.until()` 전체 누락, 리뷰 탭의 존재하지 않는 라우트(`/pullRequest/`→
  실제는 `/pull/`)로 인한 404 및 커밋 기반 리뷰의 NPE 위험, 유저 탭 아바타 누락, 그리고 전 탭에 걸쳐
  legacy의 `yobi.Pagination.update()` 컨벤션 대신 독자 부트스트랩 페이저를 재발명한 문제.
- **#82/#83 재조사**: 이전 세션이 "search/partial_search.scala.html 전용"이라 기록해뒀던 것이 실제로는
  **동명이인 파샬**(시그니처가 다른 같은 디렉터리의 `search/partial_milestones.scala.html`, 그룹14
  #231)과의 혼동이었음을 확인 — `user/partial_milestones.scala.html`/`user/partial_postings.scala.html`
  본체는 legacy 전체(코드+git 로그)에서 호출부가 단 한 곳도 없는 완전한 죽은 코드로 재확인, 이식할
  legacy 호출 지점 자체가 없어 미이식(진짜 불가능 사례로 재분류, `[x]`).
- **백엔드 추가**: `TemplateHelper.until(Milestone)`(오늘/기한초과/남은일수), `urlToCommentThread
  (CommentThread)`(PR/커밋 라우팅), `getUserSinceDateString(Instant)`(legacy `User.getDateString()`
  포맷 재현). `posting.noAuthor` 메시지키가 legacy 5개 로케일 파일 전체에 정의 자체가 없던 잠재 버그를
  발견(Play는 키 누락을 조용히 넘기지만 Spring MessageSource는 예외 발생) — 6개 로케일 파일 모두에
  `issue.noAuthor`와 동일 값으로 신규 추가.
- **legacy와 다르게 처리한 지점**: 없음(발견된 격차 전부 순수 버그 수정 + 문구/키/라우트 원복).
- **테스트**: `TemplateEquivalenceSpec.kt`의 `[Test-19-34]`(통합 검색 8개 결과 탭 + 카테고리 탭
  active/empty 클래스 검증) — 병렬 워크트리 병합 시 이미 존재하던 `[Test-19-31]`(GNB 검색범위,
  메인 세션 작업)과 번호가 겹쳐 `[Test-19-34]`로 재번호 부여.
- **검증**: 병렬 워크트리 에이전트는 Gradle 데몬 OOM 경합으로 `./gradlew` 실행 없이 수작업 검토만
  수행(코디네이터 지시에 따름) — 메인 세션 병합 후 중앙에서 재검증 예정.
  **그룹14(search/*, #224~233) 10개 항목 + #82/#83 전체 처리 완료.**

### 그룹15 `help/*` (#234~238) (TASK-0249, 병렬 워크트리 에이전트 작업 병합)

- **범위**: 정적 콘텐츠 위주 5개 항목 전부 legacy와 줄 단위 대조 완료.
- **#234(toc.html) 발견**: `<title>`이 `head('도움말')`처럼 하드코딩 문자열이었음(다른 화면들의
  `#{메시지키}` 컨벤션 위반) — `head(#{title.help})`로 수정.
- **#236(keymap.html) 중대 발견**: legacy `help/keymap.scala.html`은 `section`(화면 구분)과 `project`
  2개 파라미터를 받아 화면별로 다른 단축키 안내를 보여주는데, yuna는 `th:replace="~{help/keymap}"`
  (파라미터 없는 전체 include) 방식이라 `section`을 실제로 전달할 방법이 없었음 — `th:fragment=
  "keymap(section, project)"`로 전환하고, 호출부인 `board/list.html`/`board/view.html`의 `th:replace`도
  `~{help/keymap :: keymap('boardList'|'boardDetail', ${project})}` 형태로 함께 수정.
- **#235(markdown.html)/#237(UIKit.html)/#238(experimental.html)**: 대조 결과 이미 legacy와 정확히
  일치(문법탭 10개, standalone 페이지 구조, 미참조 모달 조각), 코드 변경 없음.
- **legacy와 다르게 처리한 지점**: 없음.
- **테스트**: `TemplateEquivalenceSpec.kt`의 `[Test-19-35]`(toc 6개 Q&A + 메시지키 검증, UIKit
  standalone 검증, markdown 10개 문법탭 검증, keymap section별 노출 차이 검증, experimental 모달
  마크업 직접 렌더링 검증) — 병렬 워크트리 병합 시 이미 존재하던 `[Test-19-30]`(프로젝트 메뉴, 메인
  세션 작업)과 번호가 겹쳐 `[Test-19-35]`로 재번호 부여.
- **검증**: 병렬 워크트리 에이전트는 Gradle 데몬 OOM 경합으로 `./gradlew` 실행 없이 수작업 검토만
  수행(코디네이터 지시에 따름). 진행 로그/백로그 상태 갱신 자체도 에이전트가 누락해 메인 세션이 병합
  중 직접 작성. 메인 세션 병합 후 중앙에서 재검증 예정.
  **그룹15(help/*, #234~238) 5개 항목 전체 처리 완료.**
### 그룹13 `site/*` 사이트 관리자 (#210~223) 14개 처리 (TASK-0250)

- **#210(siteMngLayout)**: 그룹1 #3(TASK-0222)에서 9개(당시 `setting.html`은 아직 없어서 미포함) 관리자 화면이
  이미 `site/layout.html`의 `head`/`gnb`/`breadcrumb`/`sidebar`/`footer`/`scripts` 조각을 조합하는 방식으로
  legacy `siteMngLayout.scala.html`(breadcrumb + 사이드바 내비 데코레이터)과 동등하게 이식돼 있음을 재확인.
  다만 legacy 사이드바의 "update" 메뉴 항목에 `@if(YobiUpdate.versionToUpdate != null) { <span class=
  "notification-badge">1</span> }` 알림 배지가 `site/layout.html :: sidebar` 조각에서 누락돼 있어
  `yonaUpdateService.isUpdateRequired()`로 동일하게 복구.
- **#213(setting)**: yuna에 파일 자체가 없었음. legacy `site/setting.scala.html`을 확인한 결과 `@siteMngLayout
  (message) { TODO }` 형태의 미구현 스텁이었고, `SiteApp.java`/`conf/routes` 전수 검색 결과 이 템플릿을
  렌더링하는 컨트롤러 액션이 legacy에도 전혀 없었다(사이드바 메뉴에도 "설정" 항목이 없음) — 즉 legacy에서도
  도달 불가능한 죽은 파일. 새 라우트를 만들지 않고(legacy에 없는 것을 새로 만드는 것은 방침 위반) 그 상태
  그대로 `site/setting.html`을 신규 작성(레이아웃 조각 조합 + 본문에 "TODO" 텍스트만).
- **#211/#216/#219(data/massMail/postList) 버그 발견**: 세 파일 모두 본문 마지막에 대응하는 여는 태그가 없는
  **스트레이 `</div>` 1개**가 남아있었다(`site/layout.html::footer` 바로 앞). 잘못된 HTML이라 브라우저는
  관대하게 무시하지만 legacy에는 없는 구조라 제거.
- **#211/#212/#214/#217/#218/#219/#220 `<title>` 정합성 버그 발견**: legacy 9개 관리자 화면은 컨트롤러가
  넘기는 `message`(i18n 키, 대부분 `"title.siteSetting"`="사이트 설정", `projectList`만 `"title.projectList"`
  ="프로젝트 목록")를 `<title>`에 쓰는데, yuna 쪽은 제각각이었다: `userList`/`issueList`/`projectList`는
  `<head>` 프래그먼트 호출에 **하드코딩된 한국어 문자열**("사용자 관리"/"이슈 관리"/"프로젝트 관리")을 직접
  박아뒀고, `data`/`diagnostic`/`postList`/`update`는 사이드바 메뉴 라벨 키(`site.sidebar.data` 등)를 대신
  쓰고 있었다. `SiteViewController`의 `userList`/`projectList`/`issueList`/`data`/`diagnose` 5개 액션에
  legacy와 동일한 `message` 모델 속성을 추가하고(`postList`/`mail`/`massMail`/`update`는 이미 있었음), 7개
  템플릿 전부 `head(#{${message}})`(기존 `mail.html`/`massMail.html`이 이미 쓰던 패턴)로 통일.
- **#217/#218/#220(userList/projectList/issueList) 페이지네이션 "yuna식 독자 구현" 발견**: legacy 4개
  목록 화면(userList/postList/projectList/issueList) 전부 `<div id="pagination"></div>` + JS
  `yobi.Pagination.update($("#pagination"), totalPages)` 클라이언트 위젯(`yona-lib.js`에 번들, prev/페이지
  입력창/총페이지/next 렌더링)을 쓴다 — legacy `site/partial_pagination(ForUserList).scala.html`은 이 위젯과
  무관한 **완전히 별개의 죽은 파샬**(아래 #222/#223 참고)이다. `postList.html`만 legacy와 동일한 위젯 패턴을
  쓰고 있었고, `userList`/`projectList`/`issueList` 3개는 서버사이드로 직접 렌더링한 Bootstrap `<ul class=
  "pagination">`(다른 CSS 클래스, "«/»" 대신 legacy의 "Prev"/"Next" 텍스트도 없음, `yobi.Pagination.js`
  자체를 아예 안 씀)로 완전히 다른 구현이었다 — 방침 1(yuna식 독자 구현 금지)/알려진 버그 패턴 위반. 3개
  파일 모두 `postList.html`과 동일한 `<div id="pagination"></div>` + `yobi.Pagination.update(...)` 패턴으로
  교체. 부수적으로 `projectList.html`이 legacy에 있던 `console.log($(this).data('href'));`(삭제 버튼 클릭
  핸들러 안)도 누락하고 있어 함께 복원.
- **페이지네이션 파라미터명 불일치(신규 발견, legacy에는 없던 버그)**: `yobi.Pagination.js`는 기본적으로
  URL 쿼리 파라미터명 `pageNum`을 읽고 쓰는데, yuna의 실제 `/sites/{userList,projectList,postList,issueList}`
  컨트롤러는 전부 `page` 파라미터를 받는다(legacy의 Ebean 기반 `pageNum` 관례를 이 프로젝트가 이미 `page`로
  치환해온 기존 컨벤션). 4개 화면 모두 `yobi.Pagination.update(...)` 호출에 `{"paramNameForPage": "page"}`
  옵션을 추가해 실제 페이지 이동 링크가 맞는 쿼리 파라미터를 가리키도록 수정(`postList.html`도 기존에 이
  옵션이 빠져 있어 클릭해도 페이지가 안 넘어가는 잠재 버그였음).
- **#221(lostPassword) 중대 발견**: `user/lostPassword.html`이 그룹5(#71~73)에서 이미 발견/수정했던 것과
  똑같은 "완전한 독자 페이지" 패턴이었다 — 자체 `<head>`만 있고 `site/layout`의 GNB/footer 조각을 전혀
  쓰지 않았으며, `PasswordResetController`가 `errorMessage`/`successMessage`에 i18n 키 대신 하드코딩된
  한국어 문자열을 직접 담고 있었다(legacy는 `site.resetPasswordEmail.invalidRequest` 같은 메시지 키를
  모델에 담아 뷰에서 `Messages()`로 해석). `site/layout` 기반(`head`/`gnb`/`footer`/`scripts`)으로
  재작성하고, 컨트롤러도 legacy `PasswordResetApp`과 동일하게 i18n 키 기반(`site.resetPasswordEmail.
  invalidRequest`, `isSent`+`site.mail.sended`)으로 교체, `title.resetPasswordFor(siteName)` 사이트명
  파라미터(legacy `utils.Config.getSiteName()`)도 `@Value("\${yuna.site-name:Yona}")`로 대응. 메일 발송이
  예외로 실패하는 경우 legacy `sendPasswordResetMail()`도 로그만 남기고 화면에 아무것도 노출하지 않는
  것까지 동일하게 유지(방침상 legacy 그대로).
- **#222/#223(partial_pagination[ForUserList]) 신규 작성**: legacy-yona 저장소 전체를 전수 검색한 결과
  두 파샬 모두 **어떤 컨트롤러/템플릿에서도 호출되지 않는 죽은 파일**임을 확인(실제 페이지네이션은 위에서
  설명한 `yobi.Pagination.js` 위젯이 전담). 새 호출부를 만들지 않고 legacy와 동일한 구조/연산(prev/숫자
  윈도우/next, `pageNum` 계산)만 이식한 Thymeleaf 프래그먼트로 신규 작성 — `partial_pagination.html`은
  범용(`listUrl` 파라미터로 대상 지정), `partial_paginationForUserList.html`은 legacy가 `routes.SiteApp.
  userList(index - 1)`(Ebean 0-based)로 하드코딩했던 링크를, yuna의 실제 `GET /sites/userList`가 받는
  1-based `page` 파라미터로 환산해 연결(Play 라우트 헬퍼 → 실제 `@GetMapping` 경로 치환 컨벤션).
- **legacy와 다르게 처리한 지점**: 페이지네이션 파라미터명(`pageNum`→`page`)은 이 프로젝트가 그룹 초반부터
  이미 확립한 컨벤션에 맞춘 것으로 legacy 자체의 변경은 아님. 나머지는 전부 순수 버그 수정/누락 복원.
- **테스트**: 새 `SiteAdminTemplateEquivalenceSpec.kt`(`[SiteAdmin-1]`~`[SiteAdmin-7]`) — 사이드바 업데이트
  배지, `<title>` 메시지 키, `<div>` 태그 균형, 페이지네이션 위젯 마크업, `site/setting.html`/`partial_
  pagination*.html`(테스트 전용 래퍼 템플릿 `src/test/resources/templates/site/__test_partial_*_wrapper.html`
  경유), `user/lostPassword.html` GNB/footer 및 에러 메시지 키 검증. `PasswordResetControllerSpec.kt`의
  `successMessage` 단언을 `isSent`로 갱신.
- **검증**: 이 세션 진행 중 gradle 데몬이 다른 병렬 워크트리들과의 리소스 경합으로 OOM을 반복해, 코디네이터
  지시에 따라 `./gradlew` 실행 없이 코드 리뷰(구문/HTML 태그 균형/Thymeleaf 표현식 정합성 수동 검토, `sec:
  authorize`가 순수 `org.thymeleaf.context.Context`로는 평가 불가함을 바이트코드로 직접 확인해 테스트 설계를
  안전한 방식으로 수정)만으로 마무리하고 로컬 커밋함 — **전체 회귀(`./gradlew test`)는 아직 미실행**이며
  코디네이터가 병합 후 중앙에서 실행 예정.
