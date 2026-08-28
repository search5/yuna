---
type: plan
id: P3-02
title: "yuna CLI 설계 (+ 범용 REST API·Fine-grained 토큰 모델 선행 구축)"
status: planned
priority: 2
depends_on: []
blocks: [p3-03-ssh-gpg, p3-07-mcp-server, p3-05-ci-actions-runner]
source: docs/PARITY_BACKLOG.md#P3-02
created: 2026-08-28
updated: 2026-08-28
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

### 2부 — Go CLI

7. **Step 7 — CLI 스캐폴딩**: `yona auth login/logout/status`(Personal Access Token 입력·저장)
8. **Step 8 — `yona issue`/`yona pr`/`yona project` 하위 명령**: 1부 API를 감싸는 얇은 HTTP 클라이언트
9. **Step 9 — `yona admin backup/webhook/permission`**: 기존 관리자 API 확인 후 연결(신규 서버 API 필요 시 1부 패턴으로 추가)
10. **Step 10 — `yona api <method> <path>`**: 저수준 원시 호출 명령(디버깅/스크립팅용)
11. **Step 11 — 배포**: `goreleaser`로 GitHub Releases + Homebrew tap + Scoop bucket + `.deb`/`.rpm`

## 완료 기준 (Definition of Done)

- [ ] `ApiToken` 엔티티가 `expires_at` 필수로 강제됨을 테스트로 보장
- [ ] `ApiTokenAuthenticationFilter`가 스코프 밖 요청을 403으로 거부함을 테스트로 보장
- [ ] 이슈/PR/프로젝트 REST API가 CRUD 전체를 커버하고, 각 엔드포인트에 권한 스코프 검증 테스트 존재
- [ ] Go CLI로 로그인 → 이슈 생성 → PR 목록 조회 골든 패스가 수동 검증 완료
- [ ] `./gradlew test` 전체 GREEN, JaCoCo 95%/95%/95% 유지(`docs/COVERAGE_BACKLOG.md` 기준)

## 리스크 / 미결정 사항

| 항목 | 내용 | 해소 방법 |
|---|---|---|
| 스코프 카테고리 확정 | `ResourceType` 33종을 어떻게 그룹핑할지 최종 미확정 | Step 2 착수 전 그룹 목록을 표로 확정하고 이 문서에 반영 |
| 기존 전권 토큰 마이그레이션 | 이미 발급된 `User.token` 보유자 처리 방침 미정 | Step 3에서 마이그레이션 전략 결정(자동 재발급 vs 만료 후 재발급 안내) |
| 관리자 API 존재 여부 | 백업/웹훅/권한 관리용 서버 API가 이미 있는지 미확인 | Step 9 착수 전 코드 재확인 필요 |

## 관련

- 백로그 원본: [`docs/PARITY_BACKLOG.md`](../../PARITY_BACKLOG.md#p3-02)
- 관련 계획: [[p3-03-ssh-gpg]], [[p3-07-mcp-server]], [[p3-05-ci-actions-runner]]
- 관련 소스: `config/ApiTokenAuthenticationFilter.kt`, `config/SecurityConfig.kt`, `domain/enumeration/ResourceType.kt`, `web/ProjectApiController.kt`, `domain/pullrequest/PullRequestServiceImpl.kt`
