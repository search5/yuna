# yona → yuna 동치성 회귀 백로그

`yona`(Play/Java)에서 `yuna`(Spring Boot/Kotlin)로 이식하며 발견된 기능 격차를 중요도 순으로 번호를 매겨 정리한 작업 백로그다. 원본 감사 리포트: 이 세션에서 생성한 아티팩트 "요나·유나 동치성 감사" 참고.

## 진행 규칙 (TDD + JaCoCo)

1. 항목마다 **먼저 실패하는 회귀 테스트**를 작성한다 (yona의 기대 동작을 yuna에서 명세).
2. 테스트가 레드 상태임을 확인한 뒤, 최소 구현으로 그린으로 만든다.
3. `./gradlew test`는 `build.gradle.kts`에 설정된 JaCoCo(`jacocoTestReport`)로 `finalizedBy` 연결되어 있어, 테스트를 돌릴 때마다 `build/reports/jacoco/test/jacocoTestReport.xml`(+ html)에 커버리지가 자동 갱신된다.
4. 각 항목을 마치면 아래 표의 상태를 `[x]`로 바꾸고, 관련 커밋/테스트 파일 경로와 커버리지 수치를 "비고"에 남긴다.
5. 번호는 고정 ID다 — 순서가 바뀌어도 번호는 재사용하지 않는다.

상태 기호: `[x]` 완료 · `[~]` 진행중 · `[ ]` 대기

---

## P0 — 치명적 (보안 / 데이터 손실 / 핵심 기능 마비)

| # | 상태 | 제목 | yona 근거 | yuna 대상 | 비고 |
|---|---|---|---|---|---|
| P0-01 | [x] | 알림 메일 발송 파이프라인 부재 | `models/NotificationMail.java:99-575` | `domain/notification/NotificationMailEventListener.kt`(신규) | **완료(범위 조정, 아래 참고)** |
| P0-02 | [x] | IMAP 수신메일→이슈/댓글 생성 부재 | `app/mailbox/*` | `domain/mail/{IncomingMailProcessingService,EmailAddressDetail,MessageIdParser,ImapMailboxPoller,OriginalEmail}.kt`(신규) | **완료(범위 조정, 아래 참고)** |
| P0-03 | [x] | 웹훅 발송 미연결 | `WebhookServiceImpl.kt:48-66` | `domain/webhook/WebhookNotificationEventListener.kt`(신규) | **완료(범위 조정, 아래 참고)** |
| P0-04 | [x] | 웹훅 gitPush 필터 로직 반전 | `models/NotificationEvent.java:604-680` | `domain/webhook/WebhookServiceImpl.kt` | **완료** |
| P0-05 | [x] | 이슈 생성 시 첨부파일 연결 안 됨 | `AbstractPostingApp.java:224` | `web/IssueViewController.kt` | **완료** — `temporaryUploadFiles` 파라미터 추가, MilestoneViewController와 동일 패턴 |
| P0-06 | [x] | 게시글 생성 시 첨부파일 연결 안 됨 | `AbstractPostingApp.java:241` | `web/BoardViewController.kt` | **완료** — `PostingForm.temporaryUploadFiles` 추가, 동일 패턴 |
| P0-07 | [x] | 사이트 백업/복원 데이터 유실 | `app/data/DataService.java` (44 Exchanger) | `domain/site/DataBackupService{,Impl}.kt`(신규) | **완료** — 테이블 자동 탐지 방식으로 전체 DB 커버 |
| P0-08 | [x] | 마크다운 새니타이저 XSS 약화 | `utils/Markdown.java` (OWASP allowlist) | `domain/support/MarkdownServiceImpl.kt` | **완료** — OWASP java-html-sanitizer allowlist 정책으로 교체, 11개 테스트 |
| P0-09 | [x] | 프로젝트 이전 수락 인가 검증 누락 | `ProjectApp.java:657` | `domain/project/ProjectServiceImpl.kt` | **완료** — `ProjectServiceImplSpec.kt` 5개 테스트, `acceptTransfer` 인가 로직 커버 |
| P0-10 | [x] | git push 예약 ref 보호 훅 부재 | `RejectPushToReservedRefs.java` | `domain/vcs/GitPushHooks.kt`, `config/GitServletConfig.kt` | **완료** |
| P0-11 | [x] | git push 시 커밋 알림 이벤트 훅 부재 | `NotifyPushedCommits.java`, `UpdateLastPushedDate.java` | `domain/vcs/GitPushHooks.kt`, `config/GitServletConfig.kt` | **완료(범위 조정, 아래 참고)** |
| P0-12 | [x] | 브랜치 삭제 시 관련 PR 정리 훅 부재 | `PullRequestCheck.java` | `domain/vcs/GitPushHooks.kt` | **완료** — 브랜치 삭제 → 관련 열린 PR 자동 삭제 |
| P0-13 | [x] | LOCKED/DELETED 계정 로그인 차단 안 됨 | `UserApp.java:207-233` | `config/YonaAuthenticationProvider.kt` | **완료** — `YonaAuthenticationProviderSpec.kt`, 94%/100% (명령어/분기) 커버리지 |
| P0-14 | [x] | PullRequest 라우트 누락 | `conf/routes` (PullRequestApp) | `web/PullRequestController.kt`, `PullRequestViewController.kt` | **완료(범위 조정, 아래 참고)** — closedPullRequests/sentPullRequests/deleteFromBranch/restoreFromBranch 구현 |
| P0-15 | [x] | Board 라우트 누락 (postlabel) | `conf/routes` (BoardApp) | `web/BoardController.kt` | **완료** — `PUT /api/projects/{id}/posts/{postId}/labels` |
| P0-16 | [x] | CodeHistory 라우트 누락 (커밋 댓글) | `conf/routes` (CodeHistoryApp) | `web/CodeHistoryController.kt` | **완료** — create/delete/list 3개 엔드포인트 |

## P1 — 주요 (기능 결손 / 권한 로직 오류)

