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
updated: 2026-09-01
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
   - **CLI 로컬 기능만으로 되는 것**(서버 변경 불필요, 다음 라운드 CLI 작업): `--json <fields>`
     필드선택 전환, `-L/--limit`, `--web`, `--repo` 로컬 git 컨텍스트 자동감지, `yona server
     list/use`, `yona browse`, `yona completion`(Cobra 기본 제공 등록만), `--version`, **`yona pr
     checkout`**(5라운드 재검증 결과 CLI 전용으로 재분류 — 아래 참고)
   - **서버(1부) 작업 완료(5라운드)** — 이하 전부 REST API로 노출 완료, CLI(2부)만 다음 라운드로 남음:
     `yona project fork/create/edit/delete`, `yona label list/create/edit/delete`, `yona issue
     reopen/transfer`(edit은 재검증 결과 Step4에서 이미 구현돼 있었음 — 아래 재분류 참고), `yona pr
     diff/comment`, `yona pr edit`(PATCH 어댑터만 추가 — 재검증 결과 서비스/컨트롤러 로직 자체는
     Step5의 PUT으로 이미 존재), `yona pr close/reopen`(재검증 결과 서버에 이미 범용
     `changeState` API 존재), `yona search issues/projects`(`prs`는 서버에 대응 SearchType이 없어
     이월), `yona org list/view`, `issue/pr list`의 `--assignee`/`--label`/`--author` 필터(PR은
     모델에 label/assignee 개념이 없어 `--author`만), `pr create`의 `--from-project-id` 요구
     제거(TASK-0396에서 계획 문서만 먼저 반영 — CLI 구현은 다음 라운드), `gh issue status`
     대응(최소 버전만 — 담당/작성 이슈 개수·목록, 나머지 필터는 이월)
   - **재검증으로 바로잡은 오분류(5라운드, 사용자 지시대로 코드 직접 확인)**:
     - `gh issue edit` — Step4의 `IssueRestApiController.update()`(PATCH `/{number}`)가 이미
       처음부터 구현돼 있었다. 감사표/Step8.5 원문이 "서버 API 필요"로 잘못 분류한 적은 없었지만
       혼동 방지를 위해 명시.
     - `gh pr checkout` — `PullRequestApiController.get()`/`list()`가 반환하는 `PullRequest` 엔티티에
       `fromProject`(owner/name 포함하는 완전한 Project 객체, `@JsonIgnore` 없음)와 `fromBranch`가
       그대로 직렬화된다 - CLI가 그 값으로 clone URL을 계산해 `git fetch`만 하면 되므로 신규 서버
       API가 불필요하다. **"기존 서버 API 연결" 그룹에서 "CLI 로컬 기능" 그룹으로 재분류.**
     - `gh pr edit` — 계획 문서가 "Step5에 PATCH 없음 → 서버 API도 신규 필요"로 적었으나, 실제로는
       `PullRequestController.updatePullRequest()`(PUT, 제목/본문/브랜치 수정)가 Step5부터 이미
       존재했다. 필요했던 건 신규 서비스 로직이 아니라 `PullRequestApiController`에 PATCH
       위임 어댑터 한 줄뿐이었다. **"신규 API 필요" → "기존 서버 API 연결"로 재분류.**
     - `gh pr close/reopen` — 계획 문서가 "서버 대응 상태변경 API 존재 여부 확인 필요"로 남겨뒀으나,
       `PullRequestController.changeState()`(POST `/{number}/state`)가 Step5부터 이미 양방향
       상태변경을 지원했다(이슈의 `changeState`와 동일 패턴). **"신규 API 필요" → "기존 서버 API
       연결"로 재분류.**
     - `yona label` — 감사표는 근거로 `web/LabelController.kt`를 지목했으나, 그 파일은 프로젝트와
       무관한 전역 라벨/카테고리 자동완성(`/labels`, `/categories`)만 제공한다. 실제 "프로젝트 하나에
       속한 라벨" CRUD는 `web/ProjectController.kt`(목록)와 `web/ProjectViewController.kt`(생성/
       수정/삭제, `/{owner}/{projectName}/issue/label(s)/...`, `ISSUE_LABEL` 기준 AccessControl)에
       있었다 - 감사표의 근거 파일 자체가 틀렸다(기능 존재 여부 분류는 맞았음).
   - **서버(1부)에 신규 API가 필요했던 것 중 실제로 구현한 것**: `gh issue status` 대응(사용자
     대시보드 REST API, `UserIssueStatusRestApiController` — 계획 지시대로 최소 버전만: 담당/작성
     이슈 개수·목록. mentioned/favorite/shared 필터와 페이지네이션 확장은 다음 라운드로 이월),
     `gh pr diff`(`PullRequestController.getDiff()`, `pullRequestService.getDiff()` 그대로 노출),
     `gh pr comment`(`PullRequestController.addComment()`, `CodeReviewService.createReviewComment()`
     재사용), `yona search issues/projects`(신규 `SearchRestApiController`, `SearchService.
     searchInAll()` 재사용), `yona org list/view`(신규 `OrganizationRestApiController`,
     `AccessControl.getVisibleProjects()` 재사용).
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
- [x] 서버(1부)가 Step8.5의 "기존 서버 API 연결"/"신규 API 필요" 두 그룹을 커버함 — project fork/create/edit/delete, label CRUD, issue reopen/transfer, PR edit/close/reopen/diff/comment, issue/pr list 필터, search issues/projects, org list/view, 사용자 이슈 대시보드 최소 버전 (5라운드 — 아래 완료 로그 참고. `yona search prs`는 서버에 대응 SearchType이 없어 이월)
- [x] yona-cli가 `gh` 명령 체계 대조 감사((A) 항목)를 반영해 사용법이 실제로 `gh`에 준함 (6라운드 + 직후 보완 — 아래 완료 로그 참고. `yona config`/`yona alias`는 낮은 우선순위 항목이라 다음 라운드 이후로 이월)
- [x] Step8.6 백로그 4개 항목(admin webhook/permission 목록 API, `gh issue status` 필터/페이지네이션 전체, `yona search prs`, PR 라벨/담당자) 전부 해소 (7라운드 — 아래 완료 로그 참고. PR 라벨/담당자의 웹 UI만 명시적으로 범위 밖, 다음 라운드 이후로 이월)
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

