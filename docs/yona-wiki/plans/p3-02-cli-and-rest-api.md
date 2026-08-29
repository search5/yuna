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
updated: 2026-08-29
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
9. **Step 9 — `yona admin backup/webhook/permission`**: 기존 관리자 API 확인 후 연결(신규 서버 API 필요 시 1부 패턴으로 추가)
10. **Step 10 — `yona api <method> <path>`**: 저수준 원시 호출 명령(디버깅/스크립팅용)
11. **Step 11 — 배포**: `goreleaser`로 GitHub Releases + Homebrew tap + Scoop bucket + `.deb`/`.rpm`

## 완료 기준 (Definition of Done)

- [x] `ApiToken` 엔티티가 `expires_at` 필수로 강제됨을 테스트로 보장 (1라운드 — Step 1)
- [x] `ApiTokenAuthenticationFilter`가 스코프 밖 요청을 403으로 거부함을 테스트로 보장 (1라운드 — Step 3, 신규 `/api/v1/projects/...` 네임스페이스 한정)
- [x] 이슈/PR/프로젝트 REST API가 CRUD 전체를 커버하고, 각 엔드포인트에 권한 스코프 검증 테스트 존재 (2라운드 — Step 4~6, 단 프로젝트 조회 API는 스코프 토큰이 아닌 AccessControl 기반 검증 — 아래 로그/리스크 표 참고)
- [ ] 프로젝트 조회/목록 API가 Fine-grained 스코프 토큰으로 완전히 동작함 (Step 6.5, 설계 확정·구현 대기 — 위 "설계 개요" 참고)
- [ ] Fine-grained 토큰을 웹 UI에서 발급/조회/폐기할 수 있음 (Step 6.6, 설계 확정·구현 대기 — 위 "설계 개요" 참고)
- [ ] Go CLI로 로그인 → 이슈 생성 → PR 목록 조회 골든 패스가 수동 검증 완료 (Part 2, 다음 라운드)
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

## 리스크 / 미결정 사항

| 항목 | 내용 | 해소 방법 |
|---|---|---|
| 스코프 카테고리 확정 | `ResourceType` 33종을 어떻게 그룹핑할지 최종 미확정 | **1라운드에서 해소** — `ApiTokenScopeGroup.kt`(8개 그룹)로 전수 확정, 근거는 위 완료 로그 참고 |
| 기존 전권 토큰 마이그레이션 | 이미 발급된 `User.token` 보유자 처리 방침 미정 | **미해결(다음 라운드로 이월)** — 1라운드는 문서화만 함: 신규 `/api/v1/...` 네임스페이스는 스코프 토큰만 인증하고, 그 외 기존 URL은 레거시 전권 토큰 경로를 그대로 유지하는 co-existence로 임시 처리(`ApiTokenAuthenticationFilter.kt` 주석 참고). 자동 재발급 vs 만료 후 재발급 안내 중 무엇을 택할지, 그리고 레거시 경로를 언제 끊을지는 여전히 미정 |
| 관리자 API 존재 여부 | 백업/웹훅/권한 관리용 서버 API가 이미 있는지 미확인 | Step 9 착수 전 코드 재확인 필요 |
| 프로젝트 조회 API의 스코프 패턴 불일치 | Step6의 `/api/v1/projects/{owner}`(목록)와 `/api/v1/projects/{owner}/{project}`(조회)는 리소스 세그먼트가 없어 `ApiTokenAuthenticationFilter.scopedApiPattern`(owner/project/resource 3단 필수)과 매칭되지 않는다 — Fine-grained 스코프 토큰으로 호출 불가(세션/전권 토큰만 가능), 이슈/PR API는 "issues"/"pull-requests" 세그먼트가 있어 이 문제가 없다 | **설계 확정, 구현은 Step 6.5로 이월(2026-08-29)** — 개별 조회는 "설계 개요"의 `metadata` 스코프 세그먼트(대안 (2) 채택 확정), 목록은 request attribute 기반 필터링으로 설계 확정. 2라운드는 문서화 + AccessControl 기반 대체 검증만 함(`ProjectRestApiController.kt`/`ProjectRestApiControllerSpec.kt` 상단 주석 참고) |
| 토큰 발급/관리 UI 부재 | `ApiTokenRepository`엔 조회 메서드 하나뿐, 사용자가 `ApiToken`을 발급/조회/폐기할 UI·컨트롤러·서비스가 전혀 없어 실사용자는 Fine-grained 토큰을 발급받을 방법이 없다 | **설계 확정, 구현은 Step 6.6으로 이월(2026-08-29)** — 위 "토큰 발급/관리 웹 UI 설계" 참고 |

## 관련

- 백로그 원본: [`docs/PARITY_BACKLOG.md`](../../PARITY_BACKLOG.md#p3-02)
- 관련 계획: [[p3-03-ssh-gpg]], [[p3-07-mcp-server]], [[p3-05-ci-actions-runner]]
- 관련 소스: `config/ApiTokenAuthenticationFilter.kt`, `config/SecurityConfig.kt`, `domain/enumeration/ResourceType.kt`, `web/ProjectApiController.kt`, `web/IssueRestApiController.kt`, `web/PullRequestApiController.kt`, `web/ProjectRestApiController.kt`, `domain/pullrequest/PullRequestServiceImpl.kt`
