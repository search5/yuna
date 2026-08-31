---
type: plan
id: P3-02
title: "yuna CLI 설계 (+ 범용 REST API·Fine-grained 토큰 모델 선행 구축)"
status: planned
priority: 3
depends_on: []
blocks: [p3-03-ssh-gpg, p3-07-mcp-server, p3-05-ci-actions-runner]
source: docs/PARITY_BACKLOG.md#P3-02
created: 2026-08-28
updated: 2026-08-31
tags: [plan, p3, cli, api, auth]
---

# yuna CLI 설계 (+ 범용 REST API·Fine-grained 토큰 모델 선행 구축)

## 배경

CLI(`yona` 명령)로 이슈/PR 워크플로 + 관리자 운영(백업/웹훅/권한) + CI/CD 자동화를 커버하는 것이 목표다.
Go로 결정됨(설치 직후 바로 실행되어야 하므로 JVM 콜드스타트가 있는 Kotlin 제외, `gh`(Go+Cobra)와 동일 스택).

코드 검증 결과 `ApiTokenAuthenticationFilter.kt`가 `Authorization: token <값>`/`Yona-Token` 헤더로 이미 토큰 인증을
수행 중이지만, **토큰이 저장소·권한 단위로 전혀 스코프되지 않는 전권 단일 토큰**이다. CLI에 이 토큰을 그대로 쓰면
토큰 하나 유출 시 그 사용자의 전체 저장소·전체 권한이 노출된다. GitHub Fine-grained PAT 모델을 채택하기로 결정됨.

또한 현재 yuna에는 이슈/PR을 개별적으로 생성·조회·수정하는 **범용 JSON REST API가 없다** —
`ProjectApiController.kt`는 export/import 전용이고, 나머지는 전부 Thymeleaf 렌더링용 MVC 컨트롤러다.
원본: [`docs/PARITY_BACKLOG.md#P3-02`](../../PARITY_BACKLOG.md)

## 범위

### 포함
- **1부(선행, 이 계획의 핵심)**: 이슈/PR/저장소를 다루는 범용 JSON REST API + `ApiToken` 엔티티(저장소 범위 + 리소스별 권한) + 인가 재작성
- **2부**: Go CLI 본체(`yona auth/issue/pr/project/admin/api` 명령 트리) — 1부의 API를 감싸는 얇은 클라이언트

### 제외 (비범위)
- SSH 인증(`yona auth login --with-ssh`)은 [[p3-03-ssh-gpg]]에서 다룸
- `yona runner`/`yona workflow` 명령은 [[p3-05-ci-actions-runner]]에서 다룸
- `yona mcp serve`는 [[p3-07-mcp-server]]에서 다룸

## 의존성

- **선행 조건**: 없음
- **후속 파급**: [[p3-03-ssh-gpg]](Deploy Key가 이 계획의 토큰 스코프 체계를 공유), [[p3-07-mcp-server]](동일 REST API + 토큰 모델을 그대로 사용), [[p3-05-ci-actions-runner]](CLI가 같은 Go 스택이라 러너 구현 시 재사용 이점) — **7개 P3 항목 중 가장 많은 후속 항목을 막고 있는 계획**이므로 착수 순위 2위로 배치

## 설계 개요

### `ApiToken` 엔티티 (기존 `User.token` 단일 필드 대체)

| 필드 | 설명 |
|---|---|
| `owner` | 발급자(User) |
| `token_hash` | 원문 미저장, 해시만 |
| repo scope | 전체 저장소 vs 선택 저장소 목록 |
| resource scope | 리소스별 none/read/write |
| `expires_at` | **필수**(2026-08-24 결정 — 무기한 토큰 발급 자체를 금지) |
| `last_used_at` | 마지막 사용일 |

### 권한 스코프 카테고리 — 미결정 사항 확정 필요

원본 백로그가 남긴 미결정: `domain/enumeration/ResourceType.kt`의 기존 33종 enum(`ISSUE_POST`/`PULL_REQUEST`/
`WEBHOOK`/`CODE` 등)을 스코프 매트릭스 축으로 재사용할지, GitHub 스타일 대분류(contents/issues/pull-requests 등)로
새로 만들지. **이 계획에서는 기존 `ResourceType`을 그룹핑하는 매핑 테이블을 두는 방식으로 확정** — 알림/감사 로그와의
일관성을 유지하면서(재사용), 토큰 발급 UI에는 그룹 단위(issues/pull-requests/code/webhooks/administration 등)로만
노출한다(사용성 확보). 매핑은 코드 상수로 관리(예: `ApiTokenScopeGroup.kt`).

### `ApiTokenAuthenticationFilter` 재작성

현재: 토큰 매칭 성공 시 `userDetails.authorities` 전체 부여.
변경 후: 요청 대상 저장소 + 요청 리소스를 토큰의 스코프와 대조하는 인가 체크로 전환. 기존 필터 체인 위치(`SecurityConfig.kt`)는 유지.

### 범용 REST API

`web/` 패키지에 이슈/PR/저장소 CRUD용 `@RestController` 신설. 기존 `ProjectApiController.kt`(export/import 전용)와는
별도 클래스로 분리 — 책임이 다르다(하나는 대량 이관용 JSON 조립, 하나는 개별 리소스 CRUD).

### Fine-grained PAT 완전성 갭 분석 (2026-08-29, 사용자 질의로 착수)

2라운드(Step1~6) 완료 후 "GitHub Fine-grained PAT과 비교해 완전한가?"를 코드 기준으로 재점검해 발견한 잔여 갭.
우선순위 순:

1. **토큰 발급/관리 UI 자체가 없음(최우선)** — `ApiTokenRepository`엔 `findByTokenHash()` 하나뿐이고 사용자가
   `ApiToken`을 만들거나 조회/폐기하는 컨트롤러·서비스·화면이 전혀 없다. 지금은 테스트 코드로 DB에 직접 넣는 것
   외엔 발급 경로가 없다 — 아래 "토큰 발급/관리 웹 UI 설계" 참고. `ApiToken` 엔티티에 `name`(GitHub는 토큰마다
   이름을 강제) 필드도 아직 없다.
2. **Metadata 자동 부여 없음** — 스코프가 하나라도 있으면 프로젝트 기본 정보는 자동으로 읽혀야 하는데(GitHub의
   "Metadata: Read-only"는 저장소가 스코프에 포함되는 순간 그룹/권한 매트릭스와 무관하게 자동 부여됨) 지금
   `ApiTokenAuthorizer`엔 이런 분기가 없다 — 아래 "`metadata` 스코프 세그먼트 설계" 참고.
3. **레거시 전권 토큰과의 공존** — `/api/v1/projects/**` 밖의 모든 API와, 그 안에서도 스코프 토큰이 안 걸리는
   경우는 여전히 `User.token` 전권 토큰이 그대로 통한다(`ApiTokenAuthenticationFilter.authenticateLegacy`).
   위 리스크 표 "기존 전권 토큰 마이그레이션" 항목과 동일 — 마이그레이션 방침(자동 재발급 vs 만료 후 재발급,
   레거시 경로를 언제 끊을지)이 여전히 미정.
4. **만료일 상한 없음** — `expiresAt`이 NOT NULL이라 "무기한 금지"는 지켜지지만, GitHub처럼 "최대 1년" 같은
   상한 검증이 없다(지금 값 그대로 100년짜리 만료일도 발급 가능).
5. **토큰 형식에 식별 프리픽스 없음** — GitHub는 `github_pat_...`로 시작해 시크릿 스캐닝 툴이 토큰 종류를
   인식한다. 우선순위 낮음(툴링 편의 문제, 기능 결함 아님).
6. **Step6 스코프 패턴 갭** — 아래 "프로젝트 목록 API 스코프 필터링 설계" 및 리스크 표 참고.
7. **(선택) 조직 소유 저장소 승인 워크플로 없음** — GitHub는 조직 리소스에 Fine-grained PAT을 쓸 때 관리자
   승인을 요구할 수 있다. yuna의 위협 모델상 꼭 필요한지 별도 판단 필요, 이번 계획 범위 밖으로 잠정 보류.

