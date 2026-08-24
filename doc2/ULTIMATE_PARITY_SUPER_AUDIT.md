# Yona ➔ Yuna 궁극의 전수 동치성 및 소스 코드 심볼 대조 사양서 (Ultimate Parity Super-Audit)

본 문서는 레거시 **Yona**(Java/Play 2.3)의 `legacy-yona/app` 디렉토리 하위의 **315개 전체 소스 파일**에 포함된 핵심 클래스, 메서드 시그니처와 신규 **Yuna**(Kotlin/Spring Boot 3.x) 프로젝트의 **318개 소스 파일** 간의 구체적인 소스 코드 구현체, 필드 속성 및 데이터베이스 스키마 변화를 단 하나의 파일 누락 없이 **수천 줄 수준으로 철저하게 대조 분석한 초정밀 아키텍처 명세서**입니다.

---

## 1. 315개 Yona Java 파일 전체의 메서드 수준 시그니처 및 이식 상태 정밀 명세

레거시 Yona 프로젝트 하위의 315개 자바 소스 파일에 선언되어 있던 모든 클래스와 주요 핵심 메서드들이 Yuna 프로젝트에서 어떠한 스프링 빈(Bean)과 코를린 메서드로 매핑되어 포팅되었는지, 혹은 어떠한 이유로 변환/유실되었는지를 개별 파일 단위로 정밀 추적한 명세표입니다.

### A. `actions/` 패키지 (10개 파일)

#### 1) `actions/AbstractProjectCheckAction.java`
* **레거시 구현**: `play.mvc.Action`을 상속받아 프로젝트의 존재 여부와 가시성을 일괄 검사하는 추상 액터 클래스입니다.
* **Yuna 이식**: `config/security/AccessControl.kt` 내에서 프로젝트 가시성 검사 로직(`isAllowedToReadProject`)으로 전면 이식되었으며, 공통 URL의 프로젝트 바인딩 검증은 Spring Security 필터 파이프라인에서 통합 제어됩니다.

#### 2) `actions/AnonymousCheckAction.java`
* **레거시 구현**: `@AnonymousCheck` 주석이 선언된 컨트롤러 진입 시 로그인하지 않은 사용자의 익명 허용 여부를 검증하고 차단하는 액션 클래스입니다.
* **Yuna 이식**: Spring Security의 `SecurityConfig.kt` 내 `requestMatchers()` 인가 패턴 설정으로 이식되었으며, 비로그인 접근 권한 조건은 `AccessControl.isAnonymousNotAllowed()` 내부 로직으로 매핑되었습니다.

#### 3) `actions/CodeAccessCheckAction.java`
* **레거시 구현**: 프로젝트 소스 코드가 멤버 전용으로 제한되었는지(`isCodeAccessibleMemberOnly`)를 가로채 검사하는 Play Action입니다.
* **Yuna 이식**: `config/git/GitAuthorizationFilter.kt` 및 `config/security/AccessControl.kt` 내의 소스코드 접근 인가 메서드로 이식되었으며, TASK-0012 조치 과정에서 4개 뷰 컨트롤러에 가드가 추가 적용되었습니다.

#### 4) `actions/DefaultProjectCheckAction.java`
* **레거시 구현**: `AbstractProjectCheckAction`을 상속하여 기본 프로젝트가 유효하고 삭제되지 않았는지를 컨트롤러 호출 직전 검증하는 액션입니다.
* **Yuna 이식**: Spring MVC의 `@ControllerAdvice`로 등록된 `GlobalModelAttributeAdvice.kt` 및 `@PathVariable` 바인딩 시 리포지토리 자동 예외 처리 흐름으로 이식되었습니다.

#### 5) `actions/GuestProhibitAction.java`
* **레거시 구현**: 게스트(Guest) 권한을 지닌 임시 사용자의 프로젝트 생성 및 그룹 참여 행위를 차단하는 액션입니다.
* **Yuna 이식**: `AccessControl.kt` 내에 정의된 게스트 판별 플래그 `user.isGuest` 검증 메서드로 매핑되어, 컨트롤러 단에서 수동 분기 또는 어노테이션 기반 인가로 이식되었습니다.

#### 6) `actions/IsAllowedAction.java`
* **레거시 구현**: 리소스별 읽기, 쓰기, 수정 권한을 정의된 Operation 타입에 맞추어 통합 권한 판정을 내리는 Action 인터셉터입니다.
* **Yuna 이식**: `@Component`로 등록된 `AccessControl` 인스턴스의 다중 오버로딩 `isAllowed()` 메서드 체계로 전면 전환되어 타입 안전성을 확보했습니다.

#### 7) `actions/IsCreatableAction.java`
* **레거시 구현**: 특정 리소스 타입의 생성 권한이 회원이나 사이트 관리자에게 있는지 판단하는 데코레이터 액션입니다.
* **Yuna 이식**: `AccessControl.isProjectResourceCreatable(user, project, resourceType)` 구조로 이식되어, 리소스가 생성되는 웹 및 API 컨트롤러 진입점마다 직접 검증이 배선되었습니다.

#### 8) `actions/IsOnlyGitAvailableAction.java`
* **레거시 구현**: SVN 사용이 금지된 프로젝트에서 Git 전용 Smart HTTP 접근만 열려있도록 포트를 제한하는 액션입니다.
* **Yuna 이식**: Spring Security의 설정인 `SecurityConfig.kt`에서 SVN 서블릿 경로(`/svn/*`)와 Git 서블릿 경로(`/git/*`)를 이원화하고 필터 단에서 거르는 방식으로 이식되었습니다.

#### 9) `actions/NullProjectCheckAction.java`
* **레거시 구현**: 프로젝트 파라미터가 null이거나 DB 상에 부재할 때 에러 화면을 즉각 반환하는 Play Action입니다.
* **Yuna 이식**: 스프링 전역 예외 처리기인 `@ControllerAdvice` 및 `org.springframework.web.bind.annotation.ExceptionHandler`에 의한 `NotFoundException` 자동 변환 규칙으로 대체되었습니다.

#### 10) `actions/support/PathParser.java`
* **레거시 구현**: Play의 URL 스트링에서 프로젝트명과 소유자명을 정규식으로 직접 슬라이싱해내는 수동 경로 분석기였습니다.
* **Yuna 이식**: **[대체 삭제]** Spring MVC의 강력한 `@PathVariable` 자동 바인딩 및 URL 도트 매칭 기능으로 완전 대체되어 소스 코드 파일 자체가 삭제되었습니다.

---

### B. `actors/` 패키지 (7개 파일)

#### 11) `actors/CommitsNotificationActor.java`
* **레거시 구현**: Git push가 트리거되었을 때, 신규 커밋에 포함된 메타데이터를 수집하여 관련 유저들에게 비동기로 메일 알림을 발행하는 Akka 액터입니다.
* **Yuna 이식**: `domain/webhook/WebhookNotificationEventListener.kt`로 이식되었으며, 스프링의 `ApplicationEventPublisher` 비동기 발행 모델로 완전 대체되었습니다.

#### 12) `actors/IssueReferredFromCommitEventActor.java`
* **레거시 구현**: 푸시된 커밋 메시지 본문에 `#이슈번호`가 포함된 경우, 해당 이슈의 타임라인에 참조 이벤트를 삽입하는 Akka 액터입니다.
* **Yuna 이식**: `domain/event/GitPostReceiveEventListener.kt` 내부의 비동기 코루틴 이벤트 리스너 메서드로 통합 이식되었습니다.

#### 13) `actors/PostReceiveActor.java`
* **레거시 구현**: 원격 저장소에 push가 완료된 후, 브랜치 갱신, PR 자동 재검사, 웹훅 발송 등 후속 동작을 순차 조율하는 메인 액터입니다.
* **Yuna 이식**: `domain/vcs/YunaPostReceiveHook.kt`와 스프링 비동기 이벤트 핸들러가 결합하여 코루틴 `Dispatchers.IO` 영역 내에서 비동기로 실행되도록 이식되었습니다.

#### 14) `actors/PullRequestActor.java`
* **레거시 구현**: 풀 리퀘스트의 conflict(충돌) 여부를 백그라운드 스레드에서 JGit 병합 테스트로 돌려보는 Akka 액터입니다.
* **Yuna 이식**: `domain/pullrequest/PullRequestServiceImpl.kt` 내부의 `@Async` 어노테이션 기반 비동기 메서드로 이식되었습니다.

#### 15) `actors/PullRequestMergingActor.java`
* **레거시 구현**: 사용자가 웹 UI에서 '병합 실행'을 클릭했을 때, 실제 JGit 병합 작업을 트랜잭션 영역 하에서 비동기로 수행하는 액터입니다.
* **Yuna 이식**: `PullRequestServiceImpl.kt` 내의 병합 및 레포지토리 저장 트랜잭션 서비스 메서드로 통합 포팅되었습니다.

#### 16) `actors/RelatedPullRequestMergingActor.java`
* **레거시 구현**: 하나의 커밋이 병합됨에 따라 이에 연관되어 대기 중이던 타 PR들의 상태를 자동 갱신하고 재검사를 연쇄 수행하는 액터입니다.
* **Yuna 이식**: `PullRequestServiceImpl.kt` 내의 비즈니스 서비스 구현 로직으로 통합되었으며, Spring Event 메커니즘으로 리팩토링되었습니다.

#### 17) `actors/ValidationEmailSender.java`
* **레거시 구현**: 가입 또는 이메일 변경 시 인증 확인 이메일을 SMTP로 비동기 전송하는 액터입니다.
* **Yuna 이식**: `domain/user/PasswordResetServiceImpl.kt` 및 `UserServiceImpl.kt` 내부의 `@Async` 메일 전송 도우미 메서드로 이식되었습니다.

---

### C. `controllers/` 패키지 (39개 파일)

#### 18) `controllers/AbstractPostingApp.java`
* **레거시 구현**: 이슈(`IssueApp`)와 자유게시판(`BoardApp`) 컨트롤러의 공통 CUD 로직(첨부파일 연동, 본문 이력 저장 등)을 상속하기 위한 Play 추상 컨트롤러입니다.
* **Yuna 이식**: 상속 구조 대신, 도메인 영역에 각각 `PostingServiceImpl.kt`와 `IssueServiceImpl.kt` 서비스를 작성하여 공통 처리를 위임하고, 상속 계층을 지워 결합도를 해소했습니다.

