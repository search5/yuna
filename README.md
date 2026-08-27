# yuna

<img src="src/main/resources/static/images/yona-logo.png" width="220px" alt="Yona logo">

**yuna**는 [Yona](https://github.com/yona-projects/yona)(Play Framework/Java/Ebean 기반의 설치형
프로젝트 협업 플랫폼)를 **Kotlin + Spring Boot + JPA(Hibernate)** 스택으로 새로 옮겨 쓴
프로젝트입니다. 화면 구조·데이터 모델·동작 방식은 legacy Yona와 최대한 동일하게 유지하면서,
런타임과 빌드 도구만 현재 JVM 생태계로 교체하는 것을 목표로 합니다.

## Yona란?

- Git/SVN 저장소가 내장된 설치형 이슈 트래커 + 게시판 + 코드 리뷰 플랫폼
- 네이버/네이버랩스를 비롯해 여러 기업·공공기관에서 수년간 실사용되며 다듬어진 애플리케이션

### 주요 기능

- 프로젝트 기반의 유연한 이슈 트래커와 게시판 — 프로젝트 간 이슈 이동, 서브 태스크, 본문 변경이력,
  이슈 템플릿
- 내장 코드 저장소 — Git/SVN 선택 가능, 온라인 수정·커밋, 프로젝트 멤버 전용 접근 제어
- 블록 기반 코드 리뷰 — 코드 블록 단위 리뷰 스레드, 리뷰 점수
- 그룹(조직) 기능 — 그룹 단위 이슈/게시글 통합 관리, 그룹 프로젝트·멤버
- LDAP 지원 및 소셜 로그인(OAuth2)
- 다른 서비스·다른 Yona/yuna 인스턴스로의 마이그레이션(Export/Import, GitHub 이전 등)

## Yona → yuna: 무엇이 바뀌었나

| | legacy Yona | yuna |
|---|---|---|
| 언어 | Java / Scala 템플릿 | Kotlin |
| 프레임워크 | Play Framework 2.x | Spring Boot |
| ORM | Ebean | JPA / Hibernate |
| 뷰 엔진 | Scala Template(`.scala.html`) | Thymeleaf |
| JDK | Java 8 | Java 21 |
| 지원 DB | MariaDB(기본) 또는 H2(내장형) | **MariaDB / PostgreSQL / MySQL / SQL Server / CUBRID** |

포팅 진행 상황과 legacy 대비 의도적으로 남겨둔 차이점은 `docs/PARITY_BACKLOG.md`,
`docs/TEMPLATE_BACKLOG.md`, `docs/COVERAGE_BACKLOG.md`에 기록돼 있습니다.

## 요구 사항

- JDK 21
- 운영/테스트 DB 중 하나: MariaDB(기본), PostgreSQL, MySQL, SQL Server, CUBRID

## 빌드 & 실행

```bash
# Linux / macOS
./gradlew bootRun

# Windows
gradlew.bat bootRun
```

테스트:

```bash
./gradlew test        # Linux/macOS
gradlew.bat test       # Windows
```

## 데이터베이스 선택

기본 Spring 프로파일은 `mariadb`입니다. 다른 DB로 운영하려면 `spring.profiles.active`를
아래 중 하나로 지정하세요(`src/main/resources/application.yml`에 각 프로파일의 접속 설정이 있습니다).

| 프로파일 | DB |
|---|---|
| `mariadb` (기본값) | MariaDB |
| `postgres` | PostgreSQL |
| `mysql` | MySQL |
| `mssql` | Microsoft SQL Server |
| `cubrid` | CUBRID |

```bash
java -jar yuna.jar --spring.profiles.active=postgres
```

통합 테스트는 실제 Docker 컨테이너(Testcontainers) 기준으로 5개 DB 전부 검증돼 있습니다.
특정 DB로만 테스트를 돌리려면(**동시에 두 개 이상 돌리면 gradle 빌드 출력 디렉터리가
꼬이니 항상 한 번에 하나씩만 실행하세요**):

```bash
./gradlew test -Dyona.it.db=postgres   # mariadb|postgres|mysql|mssql|cubrid
```

## 운영 환경 설정 (특히 Windows)