### `metadata` 스코프 세그먼트 설계 (2026-08-29, Step6 갭 해소 1/2)

`/api/v1/projects/{owner}/{project}`(Step6 개별 조회)는 특정 리소스 그룹(issues/PR/code 등)과 무관하게
"이 프로젝트를 볼 수 있는가"만 확인하면 되는, GitHub의 "Metadata: Read-only" 자동 부여와 동일한 개념이다.

- `ApiTokenAuthenticationFilter.ScopedApiTarget.representativeResourceType`을 `ResourceType?`(nullable)로
  변경하고 `resourceSegmentToResourceType`에 `"metadata" to null`을 명시적으로 추가한다. 단
  `map[key] ?: return null`은 "키 없음"과 "값이 null"을 구분 못 하므로 `containsKey` 기반으로 바꿔야 한다.
- `ApiTokenAuthorizer.isAuthorized`에 분기 추가: `resourceType == null`이면 그룹/권한 매트릭스는 보지 않고
  **repo scope 일치 여부 + 만료 여부만** 확인하고 통과시킨다(권한 레벨 비교 자체를 생략).
- `ProjectRestApiController`의 개별 조회 경로가 이 `metadata` 세그먼트로 인식되도록 필터의 세그먼트 매핑만
  추가하면 되고, 실제 URL(`/api/v1/projects/{owner}/{project}`)은 바꾸지 않는다.
- Step1~3에서 이미 테스트로 굳어진 로직에 새 분기만 얹는 구조라 회귀 위험이 작다. 신규 테스트: "그룹 권한이
  하나도 없어도 repo scope만 있으면 조회 가능" / "repo scope 자체가 없으면 403" 2케이스.

### 프로젝트 목록 API 스코프 필터링 설계 (2026-08-29, Step6 갭 해소 2/2)

`/api/v1/projects/{owner}`(목록)는 특정 프로젝트 하나가 아니라 "owner 밑 전체"라 3세그먼트 패턴에 애초에
맞지 않는다. 필터가 "인증됨/아님"만 판단하는 데서 그치지 않고, 스코프 토큰으로 인증된 경우 그 토큰이 어떤
프로젝트들을 볼 수 있는지를 컨트롤러에 넘겨야 한다.

- 필터에 owner 전용 패턴 추가: `^/api/v1/projects/([^/]+)/?$`(project/resource 세그먼트 없이 owner만).
  지금은 이 모양이 `scopedApiPattern`에 안 걸려 레거시 경로로 새는데, 스코프 토큰의 원문값은 `User.token`에
  없으므로 레거시 인증도 실패해 결과적으로 **항상 비로그인 취급**된다(구멍은 아니고 기능 제한 — 세션 인증
  없이 스코프 토큰만으로는 목록 API가 항상 공개 프로젝트만 보여줌).
- 새 인증 분기(`authenticateScopedList` 등): 토큰을 해시로 조회해서 있으면(만료 체크 포함) 기존
  `authenticateScoped`처럼 SecurityContext에 신원만 세팅한다. "어떤 프로젝트를 볼 수 있는지"는 특정 리소스
  하나의 문제가 아니라 목록 필터링 문제이므로 여기선 403을 내지 않는다.
- 인증에 사용된 `ApiToken` 객체를 request attribute로 남긴다(예: `request.setAttribute("SCOPED_API_TOKEN",
  apiToken)`) — Spring Security 자체도 CSRF 토큰 등을 이 방식으로 필터→다운스트림에 넘기므로 새 패턴을
  발명하는 게 아니다. 3세그먼트 케이스(`authenticateScoped`)에도 같이 남겨두면 재사용 가능.
- `ProjectRestApiController`의 목록 메서드에서:
  ```kotlin
  val allOwnerProjects = /* 기존 AccessControl 기반 로직 그대로 */
  val scopedToken = request.getAttribute("SCOPED_API_TOKEN") as ApiToken?
  val visible = when {
      scopedToken == null -> allOwnerProjects          // 세션/레거시 토큰 — 기존 동작 100% 유지
      scopedToken.allRepositories -> allOwnerProjects   // 전체 저장소 스코프
      else -> allOwnerProjects.filter { it in scopedToken.scopedProjects }  // 선택 저장소만 교집합
  }
  ```
  `scopedToken == null` 분기가 세션/레거시 인증 경로를 완전히 그대로 보존하므로 회귀 위험이 없다. 신규 테스트:
  "전체스코프 토큰→다 보임" / "선택스코프 토큰→선택한 것만" / "세션 로그인→기존과 동일" 3케이스.

### 토큰 발급/관리 웹 UI 설계 (2026-08-29)

레거시 전권 토큰 화면(`user/edit_token.html`, `/user/editform/token_reset`)은 그대로 두고(단일 값 표시 +
재생성 버튼뿐이라 다중 토큰·스코프 매트릭스를 담을 수 없음), Fine-grained 토큰은 별도 화면으로 신설한다.

- **위치**: `user/partial_edit_tabmenu.html`에 새 탭 추가(예: "API 토큰(세분화)"), URL
  `/user/editform/tokens`.
- **목록 화면**: 발급된 토큰 테이블 — 이름 / 저장소 범위 요약(전체 또는 "N개 저장소") / 권한 요약(그룹별
  뱃지, 예: `issues:write` `code:read`) / 마지막 사용일 / 만료일 / 폐기 버튼.
- **발급 화면**(같은 페이지 내 폼 또는 모달):
  - 이름(필수 텍스트, 신규 `ApiToken.name` 필드)
  - 저장소 범위: 라디오(전체 저장소 / 선택한 저장소) — 선택 시 기존 select2 다중선택
    (`issue/partial_select_label.html` 패턴 재사용) 프로젝트 검색
  - 권한 매트릭스: `ApiTokenScopeGroup`의 8개 그룹(ISSUES/PULL_REQUESTS/CODE/BOARD/WIKI/WEBHOOKS/
    ADMINISTRATION/USERS) × 3단(없음/읽기/쓰기) 라디오 행
  - 만료일: 프리셋(30/90/365일) + 커스텀, **서버에서 366일 초과 거부**(위 갭 분석 4번 해소)
  - 생성 직후: "지금 한 번만 표시됩니다" 안내 + 값 노출 + 복사 버튼(해시만 저장하므로 이후 재조회 불가 —
    GitHub와 동일한 PAT 생성 UX)
- **폐기**: 삭제 확인 모달(`common/commentDeleteModal.html`류 기존 패턴 재사용) → DELETE.
- **백엔드 신규**:
  - `ApiToken.kt`에 `name: String` 필드 추가
  - `ApiTokenRepository`에 `findByOwner(user): List<ApiToken>` 추가
  - 신규 `ApiTokenService`/`ApiTokenServiceImpl` — 발급(원문은 `SecureRandom`으로 생성, 기존
    `LdapUserProvisioningService.kt`가 쓰는 것과 동일한 패턴 재사용 + `ApiTokenHasher.hashApiToken()`으로
    해시 저장), 폐기(삭제)
  - 신규 컨트롤러(`UserController.kt` 확장 또는 별도 클래스): `GET /user/editform/tokens`(목록+발급폼),
    `POST /user/editform/tokens`(발급), `POST /user/editform/tokens/{id}/revoke`(폐기)

### CLI `gh` 명령 체계 대조 감사 (2026-08-31, 사용자 지적 — "핵심만 뽑지 말고 철저히")

지난 대화에서 `--json`/`-L`/`--web`/`pr create` 번거로움/필터 부족/`--repo` 자동감지/`server` 커맨드 등
몇 개만 뽑았던 걸, `gh` CLI의 전체 명령 체계와 yona-cli(`cmd/*.go`, `internal/api/*.go` 전체 재확인)를
하나하나 대조해 전면 재감사했다. 분류 기준: **(A)** yuna에 대응 기능이 있고 P3-02 범위인데 미구현(진짜 갭),
**(B)** yuna에 대응 기능은 있지만 다른 P3 계획 범위, **(C)** yuna에 대응 개념 자체가 없어 적용 불가.

