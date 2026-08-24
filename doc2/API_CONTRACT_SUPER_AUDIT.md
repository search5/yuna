# Yona to Yuna API 라우팅 및 컨트롤러 계약(Contract) 슈퍼 오디트 보고서

본 문서는 레거시 Yona의 Play `routes` 파일 선언과 Yuna의 Spring MVC 어노테이션 기반 컨트롤러 라우팅 간의 경로 정합성, 와일드카드 처리 규격 및 HTTP 통신 파라미터 매핑을 분석한 리포트입니다.

---

## 1. Play Routes 와일드카드 패턴과 Spring WebMvc 정규식 매핑 충돌 검증

* **문제점**:
  * 레거시 Yona의 `conf/routes`에서는 특정 브랜치명이나 커밋 해시가 슬래시(`/`)를 포함할 경우, Play 전용 와일드카드인 `*` 문법을 활용하여 처리했습니다.
    * 예: `GET /:owner/:project/code/*path` ➔ 슬래시를 포함한 전체 하위 경로가 path 변수로 캡처됨.
  * Spring WebMvc는 기본적으로 경로 상의 `/`를 디렉토리 구분자로 간주하여 패턴 매칭에 실패하고 404 Not Found를 뱉습니다.
* **Yuna의 구현**: Yuna의 [CodeViewController.kt](file:///home/jiho/yona-convert/yuna/src/main/kotlin/com/github/search5/yona/web/CodeViewController.kt) 및 `CompareViewController.kt` 등에서는 스프링이 지원하는 AntPathMatcher 정규식 패턴을 사용해 이를 포팅했습니다.
  * 예: `GET /api/projects/{projectId}/compare/{revA:.+}..{revB:.+}`
* **잠재적 위험 및 누락 백로그**:
  * Git 브랜치 이름 자체에 온점(`.`)이나 퍼센트 인코딩된 특수문자가 포함된 경우, Spring MVC의 도트(`.`) 생략 패턴 매칭이나 URL 디코딩 가드 필터로 인해 매핑이 어긋나는 경우가 대량 발생할 수 있습니다.
  * **보완 조치 백로그**: 모든 VCS 관련 WebMvc 컨트롤러의 Mapping 경로를 정밀 정규식(`{path:**}`)으로 통일하고, URL 디코딩을 수동 수행하는 필터 정합성 테스트를 전수 보강해야 합니다.

---

## 2. Play 폼 바인딩(`Form<T>`)과 Spring `@ModelAttribute` 검증 규칙 격차

레거시 Play는 폼 데이터 바인딩 시 `Form.bindFromRequest()`를 명시적으로 컨트롤러 단에서 실행하여 BindingResult 유효성 체크를 진행했습니다.

| 항목 | 레거시 Play Form 바인딩 | 신규 Spring @ModelAttribute 바인딩 | 차이점 및 오작동 잠재성 |
| :--- | :--- | :--- | :--- |
| **에러 노출** | `form.hasErrors()` ➔ `form.errorsAsJson()` 응답 | `@Valid` + `BindingResult` 파라미터 | BindingResult 인자가 생략되었을 경우, 유효성 에러 발생 시 HTML 400 에러 페이지가 노출되며 JSON 오류 응답 형식이 유실됨 |
| **Default 바인딩** | 필드가 비어있을 시 빈 문자열 또는 null 주입 | Spring은 Primitive Type 변환 실패 시 `MethodArgumentNotValidException` 발생 | 타입 변환 실패 예외가 발생할 때, 전역 `@ExceptionHandler`가 이를 잡아 Yona 사양에 대응하는 JSON 오류 번들로 조형해 돌려주지 못할 가능성 |

---

## 3. HTTP 응답 상태 코드 및 헤더 불일치 영역

* **JSON 데이터 응답 규격**:
  * Yona의 `WSResponse` 및 REST API 응답은 ObjectMapper의 `Json.newObject()` 구조로 생성되어 항상 소문자 CamelCase 필드를 리턴했습니다.
  * Yuna의 Jackson 모듈은 Kotlin 데이터 클래스를 직렬화할 때, 필드 명칭 및 기본 언어 번역 맵의 정밀 직렬화가 누락되어 프론트엔드 AJAX 콜백에서 정의되지 않은 필드(undefined) 에러를 낼 위험이 있습니다.
* **보완 조치 백로그**: REST API 전용 직렬화 ObjectMapper 빈을 별도 구성하여 SnakeCase/CamelCase 변환 규칙을 전수 맞추어야 합니다.