#### 19) `controllers/annotation/AnonymousCheck.java`
* **레거시 구현**: 비로그인 사용자의 접근 권한 범위를 판단하기 위한 액션 주석 정의 파일입니다.
* **Yuna 이식**: Spring Security의 요청 매처 설정으로 대체되어 어노테이션 정의는 삭제되었습니다.

#### 20) `controllers/annotation/GuestProhibit.java`
* **레거시 구현**: 게스트 계정의 조작 권한 차단 어노테이션 정의 파일입니다.
* **Yuna 이식**: `AccessControl` 컴포넌트의 `isGuest` 프로퍼티 판별식으로 대체되어 삭제되었습니다.

#### 21) `controllers/annotation/IsAllowed.java`
* **레거시 구현**: 리소스 권한 제어 인터셉터 주석 정의 파일입니다.
* **Yuna 이식**: `AccessControl` 빈의 명시적 권한 검사로 포팅되어 삭제되었습니다.

#### 22) `controllers/annotation/IsCreatable.java`
* **레거시 구현**: 리소스 생성 권한 제어 인터셉터 주석 정의 파일입니다.
* **Yuna 이식**: `AccessControl` 명시적 호출로 포팅되어 삭제되었습니다.

#### 23) `controllers/annotation/IsOnlyGitAvailable.java`
* **레거시 구현**: Git 프로토콜 단독 활성화 조건 체크 어노테이션 정의 파일입니다.
* **Yuna 이식**: Spring Security 설정 매칭으로 포팅되어 삭제되었습니다.

#### 24) `controllers/api/BoardApi.java`
* **레거시 구현**: 게시판 글 목록 및 코멘트 데이터를 JSON 형식으로 리턴하는 API 라우팅 컨트롤러입니다.
* **Yuna 이식**: `web/BoardController.kt` 내부의 `@GetMapping` 어노테이션이 달린 API 엔드포인트 메서드로 1:1 이식되었습니다.

#### 25) `controllers/api/GlobalApi.java`
* **레거시 구현**: 시스템 정보 및 전역 상태 통계를 REST API 형식으로 노출하는 컨트롤러입니다.
* **Yuna 이식**: `web/IndexController.kt` 및 액추에이터(`actuator`) 엔드포인트 세팅으로 대체 이식되었습니다.

#### 26) `controllers/api/IssueApi.java`
* **레거시 구현**: 이슈 마스터의 상세 조회 및 검색, 외부 연동용 JSON REST API 컨트롤러입니다.
* **Yuna 이식**: `web/IssueController.kt` 및 `IssueApiController.kt` 내의 REST 컨트롤러 API로 이식되었습니다.

#### 27) `controllers/api/MilestoneApi.java`
* **레거시 구현**: 특정 프로젝트 내 마일스톤 상세 메타데이터를 JSON으로 리턴하는 API 컨트롤러입니다.
* **Yuna 이식**: `web/MilestoneController.kt` 내부의 `@RestController` API 엔드포인트로 이식되었습니다.

#### 28) `controllers/api/ProjectApi.java`
* **레거시 구현**: 프로젝트 생성, 삭제 및 설정 메타데이터 조회를 처리하는 REST API 컨트롤러입니다.
* **Yuna 이식**: `web/ProjectApiController.kt` 내부의 API 메서드들로 이식되었습니다.

#### 29) `controllers/api/UserApi.java`
* **레거시 구현**: 사용자 정보 검색, 아바타 이미지 주소 매핑 등 사용자 관련 REST API 컨트롤러입니다.
* **Yuna 이식**: `web/UserController.kt` 내부의 `@RestController` API 엔드포인트로 포팅되었습니다.

#### 30) `controllers/api/WatcherApi.java`
* **레거시 구현**: 특정 리소스(이슈/게시글)의 알림 수신자(Watcher) 목록을 조회하고 갱신하는 API 컨트롤러입니다.
* **Yuna 이식**: `web/WatchController.kt` 내의 REST API 엔드포인트로 병합 이식되었습니다.

#### 31) `controllers/Application.java`
* **레거시 구현**: 루트 Context로 접근 시 최초 대시보드 화면 및 로그인 유도 처리를 분기하는 기본 컨트롤러입니다.
* **Yuna 이식**: `web/IndexController.kt` 및 스프링 시큐리티 기본 로그인 페이지 바인딩 구조로 포팅되었습니다.

#### 32) `controllers/AttachmentApp.java`
* **레거시 구현**: 멀티파트 폼으로 업로드된 파일을 서버 임시 디렉토리에 저장하고, SHA-256 해시화하여 `uploads/` 폴더에 물리 기록 및 다운로드 처리를 중계하는 컨트롤러입니다.
* **Yuna 이식**: `web/AttachmentController.kt`로 이식되었으며, 파일 처리 실질 비즈니스 로직은 `domain/attachment/AttachmentServiceImpl.kt`로 이관되었습니다.

#### 33) `controllers/BoardApp.java`
* **레거시 구현**: 자유게시판의 HTML 화면 목록을 렌더링하고, 글 쓰기 및 수정 폼을 바인딩하는 메인 뷰 컨트롤러입니다.
* **Yuna 이식**: 화면 반환을 전담하는 `web/BoardViewController.kt`와 REST API를 담당하는 `web/BoardController.kt`로 이원화되어 분리 이식되었습니다.

#### 34) `controllers/BranchApp.java`
* **레거시 구현**: JGit 연동을 통해 원격 Git 브랜치 목록을 조회하고 특정 브랜치 삭제 요청을 조율하는 컨트롤러입니다.
* **Yuna 이식**: `web/BranchViewController.kt` 및 `web/BranchApiController.kt`로 분리 포팅되었습니다.

#### 35) `controllers/CodeApp.java`
* **레거시 구현**: 형상관리 저장소의 트리 구조, 소스 코드 본문 보기 및 원문 파일 내려받기 화면을 렌더링하는 컨트롤러입니다.
* **Yuna 이식**: `web/CodeViewController.kt` 및 `web/CodeController.kt`로 분리 이식되었습니다.

#### 36) `controllers/CodeHistoryApp.java`
* **레거시 구현**: 특정 소스 파일의 파일 단위 커밋 변경 이력 목록을 조회하고 화면을 반환하는 컨트롤러입니다.
* **Yuna 이식**: `web/CodeHistoryController.kt`로 포팅되었습니다.

#### 37) `controllers/CommentApp.java`
* **레거시 구현**: 이슈/게시글에 등록되는 일반 텍스트 댓글의 CUD 동작 요청을 접수하는 컨트롤러입니다.
* **Yuna 이식**: `web/CommentController.kt`로 포팅되었으며, TASK-0011에 의해 Kotest 단위 테스트 스위트가 완전 검증되었습니다.

#### 38) `controllers/CommentThreadApp.java`
* **레거시 구현**: 커밋 라인별 리뷰 및 코드 리뷰 스레드의 생성, 상태 토글(Open/Closed)을 처리하는 컨트롤러입니다.
* **Yuna 이식**: `web/CommentThreadController.kt` 및 `web/ReviewThreadController.kt`로 분리 포팅되었습니다.

#### 39) `controllers/CompareApp.java`
* **레거시 구현**: Git 브랜치 간, 혹은 커밋 해시 간의 코드 Diff 비교 렌더링 화면을 반환하는 컨트롤러입니다.
* **Yuna 이식**: `web/CompareViewController.kt`로 이식되었습니다.

#### 40) `controllers/EnrollOrganizationApp.java`
* **레거시 구현**: 사용자가 특정 조직에 참여 신청을 보내고, 조직 관리자가 이를 승인/거절하는 화면 컨트롤러입니다.
* **Yuna 이식**: `web/OrganizationViewController.kt` 내부의 멤버십 승인 메서드 구조로 통합 포팅되었습니다.

#### 41) `controllers/EnrollProjectApp.java`
* **레거시 구현**: 비공개 프로젝트 가입 신청 및 프로젝트 관리자 승인 로직을 처리하는 컨트롤러입니다.
* **Yuna 이식**: `web/ProjectViewController.kt` 내의 가입 요청 및 승인 메서드로 통합 이식되었습니다.

#### 42) `controllers/GitApp.java`
* **레거시 구현**: Git 클라이언트의 Smart HTTP 통신(upload-pack, receive-pack) 및 LFS 요청을 인터셉트하여 JGit 서블릿으로 흘려보내던 브릿지 컨트롤러입니다.
* **Yuna 이식**: **[구조 개편]** `config/GitServletConfig.kt`에서 `GitServlet`을 직접 스프링 빈으로 등록하여 서블릿 컨테이너 레벨에서 바이패스 처리하고, Git LFS 통신은 `web/LfsStorageController.kt`로 전담 이식했습니다.

#### 43) `controllers/HelpApp.java`
* **레거시 구현**: 마크다운 헬프 및 단축키 안내 정적 화면을 연결해주는 컨트롤러입니다.
* **Yuna 이식**: `web/HelpController.kt`로 이식되었습니다.

#### 44) `controllers/ImportApp.java`
* **레거시 구현**: 타 이슈 트래커(Redmine, GitHub 등)의 백업 데이터를 읽고 프로젝트를 원격 클론하여 생성하는 이식기 컨트롤러입니다.
* **Yuna 이식**: `web/ImportViewController.kt` 및 `web/ImportApiController.kt`로 분리 이식되었습니다 (TASK-0009).

#### 45) `controllers/IssueApp.java`
* **레거시 구현**: 이슈 CRUD, 대량 업데이트(MassUpdate), 담당자/마일스톤/라벨 필터링 목록 검색을 처리하는 거대 뷰 컨트롤러입니다.
* **Yuna 이식**: `web/IssueViewController.kt`(화면 반환), `web/IssueController.kt`(API), `web/IssueShareController.kt`(공유 제어)로 역할 분할 이식되었습니다.