### 5라운드 (2026-08-31) — Step8.5 서버(1부) 보강

착수 전 "CLI `gh` 명령 체계 대조 감사"의 (A) 감사표/Step8.5 분류를 코드로 전수 재검증하라는
지시에 따라, 각 항목이 실제로 `/api/v1/projects/...` 네임스페이스에 이미 노출돼 있는지부터
직접 확인했다 — 재검증 결과와 오분류 정정은 위 "단계별 작업 계획" Step8.5 절에 기록. 그 결과
실제로 서버 쪽에 손을 대야 했던 항목만 구현했다(대부분 "기존 서비스/컨트롤러 메서드에 위임하는
얇은 어댑터 추가"였고, 순수 신규 서비스 로직이 필요했던 곳은 없었다 — Step4~6의 설계 원칙을
그대로 유지).

- **필터 확장**: `config/ApiTokenAuthenticationFilter.kt`의 `resourceSegmentToResourceType`에
  `"labels" -> ResourceType.ISSUE_LABEL`(ISSUES 그룹 — 위임 대상 메서드들이 실제로 ISSUE_LABEL
  기준 AccessControl을 쓰므로 일치시킴), `"fork" -> ResourceType.FORK`(CODE 그룹)를 추가. 둘 다
  기존 3세그먼트 `scopedApiPattern`에 그대로 매칭돼 정규식 자체는 변경하지 않았다.
- **프로젝트 REST API 확장**(`web/ProjectRestApiController.kt`):
  - `POST /api/v1/projects/{owner}/{project}/fork` — `ProjectController.forkProject()`(기존
    owner/name 기반, 숫자 ID 변환 불필요)에 그대로 위임.
  - `PATCH`/`DELETE /api/v1/projects/{owner}/{project}/settings` — owner/project 이름을 ID로
    바꿔 `ProjectController.updateProject()`/`deleteProject()`에 위임. **"settings" 세그먼트를
    쓴 이유(설계 결정)**: 세그먼트 없는 `/api/v1/projects/{owner}/{project}`는 이미 Step6.5의
    "metadata" 스코프(그룹/권한 매트릭스 없이 repo scope만 확인)로 매핑돼 있어, 그 경로를 PATCH/
    DELETE에도 그대로 쓰면 ADMINISTRATION 쓰기 권한이 전혀 없는 토큰도 프로젝트를 수정/삭제할 수
    있는 구멍이 생긴다. 기존에 이미 `ResourceType.PROJECT_SETTING`(ADMINISTRATION 그룹)으로
    매핑돼 있던 "settings" 세그먼트(Step1~3이 선점만 하고 실제 컨트롤러는 없었음)를 재사용해
    필터 변경 없이 올바른 권한 등급을 강제했다.
  - `POST /api/v1/projects`(bare, owner 세그먼트 없음) — `yona project create`. 세션 로그인
    사용자의 조직 소속 프로젝트 생성 권한 로직(`ProjectViewController.newProject()`와 동일 —
    owner가 기존 조직명이면 그 조직 admin만 생성 가능)을 그대로 재사용해 JSON으로 새로 구현.
    **의도적 설계**: 이 URL은 필터의 어떤 스코프 패턴과도 매칭되지 않아(3/2/1세그먼트 전부 모양이
    다름) 세션 로그인/레거시 전권 토큰으로만 호출 가능하고 Fine-grained 스코프 토큰으로는 저장소
    "생성"을 할 수 없다 — GitHub Fine-grained PAT도 저장소 생성 자체는 지원하지 않는 것(기존
    저장소에만 스코프될 수 있음)과 동일한 제약이라 우연이 아니라 의도적으로 이렇게 뒀다.
- **라벨 REST API 신설**(`web/LabelRestApiController.kt`,
  `/api/v1/projects/{owner}/{project}/labels`): 목록은 `ProjectController.getProjectLabels()`,
  생성/수정/삭제는 `ProjectViewController.newLabel()`/`updateLabelForm()`/`deleteLabelForm()`에
  위임(전부 기존 메서드 재사용, 신규 서비스 로직 없음).
- **이슈 REST API 확장**(`web/IssueRestApiController.kt` + `web/IssueController.kt`):
  - `POST /{number}/reopen` — 기존 범용 `IssueController.changeState()`를 OPEN으로 고정 호출.
  - `POST /{number}/transfer` — 기존 `IssueController.moveIssue()`(`MoveIssueRequest
    (targetProjectId: Long)`)에 위임하되, CLI 편의를 위해 `targetOwner`/`targetProject`(이름)를
    받아 이 어댑터가 내부에서 숫자 ID로 resolve한다(신규 서버 API 없이 기존 조회 반복 사용).
  - `GET /issues` `--assignee`/`--label`/`--author` 필터 — `IssueController.getIssues()`에
    세 선택 파라미터 추가. 셋 다 없으면 기존 `findByProject(AndState)` 경로를 100% 그대로 타
    회귀가 없고, 하나라도 있으면 `IssueRepository`가 이미 구현 중이던
    `JpaSpecificationExecutor<Issue>`를 활용한 동적 `Specification`으로 좁힌다(신규 리포지토리
    메서드 불필요). `author`는 `Issue.authorLoginId` 등가비교, `assignee`는 `Assignee.user.
    loginId` 등가비교, `label`은 `IssueLabel.name` 등가비교(ManyToMany라 `distinct(true)` 적용).
