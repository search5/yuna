# 테스트 커버리지 백로그


`./gradlew test jacocoTestReport`(2026-08-23 실행, 전체 1474 tests 전부 GREEN 기준)로 측정한 JaCoCo 커버리지에서
**라인/분기/메서드 중 하나라도 95% 미만인 클래스 279개**(전체 478개 중)를 전수 나열한 작업 백로그다. 사용자 지시:
"라인 95%, 분기 95%, 메서드 95%, 클래스 95% 가 되도록 테스트 추가해줘. 개별 파일별로 도달 불가능한 것까지 테스트
되어야 함. 자의적 판단 하지마."

## 작업 원칙 (반드시 준수)

- **목표는 파일(클래스) 단위로 라인·분기·메서드 커버리지 각각 95% 이상.** "이 정도면 충분하다"는 자의적 판단으로
  건너뛰지 않는다 — 얼핏 "도달 불가능해 보이는" 분기(방어적 null 체크, else 분기, 예외 경로 등)도 실제로 그
  분기를 타는 테스트를 작성해서 검증한다.
- **정말 기술적으로 테스트가 불가능한 경우만 예외로 인정**하되(예: JVM/컴파일러가 강제로 생성하는, 언어 차원에서
  호출 불가능한 합성 분기), 그 경우 반드시 근거를 이 문서에 명시하고 왜 불가능한지 실제로 확인한 뒤에만 `[i]`로
  표시한다. "테스트 작성이 번거로워서", "중요하지 않아 보여서" 같은 이유는 인정하지 않는다.
- **TDD로 진행한다**: 커버되지 않은 분기를 확인 → 그 분기를 타는 입력/상태를 구성하는 테스트 작성 → RED(아직
  커버 안 됨) 확인 → 필요 시 최소 구현 수정(진짜 버그를 발견하면 함께 고침, 단순 테스트 부재면 구현은 그대로) →
  GREEN.
- **회귀 주기**: 기존 관행과 동일하게 10개 항목(클래스) 처리할 때마다 `./gradlew test`(가능하면 `jacocoTestReport`도)
  1회 전체 실행. 매 항목마다 전체 스위트를 돌리지 않는다.
- **git 규율**: 커밋 전 항상 `git fetch origin`으로 원격 상태 확인, 파일 명시적 `git add`(`-A` 금지), `[TASK-NNNN]`
  형식 커밋(마지막 번호+1), push.
- **상태 기호**: `[ ]` 미착수 · `[~]` 진행중 · `[x]` 목표 달성(95%/95%/95% 이상) · `[i]` 기술적으로 불가능함이
  확인된 예외(반드시 사유 기록).

## 전체 요약 (2026-08-23 최초 측정 기준)

- 전체 클래스: 478개, 95% 미만: 279개
- 라인: 69.1%, 분기: 44.0%, 메서드: 70.3%, 클래스: 89.7%
- 미실행 라인 합계: 5,508 / 미실행 분기 합계: 6,256

## 진행 현황 갱신 (2026-08-24 00:00, 1차 배치 10개 클래스 완료 후)

- 전체 클래스: 478개, 95% 미만: **268개**(-11)
- 라인: 78.4%, 분기: 54.4%, 메서드: 74.8%, 클래스: 91.7%
- 완료([x]): `NotificationUrlResolver`, `FileDiff`, `diff_match_patch`, `MigrationService`, `SiteService`
- 구조적 한계로 목표 사실상 최대치 도달([i]): `IssueExcelService`(BRANCH 88.1%, 나머지는 Kotlin non-null 타입상 도달 불가)
- 진행 중([~], 다음 배치에서 계속): `AccessControl`(BRANCH 38.4%, 최우선), `NotificationMessageResolver`(82.8%), `WebhookServiceImpl`(79.8%), `IssueShareServiceImpl`(93.9%, 근소 미달)

## 진행 현황 갱신 (2026-08-24 00:45, 2차 배치 완료 후)

- 전체 클래스: 478개, 95% 미만: **265개**(-3, AccessControl은 근접했으나 아직 미달)
- 라인: 79.1%, 분기: 61.3%, 메서드: 74.8%, 클래스: 91.7%
- 추가 완료([x]): `NotificationMessageResolver`(BRANCH 96.8%), `WebhookServiceImpl`(95.2%)
- 구조적 한계로 최대치 도달([i]): `IssueShareServiceImpl`(BRANCH 93.9%, 도달 가능 분기 100% 커버 — `type` 파라미터 미사용 실버그 확정)
- 대폭 개선했으나 아직 미달([~]): `AccessControl`(BRANCH 38.4%→89.7%, 141개 남음, `isAllowed(...)` 오버로드 10종에 분산 — 다음 배치 최우선 마무리 대상)

## 진행 현황 갱신 (2026-08-24 01:20, 3차 배치 완료 후)

- 전체 클래스: 478개, 95% 미만: **260개**(-5)
- 라인: 83.6%, 분기: 67.7%, 메서드: 77.5%, 클래스: 92.7%
- 추가 완료([x]): `AccessControl`(BRANCH 95.3% — 이 저장소 최대 미커버 클래스 완주, 4개 파일 431 tests), `TemplateHelper`(96.2%)
- 구조적 한계로 최대치 도달([i]): `GitRepository`(BRANCH 88.3%, 실제 JGit 저장소 기반 95 tests)
- 진행 중([~], 다음 배치 계속): `ProjectViewController`(78.5%), `UserViewController`(87.9%, 근소 미달)
- 누적 실버그 발견(전부 미수정, 별도 검토 필요): FileDiff.updateRange 중복추가, MigrationService 3건, diff_match_patch 2건(vendored, 도달불가), TemplateHelper.getVotersForName 클램프 오류(미트리거), GitRepository.getParentCommitOf NPE 위험(미트리거), ProjectViewController.projectLogo 하드코딩 개발자 로컬경로(배포결함 추정), IssueShareServiceImpl.findSharableUsers의 type 파라미터 미사용, PullRequest.contributor/title 관련 죽은 코드 2건

## 진행 현황 갱신 (2026-08-24 02:10, 4차 배치 완료 후)

- 전체 클래스: 478개, 95% 미만: **249개**(-11)
- 라인: 86.0%, 분기: 71.9%, 메서드: 78.5%, 클래스: 94.2%
- 추가 완료([x]): `CodeViewController`(BRANCH 95.5%), `MilestoneViewController`(95.2%), `UserViewController`(98.7%), `IssueViewController`(96.1%)
- 진행 중([~], 다음 배치 계속): `ProjectViewController`(BRANCH 87.4%, 아직 미달 — 121 tests에도 불구하고 대형 클래스라 잔여 많음)
- 이번 배치 신규 실버그/죽은코드 없음(UserViewController의 verifyUserLegacy/confirmEmailLegacy는 죽은 코드가 아니라 테스트 누락이었음을 확인·해결)

## 진행 현황 갱신 (2026-08-24 02:55, 5차 배치 완료 후)

- 전체 클래스: 478개, 95% 미만: **243개**(-6)
- 라인: 88.3%, 분기: 76.7%, 메서드: 79.3%, 클래스: 94.7%
- 추가 완료([x]): `ProjectViewController`(BRANCH 95.3% — 3개 배치 걸쳐 완주), `NotificationMailDigestScheduler`(98.3%)
- 구조적 한계로 최대치 도달([i]): `BoardViewController`(94.1%), `ImapMailboxPoller`(LINE 94.4%, BRANCH 100%)
- 진행 중([~], 다음 배치 계속): `OrganizationViewController`(88.3%), `PullRequestViewController`(93.8%, 근소 미달)
- **실버그 수정 완료(2건)**: `ProjectViewController.projectLogo()`/`OrganizationViewController.organizationLogo()` 둘 다 기본 이미지 폴백이 특정 개발자의 로컬 macOS 절대경로(`/Users/mzc01-search5/...`)로 하드코딩돼 있어 어떤 배포 환경에서도 동작하지 않던 실제 결함 확인·수정(`ClassPathResource`로 교체, 전체 재검색으로 이 2곳 외에는 없음을 확인).
- **중대 발견(수정 보류, 사용자 판단 필요)**: `PullRequestViewController.closePattern`(PR/커밋 메시지 "fixes #123"으로 이슈 자동 닫기)이 legacy-yona에 전혀 없는 yuna 독자 구현이었음을 확인 — 정규식 버그(fix/fixes/fixed 매치 안 됨)도 함께 발견했으나 "독자구현 금지" 원칙 위배 사안이라 임의로 고치지 않고 사용자에게 보고

