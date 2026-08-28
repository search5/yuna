---
type: plan
id: P3-01
title: "Observability(메트릭/로깅/트레이싱) 인프라 도입"
status: planned
priority: 1
depends_on: []
blocks: []
source: docs/PARITY_BACKLOG.md#P3-01
created: 2026-08-28
updated: 2026-08-28
tags: [plan, p3, observability]
---

# Observability(메트릭/로깅/트레이싱) 인프라 도입

## 배경

`build.gradle.kts`에 `spring-boot-starter-actuator`+`micrometer-registry-prometheus`가 이미 포함돼 있고 `/actuator/health`,
`/actuator/prometheus`도 노출돼 있지만, 커스텀 메트릭 코드(`MeterRegistry`/`@Timed`/`Counter.builder`)가 저장소 전체에
0건이라 JVM/HTTP 기본 지표만 나가고 비즈니스 지표는 전혀 없다. 구조화 로깅과 분산 트레이싱 인프라도 없다.
`@Async`/`@EventListener` 체인이 많은 아키텍처(웹훅 발송·PR 재병합·알림메일 다이제스트)라 트레이싱 부재가 특히 아쉽다.
원본: [`docs/PARITY_BACKLOG.md#P3-01`](../../PARITY_BACKLOG.md)

## 범위

### 포함
- 메트릭: 기존 Prometheus 스크랩 경로에 커스텀 `MeterRegistry` 계측 6곳 추가
- 구조화 로깅: `logstash-encoder` 기반 JSON 로그
- 분산 트레이싱: `micrometer-tracing-bridge-otel` + OTLP

### 제외 (비범위)
- Grafana 대시보드 자체의 패널 설계(계측이 끝난 뒤 별도 작업)
- Loki/Promtail, Tempo/Jaeger 등 수집 인프라의 K3s/Traefik 배포 자체(홈랩 인프라 구성은 이 문서의 범위 밖 — 애플리케이션 쪽 계측만 다룬다)

## 의존성

- **선행 조건**: 없음 — 7개 P3 항목 중 유일하게 완전히 독립적이며 즉시 착수 가능
- **후속 파급**: [[p3-05-ci-actions-runner]]의 러너 폴링/잡 실행, [[p3-02-cli-and-rest-api]]의 신규 REST API 모두 운영 단계에서 이 인프라를 그대로 사용하게 되므로, 먼저 끝내두면 이후 계획들의 계측 비용이 줄어든다(강한 의존은 아님, 순서상 유리할 뿐)

## 설계 개요

- **메트릭 소비처**: 기존 Prometheus 스크랩 그대로 사용, 신규 인프라 구축 불필요
- **로깅**: `logback-spring.xml` 신설 + `logstash-encoder` → JSON 포맷 → Loki/Promtail
- **트레이싱**: `micrometer-tracing-bridge-otel` + OTLP exporter → Tempo/Jaeger
- **계측 지점 6곳**(전부 실존 클래스, 코드로 확인됨):

| # | 클래스 | 계측 내용 |
|---|---|---|
| 1 | `domain/notification/NotificationEventRecorder.kt`의 `record()` | 전체 알림이 거치는 단일 지점 — `eventType`/`resourceType` 태그 카운터로 시스템 전체 활동량 |
| 2 | `IssueEvent`/`PullRequestEvent`의 draft-time 병합 확장함수(P1-38/40 대응) | "새 이벤트 저장" vs "직전 이벤트 병합" 비율 |
| 3 | `domain/notification/NotificationMailDigestScheduler.kt` | 60초 배치 처리시간, 병합률, 발송 성공/실패, 대기 큐 적체 게이지 |
| 4 | `domain/mail/ImapMailboxPoller.kt` | 폴링 사이클 소요시간, 처리 메시지 수, UID 재동기화 발생 카운터 |
| 5 | `domain/event/PullRequestMergeEventListener.kt` | 처리 PR 수, `attemptMerge` 소요시간, 충돌 상태 전이 카운터(P1-52의 5종 부수효과 실제 발동 검증용) |
| 6 | `domain/webhook/WebhookNotificationEventListener.kt` + `domain/vcs/GitPushHooks.kt` | 웹훅 발송 성공/실패(HTTP status 태그)+응답시간, git push 훅 처리시간+`PushedBranch` 갱신 건수 |

## 단계별 작업 계획 (TDD)

1. **Step 1 — 로깅 기반 정비**
   - `logback-spring.xml` 신설(JSON 인코더), 기존 텍스트 로그와 병행 출력 여부 결정
   - 검증: 로그 라인이 유효한 JSON으로 파싱되는지 통합 테스트
2. **Step 2 — 계측 지점 1(NotificationEventRecorder)**
   - `record()`에 `Counter.builder("yona.notification.events").tag("eventType", ...).tag("resourceType", ...)` 추가
   - 실패 테스트: 이벤트 기록 후 `MeterRegistry`에서 카운터 값이 태그별로 증가했는지 단언 → RED → 구현 → GREEN
3. **Step 3 — 계측 지점 2~6**
   - 지점마다 동일 패턴(테스트 먼저 → RED → `@Timed`/`Counter`/`Gauge` 추가 → GREEN) 반복
   - 배치 처리 지점(3, 4)은 소요시간을 `Timer.Sample`로, 성공/실패는 태그 있는 `Counter`로 분리
4. **Step 4 — 트레이싱 브리지 도입**
   - `micrometer-tracing-bridge-otel` 의존성 추가, OTLP exporter 설정
   - `@Async` 경계를 넘는 트레이스 컨텍스트 전파 확인(웹훅 발송 체인 하나를 골라 trace-id가 끊기지 않는지 수동 검증)
5. **Step 5 — 회귀 스위트**
   - `./gradlew test`로 계측 코드가 기존 비즈니스 로직 테스트를 깨지 않았는지 확인

## 완료 기준 (Definition of Done)

- [ ] 계측 지점 6곳 모두 `/actuator/prometheus`에 커스텀 지표로 노출
- [ ] 구조화 JSON 로그 출력 확인
- [ ] `@Async`/`@EventListener` 체인 최소 1개 경로에서 트레이스 컨텍스트가 끊기지 않음을 확인
- [ ] 각 계측 지점에 대응하는 단위 테스트(카운터/타이머 값 검증) 존재
- [ ] `./gradlew test` 전체 GREEN 유지

## 리스크 / 미결정 사항

| 항목 | 내용 | 해소 방법 |
|---|---|---|
| 착수 우선순위 | "메트릭부터 할지 6곳 한번에 할지" 사용자 결정 대기 상태였음(원본 백로그) | 이 계획서에서는 Step 순서(로깅→계측 6곳→트레이싱)로 확정. 다른 순서를 원하면 조정 |
| 수집 인프라 | Loki/Promtail, Tempo/Jaeger의 실제 배포는 범위 밖으로 정함 | 별도 인프라 작업으로 분리, 이 계획은 애플리케이션 계측까지만 |

## 관련

- 백로그 원본: [`docs/PARITY_BACKLOG.md`](../../PARITY_BACKLOG.md#p3-01)
- 관련 계획: [[p3-05-ci-actions-runner]](운영 단계에서 동일 인프라 사용)
- 관련 소스: `domain/notification/NotificationEventRecorder.kt`, `domain/notification/NotificationMailDigestScheduler.kt`, `domain/mail/ImapMailboxPoller.kt`, `domain/event/PullRequestMergeEventListener.kt`, `domain/webhook/WebhookNotificationEventListener.kt`, `domain/vcs/GitPushHooks.kt`