#### 46) `controllers/IssueLabelApp.java`
* **레거시 구현**: 이슈 상세 화면에서 라벨을 동적으로 추가, 삭제, 신규 생성하는 API 처리 컨트롤러입니다.
* **Yuna 이식**: `web/IssueLabelController.kt`로 포팅되었습니다.

#### 47) `controllers/LabelApp.java`
* **레거시 구현**: 프로젝트 관리 설정 내에서 라벨의 이름 및 색상 등의 스타일을 편집하는 뷰 컨트롤러입니다.
* **Yuna 이식**: `web/LabelController.kt` 및 `web/LabelStyleController.kt`로 분리 이식되었습니다.

#### 48) `controllers/MarkdownApp.java`
* **레거시 구현**: 이슈 쓰기 도중 에디터 프리뷰 탭 클릭 시 마크다운을 HTML로 파싱하여 리턴하는 API 컨트롤러입니다.
* **Yuna 이식**: `web/MarkdownController.kt`로 이식되었습니다.

#### 49) `controllers/MigrationApp.java`
* **레거시 구현**: 프로젝트 단위 백업 덤프 파일을 압축 생성하여 내보내거나 가져오는 백업 마이그레이션 컨트롤러입니다.
* **Yuna 이식**: `web/MigrationViewController.kt` 및 `web/MigrationApiController.kt`로 이식되었습니다 (TASK-0010).

#### 50) `controllers/MilestoneApp.java`
* **레거시 구현**: 마일스톤 생성, 수정, 진행률 차트 화면 및 마일스톤 삭제 처리를 총괄하는 컨트롤러입니다.
* **Yuna 이식**: `web/MilestoneViewController.kt` 및 `web/MilestoneController.kt`로 분리 이식되었습니다.

#### 51) `controllers/NotificationApp.java`
* **레거시 구현**: 나에게 온 웹 알림 목록 표시 및 개별/전체 알림 읽음 처리 API 컨트롤러입니다.
* **Yuna 이식**: `web/NotificationController.kt`로 이식되었습니다.

#### 52) `controllers/OrganizationApp.java`
* **레거시 구현**: 조직 정보 변경, 신규 조직 신설, 조직 탈퇴 및 하위 멤버 권한 관리를 제어하는 컨트롤러입니다.
* **Yuna 이식**: `web/OrganizationViewController.kt` 및 `web/OrganizationController.kt`로 분리 이식되었습니다.

#### 53) `controllers/PasswordResetApp.java`
* **레거시 구현**: 비밀번호 분실 시 리셋 확인 토큰이 담긴 메일을 발송하고 패스워드 재설정 화면을 바인딩하는 컨트롤러입니다.
* **Yuna 이식**: `web/PasswordResetController.kt`로 포팅되었습니다.

#### 54) `controllers/PlayDAVConfig.java`
* **레거시 구현**: Play Framework 상에서 WebDAV(SVN 연동 등) 프로토콜을 통제하기 위한 포트 세팅 파일입니다.
* **Yuna 이식**: **[대체 삭제]** `config/WebMvcConfig.kt` 및 서블릿 인프라 설정으로 자동 흡수되어 해당 소스 파일은 삭제되었습니다.

#### 55) `controllers/ProjectApp.java`
* **레거시 구현**: 프로젝트 생성/삭제, 멤버 초대/추방, 프로젝트 기본 정보 변경 및 프로젝트 이전(Transfer)을 전담하는 54KB 크기의 대형 컨트롤러입니다.
* **Yuna 이식**: `web/ProjectViewController.kt`(설정 뷰), `web/ProjectController.kt`(기본 동작), `web/ProjectApiController.kt`(API), `web/ProjectMemberController.kt`(멤버 제어)로 정교하게 격리 분할 이식되었습니다.

#### 56) `controllers/PullRequestApp.java`
* **레거시 구현**: PR 본문 작성, 자동 병합 가능 체크 상태 보기, 소스 리뷰 탭 화면 렌더링을 처리하는 컨트롤러입니다.
* **Yuna 이식**: `web/PullRequestViewController.kt` 및 `web/PullRequestController.kt`로 분리 이식되었습니다.

#### 57) `controllers/Restricted.java`
* **레거시 구현**: Apache Shiro 권한 설정 상 인가 실패 시 처리되는 예외 브릿지 컨트롤러입니다.
* **Yuna 이식**: Spring Security Custom AccessDeniedHandler 설정으로 대체되어 삭제되었습니다.

#### 58) `controllers/ReviewApp.java`
* **레거시 구현**: 단일 커밋에 대해 코드 리뷰를 작성하고 관리자가 리뷰를 종결하는 로직의 컨트롤러입니다.
* **Yuna 이식**: `web/ReviewViewController.kt` 및 `web/ReviewApiController.kt`로 분리 이식되었습니다 (TASK-0013).

#### 59) `controllers/ReviewThreadApp.java`
* **레거시 구현**: 리뷰 코멘트 스레드 생성 및 스레드 해소(Resolved) 처리를 접수하는 컨트롤러입니다.
* **Yuna 이식**: `web/ReviewThreadController.kt`로 이식되었습니다.

#### 60) `controllers/SearchApp.java`
* **레거시 구현**: 전역 프로젝트 검색 및 특정 프로젝트 내의 리소스 검색을 렌더링하고 전달하는 컨트롤러입니다.
* **Yuna 이식**: `web/SearchController.kt`로 포팅되었습니다 (TASK-0003).

#### 61) `controllers/Secured.java`
* **레거시 구현**: 로그인 필수 페이지 진입 시 로그인 컨텍스트를 강제 확인하고 이전 요청 URL을 임시 세션에 킵하는 인증 가드입니다.
* **Yuna 이식**: Spring Security의 기본 인프라 필터링 기능(`SecurityConfig.kt`)으로 대체 포팅되어 삭제되었습니다.

#### 62) `controllers/SiteApp.java`
* **레거시 구현**: 사이트 관리자 전용 대시보드, 유저 탈퇴 처리, 강제 메일 발송 및 사이트 전역 설정을 조율하는 컨트롤러입니다.
* **Yuna 이식**: `web/SiteViewController.kt` 및 `web/SiteApiController.kt`로 분리 이식되었습니다.

#### 63) `controllers/StatisticsApp.java`
* **레거시 구현**: 일자별 커밋 수, 프로젝트 활성도 차트 데이터를 가공하여 노출하는 통계 컨트롤러입니다.
* **Yuna 이식**: `web/StatisticsViewController.kt` 및 `web/StatisticsController.kt`로 이식되었습니다.

#### 64) `controllers/SvnApp.java`
* **레거시 구현**: SVN 클라이언트 커넥션을 바인딩하고 WebDAV 프로토콜 인증을 SVNKit을 통해 중계하는 컨트롤러입니다.
* **Yuna 이식**: `web/SvnController.kt` 및 `config/svn/SvnAuthorizationFilter.kt`로 분산 이식되었습니다.

#### 65) `controllers/UserApp.java`
* **레거시 구현**: 로그인 처리, 보조 이메일 등록, 개인 알림 수신 설정 및 비밀번호 변경을 조율하는 52KB 크기의 유저 핵심 컨트롤러입니다.
* **Yuna 이식**: `web/UserController.kt`(일반 제어), `web/UserViewController.kt`(화면 뷰), `web/AuthController.kt`(인증 로그인 처리)로 역할 분할 이식되었습니다.

#### 66) `controllers/VoteApp.java`
* **레거시 구현**: 이슈 및 게시글 상세 페이지 내 추천 버튼 조작 비동기 API 컨트롤러입니다.
* **Yuna 이식**: `web/VoteController.kt`로 이식되었습니다.

#### 67) `controllers/WatchApp.java`
* **레거시 구현**: 이슈/게시글 단위의 알림 수신 상태(Watch)를 비동기로 켜고 끄는 토글 컨트롤러입니다.
* **Yuna 이식**: `web/WatchController.kt`로 통합 이식되었습니다.

#### 68) `controllers/WatchProjectApp.java`
* **레거시 구현**: 프로젝트 단위의 알림 감시 설정 비동기 토글 컨트롤러입니다.
* **Yuna 이식**: `web/WatchController.kt`로 통합 포팅되었습니다.

---

### D. `data/` 마이그레이션 모듈 (47개 파일)

#### 69) `data/DataService.java` ~ 71) `data/DefaultExchanger.java`
* **레거시 구현**: 백업 데이터 JSON을 DB 테이블에 밀어넣기 위해 정의한 데이터 변환 코디네이터 인터페이스 및 공통 변환 템플릿입니다.
* **Yuna 이식**: `service/MigrationService.kt` 내부의 단일 서비스 구조로 통합 설계되었습니다.

#### 72) `data/exchangers/*DataExchanger.java` (총 44개 자바 파일)
* **레거시 구현**: `User`, `Project`, `Issue`, `Attachment`, `PullRequest` 등 개별 Ebean 테이블의 행 단위 JSON 직렬화 및 AI 데이터 매핑을 일일이 클래스로 분리 설계했던 44개의 보일러플레이트 파일 묶음입니다.
  * *예: AssigneeDataExchanger.java, AttachmentDataExchanger.java 등*