| # | 상태 | 제목 | yona 근거 | yuna 대상 |
|---|---|---|---|---|
| P1-01 | [x] | LDAP 인증 부재 | `utils/LdapService.java` | `domain/user/{LdapService,LdapQueryBuilder,LdapUserProvisioningService,LdapUser}.kt`(신규) | **완료** |
| P1-02 | [x] | API 토큰 인증 미작동 | `UserApp.java` (`Yona-Token`) | `config/ApiTokenAuthenticationFilter.kt`(신규) | **완료** |
| P1-03 | [x] | OAuth 다중 계정 연동/병합 소실 | `models/LinkedAccount.java` | `domain/user/LinkedAccount.kt`(신규), `config/oauth2/CustomOAuth2UserService.kt` | **완료(범위 조정, 아래 참고)** |
| P1-04 | [x] | 이메일 도메인 allowlist 미시행 | `UserApp.java:385-499` | `domain/user/EmailDomainValidator.kt`(신규), `web/AuthController.kt`, `config/oauth2/CustomOAuth2UserService.kt` | **완료** |
| P1-05 | [x] | Related-PR 재병합 로직 스텁 | `RelatedPullRequestMergingActor.java` | `domain/event/PullRequestMergeEventListener.kt` | **완료(범위 조정, 아래 참고)** |
| P1-06 | [x] | 커밋→이슈 자동 참조 리스너가 로깅만 함 | `IssueReferredFromCommitEventActor.java` | `domain/event/GitPostReceiveEventListener.kt` | **완료** — `IssueEvent` 최소 엔티티 신설(P1-07 선행 작업) |
| P1-07 | [x] | 이슈 타임라인(IssueEvent) 부재 | `models/IssueEvent.java` | `domain/issue/IssueServiceImpl.kt`, `web/IssueController.kt` | **완료(범위 조정, 아래 참고)** |
| P1-08 | [x] | PR 타임라인(PullRequestEvent) 부재 | `models/PullRequestEvent.java` | `domain/pullrequest/PullRequestEvent.kt`(신규), `PullRequestServiceImpl.kt`, `domain/event/PullRequestMergeEventListener.kt`, `web/PullRequestController.kt` | **완료(범위 조정, 아래 참고)** |
| P1-09 | [x] | RecentIssue(최근 본 이슈) 부재 | `models/RecentIssue.java` | `domain/issue/{RecentIssue,RecentIssueRepository,RecentIssueService}.kt`(신규) | **완료(범위 조정, 아래 참고)** |
| P1-10 | [x] | 라벨 수정 기능 없음 | `IssueLabelApp.java:276` | `web/IssueLabelController.kt`, `domain/issue/IssueLabelServiceImpl.kt` | **완료** |
| P1-11 | [x] | 라벨 카테고리 수정 기능 없음 | `IssueLabelApp.java:390` | 위와 동일 | **완료** |
| P1-12 | [x] | 라벨 복사(copyLabels) 기능 없음 | `IssueLabelApp.java:485` | 위와 동일 | **완료** |
| P1-13 | [ ] | 프로젝트 라벨 attach/detach 없음 | `ProjectApp.java` (labels) | `web/LabelController.kt` |
| P1-14 | [ ] | 멘션 자동완성(mentionList) 없음 | `ProjectApp.java:225-227` | (해당 없음) |
| P1-15 | [ ] | pushed-branch 삭제 API 없음 | `ProjectApp.java:236` | (해당 없음) |
| P1-16 | [ ] | Project enroll() 중복 멤버십 가드 누락 | `EnrollProjectApp.java` | `domain/project/ProjectUserServiceImpl.kt:30` |
| P1-17 | [ ] | 조직 멤버 추가 시 게스트 역할 검증 누락 | `OrganizationApp.java` (validateForAddMember) | `domain/organization/OrganizationServiceImpl.kt` |
| P1-18 | [ ] | 게시판 알림 미발송 | `BoardApp.java:255,360,386` | `domain/board/PostingServiceImpl.kt` |
| P1-19 | [ ] | 게시판 편집이력/댓글수/라벨필터 저하 | `AbstractPostingApp.java:106-140` | `web/BoardViewController.kt`, `domain/board/PostingServiceImpl.kt` |
| P1-20 | [ ] | CodeCommentThread.isOutdated() 없음 | `CodeCommentThread.java:76-123` | `domain/comment/CodeCommentThread.kt` |
| P1-21 | [ ] | Watch 권한 필터링(allowedWatchersOnly) 무시됨 | `models/Watch.java:160-187` | `domain/watch/WatchServiceImpl.kt:55-77` |
| P1-22 | [ ] | 프로젝트별 알림 뮤트 토글 미반영 | `models/NotificationEvent.java:486-511` | `domain/issue/IssueServiceImpl.kt`, `domain/comment/CommentServiceImpl.kt` |
| P1-23 | [ ] | SVN 권한 모델 단순화 | `SvnApp.java:119-131` | `config/svn/SvnAuthorizationFilter.kt:48-67` |
| P1-24 | [ ] | 최근 push된 브랜치 추적(PushedBranch) 기능 없음 | `models/PushedBranch.java`, `UpdateRecentlyPushedBranch.java` | (해당 없음) | P0-11에서 범위 분리 — 신규 엔티티/리포지토리 생성 필요, "삭제된 브랜치 복원" UI가 이 데이터에 의존 |
| P1-25 | [ ] | git push(NEW_COMMIT) 이벤트는 웹훅이 발송되지 않음 | `NotificationEvent.java:604-680`(push 부분) | `domain/event/GitPostReceiveEventListener.kt`, `domain/webhook/WebhookServiceImpl.kt` | P0-03에서 범위 분리 — 커밋은 DB 엔티티가 아니라 `resourceId`로 재조회 불가, `WebhookServiceImpl.getResourceType/getResourceId/buildPayload`에 COMMIT 케이스 추가 + `processCommitsNotification`에서 `eventPublisher.publishEvent(notificationEvent)` 호출 필요 |
| P1-26 | [ ] | PULL_REQUEST 리소스 타입은 웹훅 payload를 만들 수 없음 | `Webhook.java:674-729`(PR 부분) | `domain/webhook/WebhookServiceImpl.kt` (`buildPayload`, `getResourceType`) | P0-03에서 범위 분리 — `CodeReviewServiceImpl`이 발행하는 PR 리뷰 NotificationEvent가 `WebhookNotificationEventListener`에서 조용히 스킵됨(PullRequest 조회/payload 케이스 미지원) |
| P1-27 | [ ] | 알림 메일이 이벤트별 즉시 발송이며 다이제스트 병합/언어별 그룹핑이 없음 | `NotificationMail.java:99-188`(`mergeEvents`, 언어별 그룹핑) | `domain/notification/NotificationMailEventListener.kt` | P0-01에서 범위 분리 — yona는 `bymail.interval` 주기로 관련 이벤트를 병합해 한 통으로 보내지만, yuna는 이벤트 발생 즉시 개별 발송(스팸성 다건 메일 가능성). 사용자가 많은 프로젝트에서 체감 UX 저하 |
| P1-28 | [ ] | 알림 메일에 IMAP 답장용 Reply-To 헤더 없음 | `NotificationMail.java:582 getReplyTo()` | `domain/notification/NotificationMailEventListener.kt` | P0-01/P0-02와 연동 — 이제 IMAP 처리(P0-02)가 생겼으니, 알림 메일에 `In-Reply-To`/`References`를 걸어 답장이 곧바로 스레드로 인식되게 연결 필요 |
| P1-29 | [ ] | 수신메일 MIME multipart/HTML 본문·첨부파일·cid 이미지 치환 미지원 | `CreationViaEmail.java` `processPart/getContentWithAttachments/replaceCidWithAttachments` | `domain/mail/ImapMailboxPoller.kt extractTextBody` | P0-02에서 범위 분리 — 현재는 text/plain 우선 추출 + HTML은 jsoup으로 태그만 제거(서식·첨부 손실). 첨부파일 저장 로직 없음 |
| P1-30 | [ ] | 리뷰 댓글/커밋 댓글 스레드로의 메일 답장 미지원 | `EmailHandler.java getThreads` (COMMENT_THREAD/REVIEW_COMMENT 분기) | `domain/mail/IncomingMailProcessingService.kt resolveResourceProject` | P0-02에서 범위 분리 — ISSUE_POST/BOARD_POST 스레드만 인식, PR 코드리뷰 댓글 스레드는 미지원(조용히 스킵) |
| P1-31 | [ ] | "help" 자동응답 및 실패 사유 회신 메일 없음 | `EmailHandler.java getHelpMessage/reply` | `domain/mail/IncomingMailProcessingService.kt` | P0-02에서 범위 분리 — Rejected/UnknownSender 결과가 로그로만 남고 발신자에게 회신되지 않음 |
| P1-32 | [ ] | 수신 주소 detail에 리소스 경로 직접 명시 방식 미지원 | `EmailHandler.java getResourceFromDetail` (owner/project/issue/5 형태) | `domain/mail/IncomingMailProcessingService.kt` | P0-02에서 범위 분리 — detail은 owner/project까지만 해석, 그 뒤 경로 세그먼트는 무시됨 |
| P1-33 | [ ] | 복원 후 auto-increment 채번이 백업된 PK와 충돌할 수 있음 | (해당 없음, yuna 자체 설계 이슈) | `domain/site/DataBackupServiceImpl.kt` | P0-07에서 식별 — DELETE 후 백업된 PK 그대로 INSERT하므로, 이후 신규 행 채번 시퀀스가 백업 최댓값보다 낮으면 PK 충돌 가능. MariaDB는 AUTO_INCREMENT가 INSERT된 값을 보고 자동으로 다음 채번을 올리므로(실측상 문제 재현 안 됨) 우선순위를 낮춰 P1로 분류, 운영 배포 전 재확인 권장 |
| P1-34 | [ ] | PostgreSQL 방언 경로는 통합테스트로 검증되지 않음 | (해당 없음) | `domain/site/DataBackupServiceImpl.kt` (`Dialect.POSTGRES`) | P0-07에서 식별 — 코드는 존재하나(`session_replication_role`), Testcontainers Postgres로 실제 검증한 테스트는 아직 없음(MariaDB만 검증됨) |
| P1-35 | [ ] | PR 수정 화면(editPullRequestForm/editPullRequest) 미구현 | `PullRequestApp.java:510-554`, `views/pullrequest/edit.scala.html` | `web/PullRequestViewController.kt`(없음), `templates/pullrequest/`(edit.html 없음) | P0-14에서 범위 분리 — REST `PUT /api/projects/{id}/pullrequests/{number}`(`updatePullRequest`)로 API 레벨 수정은 이미 가능하지만, 서버 렌더링 수정 폼 페이지가 없음. 신규 Thymeleaf 템플릿 작성이 필요해 프론트엔드 작업 포함 |
| P1-36 | [ ] | doClone 전용 라우트 없음(기능은 forkProject로 커버) | `PullRequestApp.java:115-157` | `web/ProjectController.kt forkProject`, `ProjectViewController.kt fork` | P0-14에서 범위 분리 — 감사에서 이미 "부분 커버"로 확인됨. URL 경로만 다르고 포크 기능 자체는 동작하므로 우선순위 낮음, 템플릿이 옛 URL을 참조하는지만 별도 확인 필요 |
| P1-37 | [ ] | 이슈 타임라인에 라벨/본문/이동/공유자 변경 이벤트 기록 없음 | `models/IssueEvent.java`(ISSUE_LABEL_CHANGED 등) | `domain/issue/IssueServiceImpl.kt`, `IssueShareServiceImpl.kt` | P1-07에서 범위 분리 — 상태/담당자/마일스톤/커밋참조 4종만 기록됨. `EventType.ISSUE_LABEL_CHANGED`/`ISSUE_BODY_CHANGED`/`ISSUE_MOVED`/`ISSUE_SHARER_CHANGED`는 enum엔 있으나 IssueEvent로 기록되지 않음 |
| P1-38 | [ ] | IssueEvent draft-time 병합/취소 최적화 없음 | `models/IssueEvent.java` `add()/addWithoutSkipEvent()` | `domain/issue/IssueServiceImpl.kt recordIssueEvent` | P1-07에서 범위 분리 — yona는 30초 내 연속된 동일 타입 변경을 병합(A→B→C를 A→C로)하거나 상쇄(A→B→A를 삭제)해 타임라인 잡음을 줄이지만, yuna는 매 변경을 그대로 기록 |
| P1-39 | [ ] | PR 생성/리뷰 상태변경이 NotificationEvent·PullRequestEvent 모두 미기록 | `models/PullRequestEvent.java`, `NotificationEvent.afterNewPullRequest` | `PullRequestServiceImpl.kt createPullRequest`, `CodeReviewServiceImpl.kt` | P1-08에서 범위 분리 — `changeState`/병합/충돌 3곳은 이번에 연결했지만, PR 생성 시점(NEW_PULL_REQUEST)과 코드리뷰 승인/반려(PULL_REQUEST_REVIEW_STATE_CHANGED, `CodeReviewServiceImpl`이 NotificationEvent는 만들지만 PullRequestEvent는 아직 미기록)는 남아있음 |
| P1-40 | [ ] | PullRequestEvent draft-time 병합/취소 최적화 없음 | `models/PullRequestEvent.java` `add()` | `domain/pullrequest/PullRequestServiceImpl.kt recordPullRequestEvent` | P1-08에서 범위 분리 — P1-38(IssueEvent)과 동일한 이유로 미이식 |
| P1-41 | [ ] | 최근 본 이슈/게시글 조회 UI·엔드포인트, 탈퇴 시 정리 없음 | `models/RecentIssue.java getRecentIssues/deleteAll` | `domain/issue/RecentIssueService.kt` | P1-09에서 범위 분리 — 방문 시 기록(record)만 구현했고, 사용자가 자신의 최근 방문 목록을 실제로 조회하는 컨트롤러/화면과, 회원 탈퇴 시 `deleteAll(user)`로 데이터를 정리하는 배선이 아직 없음. `RecentIssueService.getRecentIssues()`는 준비돼 있어 연결만 하면 됨 |