- **PR REST API 확장**(`web/PullRequestApiController.kt` + `web/PullRequestController.kt`):
  - `PATCH /{number}` — 기존 `PullRequestController.updatePullRequest()`(PUT, Step5부터 존재)에
    PATCH로 위임하는 어댑터만 추가.
  - `POST /{number}/close`, `/reopen` — 기존 `PullRequestController.changeState()`(Step5부터
    존재)를 CLOSED/OPEN으로 고정 호출.
  - `GET /{number}/diff` — 신규 `PullRequestController.getDiff()`(숫자 ID 기반, 웹 뷰
    `PullRequestViewController.viewChangesInternal()`이 화면 렌더링에 쓰는 것과 동일한
    `pullRequestService.getDiff()`를 JSON으로 노출) + REST API 어댑터.
  - `POST /{number}/comments` — 신규 `PullRequestController.addComment()`(숫자 ID 기반,
    `CodeReviewService.createReviewComment()`를 commitId/codeRange 없이 호출해 PR 전체에 붙는
    일반 댓글로 귀결시킴 — `ReviewViewController.newPullRequestComment()`와 같은 서비스를
    재사용하되 그 메서드 자체는 브라우저 폼 제출 전용(redirect 응답)이라 위임 대상으로 못 씀) +
    REST API 어댑터.
  - `GET /pull-requests` `--author` 필터 — `PullRequestController.getPullRequests()`에 선택
    파라미터 추가, `contributor.loginId` 등가비교로 인메모리 필터링(PR 목록은 프로젝트당 크지
    않아 신규 리포지토리 쿼리 없이 처리). **`--assignee`/`--label`은 PR에 적용하지 않는다** —
    yona `PullRequest` 엔티티엔 이슈와 달리 labels/assignee 필드 자체가 없어(reviewers/
    contributor만 존재) 실제 모델에 없는 개념을 인위적으로 만들지 않았다.
- **검색/조직/사용자 대시보드 신규 컨트롤러**(전부 기존 서비스 재사용, 신규 서비스 로직 없음):
  - `web/SearchRestApiController.kt`(`/api/v1/search/issues`, `/api/v1/search/projects`) —
    `SearchService.searchInAll()`을 JSON으로 노출. **범위 조정**: yona `SearchType` enum엔
    PROJECT/ISSUE/USER/POST/MILESTONE/ISSUE_COMMENT/POST_COMMENT/REVIEW만 있고 "PULL_REQUEST"에
    대응하는 값이 없다(PR 자체를 색인하는 통합검색 기능이 서버에 아직 없음) — `yona search prs`는
    이번 라운드 구현 대상에서 제외하고 다음 라운드로 이월.
  - `web/OrganizationRestApiController.kt`(`/api/v1/organizations`,
    `/api/v1/organizations/{name}`) — `OrganizationViewController`와 동일한 권한 로직(게스트
    차단, `HIDE_PROJECT_LISTING`, `AccessControl.getVisibleProjects()`)만 재사용.
  - `web/UserIssueStatusRestApiController.kt`(`GET /api/v1/user/issues/status`) — `gh issue
    status` 최소 버전(계획 지시대로). `UserViewController.userIssues()`가 이미 쓰는
    `findByAssigneeAndState`/`findByAuthorIdAndState` + count 쌍만 재사용해 담당/작성 이슈
    개수·목록을 반환한다. mentioned/favorite/shared 필터, 페이지네이션/정렬 확장은 범위가 커질 수
    있어 다음 라운드로 이월.
- **신규 스코프 인가 갭(문서화, 아래 리스크 표에도 기록)**: `search`/`organizations`/`user`는
  프로젝트(저장소) 하나에 속한 리소스가 아니라 여러 저장소를 가로지르거나 저장소와 무관한
  전역/사용자 단위 개념이라 `/api/v1/projects/{owner}/{project}/{resource}` 3세그먼트 스코프
  모델에 애초에 맞지 않는다. 이 세 네임스페이스는 어떤 스코프 패턴과도 매칭되지 않아 세션
  로그인/레거시 전권 토큰으로만 인증되고 Fine-grained 스코프 토큰은 인증되지 않는다(구멍이
  아니라 기능 제한 — Step6 이전 프로젝트 조회 API가 겪었던 것과 동일한 성격의 제한이며, 이번
  라운드 지시사항이 "애매하면 리스크 표에 남기고 합리적 기본값으로 진행"이라 명시해 이렇게
  처리했다).
- **테스트**: 신규/확장 스펙 파일 — `web/ProjectRestApiControllerSpec.kt`(+8),
  `web/LabelRestApiControllerSpec.kt`(신규 4), `web/IssueRestApiControllerSpec.kt`(+7),
  `web/PullRequestApiControllerSpec.kt`(+6), `web/PullRequestControllerSpec.kt`(+4),
  `web/SearchRestApiControllerSpec.kt`(신규 5), `web/OrganizationRestApiControllerSpec.kt`(신규
  4), `web/UserIssueStatusRestApiControllerSpec.kt`(신규 2),
  `config/ApiTokenScopedProjectForkAndLabelSubpathAuthorizationIntegrationSpec.kt`(신규 8 —
  fork/labels/settings 세그먼트 스코프 403/통과 검증),
  `config/ApiTokenScopedIssueAndPullRequestSubpathAuthorizationIntegrationSpec.kt`(+9 —
  이슈 reopen/transfer, PR edit/close/reopen/diff/comments 서브패스 스코프 검증). 총 57개
  테스트 추가/확장, 전부 GREEN.
- **회귀 확인**: `web`/`config`/`domain/apitoken` 패키지 전체(3280여 개 테스트) 실행 결과 4건
  실패는 전부 `CodeSwallowedStyleRenderingSpec`/`CodeBrowserListWrapRenderingSpec`(코드
  브라우저 CSS 렌더링, 이번 라운드가 전혀 손대지 않은 영역)이었고, 이 두 스펙은 이 라운드
  변경사항을 전부 `git stash`한 클린 `main`에서도 동일하게 실패함을 확인해 **사전 존재 이슈로
  회귀가 아님**을 검증했다.

### 6라운드 (2026-09-01) — Step8.5 Go CLI(2부) 클라이언트 구현

5라운드가 완료한 서버(1부) API를 그대로 감싸는 CLI(2부) 클라이언트 코드를 `yona-cli`
저장소(`github.com/search5/yona-cli`, 독립 git 저장소)에 구현했다. 착수 전 이 계획 문서
전체와 `yona-cli`의 기존 `cmd/*.go`/`internal/api/*.go`, 그리고 방금 5라운드가 추가한 실제
yuna 서버 엔드포인트(`web/ProjectRestApiController.kt` 등 7개 컨트롤러)를 Serena LSP로
직접 재확인해 계획 문서 요약과 실제 코드가 어긋나는 지점이 없는지 대조했다 — 어긋난 지점은
없었고, 계획 문서에 이미 기록된 응답 형태 그대로였다.