물리 저장소(git bare repo, svn repo, git-lfs 객체, 첨부파일 업로드)를 디스크의 어느 경로에 둘지는
아래 4개 설정으로 제어합니다. 기본값이 `/tmp/yona/...` 형태의 유닉스 절대경로이기 때문에,
**Windows에서 운영할 때는 반드시 아래 값들을 Windows 경로로 재설정해야 합니다.**

| 설정 키 | 기본값 | 용도 |
|---|---|---|
| `yona.git.base-dir` | `/tmp/yona/git` | Git bare 저장소 루트 |
| `yona.svn.base-dir` | `/tmp/yona/svn` | SVN 저장소 루트 |
| `yona.lfs.base-dir` | `/tmp/yona/lfs` | Git LFS 객체 저장 루트 |
| `yona.upload.base-dir` | `${yona.data:data}/uploads` (상대경로) | 첨부파일 업로드 루트 |

### 설정 변경 방법

1. **`application.yml`에 직접 지정** (가장 확실한 방법)

   ```yaml
   yona:
     git:
       base-dir: "D:/yona-data/git"
     svn:
       base-dir: "D:/yona-data/svn"
     lfs:
       base-dir: "D:/yona-data/lfs"
     upload:
       base-dir: "D:/yona-data/uploads"
   ```

   Windows 경로도 슬래시(`/`)로 적으면 됩니다(자바가 두 구분자를 모두 인식합니다). 백슬래시를 쓸
   경우 YAML 이스케이프 때문에 `\\`로 두 번 써야 하므로, 슬래시 표기를 권장합니다.

2. **실행 시 커맨드라인 인자로 지정** (`application.yml`을 건드리지 않고 배포별로 다르게 줄 때)

   ```powershell
   java -jar yuna.jar --yona.git.base-dir=D:\yona-data\git --yona.svn.base-dir=D:\yona-data\svn --yona.lfs.base-dir=D:\yona-data\lfs --yona.upload.base-dir=D:\yona-data\uploads
   ```

   `-D`로 JVM 시스템 프로퍼티를 주는 방식(`java -Dyona.git.base-dir=D:\... -jar yuna.jar`)도 동일하게 동작합니다.

3. **환경 변수** — Spring Boot의 relaxed binding 규칙상 `yona.git.base-dir`에 대응하는 환경 변수명은
   `YONA_GIT_BASEDIR`처럼 하이픈(`-`)이 빠진 형태입니다(다른 `YONA_*` 설정들처럼 밑줄로 치환되는 게
   아님). 헷갈리기 쉬우므로 **1번(yml) 또는 2번(커맨드라인 인자) 방식을 권장**합니다.

### Windows에서 Fork(하드링크 복제) 사용 시 전제 조건

프로젝트 Fork는 저장소를 실제로 복사하지 않고 파일시스템 하드링크로 복제합니다
(`ProjectServiceImpl.cloneHardLinkedRepository`). 이 방식이 정상 동작하려면:

- `yona.git.base-dir`(및 `yona.svn.base-dir`) 전체가 **하나의 NTFS 볼륨(드라이브)** 안에 있어야
  합니다. 서로 다른 드라이브 간에는 하드링크가 불가능해 Fork가 실패합니다(폴백 복사 없음, 의도적 설계).
- 저장 위치가 **NTFS**여야 합니다. FAT32/exFAT로 포맷된 외장 디스크나 일부 네트워크 드라이브는
  하드링크 자체를 지원하지 않아 Fork가 실패합니다.

## 코드 구조 개요

`docs/PARITY_BACKLOG.md`, `docs/TEMPLATE_BACKLOG.md`, `docs/COVERAGE_BACKLOG.md`에 legacy yona 대비
이식 진행 상황과 의도적으로 남겨둔 차이점들이 기록되어 있습니다.

## 라이선스

yuna는 원본 Yona/Yobi와 동일하게 [Apache License 2.0](LICENSE)으로 제공됩니다.
서드파티 구성 요소 고지는 [NOTICE](NOTICE), 원 프로젝트 기여자 명단은 [AUTHORS](AUTHORS)를
참고하세요.