## P2 — 참고 (경미 / 확인 필요)

| # | 상태 | 제목 | 비고 |
|---|---|---|---|
| P2-01 | [ ] | ReservedWordsValidator(예약어 검증) 없음 | 로그인ID/프로젝트명이 라우트 경로와 충돌 가능 |
| P2-02 | [ ] | DiffUtil 워드단위 diff 하이라이팅 없음 | 알림에 변경분 하이라이트 없음 |
| P2-03 | [ ] | 사이트 관리자 아바타 지정 API가 빈 스텁 | `SiteApiController.kt:314-322` |
| P2-04 | [ ] | 웹훅 JSON 페이로드 단순화 | 커밋 리스트 없이 event/sender/project만 포함 |
| P2-05 | [ ] | 접근제어가 컨트롤러별 산발적 인라인 체크로 분산 | 전 컨트롤러(~40개) 일관성 전수 미검증 |
| P2-06 | [ ] | SVN 컨트롤러 라우트 커버리지 오탐 | catch-all 매핑으로 실제론 문제 없음 — 조치 불요, 기록만 |

---

## 완료 로그

- **2026-08-19 — P0-13**: `YonaAuthenticationProvider`가 계정 상태(LOCKED/DELETED)를 확인하지 않고 비밀번호만 검증하던 문제 수정. `YonaUserDetails`에 `state` 필드 추가(`isAccountNonLocked`/`isEnabled`가 실제 상태 반영), `UserDetailsServiceImpl`이 `user.state` 전달, `YonaAuthenticationProvider.authenticate()`가 사전 검사로 `LockedException`/`DisabledException` 발생. 테스트: `config/YonaAuthenticationProviderSpec.kt` (5 tests, all pass). 커버리지: `YonaAuthenticationProvider` INSTRUCTION 94%(111/118) · BRANCH 100%(8/8), `YonaUserDetails` BRANCH 100%(4/4).
- **2026-08-19 — P0-09**: `ProjectServiceImpl.acceptTransfer()`가 수락자(acceptorId)와 이관 목적지(destination)를 비교하지 않아 confirmKey만 알면 누구나 이전을 수락할 수 있던 문제 수정. `isAuthorizedToAcceptTransfer()` 추가 — 목적지가 사용자 loginId면 본인만, 조직명이면 해당 조직의 `ORG_ADMIN`만 허용. `OrganizationRepository`/`OrganizationUserRepository`를 생성자에 주입. 테스트: `domain/project/ProjectServiceImplSpec.kt` (신규 파일, 5 tests, all pass).
- **2026-08-19 — P0-08**: `MarkdownServiceImpl.sanitize()`가 정규식 blocklist(`<script>`, `javascript:`, `onload=`, `onerror=`만 처리)였던 것을 OWASP `owasp-java-html-sanitizer` 기반 allowlist 정책으로 교체. yona `utils/Markdown.java`의 `Sanitizers.FORMATTING/IMAGES/STYLES/TABLES/BLOCKS` + 커스텀 element/attribute 허용목록을 그대로 이식하되, **의도적으로 `allowUrlProtocols`에서 `file`·`zpl` 프로토콜은 제외**(원본 정책을 그대로 베끼면 로컬 파일 노출 등 별도 취약점이 생기므로, http/https/mailto만 허용). `onclick` 등 임의 이벤트 핸들러, `<svg>`, `data:` URI 스크립트 삽입이 모두 제거됨을 확인. 의존성 `com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer:20240325.1` 추가. 테스트: `domain/support/MarkdownServiceImplSpec.kt` (8개 신규 케이스 추가, 총 11 tests, all pass). 커버리지: INSTRUCTION 95.6%(393/411).