#### (A) 진짜 갭 — P3-02 범위, 구현 필요

| 분류 | 항목 | 근거 |
|---|---|---|
| 사용성(지난 대화) | `--json`이 불리언(전체 덤프)이 아니라 `gh`처럼 `--json field1,field2` 필드선택 방식이어야 함 | `cmd/output.go`의 `printJSON()` |
| 사용성(지난 대화) | `-L/--limit` 페이지네이션 플래그 없음 | `cmd/issue.go`/`cmd/pr.go`/`cmd/project.go`의 list 계열 |
| 사용성(지난 대화) | `--web` 플래그(브라우저로 열기) 없음 | 전 명령 공통 |
| 사용성(지난 대화) | `pr create`가 `--from-project-id`(숫자 ID를 `project view`로 미리 조회해야 함)를 요구 — `gh pr create`는 현재 git 체크아웃의 브랜치만으로 동작 | `cmd/pr.go:131` |
| 사용성(지난 대화) | `issue list`/`pr list`에 `--assignee`/`--label`/`--author` 등 필터 부족(`--state`만 있음) | `cmd/issue.go`, `internal/api/issue.go:56` |
| 사용성(지난 대화) | `--repo` 로컬 git 컨텍스트 자동감지 없음(매번 명시 필수) — yuna clone URL은 `http://{owner}@호스트/{owner}/{project}`(`TemplateHelper.getCloneUrl`)라 `github.com/owner/repo` 파싱과 동일한 방식(호스트 뒤 마지막 두 경로 세그먼트)으로 `git remote get-url origin`을 파싱하면 됨 | `cmd/output.go`의 `parseRepo()` 호출부 전체 |
| 사용성(지난 대화) | 멀티 서버 전환 커맨드 없음(`config.go`에 `Hosts`+`CurrentHost`는 이미 있으나 전환 커맨드가 없음) — `gh auth switch`에 대응하되, yuna는 자체호스팅이라 회사/개인마다 완전히 다른 인스턴스를 오갈 일이 `gh`보다 많아 `auth`가 아닌 별도 **`yona server list/use <호스트>`** 커맨드로 신설 | `internal/config/config.go`(`Hosts map[string]Host`, `CurrentHost`) |
| `gh repo fork` | `yona project fork` 없음 — 서버에 fork 기능 존재(`ProjectViewController.fork()`/`doClone()`, 그룹11 #172) | yuna `web/ProjectViewController.kt` |
| `gh repo create` | `yona project create` 없음 — 서버에 프로젝트 생성 존재(`ProjectController`) | yuna `web/ProjectController.kt` |
| `gh repo clone` | `yona project clone <owner/project>` 없음 — clone URL 계산(`getCloneUrl`)만 있으면 `git clone`을 그대로 실행시키는 얇은 래퍼로 충분 | yuna `TemplateHelper.getCloneUrl()` |
| `gh repo edit` | `yona project edit`(개요/공개범위 등 설정 변경) 없음 — 서버에 설정 변경 API 존재(project/setting 화면) | yuna `web/ProjectController.kt`/`ProjectViewController.kt` |
| `gh repo delete`/`archive` | `yona project delete` 없음 — 서버에 삭제 기능 존재(그룹6 #101~106, TEMPLATE_BACKLOG 완료 항목) | yuna project 삭제 관련 컨트롤러 |
| `gh label list/create/edit/delete` | `yona label` 커맨드 자체가 없음 — 서버에 라벨 관리 존재 | yuna `web/LabelController.kt` |
| `gh issue edit` | 이슈 제목/본문/라벨/담당자 수정 커맨드 없음(`create`/`comment`/`close`만 있음) — REST API는 이미 PATCH 지원(Step4) | yuna `web/IssueRestApiController.kt`(PATCH `/{number}`) |
| `gh issue reopen` | 없음(`close`만 있음) — 서버는 상태 변경 API가 이미 양방향 지원 | yuna `IssueController.changeState()` |
| `gh issue transfer` | 이슈를 다른 프로젝트로 이동하는 커맨드 없음 — 서버 기능 존재(`IssueService.moveIssue`, 이전 세션에서 죽은 UI였다가 복구된 P1-66) | yuna `domain/issue/IssueService.kt` |
| `gh issue status` | "내게 배정/멘션/내가 만든" 이슈 개요 커맨드 없음 — 서버엔 사용자 대시보드는 있으나 REST API 미노출(신규 API 필요할 수 있음, 범위 큼) | yuna `web/UserViewController.kt` 대시보드 |
| `gh pr edit` | PR 제목/본문 수정 커맨드 없음 — REST API 자체도 PATCH 없음(Step5가 list/create/get/merge/reviewers만 구현) → **서버 API도 함께 추가 필요** | `docs/yona-wiki/plans/p3-02-cli-and-rest-api.md` 완료 로그 2라운드 |
| `gh pr close`/`reopen` | 없음 — 서버에 대응 상태 변경 존재할 가능성 높음(`PullRequestController` 확인 필요) | yuna `web/PullRequestController.kt` |
| `gh pr checkout` | PR 브랜치를 로컬로 체크아웃하는 커맨드 없음 — yuna PR은 GitHub의 `refs/pull/N/head` 같은 특수 ref가 아니라 **실제 `fromProject`/`fromBranch`(평범한 브랜치)**라 `git fetch <fromProject 클론URL> <fromBranch>`로 구현 가능(오히려 `gh`보다 단순) | yuna PR 모델(`fromProject`/`fromBranch` 필드) |
| `gh pr diff` | PR 변경사항 diff 출력 커맨드 없음 — 서버에 코드 비교 기능 존재 | yuna `web/CodeController.kt`/`CompareViewController.kt` |
| `gh pr comment` | PR에 댓글 다는 커맨드 없음(`issue comment`만 있음) — 서버는 이슈/PR 댓글 API 공유 가능성 높음 | yuna `web/CommentController.kt` |
| `gh search issues/prs/repos` | `yona search` 커맨드 자체가 없음 — 서버에 통합검색 기능 존재 | yuna `web/SearchController.kt` |
| `gh org` | `yona org`(조직 목록/조회) 없음 — 서버에 조직 기능 존재, 다른 P3 계획에 배정 안 됨 | yuna `web/OrganizationController.kt`/`OrganizationViewController.kt` |
| `gh browse` | 브라우저로 현재 프로젝트/이슈/PR 열기 — 서버 API 불필요(URL 계산만), CLI 로컬 기능으로 바로 추가 가능 | 없음(순수 CLI 기능) |
| `gh completion` | 쉘 자동완성 서브커맨드 없음 — Cobra가 기본 제공하는 기능이라 `root.go`에 등록만 하면 됨(구현 비용 거의 0) | `cmd/root.go`(`NewRootCmd()`에 미등록) |
| `--version`/`gh version` | 버전 출력 플래그/커맨드 없음 | `cmd/root.go` |
| `gh config` | 에디터/페이저 등 CLI 로컬 설정 커맨드 없음 — 우선순위 낮음(nice-to-have) | 없음(순수 CLI 기능) |
| `gh alias` | 사용자 정의 별칭 커맨드 없음 — 우선순위 낮음(nice-to-have) | 없음(순수 CLI 기능) |

#### (B) yuna에 대응 기능은 있으나 다른 P3 계획 범위

| gh 명령군 | 배정된 계획 |
|---|---|
| `gh ssh-key`/`gh gpg-key`(SSH 인증, GPG 서명 검증) | [[p3-03-ssh-gpg]] |
| `gh workflow`/`gh run`/`gh cache`(Actions 워크플로/실행/캐시) | [[p3-05-ci-actions-runner]] |
| `gh secret`/`gh variable`(Actions 시크릿/변수) | [[p3-05-ci-actions-runner]](Actions 부속 기능) |
| `gh ruleset`(브랜치 보호 규칙) | [[p3-04-branch-protection]] |
| `gh attestation`(커밋/아티팩트 서명 검증) | [[p3-03-ssh-gpg]](GPG 서명 검증과 개념 겹침) |

#### (C) yuna에 대응 개념 없어 적용 불가

| gh 명령군 | 사유 |
|---|---|
| `gh gist` | yuna에 gist/스니펫 개념 없음 |
| `gh codespace` | 클라우드 개발 환경 개념 없음(자체호스팅 서버일 뿐) |
| `gh project`(Projects 칸반보드, "저장소"와 별개 개념) | yuna의 "Project"는 저장소 자체를 가리켜 이름이 겹칠 뿐, GitHub Projects(칸반보드) 같은 별도 기능 없음 |
| `gh release` | yuna에 릴리즈/태그 배포 개념 없음(전수 확인 — `Release`/`ReleaseController` 0건) |
| `gh extension` | CLI 플러그인 생태계 — yona-cli 규모상 시기상조, 서버와 무관 |
| `gh sponsors`류 | GitHub 특유 기능, yuna에 대응 없음 |

`gh watch`(저장소 알림 구독)에 가장 가까운 yuna 기능은 `WatchController`/`WatchService`이지만, `gh` 자체엔
"watch" 최상위 명령이 없어(웹 UI 전용 기능) 대조 대상에서 제외했다 — 필요하면 (A)에 추가 검토 가능.

이 표의 (A) 항목은 아래 "단계별 작업 계획"의 신규 **Step 8.5**로 구체화했다. 서버 쪽 신규 API가 필요한 항목
(`gh pr edit`용 PATCH, `gh issue status`용 대시보드 API)은 별도로 표시해뒀다 — CLI 클라이언트 코드만으로
안 끝나고 yuna 서버(1부)에도 손을 대야 한다.

## 단계별 작업 계획 (TDD)

### 1부 — REST API + 토큰 모델

1. **Step 1 — `ApiToken` 엔티티 + 리포지토리**
   - 실패 테스트: 토큰 생성 시 `expires_at`이 null이면 저장 거부 → RED → 엔티티/제약 구현 → GREEN
2. **Step 2 — `ApiTokenScopeGroup` 매핑 + 스코프 판정 로직**
   - 실패 테스트: `ResourceType.ISSUE_POST` 요청이 `issues` 그룹 write 권한 없이 거부되는지 → RED → 구현 → GREEN
3. **Step 3 — `ApiTokenAuthenticationFilter` 재작성**
   - 기존 "전권 부여" 동작을 검증하는 회귀 테스트를 먼저 스코프 기반으로 갱신(RED) → 인가 체크 구현 → GREEN
   - 기존 전권 토큰 사용자에 대한 마이그레이션 경로 결정(예: 마이그레이션 시 "전체 저장소 + 전체 리소스 write"로 자동 발급) 필요
4. **Step 4 — 이슈 REST API**(`GET/POST /api/v1/projects/{owner}/{project}/issues`, 개별 조회/수정/코멘트/클로즈)
   - 각 엔드포인트마다: 실패 테스트(권한 없는 토큰으로 403) → RED → 컨트롤러/서비스 구현 → GREEN
5. **Step 5 — PR REST API**(목록/생성/조회/머지/리뷰)
   - Step 4와 동일 패턴, 기존 `PullRequestServiceImpl.kt` 재사용(신규 서비스 로직 최소화, 얇은 컨트롤러 레이어만 추가)
6. **Step 6 — 프로젝트 조회 REST API**(목록/조회)
7. **Step 6.5 — Step6 스코프 패턴 갭 해소**: 위 "`metadata` 스코프 세그먼트 설계" + "프로젝트 목록 API
   스코프 필터링 설계"를 구현. 실패 테스트 먼저(그룹 권한 없이도 metadata 조회 가능/선택스코프 토큰이
   scopedProjects 밖 프로젝트는 목록에서 제외됨 등) → RED → 구현 → GREEN.
8. **Step 6.6 — Fine-grained 토큰 발급/관리 웹 UI**: 위 "토큰 발급/관리 웹 UI 설계" 구현
   (`ApiToken.name` 필드 + `ApiTokenService` + 컨트롤러 + 목록/발급/폐기 화면). Step6.5로 실제 발급된
   토큰을 조회 API에도 바로 써볼 수 있게 됨.

### 2부 — Go CLI

**CLI 로그인 토큰의 기본 스코프(2026-08-28 결정, 사용자 지적으로 명확화)**: `yona auth login`은
사용자 본인이 본인 계정으로 CLI에 로그인하는 것이라, 웹 세션 로그인과 동등하게 **본인이 가진 전체
권한**(전체 저장소 + 모든 스코프 그룹 write)을 받아야 한다 — Fine-grained 스코프 제한(Step 1~3의
`ApiToken` 모델)은 로그인의 기본 동작이 아니라, "이 토큰을 CI/봇/서드파티 연동에 넘길 때 유출 피해를
줄이기 위해 사용자가 별도로(웹 UI 등에서) 명시적으로 선택하는" 별개 발급 기능이다(GitHub도 동일 —
`gh auth login`은 기본적으로 broad 스코프를 받고, 세분화된 Fine-grained PAT는 Settings에서 별도
발급). 즉 Step 7 구현 시 `yona auth login`이 발급/저장하는 토큰은 기본적으로 "전체 저장소 + 전체
스코프 write"로 만들어야 하며, 제한된 토큰을 쓰고 싶으면 사용자가 별도 발급 후 그 값을 대신
입력하는 경로(`yona auth login --token <제한된 토큰>` 같은 옵션)로 지원한다.