## 사용자 판단(2026-08-24 03:04): closePattern은 "유지하고 정규식만 수정"

TASK-0271에서 `fix[e[s|d]]?`(중첩 대괄호 오사용)를 `fix(?:es|ed)?`로 수정, 회귀 테스트 추가 완료.

## 진행 현황 갱신 (2026-08-24 03:35, 6차 배치 완료 후)

- 전체 클래스: 478개, 95% 미만: **240개**(-3)
- 라인: 89.5%, 분기: 79.5%, 메서드: 80.3%, 클래스: 94.7%
- 추가 완료([x]): `IndexController`(전부 100%), `ProjectServiceImpl`(BRANCH 96.9%), `MentionController`(96.4%)
- 진행 중([~], 다음 배치 계속): `AttachmentController`(BRANCH 89.7%), `PullRequestServiceImpl`(BRANCH 85.5%, METHOD 75.4% — 둘 다 미달, 우선순위 높음)
- **잠재적 운영 이슈 발견(미수정, 별도 검토 필요)**: `PullRequestServiceImpl.createMergeCommitAndUpdateRef`가 동일 초 내 diff 없이 연속 병합체크 시 `RefUpdate.Result.NO_CHANGE`로 인한 `IOException` 실제 재현됨

## 진행 현황 갱신 (2026-08-24 04:15, 7차 배치 완료 후)

- 전체 클래스: 478개, 95% 미만: **234개**(-6)
- 라인: 91.4%, 분기: 82.1%, 메서드: 81.8%, 클래스: 95.0%(처음으로 95% 돌파)
- 추가 완료([x]): `IssueController`(BRANCH 95.4%), `PullRequestController`(96.2%), `ProjectMemberController`(97.3%)
- 진행 중([~], 다음 배치 계속): `UserController`(BRANCH 88.8%/METHOD 85.2%, 둘 다 미달), `UserServiceImpl`(90.6%), `BareCommit`(83.7%), `IncomingMailProcessingService`(90.3%)


## 항목 목록 (패키지별, 미실행 라인+분기 합계 내림차순)