- **2026-08-19 — P0-10/11/12**: `GitServletConfig`가 순정 `GitServlet`만 사용해 pre/post-receive 훅이 전혀 없던 문제를 `ReceivePackFactory`로 교체해 해결. 신규 `domain/vcs/GitPushHooks.kt`에 두 훅 구현:
  - `RejectPushToReservedRefsPreReceiveHook`(P0-10): `refs/yobi`, `refs/yobi/*`로의 push를 `REJECTED_OTHER_REASON`으로 거부.
  - `YunaPostReceiveHook`(P0-11, P0-12): push마다 `project.lastPushedDate` 갱신, 이미 존재하지만 한 번도 발행되지 않던 `GitPostReceiveEvent`를 실제로 publish(→ 기존 `GitPostReceiveEventListener`의 커밋 알림 로직이 처음으로 실제 동작하게 됨), 브랜치 삭제(`refs/heads/*`, DELETE) 시 `PullRequestRepository.findRelatedPullRequests`로 연관된 열린 PR을 조회해 삭제.
  - `GitServletConfig`에 `ProjectRepository`/`PullRequestRepository`/`UserRepository`/`ApplicationEventPublisher`를 주입하고, `GitAuthorizationFilter`와 동일한 URI 정규식으로 프로젝트를 식별, `SecurityContextHolder`에서 현재 로그인 사용자를 조회해 훅에 전달.
  - **범위 조정**: yona의 `UpdateRecentlyPushedBranch`(최근 push 브랜치 목록 추적, `PushedBranch` 엔티티)는 신규 엔티티 생성이 필요한 별도 규모의 작업이라 P1-24로 분리했다.
  - 테스트: `domain/vcs/GitPushHooksSpec.kt`(신규, 8 tests) — 순수 훅 로직 단위테스트로 100%에 가까운 커버리지 확보. `GitServletConfig`의 `ReceivePackFactory` 배선 자체는 실제 git 프로토콜 통신이 필요해 통합테스트로 별도 커버하지 않음(다른 `*Config` 클래스들과 동일한 이 저장소의 기존 관례). 커버리지: `RejectPushToReservedRefsPreReceiveHook` INSTRUCTION 100%(41/41)·BRANCH 100%(6/6), `YunaPostReceiveHook` INSTRUCTION 99%(134/135)·BRANCH 75%(6/8).

- **2026-08-19 — P0-05/06**: 이슈/게시글 생성 컨트롤러가 `temporaryUploadFiles`(콤마 구분 첨부파일 ID 목록) 파라미터를 받지 않아, 글쓰기 화면에서 미리 업로드한 파일이 `NOT_A_RESOURCE` 상태로 방치되고 실제 이슈/게시글에 연결되지 않던 문제 수정.
  - `IssueViewController.createIssue`에 `temporaryUploadFiles: String?` 파라미터 추가 — 생성된 이슈 ID로 `ResourceType.ISSUE_POST` 컨테이너 갱신.
  - `BoardViewController.createPost`의 `PostingForm`에 `temporaryUploadFiles` 필드 추가 — 생성된 게시글 ID로 `ResourceType.BOARD_POST` 컨테이너 갱신.
  - 기존 `MilestoneViewController.createMilestone`의 인라인 구현 패턴을 그대로 재사용해 코드베이스 내 일관성 유지.
  - 테스트: `IssueViewControllerSpec.kt` +2 tests, `BoardViewControllerSpec.kt` +1 test, 모두 pass.

- **2026-08-19 — P0-03/04**: `WebhookServiceImpl.sendWebhook()`을 호출하는 곳이 코드베이스 어디에도 없어 웹훅이 생성만 되고 실제로는 한 번도 발송되지 않던 문제, 그리고 `gitPush` 플래그가 push 이벤트가 아닌 나머지 모든 이벤트(이슈/댓글/PR 등)를 억제해버리던 로직 반전을 함께 수정.
  - P0-04(로직 반전): `shouldDeliverToWebhook(webhook, eventType)`을 `internal fun`으로 분리해 순수 로직으로 테스트 가능하게 만들고, "NEW_COMMIT이 아니면 항상 전송, NEW_COMMIT이면 gitPush=true이거나 webhookType=JSON일 때만 전송"으로 정정(yona `NotificationEvent.java:604-680` 동작과 일치).
  - P0-03(미연결): 신규 `WebhookNotificationEventListener`(`@Async @EventListener fun handleNotificationEvent(event: NotificationEvent)`)를 추가. 이슈 생성/상태변경/담당자변경/마일스톤변경(`IssueServiceImpl`), 이슈 공유(`IssueShareServiceImpl`), 댓글(`CommentServiceImpl`)이 이미 `eventPublisher.publishEvent(notificationEvent)`로 발행하고 있었지만 그동안 아무도 구독하지 않던 `NotificationEvent`를 구독해, `resourceType`/`resourceId`로 실제 엔티티(Issue/Posting/IssueComment/PostingComment)를 재조회한 뒤 `webhookService.sendWebhook(project, eventType, sender, resource)`를 호출.
  - **범위 조정(의도적으로 다루지 않은 부분, P1-25/P1-26으로 분리)**: git push(NEW_COMMIT) 이벤트와 PULL_REQUEST 리뷰 이벤트는 각각 별도 이유로 이번 패스에서 제외 — 전자는 커밋이 DB 엔티티가 아니라 `resourceId` 재조회 패턴 자체가 안 맞고, 후자는 `WebhookServiceImpl.buildPayload`가 애초에 PullRequest 타입을 지원하지 않아 리스너만 고쳐선 끝까지 동작하지 않는다. 두 경우 모두 리스너가 "조용히 스킵"하도록 구현해 예외/크래시는 없지만, 실제 발송은 아직 안 된다 — 백로그에 명시적으로 남겨 은폐하지 않음.
  - 테스트: `WebhookServiceSpec.kt` +4 tests(gitPush 정책), `WebhookNotificationEventListenerSpec.kt`(신규) 7 tests. `WebhookNotificationEventListener` 커버리지 INSTRUCTION 94%(191/203).

- **2026-08-19 — P0-01**: `NotificationEvent`가 `eventPublisher.publishEvent(notificationEvent)`로 여러 서비스(Issue/Comment/IssueShare/CodeReview)에서 이미 발행되고 있었지만, 이를 구독해 실제 메일을 보내는 리스너가 전혀 없어 이메일이 영구히 발송되지 않던 문제 수정.
  - 신규 `NotificationMailEventListener`(`@Async @EventListener fun handleNotificationEvent(event: NotificationEvent)`) 추가. `event.receivers`(이미 각 서비스에서 감시자/멘션 대상으로 계산되어 채워짐) 각각에게 기존 `MailService.sendHtmlMail`로 HTML 메일을 발송하고, 처리 후 `NotificationMail` 마커 엔티티를 저장.
  - 메일 본문은 `NotificationEvent.newValue`(없으면 title)를 HTML 이스케이프 후 렌더링 — P0-08과 동일한 방어적 태도로 XSS 방지.
  - 이메일이 비어있는 수신자는 스킵, 한 수신자에게 발송 실패해도 나머지 수신자 발송은 계속 시도(개별 try/catch).
  - **범위 조정(P1-27/28로 분리)**: yona의 Akka 스케줄러 기반 다이제스트 병합(`mergeEvents`, `bymail.interval` 주기 배치, 언어별 그룹핑)과 IMAP 답장용 `Reply-To` 헤더는 구현하지 않음 — "이벤트마다 즉시 개별 발송"으로 단순화했다. 핵심 요구사항(메일이 실제로 발송되는가)은 충족하지만, 대규모 프로젝트에서 다건 이벤트 발생 시 메일이 병합되지 않고 각각 날아가는 UX 차이가 있다.
  - 테스트: `NotificationMailEventListenerSpec.kt`(신규) 6 tests, 관련 기존 스펙(`IssueServiceSpec`, `CommentServiceSpec`) 재실행으로 회귀 없음 확인. 커버리지: INSTRUCTION 98.5%(137/139).