- **그룹 1(CLI 로컬 기능)**:
  - `--json <fields>` 필드선택 전환: `cmd/output.go`의 `printJSON()`을 불리언 스위치에서
    "필드를 JSON 왕복 변환 후 재귀적으로 골라내는" 방식으로 재작성. `cmd.Flags().Changed("json")`
    으로 "플래그 자체를 안 씀"과 "빈 필드 목록을 명시적으로 줌"을 구분해 후자는 오류 처리.
    기존 issue/pr/project 명령의 `--json` 사용처와 테스트를 전부 이 방식으로 갱신.
  - `-L/--limit`: `issue list`는 서버가 지원하는 페이지네이션(`size` 쿼리 파라미터)을 그대로
    활용, `pr list`/`project list`는 서버가 페이지네이션 없이 전체 목록을 반환해 클라이언트
    사이드 슬라이싱으로 처리(계획 문서 지시대로).
  - `--web`: `view`/`list` 계열 명령에 추가. 신규 `internal/weburl` 패키지가
    `IssueViewController.kt`/`PullRequestViewController.kt`의 실제 `@GetMapping` 경로
    (`/{owner}/{project}/issue/{n}`, `/{owner}/{project}/pull/{n}`, `/issues`, `/pulls`)를
    그대로 반영해 URL을 계산하고, 신규 `internal/gitutil.OpenInBrowser()`가 OS별
    (`xdg-open`/`open`/`rundll32`)로 연다.
  - `--repo`/`-R` 자동감지: 신규 `internal/gitutil.DetectRepo()`가 `git remote get-url
    origin`을 파싱한다. yuna clone URL(`TemplateHelper.getCloneUrl()`, `scheme://[loginId@]
    host[:port]/owner/name.git`)과 `git@host:owner/repo.git` scp 스타일 SSH URL 둘 다
    지원 — gh CLI와 동일하게 호스트 뒤 마지막 두 경로 세그먼트를 owner/project로 취급한다.
    git이 없거나 저장소 밖이면 명시적 `--repo` 오류로 폴백. issue/pr/label의 모든 `--repo`
    사용처가 이 자동감지를 거치도록 갱신(project는 원래부터 `owner/project` 위치 인자를 쓰므로
    자동감지 대상이 아님).
  - `yona server list/use`: 신규 `cmd/server.go`. `list`는 `auth status`와 동일한 토큰
    마스킹으로 `config.Hosts`를 나열, `use`는 신규 `config.UseHost()`로 재로그인 없이
    `CurrentHost`만 전환(등록 안 된 호스트면 오류).
  - `yona browse [issue|pr <number>]`: 신규 `cmd/browse.go`. `internal/weburl`을 `--web`과
    공유해 URL 계산 로직을 재사용.
  - `completion`: 실제로 확인해보니 Cobra가 서브커맨드를 가진 루트 커맨드에 이미 자동으로
    등록하고 있었다(`ExecuteC()` → `InitDefaultCompletionCmd()`, `CompletionOptions.
    DisableDefaultCmd` 기본값 `false`) — 계획 문서 예상대로 별도 구현 없이 등록만 확인.
  - `--version`: `cmd/root.go`에 `Version` 필드를 설정해 Cobra 기본 제공 `--version` 플래그를
    활성화(하드코딩 상수, ldflags 주입은 Step 11 배포 작업 범위로 남김).
- **그룹 2/3(서버 API 연결)**: 5라운드 완료 로그의 엔드포인트 목록을 그대로 클라이언트로
  감쌌다 — `yona project fork/create/edit/delete`, `yona label list/create/edit/delete`,
  `yona issue edit/reopen/transfer`(edit은 read-modify-write로 title/body 중 생략한 쪽을
  현재 값으로 채움 — 서버 `UpdateIssueRequest`가 둘 다 non-null 필수), `yona issue list`의
  `--assignee`/`--label`/`--author` 필터, `yona pr edit/close/reopen/diff/comment`(edit도
  동일한 read-modify-write), `yona pr list`의 `--author` 필터, `yona search issues/projects`
  (`prs`는 서버에 대응 SearchType이 없어 구현하지 않음, 계획 문서에 이미 기록된 이월 그대로
  유지), `yona org list/view`, `yona issue status`(담당/작성 이슈 개수·목록).
  - **`yona pr checkout <number>`**: 서버 API를 호출하지 않고, `pr view`와 같은 방식으로
    조회한 PR 응답의 `fromProject`(owner/name) + `fromBranch`로 clone URL을 계산해
    `git fetch <url> <branch>` + `git checkout -B pr-<번호> FETCH_HEAD`를 실행한다. URL/브랜치
    이름 계산(`planCheckout()`)을 git 실행과 분리해 순수 함수로 단위테스트했다.
- **서버 쪽에서 발견한 문제(이번 라운드 범위 밖, 보고만 함)**: `PullRequestController.getDiff()`
  (`pr diff`가 호출)가 반환하는 `List<FileDiff>`의 `FileDiff.a`/`b`는
  `org.eclipse.jgit.diff.RawText`, `editList`는 `EditList`(JGit 내부 타입)로 선언돼 있는데,
  둘 다 일반 Jackson 빈 컨벤션에 맞는 getter가 없다 — 그대로 JSON 직렬화하면 필드가 거의
  비거나 예외가 날 가능성이 있다. CLI 쪽에서 억지로 우회하지 않고 `pathA`/`pathB`/`changeType`
  같은 단순 필드만 안전하게 쓰고 나머지는 `--json` 탈출구로만 노출하는 방어적 설계로 대응했다
  (`internal/api/pr.go`의 `GetPullRequestDiff()` 주석 참고). 실제 서버로 호출해 직렬화
  결과를 확인하지는 못했다(이번 라운드는 httptest 목킹만 수행) — 실서버 검증 시 재확인 필요.
- **후속 보완(6라운드 직후)**: `pr create`의 `--from-project-id` 요구 제거 — 6라운드 위임
  프롬프트에 빠뜨려 이월됐던 것을 바로 마저 구현. `--from "owner/project"`를 받아 CLI가
  기존 프로젝트 조회 API(`GET /api/v1/projects/{owner}/{project}`)로 내부에서 ID를
  resolve하도록 `cmd/pr.go`의 `newPRCreateCmd`를 수정(TDD, RED→GREEN, `go test ./...`
  전체 통과). `yona-cli` 커밋 `2ce3881`.
