# Yona to Yuna DB 스키마 및 마이그레이션 슈퍼 오디트 보고서

본 문서는 레거시 Yona가 기동 시 로딩하던 Ebean DDL 스키마(`evolutions/*.sql`) 정책과 신규 Yuna의 Spring Data JPA(Hibernate) 엔티티 기반 DDL 생성 정책 간의 데이터 구조적 동치성을 정밀하게 대조한 리포트입니다.

---

## 1. 시간 데이터 타입 정밀도 및 절사(Truncation) 위험 검증

* **문제점**: 레거시 Yona(Java 8)는 `java.util.Date` 타입을 활용하였고, MariaDB/MySQL 연동 시 밀리초 정밀도 없이 `datetime` 타입으로 컬럼이 생성되었습니다.
* **Yuna의 구현**: Kotlin 엔티티는 Java 8 표준 `java.time.Instant`를 매핑합니다. Hibernate는 기본적으로 `datetime(6)`으로 매핑하려 시도합니다.
* **아키텍처 충돌 및 위험성**:
  * 기존 Yona DB 데이터를 Yuna 백업 도구(`DataBackupServiceImpl.kt`)로 복원할 경우, 마이크로초 소수점(datetime(6))이 포함된 Yuna 데이터와 소수점이 없는 레거시 데이터 간의 문자열 해시 비교나 일자 비교 쿼리가 어긋날 위험이 도사리고 있습니다.
  * **보완 조치 백로그**: JPA 엔티티 내 `Instant` 필드에 `@Column(columnDefinition = "datetime")`을 명시하여 소수점 정밀도를 기존 프로덕션 DB 수준으로 명시 강제해야 합니다.

---

## 2. 외래키(Foreign Key) 삭제 전파 및 Cascade 제약 조건 대조

Ebean의 DDL 생성 정책과 Hibernate의 DDL 자동 생성 정책은 외래키 제약조건 설정 방식에서 명백한 차이를 보입니다.

| 레거시 테이블 관계 | 레거시 FK 동작 (Ebean DDL) | 신규 JPA FK 동작 (Hibernate) | 아키텍처 결함 및 유실 위험 분석 |
| :--- | :--- | :--- | :--- |
| `n4user` ➔ `user_enrolled_project` | `on delete cascade` | `on delete cascade` 미지정 가능성 | 유저 탈퇴 시 프로젝트 참여 테이블 데이터가 물리적으로 지워지지 않아 무결성 제약 오류 유발 |
| `project` ➔ `issue` | `on delete restrict` / `cascade` 선택적 적용 | `orphanRemoval = true` 위임 | Hibernate는 DB 수준의 FK onDelete Cascade 대신 어플리케이션 단에서 개별 삭제 쿼리를 연쇄 발행하므로 벌크 삭제 시 커넥션 타임아웃 위험성 잔존 |
| `project` ➔ `forking_projects` | `on delete set null` | JPA Cascade 정책 충돌 | **[중요 발견]** original_project_id 관계에서 원본 삭제 시 fork 프로젝트까지 연쇄 삭제되지 않고 외래키 연동만 끊기도록 CascadeType.PERSIST만 유지되도록 구현된 지점의 DB 단 물리 FK 옵션 검증 필요 |

---

## 3. 마이그레이션 백업 파일 복원 시 AI (Auto Increment) 충돌 시나리오

* **재현 조건**: Yuna의 백업 임포트 도구인 `DataBackupServiceImpl.kt`는 테이블 데이터를 백업 JSON으로부터 로드하여 순차 `save` 방식으로 저장합니다.
* **충돌 메커니즘**:
  1. JPA 엔티티가 `@GeneratedValue(strategy = GenerationType.IDENTITY)`를 채택함에 따라, 신규 생성 시 데이터베이스의 AI 카운터를 따릅니다.
  2. 복원 시 백업본의 고정된 ID(PK)를 명시적으로 삽입하려고 시도할 때, 데이터베이스가 가진 AI 내부 시퀀스 값과 이미 복원된 PK 값이 충돌하여 `Primary Key Duplication` 예외가 발생할 수 있습니다.
  3. 특히 MariaDB와 PostgreSQL은 복원 후 AI 시퀀스를 `MAX(id) + 1`로 강제 재조정(Sequence Alignment)하는 DDL 명령을 실행해주지 않으면, 다음 사용자 요청 등록 시 무조건 가입/생성 오류가 발생합니다.
* **보완 조치 백로그**: 백업 복원 로직 종료 직후, 각 RDBMS 방언(Dialect)에 맞춘 `ALTER TABLE ... AUTO_INCREMENT = ...` 또는 `SELECT setval(...)` 시퀀스 자동 얼라인먼트 쿼리를 전수 강제 실행하는 로직이 신설되어야 합니다.