7. **Step 7 — CLI 스캐폴딩**: `yona auth login/logout/status`(Personal Access Token 입력·저장, 위 기본 스코프 원칙 적용)
8. **Step 8 — `yona issue`/`yona pr`/`yona project` 하위 명령**: 1부 API를 감싸는 얇은 HTTP 클라이언트
8.5. **Step 8.5 — `gh` 명령 체계 정합화**: 위 "CLI `gh` 명령 체계 대조 감사"의 (A) 항목 구현.
   - **CLI 로컬 기능만으로 되는 것**(서버 변경 불필요): `--json <fields>` 필드선택 전환, `-L/--limit`,
     `--web`, `--repo` 로컬 git 컨텍스트 자동감지, `yona server list/use`, `yona browse`,
     `yona completion`(Cobra 기본 제공 등록만), `--version`
   - **기존 서버 API 연결만 하면 되는 것**: `yona project fork/create/edit/delete`, `yona label
     list/create/edit/delete`, `yona issue edit/reopen/transfer`, `yona pr checkout/diff/comment`,
     `yona search issues/prs/projects`, `yona org list/view`, `issue/pr list`의 `--assignee`/
     `--label`/`--author` 필터
   - **서버(1부)에 신규 API가 필요한 것**: `gh pr edit` 대응(PR 제목/본문 PATCH — Step5에 없음),
     `gh issue status` 대응(사용자 대시보드 REST API — 범위 클 수 있어 별도 하위 스텝으로 쪼갤 수 있음),
     `gh pr close/reopen`(서버에 대응 상태변경 API 존재 여부 착수 전 확인 필요)
   - **낮은 우선순위**(nice-to-have, 다음 라운드 이후로 미뤄도 무방): `yona config`, `yona alias`
9. **Step 9 — `yona admin backup/webhook/permission`**: 기존 관리자 API 확인 후 연결(신규 서버 API 필요 시 1부 패턴으로 추가)
10. **Step 10 — `yona api <method> <path>`**: 저수준 원시 호출 명령(디버깅/스크립팅용)
11. **Step 11 — 배포**: `goreleaser`로 GitHub Releases + Homebrew tap + Scoop bucket + `.deb`/`.rpm`

## 완료 기준 (Definition of Done)