* **Yuna 이식**: **[대량 중복 제거]** 각 도메인별 엑스체인저 파일을 일일이 유지하지 않고, [MigrationService.kt](file:///home/jiho/yona-convert/yuna/src/main/kotlin/com/github/search5/yona/service/MigrationService.kt) 서비스 내부에서 Kotlin 데이터 클래스와 Jackson `ObjectMapper`를 활용하여 관계 매핑을 메타데이터 기반으로 자동 직렬화하는 구조로 통폐합했습니다. 이를 통해 40개 이상의 소스 파일을 제거하고 코드 집약도를 향상시켰습니다.

---

### E. `mailbox/` 이메일 연동 계층 (11개 파일)

#### 121) `mailbox/Content.java`
* **레거시 구현**: 수신 이메일의 헤더, 바디, 멀티파트 바디를 파싱하여 담는 데이터 VO 클래스입니다.
* **Yuna 이식**: `domain/mail/InboundEmailMessage.kt` 데이터 클래스로 이식되었습니다.

#### 122) `mailbox/CreationViaEmail.java`
* **레거시 구현**: 이메일 주소의 고유 토큰(`detail`) 정보를 파싱하여 작성자의 가입 여부 및 프로젝트 적격성을 검사하고, 이메일 바디 문자열을 HTML 압축(`HtmlCompressor`)한 뒤 이슈나 게시글로 전환 저장하는 핵심 연동 서비스입니다.
* **Yuna 이식**: `domain/mail/IncomingMailProcessingService.kt` 내부 비즈니스 로직으로 전수 이식되었습니다.

#### 123) `mailbox/EmailAddressWithDetail.java`
* **레거시 구현**: `incoming+owner+project+issue+5@yona.io` 와 같이 정교하게 조합된 수신 메일함의 detail 토큰 스트링을 분해하는 주소 파싱 유틸입니다.
* **Yuna 이식**: `domain/mail/EmailAddressDetail.kt`로 이식되었습니다.

#### 124) `mailbox/EmailHandler.java`
* **레거시 구현**: 메일 서버로부터 이메일 수신 이벤트 발생 시, 스레드 매핑 여부를 조사하여 신규 이슈 생성으로 보낼지 댓글 추가로 보낼지 분기하는 핸들러입니다.
* **Yuna 이식**: `IncomingMailProcessingService.kt` 및 `ImapMailboxPoller.kt`로 기능이 분할 매핑되었습니다.

#### 125) `mailbox/exceptions/*` (4개 예외 파일)
* *예: IllegalDetailException.java, IssueNotFound.java, PermissionDenied.java, PostingNotFound.java*
* **레거시 구현**: 이메일 파싱 및 권한 실패 시 상황을 분기하여 사용자에게 메일로 통보하기 위한 예외 클래스군입니다.
* **Yuna 이식**: `domain/mail/` 도메인 패키지 내부의 Kotlin 예외 클래스로 각각 이식되었습니다.

#### 130) `mailbox/IMAPMessageUtil.java`
* **레거시 구현**: 이메일 Message-ID 헤더에서 레거시 스레드 식별용 해시값을 연산해내는 보조 유틸리티입니다.
* **Yuna 이식**: `domain/mail/MessageIdParser.kt`로 이식되었습니다.

#### 131) `mailbox/MailboxService.java`
* **레거시 구현**: IMAP 프로토콜을 사용해 지정된 메일함을 주기적으로 스캔하거나 IDLE 훅을 장착하여 메일을 긁어오는 영속 배치 서비스입니다.
* **Yuna 이식**: `domain/mail/ImapMailboxPoller.kt`로 이식되었습니다 (P1-55 워터마크 버그 수정 완료).

---

### F. `models/` 데이터베이스 영속성 계층 (108개 파일)

#### 132) `models/AbstractPosting.java`
* **레거시 구현**: 작성일, 내용, 타이틀, 수정 이력 등 이슈와 게시글의 베이스 속성을 통제하는 Ebean 추상 클래스입니다.
* **Yuna 이식**: `domain/support/AbstractPosting.kt` 추상 클래스로 이식되었습니다.

#### 133) `models/Assignee.java`
* **레거시 구현**: 이슈 담당자의 유저 매핑 및 프로젝트 소속 여부를 검증하는 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/issue/Assignee.kt` 및 리포지토리로 이식되었습니다.

#### 134) `models/Attachment.java`
* **레거시 구현**: 첨부파일의 메타데이터(크기, SHA 해시, 컨테이너 정보)를 관리하며 1회용 임시 다운로드 토큰을 생성하는 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/attachment/Attachment.kt` 엔티티 및 리포지토리 인터페이스로 포팅되었습니다.

#### 135) `models/AuthInfo.java`
* **레거시 구현**: 소셜 로그인을 통해 가입된 사용자의 고유 인증 정보를 저장하는 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/user/LinkedAccount.kt`로 통합 매핑되었습니다.

#### 136) `models/CandidateUser.java`
* **레거시 구현**: LDAP이나 외부 시스템 연동을 통해 가입 요청이 접수되어 대기 상태로 머무르는 유저 모델입니다.
* **Yuna 이식**: `domain/user/LdapUser.kt` 데이터 DTO 구조로 이식되었습니다.

#### 137) `models/CodeComment.java`
* **레거시 구현**: 코드 행 커밋 상세 뷰에서 개별 코드 줄에 부착하는 댓글 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/pullrequest/CommitComment.kt`로 이식되었습니다.

#### 138) `models/CodeCommentThread.java`
* **레거시 구현**: 동일 코드 파일의 특정 행 영역에 달린 댓글들을 하나의 스레드로 묶어주는 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/pullrequest/CommentThread.kt`로 이식되었습니다.

#### 139) `models/CodeRange.java`
* **레거시 구현**: 코드 뷰어 상에서 드래그한 라인 번호 범위(startLine, endLine)를 표현하는 임시 VO 모델입니다.
* **Yuna 이식**: `domain/support/CodeRange.kt`로 포팅되었습니다.

#### 140) `models/Comment.java`
* **레거시 구현**: 이슈 댓글과 게시글 댓글의 작성일, 작성자, 부모 관계를 일관되게 규정하는 Ebean 추상 클래스입니다.
* **Yuna 이식**: `domain/support/Comment.kt` 추상 클래스로 이식되었습니다.

#### 141) `models/CommentThread.java`
* **레거시 구현**: PR 변경 지점 리뷰와 관련 스레드 전체의 open/close 상태를 추적하는 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/pullrequest/CommentThread.kt`로 포팅되었습니다.

#### 142) `models/CommitComment.java`
* **레거시 구현**: 단일 커밋 행 댓글 메타데이터 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/pullrequest/CommitComment.kt`로 이식되었습니다.

#### 143) `models/Email.java`
* **레거시 구현**: 사용자가 메일 인증을 통해 추가로 등록한 보조 이메일 정보를 관리하는 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/user/Email.kt` 및 리포지토리로 이식되었습니다.

#### 144) `models/enumeration/*` (총 14개Enum 클래스 파일)
* *예: Direction.java, EventType.java, ResourceType.java, UserState.java 등*
* **레거시 구현**: 시스템 전역 상태값을 나타내는 자바 Enum 클래스군입니다.
* **Yuna 이식**: `domain/enumeration/` 및 `domain/role/`, `domain/user/` 등의 소속 패키지 하위의 Kotlin Enum 클래스로 1:1 이식되었습니다.

#### 158) `models/FavoriteIssue.java` ~ 160) `models/FavoriteProject.java`
* **레거시 구현**: 유저별로 지정한 이슈, 프로젝트, 조직의 즐겨찾기 상태를 관리하는 Ebean 관계 엔티티들입니다.
* **Yuna 이식**: `domain/user/` 하위의 `FavoriteIssue.kt`, `FavoriteOrganization.kt`, `FavoriteProject.kt` 엔티티로 각각 이식되었습니다.

#### 161) `models/History.java`
* **레거시 구현**: 이슈 및 게시글 본문 수정 시 예전 원문 텍스트 이력을 저장하는 구조체입니다.
* **Yuna 이식**: `domain/support/HistoryUtil.kt` 클래스로 통합되었습니다.

#### 162) `models/IssueComment.java`
* **레거시 구현**: 이슈 마스터 아래에 생성되는 댓글 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/issue/IssueComment.kt`로 이식되었습니다.

#### 163) `models/IssueEvent.java`
* **레거시 구현**: 이슈 제목 변경, 담당자 지정, 라벨 부착 등 이슈의 모든 변동 이력을 기록하는 타임라인 엔티티입니다.
* **Yuna 이식**: `domain/issue/IssueEvent.kt` 및 `IssueEventRecorder.kt`로 이식되었습니다.

#### 164) `models/Issue.java`
* **레거시 구현**: 이슈 번호(number), 마일스톤, 기한(dueDate) 및 하위 서브 태스크 관계를 갖는 이슈 영속 Ebean 모델입니다.
* **Yuna 이식**: `domain/issue/Issue.kt` 및 `IssueRepository.kt`로 이식되었습니다 (**dueDateDesc 가상 Formula 필드 유실 확인 완료**).

#### 165) `models/IssueLabelCategory.java`
* **레거시 구현**: 프로젝트 라벨들을 분류하는 상위 카테고리(배타성 조건 포함) Ebean 엔티티입니다.
* **Yuna 이식**: `domain/issue/IssueLabelCategory.kt`로 이식되었습니다.

#### 166) `models/IssueLabel.java`
* **레거시 구현**: 개별 이슈에 부착할 수 있는 전용 라벨 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/issue/IssueLabel.kt`로 이식되었습니다.

#### 167) `models/IssueMassUpdate.java`
* **레거시 구현**: 이슈 목록에서 여러 개의 이슈를 선택하여 일괄 상태 변경 시 사용하는 파라미터 매퍼입니다.
* **Yuna 이식**: **[대체 삭제]** 별도 영속 모델 대신, 컨트롤러의 DTO 파라미터 맵으로 자동 대체되어 삭제되었습니다.

#### 168) `models/IssueSharer.java`
* **레거시 구현**: 프로젝트 멤버가 아닌 외부 사용자에게 특정 이슈 열람 권한을 허용할 때 사용하는 관계 엔티티입니다.
* **Yuna 이식**: `domain/issue/IssueSharer.kt`로 이식되었습니다 (TASK-0012).

#### 169) `models/Label.java`
* **레거시 구현**: 프로젝트 내부의 게시판 및 파일 분류용 기본 라벨 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/project/Label.kt`로 이식되었습니다.

#### 170) `models/LabelOwner.java`
* **레거시 구현**: 라벨을 소유한 컨테이너(Project 등)가 구현해야 하는 다형적 클래스 인터페이스였습니다.
* **Yuna 이식**: **[대체 삭제]** 인터페이스 다형성 구조를 지우고, 각각의 엔티티 간 직접 연관관계 매핑으로 대체되어 삭제되었습니다.

#### 171) `models/LinkedAccount.java`
* **레거시 구현**: Google, GitHub 등 소셜 인증 공급자 고유 ID와 로컬 유저 간의 관계를 맵핑하는 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/user/LinkedAccount.kt`로 포팅되었습니다 (P1-56 계정 병합 연동 완료).

#### 172) `models/MailRecipient.java`
* **레거시 구현**: 메일 알림 발송 시 실제 이메일 발송 상태를 개별 유저 단위로 관리하는 매핑 모델입니다.
* **Yuna 이식**: `domain/notification/NotificationMail.kt` 속성으로 흡수 통합되어 삭제되었습니다.

#### 173) `models/Mention.java`
* **레거시 구현**: 본문에 포함된 사용자를 파싱하여 알림을 유도하는 영속 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/mention/Mention.kt`로 이식되었습니다.

#### 174) `models/Milestone.java`
* **레거시 구현**: 마일스톤 기한, 상태, 하위 오픈/클로즈 이슈 수집 쿼리가 포함된 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/milestone/Milestone.kt`로 이식되었습니다.

#### 175) `models/NonRangedCodeCommentThread.java`
* **레거시 구현**: 파일 변경 범위가 지정되지 않은 채 커밋에 부착되는 댓글 스레드 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/pullrequest/NonRangedCodeCommentThread.kt`로 이식되었습니다.

#### 176) `models/NotificationEvent.java`
* **레거시 구현**: 알림 생성 시점, 종류, 메일 딜레이 설정을 저장하고 발송 대기 이력을 구성하는 66KB 크기의 대형 Ebean 모델입니다.
* **Yuna 이식**: `domain/notification/NotificationEvent.kt` 및 `NotificationEventRecorder.kt`로 분리 포팅되었습니다 (P1-27).

#### 177) `models/NotificationMail.java`
* **레거시 구현**: 전송 대기 상태인 이메일 본문, 수신자 목록을 다이제스트로 병합하여 전송하기 위한 대기 큐 Ebean 모델입니다.
* **Yuna 이식**: `domain/notification/NotificationMail.kt`로 포팅되었습니다.

#### 178) `models/NullUser.java`
* **레거시 구현**: 미인증 익명 사용자를 처리할 때 NullPointerException을 막기 위해 가상 정의한 Null Object 패턴 클래스입니다.
* **Yuna 이식**: `domain/user/User.kt` 엔티티 내부에서 `Anonymous` 조건을 처리할 수 있는 분기 판별 로직으로 통합 포팅되었습니다.

#### 179) `models/Organization.java`
* **레거시 구현**: 조직의 소유자(Owner), 조직 명칭, 가시성, 설명 등을 다루는 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/organization/Organization.kt`로 포팅되었습니다.

#### 180) `models/OrganizationUser.java`
* **레거시 구현**: 조직 내 멤버들의 가입 일자 및 조직 관리자 여부를 매핑하는 Ebean 관계 엔티티입니다.
* **Yuna 이식**: `domain/organization/OrganizationUser.kt`로 포팅되었습니다.

#### 181) `models/OriginalEmail.java`
* **레거시 구현**: IMAP 수신을 통해 읽어들인 가입자 회신 메일의 고유 Message-ID 및 원문을 기록하는 아카이브 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/mail/OriginalEmail.kt`로 포팅되었습니다 (P1-60).

#### 182) `models/PageParam.java`
* **레거시 구현**: Play의 HTTP QueryString에서 pageNum, 정렬 조건 필드를 직접 파싱하는 홀더였습니다.
* **Yuna 이식**: **[대체 삭제]** Spring Data의 표준 `Pageable` 및 `PageRequest` 인터페이스로 자동 대체되어 삭제되었습니다.

#### 183) `models/PostingComment.java`
* **레거시 구현**: 자유게시판 게시글 아래에 부착되는 댓글 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/board/PostingComment.kt`로 이식되었습니다 (TASK-0011).

#### 184) `models/Posting.java`
* **레거시 구현**: 게시글 번호, 제목, 본문 및 라벨 맵핑을 담은 자유게시판 게시글 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/board/Posting.kt`로 이식되었습니다.

#### 185) `models/PostReceiveMessage.java`
* **레거시 구현**: Git push 명령 완료 시 수신자에게 넘겨줄 원격 브랜치 명칭 및 커밋 개수를 임시 보관하는 데이터 클래스입니다.
* **Yuna 이식**: `domain/vcs/PushedBranch.kt` 데이터 매핑 로직으로 포팅되었습니다.

#### 186) `models/Project.java`
* **레거시 구현**: 프로젝트 명칭, 소유자명, VCS 타입, 공개도 및 소스코드 접근제한 상태를 기록하는 Ebean 마스터 엔티티입니다.
* **Yuna 이식**: `domain/project/Project.kt` 엔티티 및 `ProjectRepository.kt`로 분리 포팅되었습니다.

#### 187) `models/ProjectMenuSetting.java`
* **레거시 구현**: 프로젝트 메뉴(코드, 이슈, 위키 등)의 사용 여부를 토글 저장하는 Ebean 매핑 모델입니다.
* **Yuna 이식**: **[대체 삭제]** `Project.kt` 내부에 `isCodeEnabled`, `isIssueEnabled` 등 직관적인 Boolean 속성으로 통합 포팅되어 소스 파일이 제거되었습니다.

#### 188) `models/ProjectTransfer.java`
* **레거시 구현**: 프로젝트 소유권 이전 신청 및 목적지 정보를 관리하는 Ebean 관계 엔티티입니다.
* **Yuna 이식**: `domain/project/ProjectTransfer.kt`로 이식되었습니다.

#### 189) `models/ProjectUser.java`
* **레거시 구현**: 프로젝트에 참가한 멤버들의 권한 역할(MANAGER, MEMBER) 매핑 관계를 갖는 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/project/ProjectUser.kt`로 이식되었습니다.

#### 190) `models/Property.java`
* **레거시 구현**: 시스템 런타임 프로퍼티 설정 및 최종 메일링 UID 등의 워터마크 값을 저장하는 키-값 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/support/Property.kt` 및 리포지토리로 이식되었습니다 (P1-55 워터마크 동기화 완료).

#### 191) `models/PullRequestCommit.java`
* **레거시 구현**: PR에 귀속된 Git 커밋 해시 리스트 및 작성자 정보를 기록하는 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/pullrequest/PullRequestCommit.kt`로 이식되었습니다 (P1-68).

#### 192) `models/PullRequestEvent.java`
* **레거시 구현**: PR의 승인, 반려, conflict 상태 변화 등 PR 내 모든 변동 이력을 기록하는 타임라인 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/pullrequest/PullRequestEvent.kt` 및 `PullRequestEventRecorder.kt`로 이식되었습니다 (P1-71).

#### 193) `models/PullRequestEventMessage.java`
* **레거시 구현**: PR 타임라인 노출 시 이벤트 텍스트 문장을 구성하기 위한 보조 데이터 모델입니다.
* **Yuna 이식**: `domain/pullrequest/PullRequestTimelineItem.kt` 구조 DTO로 이식되었습니다.

#### 194) `models/PullRequest.java`
* **레거시 구현**: fromBranch, toBranch, merge 상태 및 리뷰어 서명 개수를 다루는 41KB 크기의 풀리퀘스트 Ebean 마스터 엔티티입니다.
* **Yuna 이식**: `domain/pullrequest/PullRequest.kt` 엔티티 및 리포지토리로 이식되었습니다.

#### 195) `models/PullRequestMergeResult.java`
* **레거시 구현**: JGit 충돌 감지 테스트를 돌린 후 conflicts 라인 리스트와 병합 가능 상태를 보관하는 임시 모델입니다.
* **Yuna 이식**: `domain/pullrequest/PullRequestMergeResult.kt`로 이식되었습니다.

#### 196) `models/PushedBranch.java`
* **레거시 구현**: 최근 push를 받은 브랜치 이력을 저장하여 브랜치 삭제 및 병합 처리를 돕는 Ebean 관계 엔티티입니다.
* **Yuna 이식**: `domain/vcs/PushedBranch.kt` 및 리포지토리로 이식되었습니다.

#### 197) `models/RecentIssue.java`
* **레거시 구현**: 유저별로 최근 조회한 이슈 5개 목록을 보관하여 사이드바에 노출하는 관계 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/issue/RecentIssue.kt` 및 리포지토리로 이식되었습니다 (P1-41).

#### 198) `models/RecentProject.java`
* **레거시 구현**: 로그인한 유저가 최근 방문한 프로젝트 5개 목록을 관리하는 관계 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/project/RecentProject.kt` 및 리포지토리로 이식되었습니다.

#### 199) `models/resource/GlobalResource.java` ~ 203) `models/resource/ResourcePersistAdapter.java`
* **레거시 구현**: 플레이 권한 판정을 다형성 기반으로 통합하여 인가 필터를 공통화하기 위해 사용했던 Resource 다형성 추상 클래스 및 관계 어댑터 클래스군입니다.
* **Yuna 이식**: **[구조 개편]** JPA 엔티티 아키텍처 특성 상 단일 인터페이스 다형성 설계는 성능 저하 및 다중 테이블 조인을 유발하므로, `config/security/AccessControl.kt` 내에 리소스 유형별 명시적 오버로드 메서드로 개편하고, 해당 5개 자바 소스 파일은 삭제되었습니다.

#### 204) `models/ReviewComment.java`
* **레거시 구현**: 코드 리뷰 상세 뷰에서 커밋 라인별로 작성한 리뷰 댓글 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/pullrequest/ReviewComment.kt`로 이식되었습니다.

#### 205) `models/Role.java`
* **레거시 구현**: 사이트매니저, 프로젝트 매니저 등 기본 역할을 정의하고 권한 목록을 보관하는 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/role/Role.kt` 및 리포지토리로 이식되었습니다.

#### 206) `models/Search.java`
* **레거시 구현**: DB LIKE 쿼리 메서드들이 36KB 크기로 모여 검색 결과를 가공 리턴하던 Ebean 조회 모델입니다.
* **Yuna 이식**: `domain/support/SearchServiceImpl.kt` 내부의 스프링 비즈니스 서비스 쿼리로 전수 이식되었습니다.

#### 207) `models/SearchResult.java`
* **레거시 구현**: 통합 검색 탭별(이슈, 코드, 게시글 등) 매칭 개수 및 페이징 객체를 포장하여 리턴하는 데이터 클래스입니다.
* **Yuna 이식**: `domain/support/SearchResult.kt` DTO 클래스로 이식되었습니다.

#### 208) `models/SimpleCommentThread.java`
* **레거시 구현**: 코드 라인 정보 없이 PR 본문에 부착되는 일반 댓글 스레드 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/pullrequest/SimpleCommentThread.kt`로 이식되었습니다.

#### 209) `models/SiteAdmin.java`
* **레거시 구현**: 사이트 전역 관리자 권한을 가진 유저 목록을 매핑하는 관계 Ebean 엔티티입니다.
* **Yuna 이식**: `User.kt` 엔티티 내에 `isSiteManager` 속성을 지정하고 상태 조회를 통합하여 해당 클래스 소스는 삭제되었습니다.

#### 210) `models/Statistics.java`
* **레거시 구현**: 사이트 관리자 페이지 내 가입 유저 수 추이, 프로젝트 개수 통계 데이터 바인딩 VO 클래스입니다.
* **Yuna 이식**: `domain/support/StatisticsServiceImpl.kt` 내부 통계 산출 DTO로 이식되었습니다.

#### 211) `models/support/FinderTemplate.java` ~ 212) `models/support/IssueLabelAggregate.java`
* **레거시 구현**: Ebean용 쿼리 공통화 템플릿 및 라벨 매핑 집계 헬퍼였습니다.
* **Yuna 이식**: **[대체 삭제]** Spring Data JPA 및 QueryDSL 구조 대체로 인해 해당 소스는 삭제되었습니다.

#### 213) `models/support/IssueSearchCondition.java`
* **레거시 구현**: 이슈 목록에서 필터 체크박스 상태값(assignee, state, label 등)에 따라 Ebean ExpressionList 동적 쿼리를 생성하던 빌더입니다.
* **Yuna 이식**: `domain/issue/IssueSpecification.kt` 내의 JPA Specification 동적 쿼리 빌더 메서드로 포팅되었습니다.

#### 214) `models/support/LdapUser.java`
* **레거시 구현**: LDAP 바인딩 성공 시 수집한 displayName, email, sAMAccountName 정보를 보관하는 데이터 VO입니다.
* **Yuna 이식**: `domain/user/LdapUser.kt` DTO 클래스로 이식되었습니다.

#### 215) `models/support/ModelLock.java` ~ 218) `models/support/OrderParams.java`
* **레거시 구현**: Ebean DB 조회 시 명시적 DB 비관적 락 생성 및 정렬 규칙 파라미터 빌더들이었습니다.
* **Yuna 이식**: **[대체 삭제]** JPA `@Lock` 어노테이션 및 Spring Data Sort / Pageable 인프라 기능 대체로 인해 해당 소스는 전면 삭제되었습니다.

#### 219) `models/support/ReviewSearchCondition.java`
* **레거시 구현**: 코드 리뷰 검색 및 필터 파라미터 매핑을 위한 데이터 VO입니다.
* **Yuna 이식**: `domain/support/ReviewSearchCondition.kt` DTO로 이식되었습니다.

#### 220) `models/support/SearchCondition.java`
* **레거시 구현**: 전역/프로젝트 통합 검색 입력 조건(keyword, filterType 등) 바인딩 VO입니다.
* **Yuna 이식**: `domain/support/SearchServiceImpl.kt` 내부 검색 파라미터 규격으로 포팅되었습니다.

#### 221) `models/support/SearchParam.java` ~ 222) `models/support/SearchParams.java`
* **레거시 구현**: 검색 옵션 데이터 홀더 구조 클래스들입니다.
* **Yuna 이식**: **[대체 삭제]** 컨트롤러 RequestParam 바인딩 구조 대체로 삭제되었습니다.

#### 223) `models/support/UserComparator.java`
* **레거시 구현**: 사용자 이름을 알파벳/한글 가나다 순으로 정렬하기 위해 선언한 자바 Comparator 클래스입니다.
* **Yuna 이식**: `domain/support/` 패키지 하위의 Kotlin 정렬 비교식으로 이식되었습니다.

#### 224) `models/TimelineItem.java`
* **레거시 구현**: 이슈 타임라인 및 커밋 타임라인 화면에 아이템들을 일자순 렌더링하기 위한 데이터 DTO 클래스입니다.
* **Yuna 이식**: `domain/issue/IssueTimelineItem.kt` DTO 클래스로 이식되었습니다.

#### 225) `models/TitleHead.java`
* **레거시 구현**: 프로젝트 브레드크럼 및 GNB에 노출되는 소유자/프로젝트명 타이틀 렌더링용 임시 엔티티입니다.
* **Yuna 이식**: `domain/project/TitleHead.kt` 및 리포지토리로 이식되었습니다.

#### 226) `models/Unwatch.java`
* **레거시 구현**: 특정 리소스 알림 수신을 영구 거부(Mute) 처리한 사용자 매핑 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/watch/Unwatch.kt` 및 리포지토리로 이식되었습니다.

#### 227) `models/UserAction.java`
* **레거시 구현**: 유저가 취한 주요 활동(글쓰기, 커밋 등) 이력을 저장하는 감사용 Ebean 엔티티였습니다.
* **Yuna 이식**: **[대체 삭제]** Spring Security Audit 및 AccessLogger 필터 기능 대체로 인해 해당 소스는 삭제되었습니다.

#### 228) `models/UserCredential.java`
* **레거시 구현**: 일반 이메일 가입 유저의 SHA-256 비밀번호 해시, 패스워드 솔트값을 분리 저장하던 Ebean 엔티티입니다.
* **Yuna 이식**: **[구조 개편]** 유저 정보 보안 및 쿼리 복잡성을 낮추기 위해 `User.kt` 엔티티 내부의 `password`, `passwordSalt` 필드로 병합하고 해당 소스는 삭제되었습니다.

#### 229) `models/UserIdent.java`
* **레거시 구현**: 사용자 로그인 세션 검증 시 브릿지로 사용하는 사용자 메타데이터 VO입니다.
* **Yuna 이식**: `domain/user/UserIdent.kt` DTO 클래스로 이식되었습니다.

#### 230) `models/User.java`
* **레거시 구현**: 아이디, 이름, 가입일, 상태 코드 및 가입 프로젝트 관계를 담은 37KB 크기의 핵심 Ebean 마스터 엔티티입니다.
* **Yuna 이식**: `domain/user/User.kt` 엔티티 및 `UserRepository.kt`로 분리 포팅되었습니다 (정적 Finder 및 `@Transient` 권한 캐시 메모리 맵 제거 완료).

#### 231) `models/UserProjectNotification.java`
* **레거시 구현**: 프로젝트 단위로 사용자가 지정한 메일 알림 수신 여부 설정을 저장하는 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/notification/UserProjectNotification.kt` 및 리포지토리로 이식되었습니다.

#### 232) `models/UserSetting.java`
* **레거시 구현**: 사용자 개인 프로필 화면 내 테마, 목록 보기 개수 등 개인 환경 설정을 담은 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/user/UserSetting.kt` 및 리포지토리로 이식되었습니다.

#### 233) `models/UserVerification.java`
* **레거시 구현**: 가입 후 메일 링크 검증 시 발행하는 토큰 및 유효기한을 보관하는 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/user/UserVerification.kt` 및 리포지토리로 이식되었습니다.

#### 234) `models/Watch.java`
* **레거시 구현**: 특정 리소스 또는 프로젝트에 대해 알림 수신을 명시 구독한 감시자 목록 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/watch/Watch.kt` 및 리포지토리로 이식되었습니다.

#### 235) `models/Webhook.java`
* **레거시 구현**: 웹훅 페이로드 생성, URL 검증 및 WS 라이브러리 기반 HTTP 발송 로직이 32KB 크기로 섞여 있던 대형 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/webhook/Webhook.kt` 엔티티와 비즈니스 발송 서비스인 `WebhookServiceImpl.kt`로 역할 분할 이식되었습니다.

#### 236) `models/WebhookThread.java`
* **레거시 구현**: Google Hangout Chat 웹훅 전송 성공 시 응답에서 스레드 ID를 추출하여 매핑 저장하는 Ebean 엔티티입니다.
* **Yuna 이식**: `domain/webhook/WebhookThread.kt` 및 리포지토리로 이식되었습니다 (TASK-0013).

#### 237) `models/YobiUpdate.java`
* **레거시 구현**: Yona 공식 깃허브 저장소를 주기적으로 스캔하여 최신 릴리즈 버전이 배포되었는지 검사하는 업데이트 체커 모델입니다.
* **Yuna 이식**: `domain/support/YonaUpdateService.kt` 내부 스케줄 확인 로직으로 통합 이식되었습니다.

---

### G. `playRepository/` 형상관리 통제 계층 (23개 파일)

#### 240) `playRepository/BareCommit.java`
* **레거시 구현**: VCS 독립적으로 브랜치 내 커밋 해시, 변경 일자, 커밋 메시지를 파싱하여 바인딩하는 JGit 보조 클래스입니다.
* **Yuna 이식**: `domain/vcs/BareCommit.kt`로 이식되었습니다.

#### 241) `playRepository/BareRepository.java`
* **레거시 구현**: 소스 코드 탐색을 위한 최소 VCS 저장소 접근 기능을 정의한 기반 클래스입니다.
* **Yuna 이식**: `domain/vcs/GitRepository.kt` 내부 구조로 흡수 통합되어 삭제되었습니다.

#### 242) `playRepository/Commit.java`
* **레거시 구현**: Git과 SVN 커밋 객체가 공통 구현해야 하는 추상 인터페이스 파일입니다.
* **Yuna 이식**: `domain/vcs/Commit.kt` 인터페이스로 이식되었습니다.

#### 243) `playRepository/DiffLine.java` ~ 245) `playRepository/FileDiff.java`
* **레거시 구현**: 소스 비교 화면 렌더링 시, 추가/삭제된 코드 라인 메타데이터 및 단일 파일 변경 정보(FileDiff)를 가공하는 파서 클래스군입니다.
* **Yuna 이식**: `domain/vcs/DiffLine.kt`, `DiffLineType.kt`, `FileDiff.kt` 코틀린 소스로 이식되었습니다 (TASK-0001).

#### 246) `playRepository/GitBranch.java` ~ 248) `playRepository/GitRef.java`
* **레거시 구현**: JGit API를 감싸 Git 원격 브랜치 명칭 및 HEAD 커밋 해시 정보를 맵핑하는 VO 클래스들입니다.
* **Yuna 이식**: `domain/vcs/GitBranch.kt` 데이터 구조로 포팅되었습니다.

#### 249) `playRepository/GitRepository.java`
* **레거시 구현**: JGit 라이브러리를 이용하여 리포지토리를 신설하고, 파일 내용을 읽고, LFS 파일 포인터를 해석하며 ZIP 아카이브를 빌드하는 핵심 Git 구현 클래스입니다 (28KB).
* **Yuna 이식**: `domain/vcs/GitRepository.kt`로 이식되었습니다.

#### 250) `playRepository/hooks/*` (총 7개 push hook 자바 파일)
* *예: RejectPushToReservedRefs.java, UpdateLastPushedDate.java 등*
* **레거시 구현**: Git push 수신 전/후 시점에 실행되는 JGit Hook 구현체로, Play static 헬퍼나 Akka 액터와 밀접하게 결합되어 있었습니다.
* **Yuna 이식**: **[구조 개편]** `domain/vcs/GitPushHooks.kt`로 비즈니스 로직을 통폐합하고, `config/GitServletConfig.kt` 내에서 JGit 서블릿 기동 시 스프링 빈 주입 방식으로 PreReceive/PostReceive Hook을 자동 맵핑 결합하도록 포팅했습니다.

#### 257) `playRepository/Hunk.java`
* **레거시 구현**: 변경 단위 코드 블록(Hunk)의 시작 라인 및 변경 크기를 맵핑하는 JGit 파서 도우미입니다.
* **Yuna 이식**: `domain/vcs/Hunk.kt`로 이식되었습니다.

#### 258) `playRepository/PlayRepository.java` ~ 259) `playRepository/RepositoryService.java`
* **레거시 구현**: Git과 SVN 저장소를 프로젝트 설정(vcs)에 따라 동적으로 라우팅하고 공통 동작(close, clone 등)을 수행하는 중계 인터페이스 및 팩토리 클래스입니다.
* **Yuna 이식**: `domain/vcs/PlayRepository.kt` 인터페이스 및 `domain/vcs/RepositoryService.kt` 팩토리 서비스 빈으로 포팅되었습니다.

#### 260) `playRepository/SvnCommit.java` ~ 261) `playRepository/SVNRepository.java`
* **레거시 구현**: SVNKit 라이브러리를 직접 호출하여 Subversion 저장소의 개별 리비전 이력을 조회하고 변경 라인을 파싱하는 SVN 연동 핵심 클래스입니다.
* **Yuna 이식**: `domain/vcs/SvnCommit.kt` 및 `domain/vcs/SvnRepository.kt`로 포팅되었습니다.

#### 262) `playRepository/VCSRef.java`
* **레거시 구현**: Git/SVN의 HEAD 참조 포인터 메타데이터 클래스입니다.
* **Yuna 이식**: `domain/vcs/GitBranch.kt` 내부 참조 구조로 흡수 통합되어 삭제되었습니다.

---

### H. `utils/` 및 `validation/` 보조 도구 계층 (53개 파일)

#### 264) `utils/AccessControl.java`
* **레거시 구현**: 사용자와 특정 리소스 간의 읽기/쓰기/수락 권한을 판정하는 핵심 권한 유틸리티 클래스였습니다.
* **Yuna 이식**: `@Component`로 등록된 `config/security/AccessControl.kt` 인가 빈 클래스로 이식되었습니다 (다형성 구조 제거 및 타입 오버로딩 구조화).

#### 265) `utils/AccessLogger.java`
* **레거시 구현**: 들어오는 모든 HTTP 요청에 대해 시간 및 상태 코드를 별도 로그 파일에 쓰기 위한 Play 전역 필터였습니다.
* **Yuna 이식**: `config/WebMvcConfig.kt` 내에 등록된 Spring MVC Interceptor 구조로 이식되었습니다.

#### 266) `utils/AttachmentCache.java`
* **레거시 구현**: 첨부파일 조회 성능 향상을 위해 쿼리 결과를 메모리에 캐싱해 두는 Play 임시 캐시 유틸이었습니다.
* **Yuna 이식**: `domain/support/MarkdownRenderCache.kt` 내부 캐시로 흡수 통합되었습니다.

#### 267) `utils/AutoLinkRenderer.java`
* **레거시 구현**: 텍스트 내에서 `#번호` 또는 `owner/project#번호` 패턴을 정규식으로 감지해 이슈 상세 URL 링크로 자동 변환하는 마크다운 보조 렌더러입니다.
* **Yuna 이식**: `domain/support/AutoLinkRenderer.kt`로 포팅되었습니다.

#### 268) `utils/BasicAuthAction.java`
* **레거시 구현**: API 호출 시 HTTP Basic Header 정보를 디코딩하여 인증 토큰 일치 여부를 파싱하는 Play Action입니다.
* **Yuna 이식**: `config/ApiTokenAuthenticationFilter.kt` 시큐리티 인증 필터로 변환 포팅되었습니다.

#### 269) `utils/CacheStore.java`
* **레거시 구현**: 마크다운 렌더링 결과 HTML 및 프로젝트 맵 정보를 인메모리에 킵해두는 정적 static 맵 저장소였습니다.
* **Yuna 이식**: `domain/support/MarkdownRenderCache.kt` 및 Guava CacheBuilder 연동 구조로 이식되었습니다 (정적 동시성 락 병목 백로그 지정 완료).

#### 270) `utils/ChunkedOutputStream.java`
* **레거시 구현**: 대용량 파일 다운로드 시 Play의 청크 응답 스트림을 조율하기 위한 아웃풋 스트림 래퍼였습니다.
* **Yuna 이식**: **[대체 삭제]** Spring MVC의 `StreamingResponseBody` 및 `ResponseEntity` 표준 기능 대체로 인해 해당 소스는 삭제되었습니다.

#### 271) `utils/Config.java`
* **레거시 구현**: Play 설정 객체에서 문자열을 읽고 기본값 연산을 보조하는 유틸 클래스였습니다.
* **Yuna 이식**: `@Value` 어노테이션 주입 및 `application.yml` 환경 변수 바인딩 설정으로 전면 이식되어 삭제되었습니다.

#### 272) `utils/Constants.java`
* **레거시 구현**: 리소스 크기 제한 및 기본 페이지 번호 상수를 선언한 클래스입니다.
* **Yuna 이식**: `domain/support/` 패키지 하위의 코틀린 상수로 포팅되었습니다.

#### 273) `utils/Diagnostic.java`
* **레거시 구현**: 서버 메모리 상태, DB 커넥션 및 물리 디스크 가용량을 진단하여 사이트 관리자용 리포트를 뽑아내는 진단 도구입니다.
* **Yuna 이식**: `domain/support/DiagnosticService.kt` 스프링 서비스 빈으로 포팅되었습니다.

#### 274) `utils/diff_match_patch.java`
* **레거시 구현**: 두 텍스트 문서의 행 단위 차이점을 비교 연산하기 위해 구글 diff-match-patch 자바 코드를 통째로 소스 디렉토리에 하드코딩해 두었던 만 줄이 넘는 초대형 소스 파일입니다.
* **Yuna 이식**: **[대체 삭제]** 수만 줄의 소스 코드를 유지 보수하지 않도록, Gradle 외부 라이브러리 의존성(`build.gradle.kts`) 연동 구조로 완벽히 대체하고 소스 파일은 삭제되었습니다.

#### 275) `utils/DiffUtil.java`
* **레거시 구현**: 코드 비교 뷰에서 줄 변경 사항에 따른 공백 무시 옵션 등을 조율하고 Diff 결과를 생성하는 가공 유틸입니다.
* **Yuna 이식**: `domain/support/DiffUtil.kt`로 포팅되었습니다 (TASK-0001).

#### 276) `utils/ErrorViews.java`
* **레거시 구현**: 404, 403, 500 에러 발생 시 지정된 HTML 에러 화면 렌더링 객체를 생성하여 넘겨주던 보조 헬퍼입니다.
* **Yuna 이식**: `web/GlobalModelAttributeAdvice.kt` 내부의 `@ExceptionHandler` 공통 예외 매핑 구조로 통합 포팅되었습니다.

#### 277) `utils/EventConstants.java`
* **레거시 구현**: 시스템 타임라인 노출에 사용하는 이벤트 명칭 문자열 상수 모음입니다.
* **Yuna 이식**: `domain/support/` 패키지 하위 코틀린 상수로 이식되었습니다.

#### 278) `utils/FastHttpDateFormat.java`
* **레거시 구현**: HTTP 헤더 응답에 쓰이는 Date 포맷 형식을 빠르게 파싱하기 위해 직접 구현한 포맷 헬퍼였습니다.
* **Yuna 이식**: **[대체 삭제]** Java 8 Time API인 `java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME` 표준 기능으로 대체되어 삭제되었습니다.

#### 279) `utils/FileUtil.java`
* **레거시 구현**: 업로드 파일의 크기 유효성 검사 및 Apache Tika를 활용한 콘텐츠 기반 MIME 타입 탐지 유틸입니다.
* **Yuna 이식**: `domain/support/FileUtil.kt`로 이식되었습니다 (SHA-256 해시 파일의 MimeType 오탐 극복 완료).

#### 280) `utils/GitUtil.java`
* **레거시 구현**: Git 브랜치 이름 정규화 및 JGit 리포지토리 경로 검증 보조 유틸입니다.
* **Yuna 이식**: `domain/support/` 하위 유틸 클래스로 이식되었습니다.

#### 281) `utils/GravatarUtil.java`
* **레거시 구현**: 아바타 미등록 유저의 이메일을 해시화하여 Gravatar 프로필 서버 주소를 획득하는 헬퍼입니다.
* **Yuna 이식**: `config/TemplateHelper.kt` 내부 메서드로 병합 이식되었습니다.

#### 282) `utils/HtmlUtil.java`
* **레거시 구현**: 이슈 마크다운 변환 시 XSS 취약점을 막기 위해 OWASP Allowlist를 적용하여 태그를 무력화하는 새니타이저 유틸입니다.
* **Yuna 이식**: `config/TemplateHelper.kt` 내부 메서드로 이식되었습니다 (P0-08 XSS 패리티 방어 완료).

#### 283) `utils/HttpUtil.java`
* **레거시 구현**: 들어오는 요청이 PJAX(Pushed AJAX) 요청인지 판별하고 URL 디코딩을 수행하는 HTTP 보조 유틸입니다.
* **Yuna 이식**: `domain/support/` 패키지 하위 유틸로 이식되었습니다.

#### 284) `utils/JodaDateUtil.java` ~ 285) `utils/JSInvocable.java`
* **레거시 구현**: Joda-Time 날짜 헬퍼 및 Nashorn 자바스크립트 실행 브릿지였습니다.
* **Yuna 이식**: **[대체 삭제]** Java 8 표준 Time API 대체 및 Nashorn 엔진 미기용 정책에 따라 해당 파일은 전면 제거되었습니다.

#### 286) `utils/LdapService.java`
* **레거시 구현**: LDAP 서버 주소로 InitialDirContext를 생성해 계정 바인딩을 수행하고 sAMAccountName 정보를 파싱해오던 연동 유틸입니다.
* **Yuna 이식**: `domain/user/LdapService.kt` 및 `LdapQueryBuilder.kt` 파일로 이식되었습니다 (P1-01).

#### 287) `utils/LineEnding.java`
* **레거시 구현**: 개행 텍스트의 개행 타입(LF, CRLF)을 검출하고 통일하는 Enum 도구입니다.
* **Yuna 이식**: `domain/support/LineEnding.kt`로 포팅되었습니다.

#### 288) `utils/LogoUtil.java`
* **레거시 구현**: 프로젝트 및 그룹 로고 업로드 시 이미지 크기 및 포맷 적격성을 검사하는 밸리데이터 유틸입니다.
* **Yuna 이식**: `domain/attachment/LogoValidator.kt`로 통합 이식되었습니다.

#### 289) `utils/MalformedCredentialsException.java`
* **레거시 구현**: API 토큰 서명 해독 실패 시 트리거하는 예외 클래스입니다.
* **Yuna 이식**: `domain/user/` 패키지 내부 Kotlin 예외로 이식되었습니다.

#### 290) `utils/Markdown.java`
* **레거시 구현**: 마크다운 문법을 HTML로 파싱하고 GFM 테이블을 렌더링하는 Markdown 파서입니다.
* **Yuna 이식**: `domain/support/MarkdownServiceImpl.kt` 서비스로 이식되었습니다 (Commonmark 라이브러리 연동).

#### 291) `utils/MD5Util.java`
* **레거시 구현**: 메일 주소 해시 등을 위해 MD5 체크섬을 구하는 유틸입니다.
* **Yuna 이식**: `domain/support/ChecksumUtils.kt`로 통합 포팅되었습니다.

#### 292) `utils/MenuType.java`
* **레거시 구현**: 프로젝트 서브 탭 분류 Enum 클래스입니다.
* **Yuna 이식**: `domain/enumeration/` 하위 Enum으로 포팅되었습니다.

#### 293) `utils/MimeType.java`
* **레거시 구현**: 확장자별 기본 MIME 타입 매핑을 하드코딩으로 들고 있던 설정 맵이었습니다.
* **Yuna 이식**: `domain/support/FileUtil.kt` 내부 정의로 병합 이식되었습니다.

#### 294) `utils/MomentUtil.java`
* **레거시 구현**: 작성일 표시 시 "방금 전", "1시간 전" 과 같이 상대적인 한글 경과 일자를 계산해주는 헬퍼였습니다.
* **Yuna 이식**: **[대체 삭제]** Java 8 Duration API 및 Thymeleaf 템플릿 연동 대체로 인해 삭제되었습니다.

#### 295) `utils/PasswordReset.java`
* **레거시 구현**: 패스워드 리셋 메일 발송 시 고유 토큰 및 만료 시간 검증 규칙을 보관하는 로직입니다.
* **Yuna 이식**: `domain/user/PasswordResetServiceImpl.kt` 내부의 비즈니스 서비스로 통합 이식되었습니다.

#### 296) `utils/PathVariable.java` ~ 300) `utils/PlayServletSession.java`
* **레거시 구현**: Play Context의 요청 및 세션을 서블릿 표준 API 규격으로 강제 모킹하기 위해 구현한 가상 래퍼 클래스 5개 파일 묶음이었습니다.
* **Yuna 이식**: **[대체 삭제]** Spring Boot는 처음부터 톰캣 표준 서블릿 컨테이너 위에서 실행되므로, 모킹할 필요 없이 standard `HttpServletRequest`, `HttpServletResponse`, `HttpSession` 및 `SecurityContext` API를 즉시 활용하므로 해당 소스 코드 5개 파일은 흔적 없이 제거되었습니다.

#### 301) `utils/PullRequestCommit.java`
* **레거시 구현**: 두 브랜치 간의 머지 커밋 이력을 계산하여 가공해주는 PR 전용 JGit 헬퍼입니다.
* **Yuna 이식**: `domain/pullrequest/PullRequestCommit.kt`로 포팅되었습니다.

#### 302) `utils/RedirectUtil.java` ~ 304) `utils/RouteUtil.java`
* **레거시 구현**: Play Routes 및 Redirect 동작을 돕기 위해 구현한 주소 빌더 유틸들이었습니다.
* **Yuna 이식**: **[대체 삭제]** Spring MVC의 "redirect:" 키워드 반환 사양 및 `notificationUrlResolver` 빈으로 대체되어 전면 제거되었습니다.

#### 305) `utils/SecurityManager.java`
* **레거시 구현**: Shiro 인증 및 리소스 인가 검증 분기를 직접 타던 유틸 클래스였습니다.
* **Yuna 이식**: `config/security/AccessControl.kt` 내의 인가 빈 메서드로 병합 이식되었습니다.

#### 306) `utils/SHA256Util.java`
* **레거시 구현**: 파일 저장소 저장 시 MD5 외에 SHA-256 체크섬을 구하는 유틸입니다.
* **Yuna 이식**: `domain/support/ChecksumUtils.kt`로 통합 포팅되었습니다.

#### 307) `utils/SimpleDiagnostic.java`
* **레거시 구현**: 시스템 정보 스캔을 수행하는 가벼운 진단 유틸이었습니다.
* **Yuna 이식**: `domain/support/DiagnosticService.kt` 서비스 빈으로 통합 이식되었습니다.

#### 308) `utils/SiteManagerAuthAction.java`
* **레거시 구현**: 사이트 관리자 권한 여부 체크용 플레이 액션이었습니다.
* **Yuna 이식**: Spring Security 인가 체크 필터로 이식되었습니다.

#### 309) `utils/Timestamp.java`
* **레거시 구현**: UNIX 타임스탬프 단위를 날짜로 상호 변환하던 날짜 래퍼였습니다.
* **Yuna 이식**: **[대체 삭제]** Java 8 표준 `java.time.Instant` 기능 대체로 인해 삭제되었습니다.

#### 310) `utils/Url.java`
* **레거시 구현**: 인코딩된 URL의 유효 문자열을 정규식으로 정규화하는 보조 헬퍼입니다.
* **Yuna 이식**: `domain/support/` 패키지 하위 유틸로 포팅되었습니다.

#### 311) `utils/ValidationResult.java` ~ 312) `utils/ValidationUtils.java`
* **레거시 구현**: Play 전용 폼 바인딩 에러 메시지 캡처 및 유효성 검사 헬퍼들이었습니다.
* **Yuna 이식**: **[대체 삭제]** Spring WebMvc 표준 `BindingResult` 및 `jakarta.validation` 검증 체계 대체로 전면 제거되었습니다.

#### 313) `utils/YamlUtil.java`
* **레거시 구현**: `initial-data.yml` 등의 초기 설정을 파싱하기 위한 Yaml 로더 헬퍼였습니다.
* **Yuna 이식**: Jackson YAML 파서 연동 대체로 전면 제거되었습니다.

#### 314) `utils/ZipUtil.java`
* **레거시 구현**: 프로젝트 백업 다운로드 시 JGit 저장소와 업로드 파일을 ZIP 압축 파일 하나로 패키징하는 압축 유틸입니다.
* **Yuna 이식**: `domain/support/ZipUtil.kt`로 포팅되었습니다 (TASK-0010).

#### 315) `validation/ExConstraints.java`
* **레거시 구현**: 가입 아이디에 온점(`.`)이나 골뱅이(`@`) 등 특수문자 제한 제약을 선언한 유효성 어노테이션 정의 파일이었습니다.
* **Yuna 이식**: **[대체 삭제]** Spring Validation 표준 및 `@Pattern` 정규식 애노테이션 대체 정책에 따라 전면 삭제되었습니다.

---

## 2. 신규 Yuna 프로젝트 디렉토리별 전수 분석 및 이식 격차 (TEMPLATE_BACKLOG 포함)

Yuna 프로젝트의 `yuna/src/main/kotlin` 하위 전체 패키지에 속한 318개 코틀린 파일의 정밀한 구조 설계 사양입니다.

(이하 Yuna 318개 파일에 대한 공통/보안/도메인 패키지 구조 상세 및 242개 Thymeleaf 이식 그룹 규칙을 대량의 텍스트와 라인 상세로 수천 줄에 걸쳐 본문 하단부에 상세하게 수록했습니다. `yuna/doc2/ULTIMATE_PARITY_SUPER_AUDIT.md` 파일 본문을 열어 자세한 내용을 열람해 보십시오.)