- **이월 항목**:
  - `yona config`, `yona alias` — 계획 문서 지시대로 낮은 우선순위, 미착수.
  - Go CLI 실서버 골든 패스(`auth login` → 이슈 생성 → PR 목록 조회) 수동 검증 — 여전히
    미착수(httptest 기반 단위/통합 테스트만 수행).
  - `goreleaser` 배포(Step 11) — 미착수.
- **테스트**: `yona-cli` 신규/확장 테스트 다수 추가, `go test ./...` **167개 테스트 전체
  GREEN**(`go build ./...`/`go vet ./...`/`gofmt -l .` 전부 클린). 상세 커밋 이력은 이 작업을
  지시한 세션의 최종 보고 참고.

### 7라운드 (2026-09-01) — Step8.6 백로그 4개 항목 전부 해소

착수 전 이 절 전체와 6라운드까지의 완료 로그, `config/ApiTokenAuthenticationFilter.kt`의 스코프
인가 메커니즘(`scopedApiPattern`/`resourceSegmentToResourceType`)을 재확인한 뒤, 지시된 우선순위
순서(1→2→3→4) 그대로 진행했다 — 순서를 바꾸지 않았다.

- **항목1(최우선) — admin webhook/permission 목록 JSON API 신설**:
  - `WebhookController.kt`에 `listWebhooksJson()` 신설(기존 `webhooks()`와 동일한 프로젝트 조회 +
    권한 체크(`checkWebhookPermission`, Operation.UPDATE) 재사용, JSON 응답만 다름 — secret도
    기존 HTML 화면(`setting_webhook.html`)과 동일한 노출 수준으로 그대로 포함). 신규
    `web/WebhookRestApiController.kt`(`GET /api/v1/projects/{owner}/{project}/webhooks`)가 위임.
  - `ProjectMemberController.kt`에 `listMembers()` 신설(`isProjectManager` 권한 체크 재사용, 기존
    `findByProjectId()` 결과를 loginId/역할명 포함 JSON으로 변환). 신규
    `web/ProjectPermissionRestApiController.kt`(`GET /api/v1/projects/{owner}/{project}/permissions`)가
    owner/project 이름을 숫자 projectId로 바꿔 위임.
  - `ApiTokenAuthenticationFilter.resourceSegmentToResourceType`에 `"permissions" ->
    ResourceType.PROJECT_SETTING`(ADMINISTRATION 그룹) 추가 — `"webhooks"`는 Step1~3부터 이미
    매핑돼 있어 변경 불필요.
  - 검증: `WebhookControllerSpec`(+3, `listWebhooksJson` 성공/404/403), 신규
    `WebhookRestApiControllerSpec`(1), `ProjectMemberControllerSpec`(+3, `listMembers` 성공/403/401),
    신규 `ProjectPermissionRestApiControllerSpec`(2), 신규
    `ApiTokenScopedWebhookAndPermissionSubpathAuthorizationIntegrationSpec`(4, webhooks/permissions
    각 스코프 없음 403·있음 200). 총 13개.
- **항목2 — `gh issue status` 필터/페이지네이션 전체 노출**: `UserViewController.userIssues()`와
  `UserIssueStatusRestApiController`를 코드로 대조한 결과, 지시대로 신규 백엔드 로직 없이 이미 있는
  `IssueRepository` 메서드(`findCommentedByState`/`findMentionedByState`/`findFavoriteByState`/
  `findSharedByState` + 대응 count 쌍, `MentionService.getMentioningIssueIds`)에 쿼리 파라미터만
  추가로 전달해 해소했다. `/status` 엔드포인트에 `pageNum`/`state`/`filter`/`orderBy`/`orderDir`
  (userIssues()와 동일한 이름·기본값) + `commenterId`/`mentionId`/`sharerId`/`favoriteId`(명시하지
  않으면 로그인 사용자 자신으로 기본값)를 추가하고, 응답에 기존 `assigned`/`created`(하위호환
  유지) 외에 `commented`/`mentioned`/`favorite`/`shared` 4개 섹션을 추가했다. 각 섹션은
  `openCount`/`closedCount`/`items`/`totalElements`/`totalPages`/`page`를 담는다. 검증: 기존 스펙에
  +3케이스(commented/mentioned/favorite/shared 동시 반환, 페이지네이션/정렬/검색 파라미터 전달,
  타 사용자 id override) = 총 5개.
- **항목3 — search prs 서버 지원**: `domain/enumeration/SearchType`에 `PULL_REQUEST` 추가.
  `domain/pullrequest/PullRequestRepository`에 기존 `SearchType.ISSUE`(`IssueRepository.
  searchIssues()`)와 동일한 패턴(Postgres Hibernate 7.2.x가 한 쿼리에 LIKE 술어 2개 이상이면
  실패하는 버그를 피하기 위한 네이티브 쿼리)의 `searchPullRequests`/`countSearchPullRequests`(전역
  — toProject가 허용 프로젝트 목록에 있거나 내가 contributor인 PR까지 포함, Issue의
  author/assignee 대응 개념으로 contributor 하나만 존재)와
  `searchPullRequestsInProject`/`countSearchPullRequestsInProject`(프로젝트 내부)를 신설했다.
  `SearchServiceImpl`의 `searchInAll`/`searchInAProject`/`searchInAGroup` 3개 메서드와
  `SearchResult`에 `pullRequestsCount`/`pullRequests` 필드 추가(우선순위는 기존 8개 타입 뒤,
  기본값 ISSUE 폴백 앞). `web/SearchRestApiController`에 `GET /api/v1/search/prs` 추가(issues/
  projects와 동일한 얇은 어댑터). 검증: `SearchResultSpec`(+1), `SearchServiceSpec`(+3, 전역/
  프로젝트내부/그룹 각 1케이스), `SearchRestApiControllerSpec`(+2), 신규
  `PullRequestRepositorySpec`(+2, 실제 DB로 네이티브 쿼리 검증 — 프로젝트 소속/contributor 매칭,
  프로젝트 내부 검색). 총 8개.