- [x] `ApiToken` 엔티티가 `expires_at` 필수로 강제됨을 테스트로 보장 (1라운드 — Step 1)
- [x] `ApiTokenAuthenticationFilter`가 스코프 밖 요청을 403으로 거부함을 테스트로 보장 (1라운드 — Step 3, 신규 `/api/v1/projects/...` 네임스페이스 한정)
- [x] 이슈/PR/프로젝트 REST API가 CRUD 전체를 커버하고, 각 엔드포인트에 권한 스코프 검증 테스트 존재 (2라운드 — Step 4~6, 단 프로젝트 조회 API는 스코프 토큰이 아닌 AccessControl 기반 검증 — 아래 로그/리스크 표 참고)
- [x] 프로젝트 조회/목록 API가 Fine-grained 스코프 토큰으로 완전히 동작함 (3라운드 — Step 6.5)
- [x] Fine-grained 토큰을 웹 UI에서 발급/조회/폐기할 수 있음 (3라운드 — Step 6.6)
- [x] Go CLI 본체(`yona auth/issue/pr/project/admin/api`)가 1부 REST API를 감싸는 형태로 구현됨 (4라운드 — Step7~10, 별도 저장소 `yona-cli`)
- [ ] yona-cli가 `gh` 명령 체계 대조 감사((A) 항목)를 반영해 사용법이 실제로 `gh`에 준함 (Step 8.5, 다음 라운드 — 위 "CLI `gh` 명령 체계 대조 감사" 참고)
- [ ] Go CLI로 로그인 → 이슈 생성 → PR 목록 조회 골든 패스가 수동 검증 완료 (다음 라운드로 이월 — 4라운드는 httptest 기반 단위/통합 테스트만 수행)
- [ ] `goreleaser` 배포(Step 11: GitHub Releases/Homebrew/Scoop/`.deb`/`.rpm`) (다음 라운드)
- [ ] `./gradlew test` 전체 GREEN, JaCoCo 95%/95%/95% 유지(`docs/COVERAGE_BACKLOG.md` 기준) (전체 계획 완료 후 검증)

## 완료 로그

### 1라운드 (2026-08-28) — Part 1 Step 1~3

- **Step 1**: `domain/apitoken/ApiToken.kt`(+`ApiTokenScope.kt`) 엔티티, `ApiTokenRepository.kt` 신설.
  `expiresAt: Instant?` + `@Column(nullable = false)` 조합으로(Webhook.project 등 기존 엔티티와
  동일한 컨벤션) DB NOT NULL 제약이 null 저장을 거부하게 했다. repo scope는
  `allRepositories: Boolean` + `scopedProjects`(ManyToMany 조인테이블, User.enrolledProjects와
  동일 패턴) 조합으로, resource scope는 `ApiTokenScope`(ApiToken 1:N, ProjectUser가 (project,
  user)에 role을 얹는 것과 동일한 패턴)로 구현 — 조인테이블만으로는 그룹별 permission 값을 담을
  수 없어 별도 엔티티가 필요했다. 검증: `domain/apitoken/ApiTokenSpec.kt`(통합테스트).
- **Step 2**: `domain/apitoken/ApiTokenScopeGroup.kt`(8개 그룹: ISSUES/PULL_REQUESTS/CODE/BOARD/
  WIKI/WEBHOOKS/ADMINISTRATION/USERS)로 기존 `ResourceType` 33종을 전수 매핑(`NOT_A_RESOURCE`만
  미매핑=null, 항상 거부). 판정 로직은 `domain/apitoken/ApiTokenAuthorizer.kt`(순수 object,
  Spring 빈 아님 — DB/HTTP 의존이 없어 단위테스트만으로 충분). 검증:
  `ApiTokenScopeGroupSpec.kt`(매핑 전수 검증) + `ApiTokenAuthorizerSpec.kt`(만료/권한크기비교/
  repo scope 조합 단위테스트).
- **Step 3**: `config/ApiTokenAuthenticationFilter.kt` 재작성 — 신규 `/api/v1/projects/{owner}/
  {project}/{resource}` 네임스페이스(Step4~6에서 실제 컨트롤러가 채워질 예정, 이번 라운드는 필터
  로직만 선행 구현)로 오는 요청만 `ApiTokenRepository` 기반 스코프 판정(`ApiTokenAuthorizer`)을
  거치고, 그 외 기존 URL은 기존 `UserRepository.findByToken` 전권 부여 경로를 그대로 유지했다
  (아래 "기존 전권 토큰 마이그레이션" 참고, 의도적 설계). 필터 생성자에 `ApiTokenRepository`/
  `ProjectRepository`가 추가돼 `ApiTokenAuthenticationFilterSpec.kt`(기존 유닛테스트, 모두
  legacy 경로만 태워 그대로 통과)의 직접 생성 호출부를 함께 수정했다. 신규 검증:
  `config/ApiTokenScopedAuthorizationIntegrationSpec.kt`(MockMvc 통합테스트 — write 스코프 없음/
  스코프 자체 없음/다른 repo 스코프/만료 토큰 4가지 403 케이스 + write 스코프 있을 때 필터
  통과 1케이스).
- **범위 제외(그대로 유지)**: Step 4~6(이슈/PR/프로젝트 REST API 컨트롤러)과 Part 2(Go CLI) 전체는
  이번 라운드에서 전혀 손대지 않았다. `/api/v1/projects/...` URL 세그먼트(`issues`/`pull-requests`/
  `code`/`board`/`wiki`/`webhooks`/`settings`)와 리소스 매핑은 이번 라운드에서 필터 구현을 위해
  잠정 확정했지만, Step 4~6 착수 시 실제 컨트롤러 라우팅과 다시 대조 확인이 필요하다.

### 2라운드 (2026-08-29) — Part 1 Step 4~6

- **공통 설계 결정**: 세 Step 모두 신규 서비스 로직을 만들지 않고, 기존 숫자 `projectId` 기반
  웹 컨트롤러(`IssueController.kt`/`PullRequestController.kt`, `/api/projects/{projectId}/...`)가
  이미 `IssueService`/`PullRequestService`/`AccessControl`을 완비된 형태로 재사용하고 있음을 확인하고,
  새 `owner`/`project` 이름 기반 컨트롤러가 이름을 숫자 `projectId`로 바꿔 그 컨트롤러의 공개 메서드에
  그대로 위임하는 얇은 어댑터로 구현했다(계획이 요구한 "컨트롤러는 얇게" 원칙을 서비스 계층뿐 아니라
  기존 컨트롤러 계층까지 재사용해 더 철저히 지킴). 클래스명은 기존 파일과의 충돌을 피해 지었다 —
  `IssueApiController.kt`(legacy Open API `-_-api/v1/owners/...` 전용)와 `ProjectApiController.kt`
  (export/import 전용)가 이미 그 이름을 쓰고 있어 각각 `IssueRestApiController`/
  `ProjectRestApiController`로, PR은 이름 충돌이 없어 계획 문서가 제안한 `PullRequestApiController`를
  그대로 썼다.
- **Step 4 — 이슈 REST API**: `web/IssueRestApiController.kt`
  (`/api/v1/projects/{owner}/{project}/issues`) 신설 — 목록(GET)/생성(POST)/개별조회(GET
  `/{number}`)/수정(PATCH `/{number}`)/댓글(POST `/{number}/comments`, `CommentController` 위임)/
  클로즈(POST `/{number}/close`, `IssueController.changeState(state=CLOSED)` 위임)를 구현. 수정은
  `IssueController`의 웹용 대응 메서드가 PUT이지만 이 신규 API는 부분 수정 의미가 강해 PATCH로
  했다(요청 필드는 동일한 `UpdateIssueRequest` 재사용). 검증: `web/IssueRestApiControllerSpec.kt`
  (MockMvc standalone + mockk로 위임/404 검증, 6개 describe) +
  `config/ApiTokenScopedIssueAndPullRequestSubpathAuthorizationIntegrationSpec.kt`(comments/close
  하위 경로가 필터의 `scopedApiPattern` 접미부(`(?:/.*)?`)에 여전히 매칭돼 ISSUES 스코프로
  인가되는지 검증 — write 권한 없음 403 + write 권한 있으면 필터 통과(not 403) 각 2케이스).