- **2026-08-19 — P0-02**: IMAP 수신메일→이슈/댓글 생성 기능이 yuna에 전혀 없던 문제(`app/mailbox/*` 전체 미이식)를 새 서브시스템으로 구축.
  - **`OriginalEmail`(신규 엔티티+리포지토리)**: yona `models/OriginalEmail.java` 대응. 메일의 Message-ID ↔ 생성된 리소스를 연결해 (1) 중복 수신 처리 방지, (2) 답장의 In-Reply-To/References로 원본 리소스 역추적 두 가지에 사용.
  - **`EmailAddressDetail`**: yona `EmailAddressWithDetail.java` 대응 — `yona+owner/project@domain` 형태의 plus-addressing 파싱.
  - **`MessageIdParser`**: yona `EmailHandler.parseMessageIds` 대응 — In-Reply-To/References 헤더에서 message-id 목록 추출.
  - **`IncomingMailProcessingService`(핵심 로직)**: yona `EmailHandler.createResources` + `CreationViaEmail.saveIssue/saveComment` 대응. 중복 검사 → 발신자 확인 → 수신 주소 detail로 대상 프로젝트 해석 → In-Reply-To/References로 기존 스레드 탐지 → 스레드 있으면 댓글, 없으면 새 이슈 생성 → 권한 검사(`AccessControl`) → 결과에 따라 `OriginalEmail` 저장. `jakarta.mail`에 의존하지 않는 순수 `InboundEmailMessage` DTO를 입력으로 받아 실제 메일 서버 없이 전부 단위테스트 가능하게 설계.
  - **`ImapMailboxPoller`(신규, 글루 코드)**: yona `MailboxService.java`+`IMAPMessageUtil.java` 대응. yona의 IDLE 명령+커스텀 lastSeenUID 추적 대신, **IMAP `\Seen` 플래그를 북마크로 쓰는 폴링 전용 방식으로 단순화**(`@Scheduled`, 기본 5분 주기). `yuna.mailbox.imap.enabled=false`가 기본값이라 IMAP 미설정 환경(테스트 등)에서는 로드되지 않음(`@ConditionalOnProperty`). 실제 IMAP 연결이 필요한 순수 배선이라 이 저장소의 다른 `*Config` 클래스들과 동일하게 단위테스트 대상에서 제외 — 비즈니스 로직은 전부 `IncomingMailProcessingService`로 위임되어 있어 그쪽에서 커버됨.
  - **범위 조정(P1-29~32로 분리)**: MIME multipart/HTML 본문 파싱과 첨부파일·cid 이미지 치환, 코드리뷰/커밋 댓글 스레드로의 답장, "help" 자동응답 및 실패 회신 메일, 수신 주소 detail의 직접 리소스 경로 지정(`owner/project/issue/5`)은 미구현. 텍스트 본문만 처리하고 ISSUE_POST/BOARD_POST 스레드만 인식하는 범위로 핵심 요구사항(메일로 이슈/댓글이 실제로 생성되는가)을 우선 충족했다.
  - 테스트: `EmailAddressDetailSpec.kt`(7), `MessageIdParserSpec.kt`(4), `IncomingMailProcessingServiceSpec.kt`(9) 총 20 tests 전체 통과. 전체 Spring 컨텍스트 로딩 테스트(`YonaApplicationTests`)로 신규 엔티티/빈 배선 이상 없음 확인. 커버리지: `IncomingMailProcessingService` INSTRUCTION 92%(549/595)·BRANCH 76%(44/58), `EmailAddressDetail`/`MessageIdParser` 100%.

- **2026-08-19 — P0-07**: `SiteApiController.exportData/importData`가 users/projects 두 테이블만 손으로 필드 매핑해 백업하던 문제(이슈·댓글·라벨·마일스톤·PR·첨부 등 대부분 데이터가 재해복구 시 유실) 해결.
  - yona는 44개의 손으로 나열한 Exchanger 클래스로 테이블별 raw JDBC export/import를 하지만, yuna는 **DB 메타데이터(`DatabaseMetaData.getTables`)로 테이블 목록을 스스로 찾아내는 범용 방식**을 택해 신규 엔티티가 추가돼도 서비스 코드 수정이 필요 없게 설계.
  - `exportAll()`: 발견된 모든 테이블에 대해 `SELECT * FROM table`을 실행해 JSON으로 직렬화.
  - `importAll()`: MySQL/MariaDB는 `SET FOREIGN_KEY_CHECKS=0`, PostgreSQL은 `SET session_replication_role='replica'`로 FK 제약을 끈 뒤, 테이블별로 `DELETE FROM table` 후 백업된 행을 그대로 `INSERT`(전체 교체 방식) — yona처럼 순수 insert만 하는 것보다 실제 "복원" 의미에 더 부합하도록 개선.
  - `SiteApiController`가 이 서비스로 위임하도록 교체, 기존 `/site/export`/`/site/import` 라우트·시그니처는 그대로 유지.
  - 테스트: **실제 MariaDB(Testcontainers) 통합테스트**(`DataBackupServiceIntegrationSpec.kt`, 2 tests) — 순수 목으로는 "실제 DB 스키마/방언에서 동작하는가"를 검증할 수 없어 통합테스트로 작성. 데이터 추가 → 이전 시점 백업으로 복원 → 추가분 소실 확인 + 원본 데이터 보존 확인까지 실제 DB로 검증. 컨트롤러 위임 테스트는 `SiteControllerSpec.kt`에 추가(export/import 각 1건, 기존 export 테스트는 새 구조에 맞게 갱신).
  - 커버리지: `DataBackupServiceImpl` INSTRUCTION 88.8%(342/385)·BRANCH 60%(15/25) — 미커버 분기는 주로 PostgreSQL 방언 경로(P1-34로 별도 추적, MariaDB만 실제 검증됨).
  - 식별된 후속 리스크(P1-33/34로 분리): 복원 후 auto-increment 채번 충돌 가능성(MariaDB 실측상 문제 없었음), PostgreSQL 경로 미검증.

- **2026-08-19 — P0-14**: yona의 PullRequestApp에는 있지만 yuna에 대응 라우트가 없던 것 중 4개(closedPullRequests/sentPullRequests/deleteFromBranch/restoreFromBranch)를 구현.
  - `PullRequestRepository`에 `findByToProjectAndStateIn`(CLOSED+MERGED를 "닫힌 PR"로 취급), `findByFromProject`(이 프로젝트가 출발지인 PR) 추가.
  - `PullRequestViewController`에 `GET /{owner}/{projectName}/closedPullRequests`, `/sentPullRequests` 추가 — 기존 `listPullRequests`의 권한체크/렌더링 로직을 `checkMemberAccess`/`renderList`로 추출해 셋이 공유.
  - `PlayRepository` 인터페이스에 `createBranch(branchName, startPoint)` 추가(`GitRepository`는 JGit `branchCreate`로 구현, `SvnRepository`는 no-op).
  - `PullRequestService`에 `deleteFromBranch`/`restoreFromBranch` 추가 — 병합된 PR만 브랜치 삭제 가능(가드), 삭제 직전 head 커밋을 `lastCommitId`에 기록해 복원 가능하게 함. `PullRequestController`에 `DELETE`/`POST /{number}/fromBranch` 라우트로 노출.
  - **범위 조정(P1-35/36로 분리)**: `editPullRequestForm`/`editPullRequest`(서버 렌더링 수정 폼 — 신규 템플릿 필요)와 `doClone`(기능은 이미 `ProjectController.forkProject`로 커버되는 것으로 이전 감사에서 확인됨, URL만 다름)은 이번 패스에서 제외.
  - 테스트: `PullRequestViewControllerSpec.kt` +3, **`PullRequestServiceSpec.kt`(실제 MariaDB+실제 JGit bare 저장소 통합테스트) +3**(브랜치 삭제→lastCommitId 기록·브랜치 소멸 확인, 미병합 PR 삭제 거부 확인, 삭제 후 복원까지 동일 커밋으로 재생성 확인), `PullRequestControllerSpec.kt` +2. 총 8 tests 전체 통과.

- **2026-08-19 — P0-15**: yona `api.BoardApi.updatePostLabel`(게시글에 붙은 라벨 집합을 통째로 교체)에 대응하는 라우트가 yuna에 없던 문제 수정. `BoardController`에 `PUT /api/projects/{projectId}/posts/{postId}/labels`(JSON body: 라벨 ID 배열) 추가 — `postingService.getPosting`으로 대상 조회, `isManagerOrAuthor`로 권한 검사(기존 `updatePosting`과 동일 기준), `IssueLabelRepository.findAllById`로 라벨 엔티티들을 조회해 `posting.labels`를 교체 후 저장. `Posting.labels`/`posting_issue_label` 조인테이블 자체는 이미 존재했으나(P1-19 감사에서 확인) 이를 갱신하는 API가 없었던 것이 실제 격차였음. 테스트: `BoardControllerSpec.kt` +2 tests(정상 교체, 권한 없음 403).