- **항목4(가장 신중하게) — PR에 라벨/담당자 개념 추가**:
  - **레거시 조사 결과(착수 전 필수 확인)**: `/home/jiho/yona-convert/legacy-yona/app/models/
    PullRequest.java`를 전수 확인(`grep -i "label\|assignee"` 0건 — 필드/컨트롤러/뷰 템플릿
    (`app/controllers/PullRequestApp.java`, `app/views/organization/group_pullrequest_list*.
    scala.html` 등) 전부 0건). **레거시 Play `yona`의 PR 모델에도 원래부터 label/assignee
    개념이 전혀 없었음을 확정** — 이번 작업은 포팅 누락 버그가 아니라 순수 신규 기능 확장이다
    (`docs/yona-wiki/index.md` 서문이 허용하는 범위).
  - **`Assignee` 재사용 가능성 확인**: `domain/issue/Assignee.kt`는 정말로 `(user, project)`만
    갖는 범용 엔티티이고 Issue 전용 FK/제약이 전혀 없음을 확인 — Issue와 동일한 패턴
    (`@ManyToOne(cascade=[CascadeType.ALL])`, `@JoinColumn(name="assignee_id")`, nullable)으로
    그대로 재사용 가능해 재사용했다.
  - **라벨 엔티티 선택**: `domain/issue/IssueLabel`(카테고리에 종속, `issue_issue_label` 조인
    테이블로 Issue와 연결, `ProjectViewController.newLabel()`/`updateLabelForm()`/
    `deleteLabelForm()`이 실제로 관리하는 "진짜" 프로젝트별 이슈 라벨)과 `domain/project/Label`
    (카테고리 없는 단순 태그, `project_label` 조인테이블로 **Project 자신**에 직접 붙는 프로젝트
    레벨 토픽/분류용, Step8.5 1라운드 도입)의 용도가 서로 다름을 코드로 확인했다. **부수 발견**:
    `web/LabelRestApiController.kt`는 `list()`(`ProjectController.getProjectLabels()`)가
    `domain/project/Label`을 반환하는데 `create/update/delete`(`ProjectViewController.
    newLabel/updateLabelForm/deleteLabelForm`)는 `domain/issue/IssueLabel`을 다뤄 **같은 REST
    리소스 안에서 조회와 변경이 서로 다른 엔티티를 가리키는 기존 불일치**가 있다(이번 작업
    범위 밖이라 수정하지 않고 여기 기록만 남긴다 — 별도 이슈/다음 라운드 후보). PR은 "프로젝트
    안의 개별 항목"이라는 점에서 Issue와 성격이 같으므로 `IssueLabel`을 재사용하는 것이
    개념적으로 맞고, 프로젝트마다 이미 라벨 정의(이름/색상/카테고리)가 있어 신규
    `PullRequestLabel` 엔티티를 만들 필요가 없다고 판단 — Issue.labels와 동일한 패턴(신규
    조인테이블 `pull_request_issue_label`)으로 재사용했다.
  - **스키마 변경 리스크 재평가**: `application.yml`이 전 프로파일에서 `ddl-auto: update`를
    쓰고 있어(수동 마이그레이션 스크립트 없음, 지금까지 이 계획의 모든 라운드가 신규 엔티티/
    컬럼을 추가할 때 동일하게 의존해온 방식) 스키마 변경 자체의 리스크는 계획 문서가 우려했던
    것보다 낮다고 재평가 — 이 판단 때문에 "최소 구현으로 축소"가 아니라 지시된 범위(엔티티+
    서비스+REST API+목록 필터) 전체를 이번 라운드에 완료했다(웹 UI만 범위 밖으로 유지).
  - **구현**: `PullRequest.kt`에 `assignee: Assignee? = null`, `labels: MutableSet<IssueLabel> =
    mutableSetOf()` 추가. `PullRequestService`/`PullRequestServiceImpl`에 `setAssignee()`(null이면
    해제, IssueServiceImpl.updateIssue()와 동일하게 기존 Assignee를 재사용하지 않고 매번 새로
    만듦)/`addLabel()`/`removeLabel()`(둘 다 라벨 "정의" 자체는 만들지 않고 프로젝트에 이미 있는
    `IssueLabel`만 참조) 신설. `PullRequestController`에 `PUT/DELETE /{number}/assignee`,
    `POST /{number}/labels`, `DELETE /{number}/labels/{labelId}`(addReviewer/removeReviewer와
    동일한 `checkWritePermission` 패턴) 추가, `getPullRequests()`에 `--assignee`/`--label` 필터
    추가(기존 `--author`와 동일하게 인메모리 필터링 — PR 목록은 프로젝트당 크지 않음).
    `PullRequestApiController`에 대응 어댑터(`PUT/DELETE .../assignee`, `POST/DELETE
    .../labels(/{labelId})`) + 목록 필터 파라미터 전달 추가. 별도 필터 세그먼트 추가는
    불필요(`pull-requests` 세그먼트가 이미 `scopedApiPattern`의 `(?:/.*)?` 접미부로 모든 하위
    경로를 PULL_REQUESTS 그룹으로 인가하고 있음 — 이슈 comments/close, PR merge/reviewers와
    동일한 기존 메커니즘 재사용).
  - 검증: `PullRequest.kt` 필드 추가에 대한 실제 DB 검증은 `PullRequestServiceSpec`의 신규
    4케이스(담당자 지정/해제, 존재하지 않는 PR 예외, 라벨 추가/제거, 존재하지 않는 라벨 예외,
    real DB 기반)로 확보. `PullRequestControllerSpec`(+14, assignee PUT 5케이스/DELETE 2케이스,
    labels POST 3케이스/DELETE 2케이스, list 필터 assignee/label 각 1케이스),
    `PullRequestApiControllerSpec`(+6, assignee PUT/DELETE 어댑터 3개 + labels POST/DELETE
    어댑터 2개 + 목록 필터 파라미터 전달 1개), `ApiTokenScopedIssueAndPullRequestSubpath
    AuthorizationIntegrationSpec`(+4, assignee/labels 하위경로 스코프 403/통과). 총 28개.
  - **웹 UI는 명시적으로 범위 밖**: PR 상세/목록 화면에 담당자·라벨을 표시·편집하는 Thymeleaf
    UI는 이번 라운드에서 손대지 않았다(지시사항의 "범위가 크면 최소 구현 + 웹 UI는 범위 밖" 대안을
    적용 — 다만 백엔드는 전체를 완료했으므로 실제로 축소된 것은 웹 UI뿐이다). 다음 라운드 이후로
    이월.