- **Step 5 — PR REST API**: `web/PullRequestApiController.kt`
  (`/api/v1/projects/{owner}/{project}/pull-requests`) 신설 — 목록/생성/개별조회/머지(POST
  `/{number}/merge`)/리뷰(POST `/{number}/reviewers`, `PullRequestService.addReviewer` 위임)를
  구현. 계획 원문의 "리뷰"는 `PullRequestService`가 제공하는 단위(리뷰어 등록/해제)로 해석했다 —
  코드 라인 단위 리뷰 코멘트(`ReviewComment`/`CommentThread`)는 기존 `ReviewApiController`가 별도로
  다루는 영역이라 이 범용 REST API의 범위 밖으로 뒀다. 검증:
  `web/PullRequestApiControllerSpec.kt`(5개 describe) + 위 서브패스 통합테스트의 merge/reviewers
  케이스(PR은 필터 통과 후 컨트롤러의 `checkWritePermission`이 PR 조회보다 먼저 실행되므로, 통합
  테스트의 토큰 소유자를 프로젝트 멤버로 등록해 필터 통과 여부와 컨트롤러 자체 권한체크가 뒤섞이지
  않게 했다).
- **Step 6 — 프로젝트 조회 REST API**: `web/ProjectRestApiController.kt` 신설 — 목록(GET
  `/api/v1/projects/{owner}`)/조회(GET `/api/v1/projects/{owner}/{project}`)를 구현. 신규 서비스
  로직 없이 `ProjectRepository` + 기존 `AccessControl.isAllowedToReadProject()`(웹 UI와 동일한
  공개/비공개 가시성 규칙)만으로 구성. **설계상 알려진 한계(아래 리스크 표에 신규 행 추가)**: 이
  두 엔드포인트는 URL에 리소스 세그먼트가 없어(목록은 owner 1단, 조회는 owner/project 2단)
  `ApiTokenAuthenticationFilter`의 `scopedApiPattern`(owner/project/resource 3단 필수)과 매칭되지
  않는다 — 즉 Fine-grained 스코프 토큰으로는 아직 호출할 수 없고 세션 로그인/기존 전권 토큰으로만
  가능하다. Step1~3에서 이미 완성된 필터/스코프 판정 로직(건드리지 말 것으로 지정됨)을 변경해야
  해소되는 문제라 이번 라운드에서 임의로 결정하지 않고 문서화만 했다(대안: `settings` 세그먼트
  재사용, 신규 `info`/`metadata` 세그먼트 추가, 또는 필터 정규식의 3번째 세그먼트를 선택적으로
  완화). 이에 맞춰 검증 방식도 스코프 토큰 403 패턴 대신 AccessControl 기반 익명/비멤버 403·404로
  대체했다: `web/ProjectRestApiControllerSpec.kt`(공개 프로젝트 익명 조회 허용, 비공개 프로젝트
  비멤버 403, 미존재 프로젝트 404 등 4개 describe).
- **`SecurityConfig.kt` 점검 결과 및 변경**: `ApiTokenAuthenticationFilter`는 스코프 토큰이 없거나
  알 수 없는 토큰이면 인증 없이 통과시키고 컨트롤러의 401/403 처리에 위임하는데, 이 앱의 기존
  컨트롤러(`ProjectApiController`/`MigrationApiController`/`IssueController`/`PullRequestController`/
  `CommentController`)가 전부 이미 이 컨벤션(`anyRequest().permitAll()` + 컨트롤러 자체 인증/인가
  체크)을 쓰고 있어 그 자체로는 구멍이 아님을 확인했다(공개 프로젝트 익명 읽기 허용도 이 컨벤션의
  일부). 다만 신규 네임스페이스의 쓰기 경로만큼은 프레임워크 레벨 방어선을 하나 더 두는 게 안전하다고
  판단해 `.requestMatchers(HttpMethod.GET, "/api/v1/projects/**").permitAll()` +
  `.requestMatchers("/api/v1/projects/**").authenticated()`를 `anyRequest().permitAll()` 앞에
  추가했다(GET은 기존 공개 프로젝트 익명 조회 컨벤션을 깨지 않기 위해 제외).
- **전체 스위트**: `./gradlew test` 전체 GREEN(회귀 없음) — 상세 수치는 이 라운드의 커밋 메시지 참고.

### 3라운드 (2026-08-29) — Part 1 Step 6.5~6.6

2라운드가 남긴 두 갭(프로젝트 조회/목록 API의 스코프 패턴 불일치, 토큰 발급/관리 UI 부재)을
`fbeb589`(계획 문서에 이미 확정된 설계) 그대로 구현했다. 둘 다 설계 문서를 그대로 따랐고, 설계를
벗어난 판단이 필요했던 지점은 "metadata 세그먼트를 URL에 실제로 넣을지" 하나뿐(아래 Step 6.5 참고).

