import java.io.File
import java.util.concurrent.TimeUnit

plugins {
	kotlin("jvm") version "2.2.21"
	kotlin("plugin.spring") version "2.2.21"
	id("org.springframework.boot") version "4.0.6"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "2.2.21"
	jacoco
}

group = "com.github.search5"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
	maven {
		url = uri("https://packages.scm-manager.org/repository/releases/")
	}
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("io.micrometer:micrometer-registry-prometheus")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-mail")
	// yona utils/AttachmentCache.java의 Play Cache(24시간 TTL) 대응 (P2-49).
	implementation("org.springframework.boot:spring-boot-starter-cache")
	implementation("com.github.ben-manes.caffeine:caffeine")
	// yona MailboxService의 com.sun.mail.imap.IMAPFolder/IMAPStore(IDLE 명령, UID 조회) 대응 (P1-55).
	// spring-boot-starter-mail은 angus-mail을 runtimeOnly로만 끌어와 IMAPFolder 등 구현 클래스가
	// 컴파일 시점엔 보이지 않으므로 명시적으로 추가한다(버전은 Spring Boot 의존성 관리로 고정됨).
	implementation("org.eclipse.angus:angus-mail")
	// yona CreationViaEmail.postprocessForHTML()의 new HtmlCompressor().compress() 대응 (P1-61).
	implementation("com.googlecode.htmlcompressor:htmlcompressor:1.4")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")
	implementation("tools.jackson.module:jackson-module-kotlin")
	runtimeOnly("org.mariadb.jdbc:mariadb-java-client")
	runtimeOnly("org.postgresql:postgresql")
	// MariaDB/PostgreSQL 외 지원 대상 DB(MySQL/SQL Server/CUBRID) 드라이버.
	runtimeOnly("com.mysql:mysql-connector-j:9.1.0")
	runtimeOnly("com.microsoft.sqlserver:mssql-jdbc:12.8.1.jre11")
	runtimeOnly("org.cubrid:cubrid-jdbc:11.3.2.0053")
	// CUBRIDDialect는 hibernate-core가 아니라 hibernate-community-dialects에 있다(공식 유지보수
	// 대상은 아니지만 현재 Hibernate 7.x용으로 갱신돼 있음, org.hibernate.community.dialect.CUBRIDDialect).
	implementation("org.hibernate.orm:hibernate-community-dialects")

	// JGit
	implementation("org.eclipse.jgit:org.eclipse.jgit:7.6.0.202603022253-r")
	implementation("org.eclipse.jgit:org.eclipse.jgit.http.server:7.6.0.202603022253-r")
	implementation("org.eclipse.jgit:org.eclipse.jgit.lfs:7.6.0.202603022253-r")
	implementation("org.eclipse.jgit:org.eclipse.jgit.lfs.server:7.6.0.202603022253-r")

	// SVNKit
	implementation("org.tmatesoft.svnkit:svnkit:1.10.11")
	implementation("sonia.svnkit:svnkit-dav:1.10.10-scm2-jakarta")

	// juniversalchardet
	implementation("com.github.albfernandez:juniversalchardet:2.5.0")

	// Commonmark
	implementation("org.commonmark:commonmark:0.22.0")
	implementation("org.commonmark:commonmark-ext-gfm-tables:0.22.0")
	implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.22.0")
	implementation("org.commonmark:commonmark-ext-autolink:0.22.0")

	// JSoup
	implementation("org.jsoup:jsoup:1.17.2")

	// OWASP HTML Sanitizer (allowlist 기반 XSS 방지, yona Markdown.java와 동등 정책)
	implementation("com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer:20240325.1")

	// Apache Commons Lang3
	implementation("org.apache.commons:commons-lang3:3.14.0")

	// JExcelAPI (Legacy Yona Excel support)
	implementation("net.sourceforge.jexcelapi:jxl:2.6.12")

	// Apache Tika (yona FileUtil.detectMediaType()의 콘텐츠 기반 MIME 감지 대응, P2-25) — 확장자가
	// 없는 해시 파일명(SHA-256 원문 저장 방식) 그대로 JDK Files.probeContentType()에 넘기면 사실상
	// 항상 감지 실패해 모든 첨부가 application/octet-stream으로 저장된다.
	implementation("org.apache.tika:tika-core:2.9.2")

	// Guava (yona utils/CacheStore.java의 renderedMarkdown 캐시 대응, P2-43) — 사용자 지시로 원본
	// 그대로 Guava Cache/CacheBuilder를 사용한다(Caffeine 등으로 대체하지 않음).
	implementation("com.google.guava:guava:33.3.1-jre")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

	// Kotest & MockK
	testImplementation("io.kotest:kotest-runner-junit5:5.9.0")
	testImplementation("io.kotest:kotest-assertions-core:5.9.0")
	testImplementation("io.kotest.extensions:kotest-extensions-spring:1.3.0")
	testImplementation("io.mockk:mockk:1.13.11")

	// Testcontainers — MariaDB/PostgreSQL/MySQL/SQL Server/CUBRID 5개 DB를 전부 도커 컨테이너
	// 기준으로 검증하기 위해 버전을 1.21.4로 통일하고 mssqlserver/mysql 모듈을 추가했다.
	testImplementation("org.testcontainers:testcontainers:1.21.4")
	testImplementation("org.testcontainers:junit-jupiter:1.21.4")
	testImplementation("org.testcontainers:postgresql:1.21.4")
	testImplementation("org.testcontainers:mariadb:1.21.4")
	testImplementation("org.testcontainers:mysql:1.21.4")
	testImplementation("org.testcontainers:mssqlserver:1.21.4")
	// CUBRID 공식 Testcontainers 모듈(testcontainers.com Official Module, CUBRID사 직접 관리).
	testImplementation("org.cubrid:testcontainers-cubrid:0.1.0")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