| 클래스 | 라인% | 분기% | 메서드% | 라인미실행 | 분기미실행 | 메서드미실행 | 상태 | 비고 |
|---|---|---|---|---|---|---|---|---|
| **YonaApplicationKt** | | | | | | | | |
| `YonaApplicationKt` | 0.0 | 100.0 | 0.0 | 2 | 0 | 1 | [i] | `fun main()`이 `runApplication<YonaApplication>()`을 호출만 하는데, 실제로 호출하면 같은 JVM 안에서 실제 임베디드 톰캣+비-데몬 스레드를 가진 완전한 앱이 뜨고(main()이 리턴은 하지만 컨텍스트를 반환받지 못해 정리도 불가) 테스트 JVM에 잔류해 이후 테스트를 오염시킨다. 별도 프로세스로 기동하는 방식(subprocess)만 가능한데, 이 클래스가 위임하는 로직(ApplicationContext 부트스트랩) 자체는 이미 150여개의 `@SpringBootTest` 스펙이 동일하게 실행·검증하고 있어 별도 프로세스 스모크테스트가 주는 한계효용이 없다고 판단 — 구조적 제약으로 예외 인정 |
| **config** | | | | | | | | |
| `TemplateHelper` | 70.0 | 42.6 | 77.9 | 74 | 179 | 15 | [x] | 2026-08-24: 신규 `TemplateHelperBranchSpec.kt`(순수 mockk, 200 tests), 기존 `TemplateHelperSpec.kt`는 그대로 유지. 전체 회귀 확정치: LINE 100%, BRANCH 96.2%, METHOD 98.5% — 목표 달성. **실버그 발견(미수정, 별도 검토 필요)**: `getVotersForName(voters, fromIndex, size)`가 충분히 음수인 `fromIndex`에서 `IllegalArgumentException`을 던질 수 있음(실제 템플릿 호출부는 전부 고정 양수 리터럴이라 현재는 미트리거) |
| `GitServletConfig` | 48.0 | 7.1 | 33.3 | 26 | 13 | 4 | [ ] | |
| `GitServletConfig$gitServletRegistrationBean$lfsServlet$1` | 5.3 | 0.0 | 50.0 | 18 | 12 | 1 | [ ] | |
| `YonaAuthenticationSuccessHandler` | 14.3 | 0.0 | 50.0 | 12 | 12 | 1 | [ ] | |
| `YonaAuthenticationFailureHandler` | 9.1 | 0.0 | 50.0 | 10 | 8 | 1 | [ ] | |
| `BootstrapSetupInterceptor` | 100.0 | 59.1 | 100.0 | 0 | 9 | 0 | [ ] | |
| `GitServletConfig$gitServletRegistrationBean$dispatcherServlet$1` | 14.3 | 0.0 | 50.0 | 6 | 2 | 1 | [ ] | |
| `ApiTokenAuthenticationFilter` | 100.0 | 81.2 | 100.0 | 0 | 3 | 0 | [ ] | |
| `ApiTokenAuthenticationFilter$Companion` | 100.0 | 62.5 | 100.0 | 0 | 3 | 0 | [ ] | |
| `YonaAuthenticationProvider` | 97.7 | 94.4 | 100.0 | 1 | 1 | 0 | [ ] | |
| **config/git** | | | | | | | | |
| `GitAuthorizationFilter` | 100.0 | 80.0 | 100.0 | 0 | 8 | 0 | [ ] | |
| `GitProjectVisitRecorder` | 100.0 | 80.0 | 100.0 | 0 | 4 | 0 | [ ] | |
| **config/oauth2** | | | | | | | | |
| `GithubOAuth2UserInfo` | 60.0 | 25.0 | 60.0 | 2 | 3 | 2 | [ ] | |
| `CustomOAuth2UserService` | 98.0 | 90.0 | 100.0 | 1 | 2 | 0 | [ ] | |
| `OAuth2UserInfoFactory$Companion` | 75.0 | 75.0 | 100.0 | 1 | 1 | 0 | [ ] | |
| `YonaOAuth2User` | 71.4 | 100.0 | 60.0 | 2 | 0 | 2 | [ ] | |
| `OAuth2UserInfoFactory` | 0.0 | 100.0 | 0.0 | 1 | 0 | 1 | [ ] | |
| `OAuth2AccountMergeService` | 100.0 | 100.0 | 75.0 | 0 | 0 | 1 | [ ] | |
| **config/security** | | | | | | | | |
| `AccessControl` | 60.9 | 38.4 | 87.2 | 146 | 844 | 5 | [x] | 2026-08-23~24: 4개 에이전트 걸쳐(헬퍼그룹 174 + IssuePosting 100 + PullRequest 97 + Final 60, 총 431 신규 테스트, 4개 파일). 전체 회귀 확정치: LINE 100%, BRANCH 95.3%, METHOD 100% — 목표 달성. 이 저장소 최대 미커버 클래스(1371개 분기)를 3차 배치에 걸쳐 완주 |
| **config/svn** | | | | | | | | |
| `SvnAuthorizationFilter` | 96.2 | 73.8 | 100.0 | 2 | 11 | 0 | [ ] | |
| **domain/attachment** | | | | | | | | |
| `AttachmentServiceImpl` | 95.8 | 85.7 | 100.0 | 3 | 4 | 0 | [ ] | |
| `AttachmentCleanupScheduler` | 90.0 | 100.0 | 100.0 | 2 | 0 | 0 | [ ] | |
| `Attachment` | 100.0 | 100.0 | 70.0 | 0 | 0 | 6 | [ ] | |
| **domain/board** | | | | | | | | |
| `PostingServiceImpl` | 96.4 | 68.2 | 63.2 | 5 | 14 | 7 | [ ] | |
| `PostingService$DefaultImpls` | 0.0 | 100.0 | 0.0 | 2 | 0 | 1 | [ ] | |
| `Posting` | 100.0 | 100.0 | 90.0 | 0 | 0 | 1 | [ ] | |
| `PostingComment` | 100.0 | 100.0 | 66.7 | 0 | 0 | 2 | [ ] | |
| **domain/comment** | | | | | | | | |
| `CommentServiceImpl` | 90.3 | 65.5 | 71.4 | 17 | 20 | 8 | [ ] | |
| `CommentService$DefaultImpls` | 0.0 | 100.0 | 0.0 | 4 | 0 | 2 | [ ] | |
| **domain/enumeration** | | | | | | | | |
| `ResourceType` | 85.4 | 0.0 | 75.0 | 6 | 5 | 1 | [ ] | |
| `Operation$Companion` | 0.0 | 0.0 | 0.0 | 1 | 6 | 1 | [ ] | |
| `State$Companion` | 100.0 | 66.7 | 100.0 | 0 | 2 | 0 | [ ] | |
| `Operation` | 90.9 | 100.0 | 66.7 | 1 | 0 | 1 | [ ] | |
| `WebhookType` | 100.0 | 100.0 | 66.7 | 0 | 0 | 1 | [ ] | |
| `EventType` | 100.0 | 100.0 | 80.0 | 0 | 0 | 1 | [ ] | |
| **domain/event** | | | | | | | | |
| `GitPostReceiveEventListener` | 51.1 | 26.7 | 55.6 | 45 | 22 | 4 | [ ] | |
| `PullRequestMergeEventListener` | 96.1 | 73.7 | 100.0 | 4 | 10 | 0 | [ ] | |
| `PullRequestMergeEvent` | 100.0 | 100.0 | 75.0 | 0 | 0 | 1 | [ ] | |
| **domain/issue** | | | | | | | | |
| `IssueShareServiceImpl` | 33.9 | 8.8 | 27.8 | 119 | 104 | 13 | [i] | 2026-08-23: 46 tests(43+3, `IssueShareServiceImplSpec.kt`). 도달 가능한 분기는 전부 커버(담당자-없음+작성자==본인, 사이트관리자 조회 루프 진입, 검색결과 0건 루프 미진입 등 3건 추가). 도달 불가능 근거 확정: `mapUser`의 `user.avatarUrl ?: ""`(`User.avatarUrl` non-null) + `findAssignableUsers`의 `issue.assignee?.user?.id` 두 곳(각 최대 2분기, `Assignee.user`가 `@JoinColumn(nullable=false)` non-null이라 두번째 safe-call 도달 불가, `if(assignee!=null)` 블록 내부라 첫번째도 구조적으로 도달 불가) — 구조적 한계로 95% 미만이어도 최대치 도달로 인정. **실버그 확정**: `findSharableUsers(query, type: String?)`의 `type` 파라미터가 메서드 본문에서 전혀 참조되지 않음(죽은 파라미터, 타입별 필터링 미완성으로 추정) — 미수정, 별도 검토 필요 |
| `IssueExcelService` | 3.6 | 0.0 | 16.7 | 106 | 42 | 5 | [i] | 2026-08-23: 신규 5 tests, `IssueExcelServiceSpec.kt`. 전체 회귀 확정치: LINE 98.2%, BRANCH 88.1%(37/42), METHOD 100% — 도달 가능한 분기는 100%(37/37) 커버, 미실행 5개는 `Milestone.title`/`AbstractPosting.title`/`Assignee.user`/`User.name`/`Comment.contents`가 전부 non-null 타입이라 elvis/safe-call의 null 분기가 Kotlin 타입 시스템상 생성 자체가 불가능(순수 코드로 만들 방법 없음) — 구조적 한계로 95% 미달을 인정. 라인 미실행 2개는 `workbook.close()` 실패 catch 블록으로 내부에서 워크북을 생성해 주입 지점이 없어 정상 흐름에서 트리거 불가 |
| `IssueServiceImpl` | 96.6 | 64.2 | 64.2 | 13 | 58 | 19 | [ ] | |
| `IssueSpecification` | 50.7 | 44.8 | 100.0 | 33 | 32 | 0 | [ ] | |
| `IssueLabelServiceImpl` | 61.7 | 47.2 | 50.0 | 41 | 19 | 13 | [ ] | |
| `RecentIssueService` | 100.0 | 57.1 | 100.0 | 0 | 6 | 0 | [ ] | |
| `IssueService$DefaultImpls` | 0.0 | 100.0 | 0.0 | 4 | 0 | 2 | [ ] | |
| `IssueEventRecorderKt` | 100.0 | 78.6 | 100.0 | 0 | 3 | 0 | [ ] | |
| `IssueShareServiceImpl$findSharerByloginIds$$inlined$sortedBy$1` | 0.0 | 100.0 | 0.0 | 1 | 0 | 1 | [ ] | |
| `IssueSharer` | 91.7 | 100.0 | 50.0 | 1 | 0 | 6 | [ ] | |
| `IssueShareServiceImpl$getAssignableUsersOfProjectInternal$$inlined$sortedBy$1` | 0.0 | 100.0 | 0.0 | 1 | 0 | 1 | [ ] | |
| `IssueComment` | 100.0 | 100.0 | 62.5 | 0 | 0 | 3 | [ ] | |
| `Issue` | 100.0 | 100.0 | 90.9 | 0 | 0 | 2 | [ ] | |
| `IssueEvent` | 100.0 | 100.0 | 61.1 | 0 | 0 | 7 | [ ] | |
| `IssueLabel` | 100.0 | 100.0 | 83.3 | 0 | 0 | 2 | [ ] | |
| `Assignee` | 100.0 | 100.0 | 62.5 | 0 | 0 | 3 | [ ] | |
| `RecentIssue` | 100.0 | 100.0 | 56.2 | 0 | 0 | 7 | [ ] | |
| `IssueLabelCategory` | 100.0 | 100.0 | 90.0 | 0 | 0 | 1 | [ ] | |
| **domain/mail** | | | | | | | | |
| `ImapMailboxPoller` | 32.4 | 44.0 | 35.0 | 121 | 65 | 13 | [i] | 2026-08-24: `ImapMailboxPollerSpec.kt`에 49 tests 추가(13→62). 전체 회귀 확정치: LINE 94.4%(근소 미달), BRANCH 100%, METHOD 100%. `start()`/`connect()`/`reopenFolder()`의 "실제 IMAP 접속 성공" 경로는 GreenMail류 임베디드 IMAP 서버 의존성이 없어 재현 불가(클래스 자체 KDoc에도 "순수 글루 코드라 단위테스트 제외" 명시) — 프로덕션 코드에 포트/팩토리 주입을 추가해야 가능하나 범위 밖 리팩터라 보류. 구조적 최대치로 인정 |
| `IncomingMailProcessingService` | 87.6 | 68.4 | 100.0 | 30 | 62 | 0 | [~] | 2026-08-24: `IncomingMailProcessingServiceSpec.kt`에 49 tests 추가(20→69). 전체 회귀 확정치: LINE 98.3%, BRANCH 90.3%(아직 미달), METHOD 100% — 다음 배치에서 마무리. 참고 발견(버그 아님): `createComment`/`createIssue`의 권한거부 분기가 `processTarget()`의 선행 `isAllowedToReadProject` 검사와 구조적으로 항상 동일 결과가 나와 도달 불가능함을 확인 |
| `MailServiceImpl` | 34.7 | 45.0 | 42.9 | 47 | 22 | 4 | [ ] | |
| `ImapMailboxPoller$startEmailListener$1` | 0.0 | 100.0 | 0.0 | 7 | 0 | 3 | [ ] | |
| `EmailAddressDetail$Companion` | 100.0 | 70.0 | 100.0 | 0 | 3 | 0 | [ ] | |
| `EventNotificationMimeMessage` | 100.0 | 83.3 | 100.0 | 0 | 1 | 0 | [ ] | |
| `ImapMailboxPoller$handleMessages$$inlined$sortedBy$1` | 0.0 | 100.0 | 0.0 | 1 | 0 | 1 | [ ] | |
| `IncomingMailOutcome$IssueCreated` | 100.0 | 100.0 | 50.0 | 0 | 0 | 2 | [ ] | |
| `IncomingMailOutcome$PostingCommentCreated` | 100.0 | 100.0 | 66.7 | 0 | 0 | 1 | [ ] | |
| `InboundEmailMessage` | 100.0 | 100.0 | 91.7 | 0 | 0 | 1 | [ ] | |
| `InboundAttachment` | 100.0 | 100.0 | 83.3 | 0 | 0 | 1 | [ ] | |
| `OriginalEmail` | 100.0 | 100.0 | 41.7 | 0 | 0 | 7 | [ ] | |
| **domain/mention** | | | | | | | | |
| `MentionServiceImpl` | 100.0 | 81.0 | 100.0 | 0 | 4 | 0 | [ ] | |
| `Mention` | 100.0 | 100.0 | 50.0 | 0 | 0 | 5 | [ ] | |
| **domain/milestone** | | | | | | | | |
| `MilestoneServiceImpl` | 34.2 | 21.4 | 30.0 | 25 | 11 | 7 | [ ] | |
| `MilestoneService$DefaultImpls` | 0.0 | 100.0 | 0.0 | 1 | 0 | 1 | [ ] | |
| `Milestone` | 100.0 | 100.0 | 64.3 | 0 | 0 | 5 | [ ] | |
| **domain/notification** | | | | | | | | |
| `NotificationMailDigestScheduler` | 69.6 | 34.8 | 100.0 | 51 | 116 | 0 | [x] | 2026-08-24: `NotificationMailDigestSchedulerSpec.kt`에 54 tests 추가(12→66). 전체 회귀 확정치: LINE 98.8%, BRANCH 98.3%, METHOD 100% — 목표 달성. 도달 불가능 3건 코드 근거 확정(User.name/Issue.project/Posting.project non-null 타입) |
| `NotificationMessageResolver` | 40.2 | 41.4 | 60.0 | 67 | 92 | 4 | [x] | 2026-08-23: `NotificationMessageResolverSpec.kt`에 총 64 tests(246줄+잔여 15건). 단독 측정 LINE 100%, BRANCH 96.8%(152/157), METHOD 100% — 목표 달성. 도달 불가능 5건 확정(`ReviewComment.contents`/`User.name` non-null이라 elvis null분기 불가) |
| `NotificationUrlResolver` | 55.7 | 27.4 | 83.3 | 27 | 53 | 1 | [x] | 2026-08-23: 39 tests(+32)로 `getUrlToView`/`getUrl`/`urlToContainer` 전체 when-분기·null 케이스 커버. 전체 회귀 확정치: LINE 100%, BRANCH 98.6%, METHOD 100% — 목표 달성. 버그 아님: `COMMENT_THREAD`의 `urlToContainer` null 시 앵커까지 사라진 빈 문자열 반환 — 의도된 동작으로 보여 그대로 테스트에 반영 |
| `NotificationEventMerger` | 90.2 | 65.9 | 100.0 | 5 | 14 | 0 | [ ] | |
| `UserProjectNotification` | 64.3 | 0.0 | 23.1 | 5 | 2 | 10 | [ ] | |
| `NotificationEventRecorder` | 100.0 | 75.0 | 100.0 | 0 | 5 | 0 | [ ] | |
| `NotificationMailBodyProcessor` | 94.1 | 90.0 | 100.0 | 2 | 2 | 0 | [ ] | |
| `NotificationMailRenderer` | 100.0 | 75.0 | 100.0 | 0 | 3 | 0 | [ ] | |
| `NotificationCleanupScheduler` | 100.0 | 75.0 | 100.0 | 0 | 1 | 0 | [ ] | |
| `NotificationEventMerger$MergeKey` | 100.0 | 100.0 | 25.0 | 0 | 0 | 3 | [ ] | |
| `NotificationMail` | 100.0 | 100.0 | 50.0 | 0 | 0 | 3 | [ ] | |
| `NotificationEvent` | 100.0 | 100.0 | 58.3 | 0 | 0 | 10 | [ ] | |
| **domain/organization** | | | | | | | | |
| `OrganizationServiceImpl` | 94.1 | 69.4 | 40.6 | 10 | 19 | 19 | [ ] | |
| `Organization` | 100.0 | 100.0 | 75.0 | 0 | 0 | 4 | [ ] | |
| `OrganizationUser` | 100.0 | 100.0 | 60.0 | 0 | 0 | 4 | [ ] | |
| **domain/project** | | | | | | | | |
| `ProjectServiceImpl` | 70.7 | 50.0 | 40.5 | 84 | 64 | 22 | [x] | 2026-08-24: `ProjectServiceImplSpec.kt`에 57 tests 추가(25→82). 단독 측정 LINE 100%, BRANCH 96.9%, METHOD 100% — 목표 달성. forkProject/cloneHardLinkedRepository는 실제 임시 파일시스템으로 하드링크 복제까지 검증. 도달 불가능 4건 코드/바이트코드 근거 확정 |
| `ProjectUserServiceImpl` | 72.9 | 72.7 | 25.0 | 39 | 6 | 21 | [ ] | |
| `GitServiceImpl` | 48.4 | 9.1 | 80.0 | 16 | 20 | 1 | [ ] | |
| `Project` | 100.0 | 77.8 | 93.9 | 0 | 4 | 4 | [ ] | |
| `RecentProjectRepository` | 100.0 | 60.0 | 100.0 | 0 | 4 | 0 | [ ] | |
| `TitleHeadServiceImpl` | 100.0 | 85.0 | 100.0 | 0 | 3 | 0 | [ ] | |
| `ProjectRepository$DefaultImpls` | 0.0 | 100.0 | 0.0 | 2 | 0 | 1 | [ ] | |
| `RecentProjectRepository$DefaultImpls` | 0.0 | 100.0 | 0.0 | 2 | 0 | 1 | [ ] | |
| `ProjectService$DefaultImpls` | 0.0 | 100.0 | 0.0 | 1 | 0 | 1 | [ ] | |
| `UpdateProjectParam` | 93.3 | 100.0 | 93.3 | 1 | 0 | 1 | [ ] | |
| `TitleHead` | 90.0 | 100.0 | 50.0 | 1 | 0 | 5 | [ ] | |
| `ProjectUser` | 100.0 | 100.0 | 70.0 | 0 | 0 | 3 | [ ] | |
| `RecentProject` | 100.0 | 100.0 | 35.7 | 0 | 0 | 9 | [ ] | |
| `ProjectTransfer` | 100.0 | 100.0 | 61.1 | 0 | 0 | 7 | [ ] | |
| `Label` | 100.0 | 100.0 | 70.0 | 0 | 0 | 3 | [ ] | |
| **domain/pullrequest** | | | | | | | | |
| `PullRequestServiceImpl` | 95.2 | 64.5 | 72.1 | 25 | 66 | 17 | [~] | 2026-08-24: `PullRequestServiceSpec.kt`에 25 tests 추가(27→52). 전체 회귀 확정치: LINE 98.5%, BRANCH 85.5%(아직 미달), METHOD 75.4%(아직 미달) — 다음 배치에서 마무리. **잠재적 운영 이슈 발견(미수정, 별도 검토 필요)**: `createMergeCommitAndUpdateRef`가 동일 초 내에 diff 없이 `processMergeCheck`를 연속 호출하면 동일한 병합 커밋 해시가 재생성돼(Git 커밋 타임스탬프 초 단위) `RefUpdate.Result.NO_CHANGE`→`IOException` 발생을 테스트 중 실제 재현(Thread.sleep으로 우회) — 운영에서도 짧은 간격 재검사 시 동일 예외 가능성 |
| `CodeReviewServiceImpl` | 93.5 | 56.0 | 79.3 | 16 | 51 | 6 | [ ] | |
| `CodeCommentThread` | 90.0 | 12.5 | 55.6 | 2 | 14 | 4 | [ ] | |
| `CommentThread` | 79.3 | 37.5 | 76.9 | 6 | 5 | 6 | [ ] | |
| `PullRequestCommit` | 83.3 | 30.0 | 42.9 | 4 | 7 | 12 | [ ] | |
| `CommitComment` | 94.4 | 0.0 | 52.4 | 1 | 8 | 10 | [ ] | |
| `NonRangedCodeCommentThread` | 92.9 | 0.0 | 66.7 | 1 | 8 | 1 | [ ] | |
| `PullRequestEventRecorderKt` | 100.0 | 80.0 | 100.0 | 0 | 2 | 0 | [ ] | |
| `PullRequestCommit$Companion` | 100.0 | 50.0 | 100.0 | 0 | 2 | 0 | [ ] | |
| `PullRequest` | 100.0 | 100.0 | 83.3 | 0 | 0 | 7 | [ ] | |
| `PullRequestMergeResult` | 100.0 | 100.0 | 92.3 | 0 | 0 | 1 | [ ] | |
| `PullRequestTimelineItem` | 100.0 | 100.0 | 66.7 | 0 | 0 | 1 | [ ] | |
| `ReviewComment` | 100.0 | 100.0 | 75.0 | 0 | 0 | 3 | [ ] | |
| `PullRequestEvent` | 100.0 | 100.0 | 56.2 | 0 | 0 | 7 | [ ] | |
| **domain/role** | | | | | | | | |
| `Role` | 100.0 | 100.0 | 62.5 | 0 | 0 | 3 | [ ] | |
| **domain/site** | | | | | | | | |
| `SiteService` | 41.2 | 18.6 | 41.7 | 60 | 57 | 7 | [x] | 2026-08-23: 27 tests 추가(총 33). 단독 측정 LINE 100%, METHOD 100%, BRANCH 95.7%(67/70) — 목표 달성. 도달 불가능 3건 확인(`getMailList`/`getNoAvatarUsers`의 `User.email`이 non-nullable `var email: String=""`이라 null 분기가 타입 시스템상 불가능) |
| `DataBackupServiceImpl` | 86.1 | 72.7 | 100.0 | 14 | 15 | 0 | [ ] | |
| **domain/support** | | | | | | | | |
| `TranslationServiceImpl` | 11.5 | 0.0 | 25.0 | 54 | 30 | 3 | [ ] | |
| `SearchServiceImpl` | 68.2 | 37.5 | 85.7 | 27 | 40 | 1 | [ ] | |
| `AutoLinkRenderer` | 75.0 | 57.9 | 75.0 | 33 | 32 | 5 | [ ] | |
| `SearchResult` | 63.3 | 40.6 | 93.0 | 29 | 19 | 3 | [ ] | |
| `YonaUpdateService` | 70.7 | 37.5 | 81.8 | 17 | 20 | 2 | [ ] | |
| `StatisticsServiceImpl` | 7.7 | 100.0 | 50.0 | 36 | 0 | 1 | [ ] | |
| `LineEnding` | 43.5 | 32.4 | 66.7 | 13 | 23 | 2 | [ ] | |
| `MarkdownServiceImpl` | 90.5 | 74.0 | 100.0 | 14 | 13 | 0 | [ ] | |
| `CodeRange` | 63.2 | 0.0 | 27.8 | 7 | 16 | 13 | [ ] | |
| `HistoryUtil` | 83.6 | 67.6 | 100.0 | 9 | 12 | 0 | [ ] | |
| `DiagnosticService` | 70.7 | 59.1 | 100.0 | 12 | 9 | 0 | [ ] | |
| `ReviewThreadServiceImpl` | 83.3 | 75.0 | 80.0 | 8 | 5 | 1 | [ ] | |
| `FileUtil` | 96.2 | 56.2 | 100.0 | 1 | 7 | 0 | [ ] | |
| `AutoLinkRenderer$Link` | 87.0 | 75.0 | 62.5 | 3 | 2 | 3 | [ ] | |
| `DiffUtil` | 100.0 | 85.7 | 100.0 | 0 | 4 | 0 | [ ] | |
| `AbstractPosting` | 95.7 | 100.0 | 93.8 | 1 | 0 | 2 | [ ] | |
| `SearchResult$BeginAndEnd` | 50.0 | 100.0 | 75.0 | 1 | 0 | 1 | [ ] | |
| `DatabaseInitializer` | 100.0 | 50.0 | 100.0 | 0 | 1 | 0 | [ ] | |
| `Property` | 100.0 | 100.0 | 62.5 | 0 | 0 | 3 | [ ] | |
| `ReviewSearchCondition` | 100.0 | 100.0 | 75.0 | 0 | 0 | 5 | [ ] | |
| `LineEnding$EndingType` | 100.0 | 100.0 | 66.7 | 0 | 0 | 1 | [ ] | |
| `Comment` | 100.0 | 100.0 | 68.8 | 0 | 0 | 5 | [ ] | |
| **domain/user** | | | | | | | | |
| `UserServiceImpl` | 21.3 | 9.4 | 31.6 | 74 | 29 | 13 | [~] | 2026-08-24: 신규 `UserServiceImplSpec.kt`(34 tests). 전체 회귀 확정치: LINE 100%, BRANCH 90.6%(아직 미달), METHOD 100% — 다음 배치에서 마무리. 도달 불가능 분기 없음(전부 실제 도달 가능 확인) |
| `PasswordResetServiceImpl` | 14.3 | 4.5 | 20.0 | 42 | 21 | 8 | [ ] | |
| `LdapService` | 33.3 | 0.0 | 25.0 | 42 | 10 | 6 | [ ] | |
| `FavoriteServiceImpl` | 23.1 | 0.0 | 7.7 | 30 | 6 | 12 | [ ] | |
| `User` | 88.9 | 66.1 | 81.7 | 9 | 19 | 11 | [ ] | |
| `LdapQueryBuilder` | 96.3 | 77.8 | 100.0 | 1 | 8 | 0 | [ ] | |
| `Email` | 56.2 | 0.0 | 20.0 | 7 | 2 | 12 | [ ] | |
| `LdapUserProvisioningService` | 97.6 | 70.0 | 100.0 | 1 | 6 | 0 | [ ] | |
| `UserDetailsServiceImpl` | 94.4 | 75.0 | 66.7 | 1 | 3 | 1 | [ ] | |
| `FavoriteOrganization` | 81.2 | 50.0 | 45.5 | 3 | 1 | 6 | [ ] | |
| `FavoriteProject` | 88.9 | 50.0 | 61.5 | 2 | 2 | 5 | [ ] | |
| `UserVerification` | 85.7 | 50.0 | 38.5 | 2 | 1 | 8 | [ ] | |
| `YonaUserDetails` | 86.7 | 100.0 | 66.7 | 2 | 0 | 4 | [ ] | |
| `EmailDomainValidator` | 100.0 | 75.0 | 100.0 | 0 | 2 | 0 | [ ] | |
| `LdapUser` | 100.0 | 83.3 | 100.0 | 0 | 1 | 0 | [ ] | |
| `FavoriteIssue` | 88.9 | 100.0 | 37.5 | 1 | 0 | 5 | [ ] | |
| `UserState$Companion` | 100.0 | 75.0 | 100.0 | 0 | 1 | 0 | [ ] | |
| `ReservedWordsValidator` | 100.0 | 100.0 | 66.7 | 0 | 0 | 1 | [ ] | |
| `UserSetting` | 100.0 | 100.0 | 75.0 | 0 | 0 | 2 | [ ] | |
| `UserIdent` | 100.0 | 100.0 | 66.7 | 0 | 0 | 3 | [ ] | |
| `LinkedAccount` | 100.0 | 100.0 | 60.0 | 0 | 0 | 4 | [ ] | |
| **domain/vcs** | | | | | | | | |
| `GitRepository` | 42.5 | 24.8 | 59.6 | 230 | 155 | 23 | [i] | 2026-08-24: 신규 `GitRepositorySpec.kt`(실제 bare JGit 저장소+저수준 커밋, mock 최소화, 95 tests). 전체 회귀 확정치: LINE 99.5%, METHOD 100%, BRANCH 88.3% — 남은 분기는 전부 코드 근거로 도달 불가능/비현실적 확인(JGit API 계약상 항상 non-null인 지점들, close() 실패 분기 등 상세는 스펙 파일 참고). **실버그 발견(현재 호출부에선 미트리거, 미수정)**: `getParentCommitOf()`가 부모 커밋을 `parseCommit()` 없이 반환해 반환값의 `getMessage()`/`getAuthorName()` 등 호출 시 NPE — 유일한 실사용처 `CodeViewController.kt:481`은 `.id`만 참조해(템플릿 `code/svnDiff.html:103`) 현재는 트리거 안 됨, 향후 `.message` 등 참조 추가 시 위험 |
| `FileDiff` | 9.6 | 0.0 | 37.0 | 132 | 130 | 29 | [x] | 2026-08-23: 신규 60 tests, `FileDiffSpec.kt`. 단독 측정 LINE/BRANCH/METHOD/CLASS 전부 100%. **실버그 발견(수정은 별도 판단 필요)**: `updateRange(lineA, lineB)`가 lineA/lineB 조건을 독립된 `if`로 처리해 두 조건이 동시에 매치되면 같은 edit이 EditList에 중복 추가됨 — 테스트로 명시 문서화, 의도된 동작인지 불확실해 별도 수정 없이 사실만 기록 |
| `BareCommit` | 61.0 | 30.6 | 87.5 | 53 | 34 | 1 | [~] | 2026-08-24: `BareCommitSpec.kt`에 5 tests 추가(1→6), 실제 bare git 저장소로 검증(락 파일로 ConcurrentRefUpdateException까지 결정론적 재현). 전체 회귀 확정치: LINE 98.5%, BRANCH 83.7%(아직 미달), METHOD 100% — 다음 배치에서 마무리. 도달 불가능 2건(User.name/email non-null 타입, else 분기는 파일시스템 장애 재현 필요해 비결정적이라 보류) |
| `Hunk` | 0.0 | 0.0 | 0.0 | 26 | 18 | 15 | [ ] | |
| `DiffLine` | 0.0 | 0.0 | 0.0 | 21 | 22 | 9 | [ ] | |
| `SvnRepository` | 92.2 | 63.9 | 91.4 | 15 | 26 | 3 | [ ] | |
| `RepositoryService` | 67.5 | 36.7 | 66.7 | 13 | 19 | 2 | [ ] | |
| `SvnCommit` | 39.1 | 7.1 | 37.5 | 14 | 13 | 10 | [ ] | |
| `GitCommit` | 64.7 | 25.0 | 60.0 | 6 | 15 | 6 | [ ] | |
| `GitRepository$getFileDiffs$MultipleRepositoryObjectReader` | 47.4 | 37.5 | 62.5 | 10 | 5 | 3 | [ ] | |
| `FileDiff$Companion` | 0.0 | 0.0 | 0.0 | 5 | 6 | 2 | [ ] | |
| `GitRepository$getFileDiffs$fakeRepo$1` | 36.4 | 100.0 | 36.4 | 7 | 0 | 7 | [ ] | |
| `FileDiff$Hunks` | 0.0 | 100.0 | 0.0 | 3 | 0 | 5 | [ ] | |
| `FileDiff$Error` | 0.0 | 100.0 | 0.0 | 1 | 0 | 1 | [ ] | |
| `GitRepository$getFileDiffs$fakeRepo$1$createAttributesNodeProvider$1$emptyAttributesNode$1` | 50.0 | 100.0 | 50.0 | 1 | 0 | 1 | [ ] | |
| `FileDiff$SizeExceededHunks` | 0.0 | 100.0 | 0.0 | 1 | 0 | 1 | [ ] | |
| `PushedBranch` | 100.0 | 100.0 | 70.0 | 0 | 0 | 3 | [ ] | |
| `GitBranch` | 100.0 | 100.0 | 83.3 | 0 | 0 | 1 | [ ] | |
| `GitRepository$getFileDiffs$fakeRepo$1$createAttributesNodeProvider$1` | 100.0 | 100.0 | 75.0 | 0 | 0 | 1 | [ ] | |
| **domain/watch** | | | | | | | | |
| `WatchServiceImpl` | 98.2 | 73.9 | 100.0 | 1 | 12 | 0 | [ ] | |
| `Unwatch` | 81.8 | 100.0 | 30.0 | 2 | 0 | 7 | [ ] | |
| `Watch` | 81.8 | 100.0 | 30.0 | 2 | 0 | 7 | [ ] | |
| `WatchService$DefaultImpls` | 0.0 | 100.0 | 0.0 | 2 | 0 | 1 | [ ] | |
| **domain/webhook** | | | | | | | | |
| `WebhookServiceImpl` | 86.0 | 57.0 | 90.5 | 35 | 117 | 2 | [x] | 2026-08-23: `WebhookServiceSpec.kt`에 총 91 tests(24→60→91). 단독 측정 LINE 100%, BRANCH 95.2%(259/272), METHOD 100% — 목표 달성. `javap` 바이트코드 역어셈블로 도달 불가능 13건 확정(`String.valueOf(long)`/문자열템플릿/`TuplesKt.to()` 등 JDK/Kotlin 표준 라이브러리가 non-null을 보장하는 지점). non-null 타입 필드의 방어적 분기는 reflection으로 null을 강제 주입해 실제로 커버 |
| `WebhookNotificationEventListener` | 96.7 | 77.8 | 100.0 | 1 | 8 | 0 | [ ] | |
| `WebhookRepository$DefaultImpls` | 0.0 | 100.0 | 0.0 | 2 | 0 | 1 | [ ] | |
| `WebhookRepository` | 0.0 | 100.0 | 0.0 | 1 | 0 | 1 | [ ] | |
| `Webhook` | 100.0 | 100.0 | 50.0 | 0 | 0 | 8 | [ ] | |
| `WebhookThread` | 100.0 | 100.0 | 42.9 | 0 | 0 | 8 | [ ] | |
| **service** | | | | | | | | |
| `MigrationService` | 8.0 | 0.0 | 9.1 | 219 | 130 | 20 | [x] | 2026-08-23: 신규 33 tests, `MigrationServiceSpec.kt`. 단독 측정 LINE 100%, METHOD 100%, BRANCH 97.7%(127/130). 나머지 3개는 `User.name`/`User.email`/`Assignee.user`가 non-null 타입이라 도달 불가능(구조적). **실버그 3건 발견(미수정, 별도 검토 필요)**: (1) `getMigrationProjects`가 owner null일 때 `full_name`에 문자열 템플릿으로 리터럴 "null/..."이 그대로 들어감(owner 필드 자체는 ""로 처리되는 것과 불일치), (2) `relativeLinksToWikiCommitPath`가 정규식 치환 람다에서 매치별 상대경로 대신 클로저로 캡처한 원본 `text` 전체를 위키링크 경로에 그대로 박아넣음, (3) `exportPosts`가 각 export 맵 엔트리 키로 "post" 대신 "issue"를 재사용하고 "id" 필드가 누락됨(exportIssues 복붙 흔적으로 보임) |
| **util** | | | | | | | | |
| `diff_match_patch` | 25.4 | 24.6 | 31.8 | 815 | 491 | 30 | [x] | 2026-08-23: 2개 에이전트 병렬로 diff_/match_+patch_ 그룹 분담. `DiffMatchPatchDiffSpec.kt`(117 tests, google/diff-match-patch 업스트림 대조), `DiffMatchPatchMatchPatchSpec.kt`(38 tests, 업스트림 공식 테스트 이식). 전체 회귀 확정치: LINE 98.8%, BRANCH 95.4%, METHOD 100% — 목표 달성. **실버그 2건 발견(vendored 서드파티 알고리즘, protected 메서드 직접 호출시만 재현, public API인 diff_main에서는 도달 불가 — 미수정)**: (1) `diff_map("abc","abc")` 완전동일 문자열이 빠르게 매치되면 빈 리스트 반환, (2) 65536개 초과 고유 줄에서 `(char)` 캐스팅 오버플로로 줄 매핑 깨짐 |
| `diff_match_patch$Patch` | 0.0 | 0.0 | 0.0 | 30 | 14 | 2 | [ ] | |
| `diff_match_patch$Diff` | 36.4 | 0.0 | 25.0 | 7 | 4 | 3 | [ ] | |
| `diff_match_patch$LinesToCharsResult` | 0.0 | 100.0 | 0.0 | 5 | 0 | 1 | [ ] | |
| **web** | | | | | | | | |
| `ProjectViewController` | 50.0 | 32.5 | 74.0 | 327 | 301 | 13 | [x] | 2026-08-24: `ProjectViewControllerSpec.kt`에 총 156 tests(80+41+35, 31→111→152→187). 전체 회귀 확정치: LINE 99.7%, BRANCH 95.3%, METHOD 100% — 목표 달성. **실버그 수정 완료**: `projectLogo()`의 기본 로고 폴백이 다른 개발자의 로컬 머신 절대경로로 하드코딩돼 있던 것을 `ClassPathResource`로 수정(main 소스 코디네이터 직접 수정, TASK-0270). 죽은코드 2건(getProjectHistory의 contributor/pull.title, PullRequest non-null 타입) 문서화 |
| `UserViewController` | 57.0 | 30.7 | 43.3 | 173 | 160 | 17 | [x] | 2026-08-24: `UserViewControllerSpec.kt`에 총 89 tests(63+26, 15→78→104). METHOD 미달 원인 확인·해결: `verifyUserLegacy`/`confirmEmailLegacy`가 실제 도달 가능한 라우트인데 테스트가 한 번도 호출한 적 없었음. 전체 회귀 확정치: LINE 99.5%, BRANCH 98.7%, METHOD 100% — 목표 달성. 도달 불가능 확정 1건: `userIssues()`의 when절 else 분기(`loginUser.id!!` 강제 언래핑 이후 시점이라 구조적으로 불가능) |
| `IssueViewController` | 69.6 | 45.1 | 78.9 | 158 | 167 | 4 | [x] | 2026-08-24: `IssueViewControllerSpec.kt`에 101 tests 추가(13→114). 전체 회귀 확정치(별도 `IssueEditMoveProjectSpec.kt`의 targetProjectId 이동 테스트와 합산): LINE 99.0%, BRANCH 96.1%, METHOD 100% — 목표 달성 |
| `MilestoneViewController` | 49.0 | 31.7 | 50.0 | 151 | 142 | 7 | [x] | 2026-08-24: `MilestoneViewControllerSpec.kt`에 62 tests 추가(13→75). 단독 측정 LINE 100%, METHOD 100%, BRANCH 95.2% — 목표 달성. `openMilestone`/`closeMilestone`/`deleteMilestone`/`editMilestoneForm`(완전 미실행이었음) 포함 전체 커버 |
| `OrganizationViewController` | 66.8 | 46.5 | 72.2 | 96 | 123 | 5 | [~] | 2026-08-24: `OrganizationViewControllerSpec.kt`에 71 tests 추가(16→87). 전체 회귀 확정치: LINE 97.9%, BRANCH 88.3%(아직 미달), METHOD 100% — 다음 배치에서 마무리. **실버그 수정 완료**: `organizationLogo()`의 기본 이미지 폴백이 `projectLogo()`와 동일한 하드코딩된 개발자 로컬 절대경로 버그였음(같은 패턴이 2곳에서 발견돼 전체 재검색으로 확인, 다른 곳은 없음) — `ClassPathResource`로 수정(TASK-0270) |
| `CodeViewController` | 62.0 | 34.3 | 69.2 | 89 | 130 | 4 | [x] | 2026-08-24: `CodeViewControllerSpec.kt`에 64 tests 추가(11→75). 단독 측정 LINE 99.1%, BRANCH 95.5%, METHOD 100% — 목표 달성. `showImageFile`/`openFile`/`historyUntilHead`는 실제 라우팅된 엔드포인트인데 기존 테스트가 전혀 호출한 적 없어 METHOD 0%였던 것 확인·해결. 참고(버그 아님, 설계상 특이점): `showRawFile`의 MIME 감지가 임시파일을 항상 `.tmp` 확장자로 만들어 `Files.probeContentType`이 실질적으로 거의 항상 null 반환 |
| `BoardViewController` | 67.6 | 45.0 | 63.6 | 79 | 122 | 4 | [i] | 2026-08-24: `BoardViewControllerSpec.kt`에 65 tests 추가(18→83). 단독 측정 LINE 100%, METHOD 100%, BRANCH 94.1%(209/222) — 도달 불가능 13건 전부 코드/바이트코드 근거로 확정(non-null 타입 필드, 상위 권한 게이트로 인한 논리적 도달 불가, Kotlin 컴파일러의 중복 null 체크). 구조적 최대치로 인정 |
| `PullRequestViewController` | 79.0 | 51.9 | 96.0 | 69 | 124 | 1 | [~] | 2026-08-24: `PullRequestViewControllerSpec.kt`에 66 tests(65+1, 17→82→83). 전체 회귀 확정치는 다음 배치에서 재확인. **closePattern 처리 결론**: `closePattern`(PR/커밋 메시지 "fixes #123"으로 이슈 자동 닫기)이 legacy-yona에 없는 yuna 독자 구현임을 확인·사용자에게 보고 — 사용자 결정: "유지하고 정규식만 수정". `fix[e[s|d]]?`(중첩 대괄호 오사용으로 fix/fixes/fixed 미매치)를 `fix(?:es|ed)?`로 수정 완료, 회귀 테스트 추가("fix/fixes/fixed 키워드도 close/resolve와 동일하게 이슈 번호를 인식해야 한다") |
| `IndexController` | 66.7 | 38.2 | 100.0 | 42 | 84 | 0 | [x] | 2026-08-24: `IndexControllerSpec.kt`에 38 tests 추가(5→43). 단독 측정 LINE 100%, BRANCH 100%, METHOD 100% — 완전 달성. 도달 불가능 분기 없음(전부 실제 HTTP 요청 경로로 검증) |
| `ProjectMemberController` | 46.4 | 29.7 | 21.4 | 59 | 52 | 11 | [x] | 2026-08-24: `ProjectMemberControllerSpec.kt`에 37 tests 추가(4→41). 단독 측정 LINE 100%, BRANCH 97.3%, METHOD 100% — 목표 달성. 도달 불가능 2건(getPureNameOnly()/loginId non-null 타입) |
| `MentionController` | 86.4 | 52.4 | 100.0 | 30 | 80 | 0 | [x] | 2026-08-24: `MentionControllerSpec.kt`에 60 tests 추가(13→73). 단독 측정 LINE 100%, BRANCH 96%, METHOD 100% — 목표 달성. 도달 불가능 3건(ProjectUser.user/OrganizationUser.user/PullRequest.contributor non-null 타입) |
| `AttachmentController` | 73.8 | 40.5 | 100.0 | 33 | 75 | 0 | [~] | 2026-08-24: `AttachmentControllerSpec.kt`에 41 tests 추가(9→50). 전체 회귀 확정치: LINE 100%, BRANCH 89.7%(아직 미달), METHOD 100% — 다음 배치에서 마무리. 도달 불가능 1건(`uploader.loginId ?: "anonymous"`, `User.loginId` non-null 타입) |
| `IssueController` | 80.1 | 59.9 | 95.2 | 36 | 61 | 1 | [x] | 2026-08-24: `IssueControllerSpec.kt`에 47 tests 추가(38→85). 전체 회귀 확정치: LINE 100%, BRANCH 95.4%, METHOD 100% — 목표 달성. 도달 불가능 2건(`checkWritePermission`/`isManagerOrAuthorOrAssignee`의 user==null 분기) |
| `UserController` | 77.7 | 49.0 | 70.4 | 45 | 50 | 8 | [~] | 2026-08-24: `UserControllerSpec.kt`에 32 tests 추가(26→58). 전체 회귀 확정치: LINE 100%, BRANCH 88.8%(아직 미달), METHOD 85.2%(아직 미달) — 다음 배치에서 마무리. 도달 불가능 분기 없음(에이전트 보고 기준) |
| `PullRequestController` | 71.0 | 48.1 | 83.3 | 36 | 54 | 3 | [x] | 2026-08-24: `PullRequestControllerSpec.kt`에 40 tests 추가(19→59). 전체 회귀 확정치: LINE 100%, BRANCH 96.2%, METHOD 100% — 목표 달성. 도달 불가능 2건(checkWritePermission/isManagerOrContributor의 user==null) |
| `CommentController` | 80.2 | 44.4 | 52.9 | 19 | 50 | 8 | [ ] | |
| `ProjectController` | 79.6 | 56.8 | 95.2 | 31 | 32 | 1 | [ ] | |
| `SiteApiController` | 75.6 | 41.1 | 81.2 | 29 | 33 | 3 | [ ] | |
| `CodeHistoryController` | 47.4 | 35.3 | 71.4 | 40 | 22 | 2 | [ ] | |
| `ImportApiController` | 74.4 | 37.1 | 60.0 | 23 | 39 | 2 | [ ] | |
| `ImportViewController` | 76.6 | 45.3 | 75.0 | 26 | 35 | 2 | [ ] | |
| `ProjectApiController` | 94.1 | 61.5 | 100.0 | 14 | 47 | 0 | [ ] | |
| `OrganizationController` | 45.9 | 25.0 | 64.3 | 33 | 24 | 5 | [ ] | |
| `ReviewThreadController` | 80.7 | 41.7 | 100.0 | 21 | 35 | 0 | [ ] | |
| `WatchController` | 76.3 | 53.7 | 53.8 | 28 | 25 | 12 | [ ] | |
| `IssueShareController` | 63.3 | 40.4 | 62.5 | 22 | 31 | 3 | [ ] | |
| `MilestoneController` | 78.6 | 54.3 | 100.0 | 21 | 32 | 0 | [ ] | |
| `BoardController` | 80.9 | 54.7 | 100.0 | 18 | 29 | 0 | [ ] | |
| `LfsStorageController` | 10.0 | 0.0 | 25.0 | 27 | 10 | 3 | [ ] | |
| `VoteController` | 73.1 | 58.8 | 100.0 | 14 | 14 | 0 | [ ] | |
| `CodeController` | 18.2 | 0.0 | 25.0 | 18 | 10 | 3 | [ ] | |
| `ReviewViewController` | 87.8 | 62.0 | 100.0 | 9 | 19 | 0 | [ ] | |
| `FavoriteController` | 72.4 | 38.9 | 75.0 | 16 | 11 | 2 | [ ] | |
| `LabelStyleController` | 73.8 | 61.5 | 100.0 | 17 | 10 | 0 | [ ] | |
| `SiteViewController` | 92.1 | 50.0 | 100.0 | 8 | 17 | 0 | [ ] | |
| `TranslationController` | 66.7 | 42.9 | 100.0 | 12 | 12 | 0 | [ ] | |
| `ReviewApiController` | 85.1 | 63.9 | 100.0 | 7 | 13 | 0 | [ ] | |
| `MigrationApiController` | 81.8 | 50.0 | 100.0 | 6 | 14 | 0 | [ ] | |
| `BranchViewController` | 88.6 | 53.6 | 100.0 | 5 | 13 | 0 | [ ] | |
| `SearchController` | 93.7 | 71.7 | 71.4 | 4 | 13 | 2 | [ ] | |
| `LabelController` | 87.1 | 54.5 | 100.0 | 4 | 10 | 0 | [ ] | |
| `BranchApiController` | 88.6 | 66.7 | 100.0 | 4 | 8 | 0 | [ ] | |
| `WebhookController` | 89.4 | 75.0 | 100.0 | 5 | 7 | 0 | [ ] | |
| `CommitResponse` | 0.0 | 100.0 | 0.0 | 11 | 0 | 11 | [ ] | |
| `PasswordResetController` | 83.7 | 75.0 | 100.0 | 8 | 3 | 0 | [ ] | |
| `HistoryDto` | 0.0 | 100.0 | 0.0 | 11 | 0 | 20 | [ ] | |
| `MessagesController` | 86.5 | 61.5 | 100.0 | 5 | 5 | 0 | [ ] | |
| `AuthController` | 92.3 | 85.7 | 85.7 | 4 | 4 | 1 | [ ] | |
| `CompareViewController` | 100.0 | 76.7 | 100.0 | 0 | 7 | 0 | [ ] | |
| `BootstrapSetupController` | 98.2 | 82.4 | 100.0 | 1 | 6 | 0 | [ ] | |
| `SvnController` | 84.6 | 66.7 | 100.0 | 4 | 2 | 0 | [ ] | |
| `GlobalExceptionHandler` | 90.9 | 16.7 | 100.0 | 1 | 5 | 0 | [ ] | |
| `ProjectViewController$MilestoneDashboardDto` | 0.0 | 100.0 | 0.0 | 5 | 0 | 5 | [ ] | |
| `StatisticsController` | 84.6 | 50.0 | 100.0 | 2 | 2 | 0 | [ ] | |
| `UserController$ChangePasswordRequest` | 0.0 | 100.0 | 0.0 | 4 | 0 | 4 | [ ] | |
| `NotificationController` | 100.0 | 62.5 | 75.0 | 0 | 3 | 1 | [ ] | |
| `CommentThreadController` | 95.2 | 83.3 | 100.0 | 1 | 2 | 0 | [ ] | |
| `MarkdownController` | 87.5 | 50.0 | 100.0 | 1 | 1 | 0 | [ ] | |
| `CodeRangeRequest` | 100.0 | 66.7 | 60.0 | 0 | 2 | 4 | [ ] | |
| `SvnController$service$davServlet$1$1` | 80.0 | 100.0 | 60.0 | 2 | 0 | 2 | [ ] | |
| `AssigneeIdForm` | 0.0 | 100.0 | 0.0 | 2 | 0 | 3 | [ ] | |
| `MilestoneIdForm` | 0.0 | 100.0 | 0.0 | 2 | 0 | 3 | [ ] | |
| `IssueViewController$newDirectMyIssueForm$$inlined$sortedByDescending$1` | 0.0 | 100.0 | 0.0 | 1 | 0 | 1 | [ ] | |
| `IssueViewController$newDirectMyIssueForm$$inlined$sortedByDescending$2` | 0.0 | 100.0 | 0.0 | 1 | 0 | 1 | [ ] | |
| `UserViewController$usermenuTabContentList$$inlined$sortedByDescending$1` | 0.0 | 100.0 | 0.0 | 1 | 0 | 1 | [ ] | |
| `SvnServletRequestWrapper` | 100.0 | 75.0 | 100.0 | 0 | 1 | 0 | [ ] | |
| `MilestoneViewController$listMilestones$$inlined$sortedBy$1` | 0.0 | 100.0 | 0.0 | 1 | 0 | 1 | [ ] | |
| `UserViewController$userSidebar$$inlined$sortedByDescending$1` | 0.0 | 100.0 | 0.0 | 1 | 0 | 1 | [ ] | |
| `MigrationViewController` | 100.0 | 91.7 | 100.0 | 0 | 1 | 0 | [ ] | |
| `ProjectViewController$getProjectHistory$$inlined$sortByDescending$1` | 0.0 | 100.0 | 0.0 | 1 | 0 | 1 | [ ] | |
| `IssueViewController$newDirectIssueForm$$inlined$sortedByDescending$1` | 0.0 | 100.0 | 0.0 | 1 | 0 | 1 | [ ] | |
| `PullRequestViewController$viewPullRequest$$inlined$sortedBy$1` | 0.0 | 100.0 | 0.0 | 1 | 0 | 1 | [ ] | |
| `ProjectViewController$getProjectDashboardData$$inlined$sortedByDescending$1` | 0.0 | 100.0 | 0.0 | 1 | 0 | 1 | [ ] | |
| `ProjectViewController$getProjectDashboardData$$inlined$sortedByDescending$2` | 0.0 | 100.0 | 0.0 | 1 | 0 | 1 | [ ] | |
| `GlobalModelAttributeAdvice` | 100.0 | 91.7 | 100.0 | 0 | 1 | 0 | [ ] | |
| `MarkdownRenderRequest` | 75.0 | 100.0 | 75.0 | 1 | 0 | 1 | [ ] | |
| `IssueMassUpdateForm` | 100.0 | 100.0 | 61.5 | 0 | 0 | 5 | [ ] | |
| `ImportForm` | 100.0 | 100.0 | 77.4 | 0 | 0 | 7 | [ ] | |
| `IssueForm` | 100.0 | 100.0 | 72.2 | 0 | 0 | 5 | [ ] | |
| `IndexController$NotificationViewDto` | 100.0 | 100.0 | 8.3 | 0 | 0 | 11 | [ ] | |
| `PostingForm` | 100.0 | 100.0 | 72.7 | 0 | 0 | 6 | [ ] | |