- **Step 6.5 — 스코프 패턴 갭 해소**:
  - `domain/apitoken/ApiTokenAuthorizer.kt`: `isAuthorized()`의 `resourceType` 파라미터를
    `ResourceType?`로 바꾸고, null이면(= "metadata" 스코프) 그룹/권한 매트릭스를 전혀 보지 않고
    만료 여부 + repo scope 일치 여부만으로 판정하도록 분리(`isProjectInRepoScope()` 추출). 기존
    호출부(비-null 인자)는 전부 그대로 통과 — 회귀 없음.
  - `config/ApiTokenAuthenticationFilter.kt`:
    - **설계 문서와 실제 URL 구조 사이의 유일한 판단 지점**: 설계는 "resourceSegmentToResourceType에
      metadata to null을 추가하고, 실제 URL은 바꾸지 않는다"고 했는데, 기존 `scopedApiPattern`은
      3세그먼트(owner/project/resource)를 **문자 그대로** 요구해 개별 프로젝트 조회
      (`/api/v1/projects/{owner}/{project}`, 2세그먼트)와 애초에 매칭될 수 없다 — "metadata"라는
      문자열이 URL 어디에도 없기 때문이다. 그래서 신규 `individualProjectPattern`(정확히
      2세그먼트)을 별도로 두고, 이 패턴에 매칭되면 `resourceSegmentToResourceType.getValue("metadata")`
      (= null)를 대입하는 방식으로 "URL은 안 바꾸면서 개념적으로 metadata 스코프를 쓴다"는 설계
      의도를 그대로 지켰다. `resourceSegmentToResourceType`는 `Map<String, ResourceType?>`로 바뀌었고
      조회는 전부 `containsKey` 기반(설계가 지적한 "map[key] ?: return null이 키 없음/값 null을
      구분 못 하는" 문제를 그대로 회피).
    - 목록(`/api/v1/projects/{owner}`, 1세그먼트) 전용 `ownerOnlyPattern` + `authenticateScopedList()`
      신설 — 403을 내지 않고 `setAuthenticatedIdentity()`(기존 `authenticateScoped()`의 신원 설정
      로직을 추출해 공유)로 SecurityContext만 세팅한 뒤, 인증된 `ApiToken`을 request attribute
      `SCOPED_API_TOKEN_ATTRIBUTE`("SCOPED_API_TOKEN")로 다운스트림에 넘긴다. 3세그먼트 경로
      (`authenticateScoped()`)에도 같은 attribute를 세팅해 재사용 가능하게 했다(설계 문서 그대로).
  - `web/ProjectRestApiController.kt`: `list()`가 request attribute로 넘어온 `ApiToken`을 읽어
    `scopedToken == null`(세션/레거시/비로그인 — 기존 동작 100% 유지) / `allRepositories`(전체
    반환) / 선택 스코프(`scopedProjects` 교집합) 3분기로 필터링(설계 문서의 `when` 블록 그대로).
    `get()`(개별 조회)은 컨트롤러 변경이 필요 없었다 — 필터가 이미 스코프 밖이면 403으로 막아준다.
  - 검증: `domain/apitoken/ApiTokenAuthorizerSpec.kt`에 metadata(resourceType=null) 3케이스 추가,
    신규 `config/ApiTokenScopedMetadataAndListAuthorizationIntegrationSpec.kt`(실제 시큐리티 필터
    체인을 태운 MockMvc, ApiTokenScopedAuthorizationIntegrationSpec과 동일 패턴) 5케이스(개별 조회
    허용/거부 각 1 + 목록 전체스코프/선택스코프/세션로그인 각 1), `web/ProjectRestApiControllerSpec.kt`
    에 목록 필터링 3케이스(속성 없음/전체스코프/선택스코프) 추가. 신규 8 + 기존 파일에 추가된 6 =
    총 14개 테스트로 검증.
- **Step 6.6 — Fine-grained 토큰 발급/관리 웹 UI**: 레거시 전권 토큰 화면(`user/edit_token.html`,
  `/user/editform/token_reset`)은 전혀 손대지 않았다(계획 지시대로 완전히 별개 화면으로 신설).
  - `domain/apitoken/ApiToken.kt`: `name: String` 필드 추가(owner/tokenHash 옆, `nullable = false`).
    기존 테스트가 전부 named argument로 `ApiToken(...)`을 생성해 위치 무관하게 안전함을 사전 확인.
  - `domain/apitoken/ApiTokenRepository.kt`: `findByOwner(owner): List<ApiToken>` 추가(scopes/
    scopedProjects까지 JOIN FETCH — 목록 화면이 권한 뱃지/저장소 범위 요약을 렌더링해야 하므로
    `findByTokenHash()`와 동일한 이유로 즉시 로딩).
  - 신규 `domain/apitoken/ApiTokenService.kt`(인터페이스, `IssuedApiToken` DTO 포함) +
    `ApiTokenServiceImpl.kt` — `issue()`는 `LdapUserProvisioningService.generateSalt()`와 동일한
    `SecureRandom` 패턴으로 원문 토큰을 만들고 `ApiTokenHasher.hashApiToken()`으로 해시만 저장,
    이름 공백 검증 + **만료일 366일 상한 검증**(갭 분석 4번 "만료일 상한 없음"을 이번에 함께 해소 —
    설계 문서에 명시된 항목). `revoke()`는 owner 소유가 아니면 조용히 무시(존재 여부 비노출).
  - `web/UserController.kt`(기존 레거시 전권 토큰 `@RestController`)는 손대지 않고, 계획이 제시한
    대안대로 기존 `web/UserViewController.kt`(`@Controller`, 세션 기반 `/user/editform/*` 화면
    전체를 담당)를 확장했다 — `GET /user/editform/tokens`(목록+발급폼), `POST
    /user/editform/tokens`(발급, 권한 매트릭스는 `scope_<GROUP_NAME>` 파라미터로 그룹 수만큼 수신),
    `POST /user/editform/tokens/{id}/revoke`(폐기, `/user/editform/tokens`로 리다이렉트).
  - `user/partial_edit_tabmenu.html`에 "API 토큰(세분화)" 탭 추가, 신규 `user/edit_tokens.html`
    (목록 테이블 + 저장소범위 라디오/select2 다중선택 + 8개 그룹×3단 권한 라디오 매트릭스 + 만료일
    프리셋 + 발급 직후 "지금 한 번만 표시됩니다" 배너). **설계에서 벗어난 지점 하나**: 폐기
    confirm을 `common/commentDeleteModal.html`류 모달 대신 네이티브 `confirm()` + 이벤트 위임
    스크립트로 구현했다(`th:onsubmit`에 `#{message}`를 문자열 결합해 넣는 방식이 따옴표 이스케이프로
    깨지기 쉬워, `data-confirm-message` attribute + 전역 `submit` 리스너로 대체) — 기능적으로는
    "폐기 전 확인" 요구사항을 동일하게 충족한다.
  - i18n 메시지 키 `apitoken.*`/`userinfo.tokens`/`button.copy`를 `messages.properties`,
    `messages_ko_KR.properties`에 추가(다른 로케일 파일은 Thymeleaf가 기본 `messages.properties`로
    폴백하므로 추가하지 않음).
  - 검증: 신규 `domain/apitoken/ApiTokenServiceImplSpec.kt`(발급/스코프 저장/이름공백거부/
    366일상한거부/목록조회/폐기소유권검증 7케이스), `web/UserViewControllerSpec.kt`에 3개
    엔드포인트의 미인증/성공/실패 분기 7케이스 추가, 신규
    `web/ApiTokenEditFormTemplateRenderingSpec.kt`(webAppContextSetup + 실제 시큐리티로 Thymeleaf
    렌더링까지 확인 — standaloneSetup MockMvc는 실제 뷰 리졸버를 안 태워 템플릿 문법 오류를 못
    잡으므로 `PostingHistoryTemplateRenderingSpec` 패턴을 그대로 따름) 3케이스(발급폼 렌더링/발급
    직후 배너+목록 반영/폐기 후 목록에서 제거). 총 17개 테스트로 검증.
- **전체 스위트**: `./gradlew test` 전체 GREEN — 유일한 실패는 2라운드에도 있었던 사전 존재 이슈
  (`ApiTokenSpec.kt`, MariaDB 컬럼 타임스탬프 마이크로초 절삭으로 인한 `Instant` 나노초 정밀도
  불일치, 이번 라운드가 손대지 않은 Step1 코드)뿐 — 회귀 아님.

### 4라운드 (2026-08-30) — Part2 Step7~10 (Go CLI)

yuna와 완전히 별개인 새 git 저장소 `~/yona-convert/yona-cli`(`github.com/search5/yona-cli`
모듈)에 Go 1.26 + Cobra 1.10 스택으로 CLI 본체를 구현했다. 배포(Step 11)는 이번 라운드
범위 밖이라 손대지 않았다. 커밋 3개로 진행: 설정/HTTP 클라이언트 코어 → REST API 클라이언트
→ Cobra 명령 트리. Go 표준 `testing` + `stretchr/testify` + `net/http/httptest`로 TDD,
실제 yuna 서버 없이 63개 테스트 전체 GREEN(`go test ./...`), `go build ./...`/`gofmt -l .`/
`go vet ./...` 클린.

- **Step 7 — CLI 스캐폴딩**: `internal/config`(`~/.config/yona-cli/config.yml`, gh의
  `hosts.yml` 패턴 참고 — 서버 URL을 키로 삼아 여러 호스트의 토큰을 동시에 보관) +
  `yona auth login/logout/status`. "CLI 로그인 토큰의 기본 스코프"(위 2026-08-28 결정) 그대로,
  yuna 서버엔 OAuth 유사 로그인 플로우가 없어 **로그인 자체가 아니라 "이미 발급받은 토큰 값을
  CLI에 알려주는" `gh auth login --with-token`류 흐름으로 구현했다** — 스코프가 얼마나
  넓은지는 사용자가 웹 UI에서 어떤 토큰(레거시 전권 토큰 또는 전체 스코프 Fine-grained 토큰)을
  발급해 붙여넣는지에 달려 있고, CLI는 그 값을 그대로 저장할 뿐 스코프를 계산하지 않는다.
  `--token`으로 제한된 토큰을 그때그때 넘기는 경로도 함께 구현(설정 파일에 저장하지 않음).
  서버/토큰 결정 순서는 `--server`/`--token` 플래그 > `YONA_HOST`/`YONA_TOKEN` 환경변수 >
  설정 파일.
- **Step 8 — `yona issue`/`yona pr`/`yona project`**: `internal/api`에 2라운드가 만든 REST API
  엔드포인트를 그대로 감싸는 얇은 클라이언트를 구현. **응답 파싱 설계 결정**: `ProjectRestApiController`
  응답(`web/ProjectRestApiController.kt`의 `toProjectNode()`)은 컨트롤러가 직접 조립한 맵이라
  필드가 안정적이어서 타입 있는 `Project` 구조체로 받았지만, Issue/PullRequest 응답은 JPA
  엔티티를 그대로 직렬화한 결과라 필드 구성이 코드 변경에 취약하다고 판단해 의도적으로
  `map[string]interface{}`로 느슨하게 받고 CLI 출력은 `number`/`title`/`state`/`body` 등
  흔한 키만 방어적으로 꺼내 쓰게 했다(모든 view/list 명령에 `--json` 플래그로 원본 그대로
  출력하는 탈출구를 남김). 요청 바디(`CreateIssueRequest`/`UpdateIssueRequest`/
  `CommentRequest`/`CreatePullRequestRequest`)는 실제 yuna Kotlin DTO 필드명을 그대로
  맞췄다(`web/IssueController.kt`/`web/CommentController.kt`/`web/PullRequestController.kt`
  Serena LSP로 직접 대조 확인). `yona pr review`는 계획 문서 원문의 "리뷰"가 실제로는
  리뷰어 지정이 아니라 인증된 본인을 리뷰어로 자기등록하는 동작임을 코드로 확인하고
  (`PullRequestController.addReviewer`) 그대로 반영했다. 프로젝트 주소 지정은 계획 문서
  예시(`yona project view <name>`)와 달리 서버 API가 owner도 요구해 gh 관례인
  `owner/project` 단일 인자(이슈/PR은 `-R/--repo` 플래그)로 통일했다 — **계획 문서 예시와
  실제 구현이 다른 지점**.
- **Step 9 — `yona admin backup/webhook/permission`**: 착수 전 `web/` 패키지를 grep +
  Serena LSP로 전수 조사한 결과:
  - **백업**: `web/SiteApiController.kt`에 `GET /site/export`(전체 DB JSON 백업 다운로드,
    `checkAdmin()`으로 사이트매니저만 허용)와 `POST /site/import`(멀티파트 업로드, 전체
    테이블 교체 복원)가 실제로 존재해 그대로 연결했다(`yona admin backup export/import`).
  - **웹훅**: `web/WebhookController.kt`에 CRUD가 있지만 **세션/폼 기반 레거시 MVC
    컨트롤러**다(`/projects/{owner}/{projectName}/webhooks`, `/api/v1` 네임스페이스 밖,
    JSON이 아닌 form-urlencoded 요청/HTML 또는 빈 응답). 생성(POST)과 삭제(DELETE)는 구조상
    CLI에서도 그대로 호출 가능해 연결했지만(`yona admin webhook create/delete`), **목록
    조회(GET)는 Thymeleaf가 렌더링한 HTML 페이지(`project/setting_webhook`)만 반환**해 CLI가
    파싱할 구조화된 데이터가 전혀 없다 — `yona admin webhook list`는 명확한 안내 메시지와
    함께 미구현 스텁으로 남겼다.
  - **권한**: `web/ProjectMemberController.kt`에 멤버 추가/역할변경/삭제가
    `/api/projects/{projectId}(숫자 ID)/members/...`로 JSON 응답(`Map<String,String>`)과 함께
    존재해 연결했다(`yona admin permission add/update-role/remove`) — 다만 이 컨트롤러가
    숫자 `projectId`를 요구해, CLI는 먼저 `GET /api/v1/projects/{owner}/{project}`로 id를
    조회한 뒤 그 값을 넘기는 2단계로 구현했다(`resolveProjectID` 헬퍼). **"현재 멤버+역할
    목록"을 내려주는 엔드포인트는 존재하지 않는다** — 가장 가까운 `assignableUsers`는 "할당
    가능한 후보" 목록이지 이미 배정된 권한 매트릭스가 아니다 — `yona admin permission list`도
    미구현 스텁으로 남겼다.
  - 위 두 "미구현" 결정은 지시사항대로 yuna 쪽에 새 API를 추가해 임의로 해소하지 않고 그대로
    보고한다 — 이 CLI 프로젝트의 범위를 넘는 서버 쪽 변경이 필요하다.
- **Step 10 — `yona api <path>`**: `gh api`와 동일한 컨셉의 원시 HTTP 호출(`-X` 메서드,
  `-f key=value` 반복으로 JSON 바디 조립, `-H`로 추가 헤더, `--input`으로 파일/표준입력을
  바디로 그대로 전달). 상태 코드 4xx/5xx는 응답 본문을 그대로 출력한 뒤 0이 아닌 종료 코드로
  끝난다.
- **Step 11(배포)은 착수하지 않음** — 계획 문서 지시대로 이번 라운드 범위 밖.
- **저장소/커밋**: `yona-cli`는 yuna와 무관한 독립 git 저장소(원격 없음, 로컬 전용)로
  커밋 3개(설정+HTTP 클라이언트 코어 / REST API 클라이언트 / Cobra 명령 트리)로 나눠
  진행했다. 상세 커밋 이력과 파일 경로는 이 작업을 지시한 세션의 최종 보고 참고.

## 리스크 / 미결정 사항

| 항목 | 내용 | 해소 방법 |
|---|---|---|
| 스코프 카테고리 확정 | `ResourceType` 33종을 어떻게 그룹핑할지 최종 미확정 | **1라운드에서 해소** — `ApiTokenScopeGroup.kt`(8개 그룹)로 전수 확정, 근거는 위 완료 로그 참고 |
| 기존 전권 토큰 마이그레이션 | 이미 발급된 `User.token` 보유자 처리 방침 미정 | **미해결(다음 라운드로 이월)** — 1라운드는 문서화만 함: 신규 `/api/v1/...` 네임스페이스는 스코프 토큰만 인증하고, 그 외 기존 URL은 레거시 전권 토큰 경로를 그대로 유지하는 co-existence로 임시 처리(`ApiTokenAuthenticationFilter.kt` 주석 참고). 자동 재발급 vs 만료 후 재발급 안내 중 무엇을 택할지, 그리고 레거시 경로를 언제 끊을지는 여전히 미정 |
| 관리자 API 존재 여부 | 백업/웹훅/권한 관리용 서버 API가 이미 있는지 미확인 | **4라운드에서 해소** — 백업(`GET /site/export`, `POST /site/import`)은 실사용 가능한 형태로 존재해 CLI에 완전히 연결. 웹훅(`web/WebhookController.kt`)·권한(`web/ProjectMemberController.kt`)은 생성/변경/삭제 API는 있지만 세션·폼 기반 레거시라 목록 조회용 JSON API가 없다(웹훅 목록은 HTML 렌더링 전용, 권한 목록은 엔드포인트 자체가 없음) — `yona admin webhook list`/`yona admin permission list`는 명확한 안내 메시지의 미구현 스텁으로 유지. 새 JSON API를 yuna에 추가하는 것은 CLI 프로젝트 범위 밖이라 이번 라운드는 시도하지 않음. 상세는 위 "4라운드" 로그 참고 |
| 프로젝트 조회 API의 스코프 패턴 불일치 | Step6의 `/api/v1/projects/{owner}`(목록)와 `/api/v1/projects/{owner}/{project}`(조회)는 리소스 세그먼트가 없어 `ApiTokenAuthenticationFilter.scopedApiPattern`(owner/project/resource 3단 필수)과 매칭되지 않는다 — Fine-grained 스코프 토큰으로 호출 불가(세션/전권 토큰만 가능), 이슈/PR API는 "issues"/"pull-requests" 세그먼트가 있어 이 문제가 없다 | **3라운드에서 해소** — 개별 조회는 `metadata` 스코프(그룹/권한 매트릭스 없이 repo scope만 확인), 목록은 request attribute(`SCOPED_API_TOKEN_ATTRIBUTE`) 기반 필터링으로 구현 완료. 상세는 아래 "3라운드" 로그 참고 |
| 토큰 발급/관리 UI 부재 | `ApiTokenRepository`엔 조회 메서드 하나뿐, 사용자가 `ApiToken`을 발급/조회/폐기할 UI·컨트롤러·서비스가 전혀 없어 실사용자는 Fine-grained 토큰을 발급받을 방법이 없다 | **3라운드에서 해소** — `ApiTokenService`/`ApiTokenServiceImpl` + `UserViewController` 확장 + `user/edit_tokens.html` 신설로 발급/조회/폐기 가능. 상세는 아래 "3라운드" 로그 참고 |

## 관련

- 백로그 원본: [`docs/PARITY_BACKLOG.md`](../../PARITY_BACKLOG.md#p3-02)
- 관련 계획: [[p3-03-ssh-gpg]], [[p3-07-mcp-server]], [[p3-05-ci-actions-runner]]
- 관련 소스: `config/ApiTokenAuthenticationFilter.kt`, `config/SecurityConfig.kt`, `domain/enumeration/ResourceType.kt`, `web/ProjectApiController.kt`, `web/IssueRestApiController.kt`, `web/PullRequestApiController.kt`, `web/ProjectRestApiController.kt`, `domain/pullrequest/PullRequestServiceImpl.kt`
