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
| 1 | [x] | `layout.scala.html` | `site/layout.html` | 완료(TASK-0220, TDD). og/twitter 메타태그·업데이트알림배너·NProgress/ViewerJS 자산 이식. `\|:\|` 제목 분리 컨벤션은 미이식(비고 참고) |
| 2 | [x] | `layout_framed.scala.html` | `site/layout_framed.html` | 완료(TASK-0221, TDD). og/twitter 메타태그·nprogress/magnific-popup 자산·popover 초기화·GA 스크립트 이식. sidebar()는 이미 이 파일에 인라인되어 있었음(→ #7과 동일 파일로 처리) |
| 3 | [x] | `siteLayout.scala.html` | `site/{data,diagnostic,issueList,mail,massMail,postList,projectList,update,userList}.html` (각 파일에 인라인 조합) | 완료(TASK-0222, TDD). 신규 데코레이터 파일 대신, 관리자 화면 9개가 이미 `head`/`gnb`/`breadcrumb`/`sidebar`/`scripts` 조각을 조합하고 있었음 — 빠져있던 `footer` 조각만 9개 파일에 공통 추가(legacy `siteLayout`이 `@content` 뒤에 `@common.footer()`를 감싸는 것과 동일 동작) |
| 4 | [i] | `siteLayout_framed.scala.html` | `site/layout_framed.html` | 확인 결과 legacy에서 이 파일의 유일한 사용처는 `index/sidebar.scala.html`(빈 content로 `siteLayout_framed`→`layout_framed` 호출) — 즉 "사이드바+iframe 프레임 셸"이며, 이는 #2에서 이미 완료한 `site/layout_framed.html`(→`/user/sidebar`)과 동일 화면. 별도 신규 파일 불필요 |
| 5 | [i] | `projectLayout.scala.html` | `project/*.html` 각 파일(인라인 조합) | `navbar+project.header+content+footer` 데코레이터 패턴은 이미 `project/home.html`/`members.html`/`setting.html`/`statistics.html`가 `site/layout::gnb`+`project/header::header`+`project/menu::menu`+`site/layout::footer` 조합으로 실현 중. **단, `change_vcs/delete/fork/issuelabels/setting_webhook/transfer/watchers.html`은 header/footer 조각이 빠져있음을 확인** — 그룹6(#87~112) 작업 시 각 파일 항목에서 직접 채워 넣을 것(지금은 그룹1 범위를 넘어서므로 보류, 그룹6 착수 시 최우선 처리) |
| 6 | [i] | `organizationLayout.scala.html` | `organization/*.html` 각 파일(인라인 조합 필요) | `navbar(menuType,null,group)+content+footer` 패턴 대응. **`organization/*.html` 10개 파일 전부 `site/layout::gnb`/`footer` 조각이 하나도 포함돼 있지 않음을 확인** — project 그룹보다 미착수 정도가 훨씬 심함. 그룹12(#193~209) 착수 시 최우선 처리 |
| 7 | [i] | `sidebar.scala.html` | `site/layout_framed.html` (인라인) | #2 작업 중 확인: `site/layout_framed.html`의 `#sidebar` div가 이미 이 파일 내용을 인라인 포함(로그인 필수 분기는 컨트롤러 레벨 리다이렉트로 대체) |
| 8 | [~] | `projectMenu.scala.html` | `project/menu.html` | 프로젝트 상단 탭 메뉴(코드/이슈/PR/게시판/마일스톤) |
| 9 | [x] | `restricted.scala.html` | (포팅 보류, 아래 참고) | **보류 결정(사유 기록)** — play-authenticate 모듈의 데모/테스트용 페이지(`Sshhh...don't tell anyone`, 하드코딩된 유튜브 영상, `currentAuth()`/`auth.getProvider()`/`auth.expires()` 등 해당 라이브러리 전용 API 표시). yuna는 Spring Security 기반이라 동등 개념(OAuth2AuthorizedClientService 등)을 새로 엮어야 하는데, 실사용 가치가 없는 라이브러리 데모 화면이라 투입 대비 효과가 지나치게 낮다고 판단해 보류. `docs/PARITY_BACKLOG.md`의 P1-27 최초 보류 결정처럼, 사용자가 이식을 원하면 언제든 재지시 가능 |

## 그룹 2 — `common/*` 공용 파샬 (35개, #10~44)

거의 모든 화면이 하나 이상 include. 레이아웃 다음으로 최우선.

| # | 상태 | legacy 경로 | yuna 대상 경로 | 비고 |
|---|---|---|---|---|
| 10 | [x] | `common/navbar.scala.html` | `site/layout.html :: gnb` (인라인) | 완료(TASK-0224, TDD). `HIDE_PROJECT_LISTING`+게스트 가드로 "전체 목록" 링크 숨김 이식. org 검색범위의 좁은 게스트/HIDE 모드 조합 세부조건은 미이식(비고: 저가치 코너케이스로 판단, 문서에 기록) |
| 11 | [x] | `common/footer.scala.html` | `site/layout.html :: footer` (인라인) | 확인 완료 — 완전 일치(TASK-0224 조사 중 대조 완료, 코드 변경 없음) |
| 12 | [x] | `common/scripts.scala.html` | `site/layout.html :: scripts` (인라인) | 완료(TASK-0224, TDD). tplYobiToast, "U" 단축키, pageshow NProgress 해제, iframe 히스토리 동기화 스크립트 이식. Play flash-scope 제네릭 순회(title/description 특수케이스)는 yuna의 warning/error/info 3키 모델로 이미 아키텍처 치환되어 있었음(선행 세션) |
| 13 | [x] | `common/usermenu.scala.html` | `site/layout.html :: gnb` (인라인) | 완료(TASK-0224, TDD). 내 이슈 카운터 배지(`myOpenIssueCount`), 게스트 새 그룹 만들기 숨김 이식. `NAVBAR_CUSTOM_LINK_NAME/URL` 커스텀 링크와 OAuth 세션 불일치 경고는 미이식(저가치·백엔드 설정 부재, 문서에 기록) |
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
| 55 | [i] | `index/sidebar.scala.html` | `site/layout_framed.html` | #4/#7과 동일 발견: `siteLayout_framed(...){}`을 빈 content로 호출하는 것이 이 파일의 전부라, `site/layout_framed.html`(→`/user/sidebar`)이 이미 이 화면을 대체 |
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
