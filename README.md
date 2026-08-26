# yuna

레거시 [yona](https://github.com/yona-projects/yona)(Play Framework/Java/Ebean)를 Spring Boot/Kotlin/JPA로 이식한 프로젝트입니다.

## 요구 사항

- JDK 21
- (테스트/운영 DB) MariaDB — 기본 스프링 프로파일이 `mariadb`

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