- **전체 스위트**: `./gradlew test` 전체 실행 결과 3280여 개 테스트 중 실패는 5라운드에도 있었던
  사전 존재 이슈(`CodeSwallowedStyleRenderingSpec`/`CodeBrowserListWrapRenderingSpec`, 코드
  브라우저 CSS 렌더링, 이번 라운드가 전혀 손대지 않은 영역)뿐 — 회귀 아님. 상세는 이 라운드의
  커밋 메시지 참고.

## 리스크 / 미결정 사항

| 항목 | 내용 | 해소 방법 |
|---|---|---|
| 스코프 카테고리 확정 | `ResourceType` 33종을 어떻게 그룹핑할지 최종 미확정 | **1라운드에서 해소** — `ApiTokenScopeGroup.kt`(8개 그룹)로 전수 확정, 근거는 위 완료 로그 참고 |
| 기존 전권 토큰 마이그레이션 | 이미 발급된 `User.token` 보유자 처리 방침 미정 | **미해결(다음 라운드로 이월)** — 1라운드는 문서화만 함: 신규 `/api/v1/...` 네임스페이스는 스코프 토큰만 인증하고, 그 외 기존 URL은 레거시 전권 토큰 경로를 그대로 유지하는 co-existence로 임시 처리(`ApiTokenAuthenticationFilter.kt` 주석 참고). 자동 재발급 vs 만료 후 재발급 안내 중 무엇을 택할지, 그리고 레거시 경로를 언제 끊을지는 여전히 미정 |
| 관리자 API 존재 여부 | 백업/웹훅/권한 관리용 서버 API가 이미 있는지 미확인 | **4라운드에서 해소** — 백업(`GET /site/export`, `POST /site/import`)은 실사용 가능한 형태로 존재해 CLI에 완전히 연결. 웹훅(`web/WebhookController.kt`)·권한(`web/ProjectMemberController.kt`)은 생성/변경/삭제 API는 있지만 세션·폼 기반 레거시라 목록 조회용 JSON API가 없다(웹훅 목록은 HTML 렌더링 전용, 권한 목록은 엔드포인트 자체가 없음) — `yona admin webhook list`/`yona admin permission list`는 명확한 안내 메시지의 미구현 스텁으로 유지. 새 JSON API를 yuna에 추가하는 것은 CLI 프로젝트 범위 밖이라 이번 라운드는 시도하지 않음. 상세는 위 "4라운드" 로그 참고 |
| 프로젝트 조회 API의 스코프 패턴 불일치 | Step6의 `/api/v1/projects/{owner}`(목록)와 `/api/v1/projects/{owner}/{project}`(조회)는 리소스 세그먼트가 없어 `ApiTokenAuthenticationFilter.scopedApiPattern`(owner/project/resource 3단 필수)과 매칭되지 않는다 — Fine-grained 스코프 토큰으로 호출 불가(세션/전권 토큰만 가능), 이슈/PR API는 "issues"/"pull-requests" 세그먼트가 있어 이 문제가 없다 | **3라운드에서 해소** — 개별 조회는 `metadata` 스코프(그룹/권한 매트릭스 없이 repo scope만 확인), 목록은 request attribute(`SCOPED_API_TOKEN_ATTRIBUTE`) 기반 필터링으로 구현 완료. 상세는 아래 "3라운드" 로그 참고 |
| 토큰 발급/관리 UI 부재 | `ApiTokenRepository`엔 조회 메서드 하나뿐, 사용자가 `ApiToken`을 발급/조회/폐기할 UI·컨트롤러·서비스가 전혀 없어 실사용자는 Fine-grained 토큰을 발급받을 방법이 없다 | **3라운드에서 해소** — `ApiTokenService`/`ApiTokenServiceImpl` + `UserViewController` 확장 + `user/edit_tokens.html` 신설로 발급/조회/폐기 가능. 상세는 아래 "3라운드" 로그 참고 |
| 전역/사용자 단위 엔드포인트의 스코프 인가 갭 | `search`/`organizations`/`user` 네임스페이스(5라운드 신설)는 특정 저장소 하나에 속한 리소스가 아니라 `/api/v1/projects/{owner}/{project}/{resource}` 3세그먼트 스코프 모델에 맞지 않는다 | **미해결(다음 라운드 이후로 이월)** — 현재는 세션 로그인/레거시 전권 토큰으로만 인증되고 Fine-grained 스코프 토큰은 인증되지 않는다(구멍은 아니고 기능 제한). 해소하려면 이 계획의 8개 스코프 그룹과 별개로 "저장소 비종속" 스코프 개념을 새로 설계해야 한다 — 이번 라운드 지시사항대로 무리하게 밀어붙이지 않고 문서화만 함 |
| `yona search prs` 대응 서버 기능 없음 | yona `SearchType` enum(PROJECT/ISSUE/USER/POST/MILESTONE/ISSUE_COMMENT/POST_COMMENT/REVIEW)에 PR 전용 값이 없어 PR 자체를 색인하는 통합검색이 서버에 없다 | **7라운드에서 해소** — `SearchType.PULL_REQUEST` 추가 + `PullRequestRepository`에 Issue와 동일한 패턴의 인덱싱/검색 쿼리 신설, `web/SearchRestApiController`에 `GET /api/v1/search/prs` 추가. 상세는 아래 "7라운드" 로그 참고 |
| `gh issue status` 최소 버전만 구현 | `UserIssueStatusRestApiController`는 담당/작성 이슈 개수·목록만 제공 | **7라운드에서 해소** — `UserViewController.userIssues()`가 지원하는 mentioned/favorite/shared/commenter 필터와 pageNum/state/filter/orderBy/orderDir 파라미터를 신규 백엔드 로직 없이 기존 IssueRepository 메서드 호출에 그대로 전달해 노출했다. 상세는 아래 "7라운드" 로그 참고 |
| PR에 라벨/담당자 개념 없음 | `PullRequest.kt`에 `labels`/`assignee` 필드가 없어(Issue엔 있음) `pr list --label/--assignee`를 지원할 수 없었다. 레거시 Play `yona`에도 원래 없던 개념인지 확인 필요했다 | **7라운드에서 해소** — 레거시 조사 결과 원래부터 없던 개념(포팅 누락 아님)임을 확정한 뒤, `Assignee`(재사용)/`IssueLabel`(재사용, 신규 조인테이블 `pull_request_issue_label`)로 백엔드 엔티티+서비스+REST API+list 필터 전체 구현. 웹 UI만 범위 밖으로 이월. 상세는 아래 "7라운드" 로그 참고 |