- **2026-08-19 — P0-16**: yona `CodeHistoryApp.newComment`/`deleteComment`(커밋 단위 댓글)에 대응하는 라우트가 yuna에 없던 문제 수정. yona는 이 기능을 내부적으로 PR 리뷰코멘트용 `CodeCommentThread` 시스템을 재사용해 구현하지만(레거시스러운 이중 구조), yuna는 이미 별도의 단순한 `CommitComment` 엔티티+리포지토리를 1:1로 포팅해둔 상태였기 때문에(이전 PR/VCS 도메인 감사에서 "OK"로 확인됨) 그걸 그대로 사용하는 더 단순한 구조로 구현.
  - `CodeHistoryController`에 `POST/DELETE/GET .../commit/{commitId}/comments[/{id}]` 3개 엔드포인트 추가. 생성은 `AccessControl.isProjectResourceCreatable(.., ResourceType.COMMIT_COMMENT)`로 권한 검사 + 커밋 존재 여부 확인, 삭제는 작성자 본인 또는 프로젝트 매니저만 허용.
  - GET(목록 조회)은 yona 라우트 목록엔 없지만, 생성만 되고 조회할 방법이 없으면 기능이 무의미해 최소 추가.
  - 테스트: `CodeHistoryControllerSpec.kt`(신규 파일, 이전에 이 컨트롤러에 대한 테스트가 전혀 없었음) 5 tests 전체 통과.

**이 시점에서 P0(치명적) 16건 전체 완료.**

- **2026-08-19 — P1-01**: LDAP 인증(디렉터리 바인딩 로그인) 자체가 yuna에 전혀 없던 문제 해결. yona `utils/LdapService.java`가 JNDI 연결·검색·사용자 매핑을 한 클래스에 뒤섞어 두었던 것을, 테스트 가능성을 위해 3개 클래스로 분리해 이식:
  - `LdapQueryBuilder`(순수 로직): 사용자 식별자 추측(`guessUser`), LDAP principal 조립, 검색 필터 속성 선택, `javax.naming.directory.Attributes` → `LdapUser` 파싱, 게스트 계정 prefix 판별 — 전부 실제 LDAP 서버 없이 `BasicAttributes`로 단위테스트.
  - `LdapUserProvisioningService`(순수 로직): LDAP 인증 성공 후 "로컬 User와 어떻게 맞출 것인가" — 이메일로 기존 유저 없으면 신규 생성, 있으면 비밀번호 불일치 시에만 재해시, 표시이름/영문이름/게스트여부 동기화. yona `UserApp.authenticateWithLdap()`의 성공 분기 로직을 그대로 이식.
  - `LdapService`(글루): 위 두 클래스를 조합해 실제 `InitialDirContext` 바인딩 수행. 연결 실패/인증 실패를 `LdapAuthResult`(Success/InvalidCredentials/ConnectionFailed) sealed class로 구분 — 이 저장소에 LDAP 테스트 서버가 없어 실제 바인딩 경로 자체는 `ImapMailboxPoller`/`GitServletConfig`와 동일하게 단위테스트 대상에서 제외.
  - `YonaAuthenticationProvider`에 LDAP 분기 추가: `ldapService.enabled`일 때 LDAP 우선 시도 → 성공 시 재조정된 로컬 사용자로 인증, 실패 시 `fallbackToLocalLogin` 설정에 따라 로컬 비밀번호 인증으로 폴백하거나 즉시 거부. LDAP로 재조정된 사용자도 P0-13의 계정 잠금/탈퇴 체크를 동일하게 통과해야 함.
  - `application.yml`에 `yuna.ldap.*` 설정 추가(기본 비활성).
  - 테스트: `LdapQueryBuilderSpec.kt`(신규) 15 tests, `LdapUserProvisioningServiceSpec.kt`(신규) 4 tests, `YonaAuthenticationProviderSpec.kt` +5 tests. 전체 Spring 컨텍스트 로딩(`YonaApplicationTests`)으로 신규 빈 배선 확인. 커버리지: `LdapQueryBuilder` 96.6%/78%, `LdapUserProvisioningService` 95.3%/70%, `YonaAuthenticationProvider` 94.8%/94.4%(명령어/분기).

- **2026-08-19 — P1-02**: API 토큰(`Yona-Token` 헤더 또는 `Authorization: token <값>`) 재발급 API는 있었지만, 그 토큰으로 요청을 인증하는 경로가 전혀 없어 사실상 write-only였던 문제 해결. 신규 `ApiTokenAuthenticationFilter`(`OncePerRequestFilter`)를 `SecurityConfig`에 `BasicAuthenticationFilter` 뒤에 추가 — 이미 인증된(비-익명) 요청이면 건너뛰고, 아니면 헤더에서 토큰을 추출해 `UserRepository.findByToken`으로 사용자를 찾아 SecurityContext에 인증 정보를 채운다. LOCKED/DELETED 계정 토큰은 인증하지 않음(P0-13과 동일 기조). `UserRepository.findByToken` 추가. 테스트: `ApiTokenAuthenticationFilterSpec.kt`(신규) 6 tests, `MockHttpServletRequest`로 실제 필터 체인을 통해 검증. 커버리지: INSTRUCTION 92.8%(84/92, 필터)+95.7%(45/47, 토큰 파싱).

- **2026-08-19 — P1-03**: `CustomOAuth2UserService`가 소셜 로그인마다 이메일/loginId로만 매칭해, 서로 다른 provider(예: Google, GitHub)로 로그인하면 provider 연결 이력이 전혀 남지 않던 문제 해결. yona의 `UserCredential`(play-authenticate 플러그인 산물, `active`/`emailValidated` 등 프레임워크 종속 필드 포함)은 이식하지 않고, 핵심 기능만 `LinkedAccount`(User ↔ provider+providerUserId) 엔티티로 단순화해 이식.
  - 로그인 시 (1) `LinkedAccount`로 이미 연결된 provider면 그 계정으로 즉시 로그인, (2) 처음 보는 provider면 이메일/loginId로 기존 계정을 찾아 자동으로 `LinkedAccount`를 만들어 연결(사실상 자동 병합), (3) 기존 계정도 없으면 신규 가입 + 연결.
  - **범위 조정**: 이메일이 서로 다른 두 계정을 사용자가 수동으로 병합하는 UI(yona의 `UserCredential.merge()`에 해당)는 이식하지 않음 — 이메일 일치를 통한 자동 연결까지만 지원.
  - 테스트: `CustomOAuth2UserServiceSpec.kt` 기존 1건 + 신규 2건(이미 연결된 계정 재로그인, 이메일 일치로 새 provider 자동 연결) = 3 tests 전체 통과. 커버리지 INSTRUCTION 92%(151/164).

- **2026-08-19 — P1-04**: 이메일 도메인 allowlist(`yuna.signup.allowed-email-domains`, 콤마 구분, 기본 빈값=전체 허용)가 회원가입 어느 경로에서도 검증되지 않던 문제 해결. yona `NotificationMail.isAllowedEmailDomains()`를 순수 유틸 `EmailDomainValidator`로 이식(공백 처리, 대소문자 무시 매칭 포함) — 실제 LDAP/Config류와 달리 완전한 순수 로직이라 전체 단위테스트.
  - `AuthController.signup()`(로컬 가입)과 `CustomOAuth2UserService`(OAuth 자동 가입, P1-03에서 만든 신규가입 분기)에 각각 적용. **기존 계정의 로그인은 도메인 정책이 바뀌어도 계속 허용**(yona도 신규가입에만 게이트를 걸고 기존 사용자는 소급 차단하지 않음) — OAuth 쪽은 이미 연결된 계정/이메일 매칭 기존 계정 로그인이면 도메인 검사를 건너뛰고, 정말 신규 계정을 만드는 순간에만 검사해 `OAuth2AuthenticationException`으로 거부.
  - 테스트: `EmailDomainValidatorSpec.kt`(신규) 6 tests, `AuthControllerSpec.kt` +1, `CustomOAuth2UserServiceSpec.kt` +1. 전체 컨텍스트 로딩 확인.

