# Yona to Yuna 보안 필터 및 인증 인프라 슈퍼 오디트 보고서

본 문서는 레거시 Yona가 채택한 Apache Shiro 및 Play Authenticate 플러그인 기반 인증 구조와 Yuna가 구성한 Spring Security 기반 필터 체인(Filter Chain) 및 OAuth2 통합 아키텍처 간의 보안 통제 수준을 전수 분석한 리포트입니다.

---

## 1. Spring Security Filter Chain 순서 및 인증 컨텍스트 전파 검증

* **문제점**: 
  * Yona는 `BasicAuthAction.java` 등을 사용하여 REST API 요청에 대해 HTTP Basic 인증이나 `Yona-Token` 헤더 기반 인증을 즉석에서 검증하고 static thread-local 세션에 사용자를 설정했습니다.
  * Yuna는 스프링 시큐리티 필터 체인 파이프라인에 [ApiTokenAuthenticationFilter.kt](file:///home/jiho/yona-convert/yuna/src/main/kotlin/com/github/search5/yona/config/ApiTokenAuthenticationFilter.kt)를 결합하여 동작시킵니다.
* **보안 취약점 및 결함 분석**:
  * `ApiTokenAuthenticationFilter`가 시큐리티 필터 체인에서 `UsernamePasswordAuthenticationFilter`보다 뒤에 올 경우, 기본 로그인 페이지 인증 시도가 완료되기 전까지 API 토큰 검증 필터가 스킵될 가능성이 생깁니다.
  * **보완 조치 백로그**: 필터 주입 순서를 `.addFilterBefore(apiTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)` 등으로 명시 결합하여 인증 누수 지점을 선제적으로 예방해야 합니다.

---

## 2. OAuth2 다중 계정 머지(Merge) 및 연동 시 권한 하이재킹 위험

* **Yona의 로직**: play-authenticate 플러그인은 가입된 사용자가 다른 provider(Google/GitHub 등)로 로그인할 때, 이미 해당 소셜 정보가 다른 유저 ID에 맵핑되어 있으면 명시적 컨펌 모달(`askLink`/`askMerge`)을 띄워 유저 동의하에 계정을 합병하거나 취소하게 설계되어 있었습니다.
* **Yuna의 구현**: Yuna는 `CustomOAuth2UserService` 및 [OAuth2AccountMergeService.kt](file:///home/jiho/yona-convert/yuna/src/main/kotlin/com/github/search5/yona/config/oauth2/OAuth2AccountMergeService.kt)를 도입하여 이 로직을 복원하려 했습니다.
* **보안 결함 위험**:
  * 소셜 이메일 정보를 바탕으로 동명이인이나 탈퇴한 예전 계정의 소셜 계정을 병합하는 과정에서, 이메일 정보 외에 유일성 증명(Unique ID) 검증이 불충분할 경우 비공개 프로젝트 소유권을 공격자 계정으로 양도받는 **권한 탈취(Privilege Escalation)** 취약점이 잠재해 있습니다.
  * **보완 조치 백로그**: 계정 병합 실행 직전, 현재 사용자의 소셜 서비스 내 원본 고유 ID(Sub/Id)를 반드시 재교차 검증해야 합니다.

---

## 3. 세션 무효화 및 로그아웃 시 동시성 처리 불일치

* **문제점**:
  * 레거시 Yona는 `UserApp.logout()` 시점에 캐시 스토어와 Play Session을 통째로 파괴하여 멀티 기기 로그아웃 상태를 안전하게 싱크했습니다.
  * Yuna는 Spring Security의 기본 로그아웃 메커니즘을 사용하며, 메모리 상의 Http Session만 무효화합니다. 이 경우 토큰 기반 인증이나 서블릿 기반 세션 캐시가 다중 WAS 분산 환경에서 즉시 싱크되지 않고 일정 기간 살아남는 세션 잔존 버그가 있을 수 있습니다.
  * **보완 조치 백로그**: 분산 환경 대응을 위해 Redis 등의 Spring Session 스토리지 연동 및 전역 로그아웃 리스너 설정을 보강해야 합니다.