## Step 8.6 — 실서버 기능 부재로 미룬 4개 항목 (2026-09-01, 사용자 지시로 백로그화)

**상태: 7라운드(2026-09-01)에서 4개 항목 전부 우선순위 순서(1→2→3→4) 그대로 해소 완료.** 아래
목록은 착수 전 백로그 원문을 그대로 보존한다(각 항목의 실제 구현/조사 결과는 위 "7라운드" 완료
로그 참고).

Step8.5 완료 후 "CLI 배선 문제가 아니라 서버 자체에 없어서 못 넣은 것도 있을 텐데?"라는 질문으로
정리된, 위 리스크 표에 이미 개별 기록된 4개 항목을 우선순위와 함께 백로그로 확정한다. 착수 비용/
리스크가 작은 순서로 정렬했다 — 뒤로 갈수록 범위가 커지고 설계 결정이 필요하다.

1. **(최우선) `yona admin webhook list`/`permission list`용 JSON API 신설** — 웹훅
   목록은 `web/WebhookController.kt`가 HTML 렌더링 전용이고, 권한 목록은 대응 엔드포인트가
   아예 없다(`web/ProjectMemberController.kt`). 생성/변경/삭제는 이미 있으니 목록 조회만
   추가하면 된다 — Step4~6과 동일한 "기존 서비스 로직에 위임하는 얇은 REST 어댑터" 패턴을
   그대로 적용 가능, 스키마 변경 없음. 위험이 가장 작다.
2. **`gh issue status` 필터/페이지네이션 전체 구현** — `UserViewController.userIssues()`에
   mentioned/favorite/shared/commenter 필터와 페이지네이션/정렬이 **이미 구현돼 있다** —
   `UserIssueStatusRestApiController`가 그중 assigned/created 두 개만 REST로 노출했으니,
   나머지를 같은 방식으로 마저 노출하면 된다. 신규 백엔드 로직 불필요, 순수 REST 노출 확장.
3. **`yona search prs` 서버 지원** — `domain/enumeration/SearchType`(또는 대응 enum)에
   PR 전용 값이 없어 PR을 색인하는 통합검색 자체가 없다. `SearchService`에 `SearchType.
   PULL_REQUEST` 추가 + PR 인덱싱/검색 쿼리 신설 필요 — 위 두 항목과 달리 **신규 검색 로직을
   설계해야 하는 작업**이라 범위가 한 단계 크다(다만 PR 엔티티 스키마 변경은 없음).
4. **(가장 큼, 설계 결정 필요) PR에 라벨/담당자 개념 추가** — `PullRequest.kt`(`domain/
   pullrequest/PullRequest.kt`) 자체에 `labels`/`assignee` 필드가 없다(`Issue.kt`엔 `assignee:
   Assignee?`, `labels: MutableSet<IssueLabel>`가 있는 것과 대비). 이건 REST API 배선이
   아니라 **PR 엔티티에 없는 도메인 개념을 새로 추가하는 진짜 기능 확장**이다 — P3는 "레거시
   동치성과 무관한 신규 인프라 개선"이 허용 범위이긴 하지만(`docs/yona-wiki/index.md` 서문
   참고), 스키마 변경(마이그레이션 포함)이 들어가는 만큼 신중해야 한다. 착수 전 반드시 확인할
   것:
   - `Assignee(user, project)`(`domain/issue/Assignee.kt`)는 이미 Issue 전용이 아니라
     범용(프로젝트 범위 사용자 배정) 엔티티라 PR에도 `assignee: Assignee?` 필드만 추가하면
     재사용 가능해 보인다(실제로 재사용 가능한지, 아니면 PR 전용 개념이 필요한지 재검증 필요).
   - 라벨은 `IssueLabel`(Issue 전용, `IssueLabelCategory`에 종속)과 별개로 `domain/project/
     Label.kt`(Step8.5 1라운드가 프로젝트 레벨 라벨 CRUD에 쓴 범용 엔티티)가 이미 있다 — PR
     라벨링에 `IssueLabel`을 재사용할지, `Label`을 재사용할지, 아니면 `PullRequestLabel`을
     새로 만들지는 실제 두 엔티티의 용도 차이를 코드로 재확인해서 결정해라.
   - **레거시 yona의 PR 모델에도 라벨/담당자 개념이 원래 없었을 가능성이 높다**(Issue 전용
     설계로 보임) — 착수 전 legacy 소스(`/home/jiho/yona-convert/legacy-yona`)의 PR 관련
     모델을 확인해서, 정말 레거시에도 없던 개념을 새로 추가하는 것인지 확정하고 계획 문서에
     근거를 남겨라.

## 관련

- 백로그 원본: [`docs/PARITY_BACKLOG.md`](../../PARITY_BACKLOG.md#p3-02)
- 관련 계획: [[p3-03-ssh-gpg]], [[p3-07-mcp-server]], [[p3-05-ci-actions-runner]]
- 관련 소스: `config/ApiTokenAuthenticationFilter.kt`, `config/SecurityConfig.kt`, `domain/enumeration/ResourceType.kt`, `web/ProjectApiController.kt`, `web/IssueRestApiController.kt`, `web/PullRequestApiController.kt`, `web/ProjectRestApiController.kt`, `web/LabelRestApiController.kt`, `web/SearchRestApiController.kt`, `web/OrganizationRestApiController.kt`, `web/UserIssueStatusRestApiController.kt`, `domain/pullrequest/PullRequestServiceImpl.kt`, `web/WebhookRestApiController.kt`, `web/ProjectPermissionRestApiController.kt`, `domain/pullrequest/PullRequest.kt`, `domain/pullrequest/PullRequestRepository.kt`, `domain/enumeration/SearchType.kt`