// Docker가 없고 Podman만 있는 환경(예: macOS)에서는 표준 유닉스 소켓 탐색이 실패하므로,
// 명령어 존재 여부를 확인해 Podman의 로컬 API 소켓을 DOCKER_HOST로 지정한다.
fun resolveDockerHost(): String? {
	if (System.getenv("DOCKER_HOST") != null) return null
	fun commandExists(cmd: String) = runCatching {
		ProcessBuilder("which", cmd).start().waitFor() == 0
	}.getOrDefault(false)
	if (commandExists("docker")) return null
	if (!commandExists("podman")) return null
	return runCatching {
		val proc = ProcessBuilder("podman", "machine", "inspect", "--format", "{{.ConnectionInfo.PodmanSocket.Path}}")
			.redirectErrorStream(true).start()
		proc.waitFor(10, TimeUnit.SECONDS)
		val socketPath = proc.inputStream.bufferedReader().readText().trim()
		if (socketPath.isNotBlank() && File(socketPath).exists()) "unix://$socketPath" else null
	}.getOrNull()
}

tasks.withType<Test> {
	useJUnitPlatform()
	systemProperty("spring.profiles.active", "test")
	systemProperty("testcontainers.host", "127.0.0.1")
	systemProperty("api.version", "1.44")
	// AbstractIntegrationTest의 DB 컨테이너 선택 스위치(mariadb|postgres|mysql|mssql|cubrid).
	// -Dyona.it.db=... 로 gradle CLI에 준 값을 포크된 테스트 JVM까지 그대로 전달한다.
	systemProperty("yona.it.db", System.getProperty("yona.it.db", "mariadb"))
	environment("DOCKER_API_VERSION", "1.44")
	environment("TESTCONTAINERS_RYUK_DISABLED", "true")
	environment("TESTCONTAINERS_CONTAINER_STARTUP_TIMEOUT", "120")
	environment("TESTCONTAINERS_HOST_OVERRIDE", "127.0.0.1")
	resolveDockerHost()?.let { environment("DOCKER_HOST", it) }
	finalizedBy(tasks.jacocoTestReport)
}

jacoco {
	toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		xml.required.set(true)
		html.required.set(true)
		csv.required.set(false)
	}
}

tasks.jacocoTestCoverageVerification {
	dependsOn(tasks.jacocoTestReport)
	violationRules {
		rule {
			// 회귀 감사 백로그(docs/PARITY_BACKLOG.md) 항목을 구현할 때마다
			// 해당 클래스 단위로 커버리지를 개별 확인한다. 전역 최소치는
			// 아직 레거시 코드가 많아 0으로 두고 리포트만 강제 생성한다.
			limit {
				minimum = "0.00".toBigDecimal()
			}
		}
	}
}