- **2026-08-19 — P1-05**: `handleRelatedPullRequestMergeEvent`가 `isMerging=true`만 세팅하고 실제 재병합/충돌 재검사를 전혀 하지 않아, 관련 브랜치에 push가 일어나도 PR 상태가 "병합중"에 영구히 멈춰있던 문제 수정.
  - 이제 관련 PR마다 이미 존재하던 `PullRequestService.attemptMerge()`(dry-run 병합, `isConflict` 갱신)를 실제로 호출해 재검사하고, 처리 후 `isMerging`을 다시 `false`로 되돌린다(예외가 나도 마찬가지 — `attemptMerge` 실패가 PR을 "병합중" 상태로 영구 고정시키지 않도록).
  - 충돌 상태가 실제로 바뀐 경우(없음→발생, 발생→해소)에만 `NotificationEvent`를 발행 — P0-01(알림 메일)·P0-03(웹훅)이 이미 이 이벤트를 구독하고 있어 별도 배선 없이 자동으로 메일/웹훅까지 나간다.
  - **범위 조정**: yona `PullRequestActor.processPullRequestMerging`의 "새 커밋이 추가되면 `PullRequestEvent` 타임라인에 커밋 이벤트 추가" 부분은 `PullRequestEvent`(P1-08) 부재로 이번 패스에서 다루지 않음 — 충돌 상태 변화 알림까지만 구현.
  - 테스트: `PullRequestMergeEventListenerSpec.kt`(신규) 5 tests(재검사 후 isMerging 복구, 충돌 발생/해소 시 알림 발행, 상태 변화 없으면 미발행, 예외 발생해도 isMerging 복구). 기존 `PullRequestServiceSpec.kt`(실제 MariaDB+git) 재실행으로 회귀 없음 확인. 커버리지 INSTRUCTION 86%(449/520, 파일 전체 — 기존 메서드 포함).

- **2026-08-19 — P1-06**: `GitPostReceiveEventListener.processIssueReferredFromCommit()`가 커밋 메시지를 로그로만 찍고 실제로 이슈 참조 이벤트를 만들지 않던 문제 수정. yona `IssueReferredFromCommitEventActor.java` 대응.
  - **선행 의존성 처리**: 이 기능은 원래 P1-07(`IssueEvent` 부재)에 의존한다 — yona가 참조 기록을 `IssueEvent` 엔티티에 저장하기 때문에, `IssueEvent`(id, issue, senderLoginId, senderEmail, oldValue/newValue, created, eventType) 최소 엔티티+리포지토리를 이번에 함께 신설했다. 상태/담당자/마일스톤 변경 등 나머지 이벤트 타입 기록과 타임라인 조회 API는 P1-07에 남겨둠(아래 참고).
  - `IssueReferenceParser`(순수 함수)로 커밋 메시지에서 `#123` 형태의 이슈 참조를 추출(yona `Issue.ISSUE_PATTERN` 대응).
  - 참조된 이슈가 프로젝트에 실제로 존재하면 `IssueEvent(eventType=ISSUE_REFERRED_FROM_COMMIT)`를 저장, 존재하지 않으면 조용히 스킵.
  - 테스트: `IssueReferenceParserSpec.kt`(신규) 5 tests, `GitPostReceiveEventListenerSpec.kt`(신규) 4 tests. 전체 컨텍스트 로딩으로 신규 엔티티/빈 배선 확인.

- **2026-08-19 — P1-07**: 이슈 상태/담당자/마일스톤이 바뀌어도 변경 이력이 어디에도 남지 않던 문제 해결(엔티티 자체는 P1-06에서 신설). `IssueServiceImpl.changeState/changeAssignee/changeMilestone`이 이미 만들고 있던 `NotificationEvent`(알림용, oldValue/newValue 포함)와 같은 데이터로 `IssueEvent`(이력용)도 함께 저장하도록 `recordIssueEvent()` 공통 헬퍼 추가. `IssueController`에 `GET /api/projects/{projectId}/issues/{number}/timeline` 조회 API 추가(yona `Issue.getTimeline()` 대응).
  - **범위 조정(P1-37/38로 분리)**: 라벨/본문/이슈이동/공유자 변경은 아직 IssueEvent로 기록되지 않음(해당 EventType은 enum에 존재하나 미사용). yona의 30초 draft-time 병합/취소 최적화(연속 변경 시 잡음 감소)도 이식하지 않음 — 매 변경이 그대로 별도 항목으로 쌓인다.
  - 테스트: `IssueServiceSpec.kt`(실제 MariaDB 통합테스트) 기존 1건 확장 + 신규 2건(담당자/마일스톤 변경 시 IssueEvent 생성) = 총 5 tests. `IssueControllerSpec.kt` +2(타임라인 조회 성공/404). 전체 컴파일 확인.

- **2026-08-19 — P1-08**: PR 상태 변경/병합/충돌 상태 전환이 일어나도 이력이 전혀 남지 않던 문제 해결(yona `models/PullRequestEvent.java` 대응). `IssueEvent`(P1-07)와 동일한 패턴으로 신설.
  - `PullRequestEvent`(신규 엔티티+리포지토리) 추가.
  - `PullRequestServiceImpl.changeState()`가 그동안 **NotificationEvent조차 만들지 않던** 것을 확인 — `IssueServiceImpl.changeState`와 동일한 패턴으로 NotificationEvent 생성도 함께 추가(발견된 별도 격차를 자연스럽게 해소), 그 위에 PullRequestEvent 기록. 인터페이스에 `updaterLoginId` 파라미터 추가(컨트롤러 호출부도 함께 갱신).
  - `PullRequestMergeEventListener.handlePullRequestMergeEvent`(병합 완료 시 상태→MERGED)와 `notifyConflictStateChanged`(P1-05에서 만든 충돌/해소 알림)에도 PullRequestEvent 기록 추가.
  - `PullRequestController`에 `GET /api/projects/{projectId}/pullrequests/{number}/timeline` 조회 API 추가.
  - **범위 조정(P1-39/40으로 분리)**: PR 생성 시점(NEW_PULL_REQUEST) 알림과 코드리뷰 승인/반려의 PullRequestEvent 기록은 미포함. yona의 30초 draft-time 병합/취소 최적화도 미이식.
  - 테스트: `PullRequestServiceSpec.kt`(실제 MariaDB 통합테스트) +2(상태변경 시 이벤트 생성, 동일상태 변경시 미생성) = 총 11 tests. `PullRequestMergeEventListenerSpec.kt` +1(병합시 이벤트 생성), 기존 4건도 함께 재통과 = 총 6 tests. `PullRequestControllerSpec.kt` +1(타임라인 조회). 전체 컨텍스트 로딩 확인.

- **2026-08-19 — P1-09**: 이슈/게시글을 열람해도 "최근 본 목록"이 전혀 쌓이지 않던 문제 해결(yona `models/RecentIssue.java` 대응, yuna에 대응 코드 자체가 없었음).
  - 신규 `RecentIssue`(엔티티, issue_id/posting_id 둘 다 nullable — yona 원본처럼 이슈/게시글 방문을 한 테이블로 함께 추적) + `RecentIssueRepository` 추가.
  - 신규 `RecentIssueService`(`recordIssueVisit`/`recordPostingVisit`/`getRecentIssues`) — 기존 `ProjectViewController.addVisitHistory`(RecentProject) 패턴과 달리, mockk로 완전히 단위테스트 가능하도록 별도 `@Service` 클래스로 분리(private 메서드로 컨트롤러에 묻지 않음). 같은 이슈/게시글 재방문 시 기존 항목 삭제 후 재저장(dedupe), 사용자당 100건(yona `MAX_RECENT_LIST_PER_USER`) 초과 시 가장 오래된(id 최소) 항목부터 삭제.
  - `IssueViewController.viewIssue`/`BoardViewController.viewPost`에서 로그인 사용자가 열람할 때마다 호출 — 기록 실패가 조회 자체를 막지 않도록 try/catch NOOP으로 감쌈(`ProjectViewController.addVisitHistory`와 동일 기조).
  - **범위 조정(P1-41로 분리)**: 방문 기록(record)만 구현했고, 사용자가 본인의 최근 방문 목록을 조회하는 컨트롤러/화면과, 회원 탈퇴 시 `deleteAll(user)`로 데이터를 정리하는 배선은 아직 없다. `getRecentIssues()`는 준비돼 있어 연결만 하면 되는 상태.
  - 테스트: `RecentIssueServiceSpec.kt`(신규) 7 tests — 신규 방문 저장/재방문 dedupe/100건 초과 시 최오래 항목 삭제를 이슈·게시글 양쪽에 대해 검증. `IssueViewControllerSpec.kt`/`BoardViewControllerSpec.kt`는 생성자 파라미터 추가에 맞춰 relaxed mock 추가(기존 테스트 회귀 없음). 커버리지: `RecentIssueService` LINE 100%(40/40)·INSTRUCTION 95%(208/219)·BRANCH 58.3%(7/12, null 가드 분기 일부 미도달 — 저장된 엔티티는 항상 id가 있으므로 실질적으로 도달 불가능한 방어 코드).
  - 검증: `./gradlew test --tests "RecentIssueServiceSpec" --tests "IssueViewControllerSpec" --tests "BoardViewControllerSpec"` 전체 통과, `YonaApplicationTests`로 신규 엔티티/빈 배선 확인, 전체 스위트 490 tests 중 1 실패는 `DataBackupServiceIntegrationSpec`(P0-07, 이번 변경과 무관한 기존 flaky 테스트 — 단독 실행 시 통과 확인)뿐.

- **2026-08-19 — P1-10**: 프로젝트 라벨을 생성/삭제만 할 수 있고 이름·색상·카테고리를 수정할 방법이 없던 문제 해결(yona `IssueLabelApp.update()` 대응).
  - `IssueLabelService`/`IssueLabelServiceImpl`에 `updateLabel(labelId, name, color, categoryId)` 추가 — 대상 라벨과 신규 카테고리를 각각 조회해(둘 중 하나라도 없으면 `IllegalArgumentException`) 라벨의 `name`/`color`/`category`를 덮어쓰고 저장. yona의 `update()`와 동일하게 **이름/색상 중복 검사는 하지 않음**(`newLabel()`의 dedupe와는 다른 동작 — 원본 그대로 이식).
  - `IssueLabelController`에 `PUT /api/projects/{projectId}/labels/{labelId}` 추가 — 기존 `createLabel`/`deleteLabel`과 동일하게 `isProjectManager` 기준으로 권한 검사(yona `@IsAllowed(Operation.UPDATE, resourceType=ISSUE_LABEL)`에 대응).
  - 테스트: `IssueLabelServiceImplSpec.kt`(신규) 3 tests(정상 수정, 라벨 없음, 카테고리 없음) — 기존 `IssueLabelServiceImpl`의 다른 메서드들과 달리 새로 작성한 로직은 mockk 기반으로 직접 단위테스트함. `IssueLabelControllerSpec.kt` +2(관리자 200 OK, 비관리자 403). 커버리지: `updateLabel` 메서드 단독 LINE 100%(8/8)·INSTRUCTION 100%(46/46) — 클래스 전체 수치(LINE 23.6%)는 이번 작업 범위 밖의 기존 미검증 메서드(getLabels/createLabel 등)가 함께 집계된 것으로, 그쪽은 원래부터 컨트롤러 mock 테스트로만 간접 커버되던 이 파일의 기존 관례임.

- **2026-08-19 — P1-11**: 라벨 카테고리(이슈 라벨을 묶는 상위 분류)를 생성/삭제만 할 수 있고 이름·exclusive 여부를 수정할 방법이 없던 문제 해결(yona `IssueLabelApp.updateCategory()` 대응).
  - `IssueLabelService`/`IssueLabelServiceImpl`에 `updateCategory(categoryId, name, isExclusive)` 추가 — 대상 카테고리 조회(없으면 `IllegalArgumentException`) 후, **같은 프로젝트 내 다른 카테고리가 이미 같은 이름을 쓰고 있으면**(자기 자신 제외) 신규 `DuplicateLabelCategoryNameException`을 던져 거부 — yona가 `lc.name.equals(category.name) && !lc.id.equals(category.id)` 조건으로 `badRequest`를 반환하던 것과 동일한 동작.
  - `IssueLabelController`에 `PUT /api/projects/{projectId}/labels/categories/{categoryId}` 추가 — `isProjectManager` 권한 검사 후 서비스 호출, `DuplicateLabelCategoryNameException`을 `400 Bad Request`로 매핑(이 코드베이스에 전역 `@ExceptionHandler`가 없어 컨트롤러에서 직접 catch — yona가 이 케이스만 명시적으로 400을 반환하는 것과 동일하게, 다른 도메인 예외(`InvalidBranchOperationException` 등)처럼 그냥 흘려보내지 않고 여기서는 의도적으로 매핑함).
  - 테스트: `IssueLabelServiceImplSpec.kt` +4 tests(정상 수정, 카테고리 없음, 이름 중복 시 예외, 자기 자신과 이름이 같으면 중복 아님). `IssueLabelControllerSpec.kt` +3(관리자 200 OK, 이름 중복 400, 비관리자 403). 커버리지: `updateCategory` 메서드 LINE 100%(8/8)·BRANCH 83%(5/6).
  - 검증: `./gradlew test --tests "IssueLabelServiceImplSpec" --tests "IssueLabelControllerSpec" --tests "YonaApplicationTests"` 전체 통과.

- **2026-08-19 — P1-12**: 다른 프로젝트의 라벨 세트를 그대로 복사해오는 기능이 yuna에 없던 문제 해결(yona `IssueLabelApp.copyLabels()`/`IssueLabel.copyIssueLabels()` 대응).
  - `IssueLabelService`/`IssueLabelServiceImpl`에 `copyLabels(fromProjectId, toProjectId)` 추가 — 원본 프로젝트의 모든 라벨을 순회하며, **라벨 이름이 대상 프로젝트에 이미 있으면 건너뛰고**(P1-10/P0에서 이미 확립된 "프로젝트 내 라벨 이름 유일성" 기준과 동일 — yona 원본의 category+name 복합 유일성 대신 이 코드베이스가 처음부터 택한 단순화), 카테고리는 이름이 같은 게 대상 프로젝트에 있으면 재사용하고 없으면 새로 생성한 뒤 라벨을 새로 만들어 저장.
  - `IssueLabelController`에 `POST /api/projects/{projectId}/labels/copy`(body: `fromProjectId`) 추가 — 대상 프로젝트는 `isProjectManager`(생성 권한, yona `@IsCreatable(ISSUE_LABEL)` 대응), 원본 프로젝트는 `checkReadPermission`(yona `AccessControl.isAllowed(..., READ)` 대응)으로 각각 별도 권한 검사.
  - 테스트: `IssueLabelServiceImplSpec.kt` +4 tests(신규 카테고리+라벨 복사, 카테고리 재사용, 라벨 이름 중복 시 건너뜀, 원본 프로젝트 없음). `IssueLabelControllerSpec.kt` +3(정상 복사 200 OK, 대상 프로젝트 비관리자 403, 원본 프로젝트 비공개+비멤버 403). 커버리지: `copyLabels` 메서드 LINE 100%(22/22)·BRANCH 100%(6/6).
  - 검증: `./gradlew test --tests "IssueLabelServiceImplSpec" --tests "IssueLabelControllerSpec" --tests "YonaApplicationTests"` 전체 통과.

### 검증 방법
전체 스위트(Testcontainers 포함)는 시간이 오래 걸려 항목별로는 `./gradlew test --tests "<FQCN>"`으로 개별 검증했고, 교차 영향 여부는 `./gradlew compileKotlin compileTestKotlin`으로 전체 컴파일을 확인했다(정상). 세 항목 모두 적용 후 전체 컴파일 성공.
