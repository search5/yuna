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

	// Testcontainers
	testImplementation("org.testcontainers:testcontainers:1.20.0")
	testImplementation("org.testcontainers:junit-jupiter:1.20.0")
	testImplementation("org.testcontainers:postgresql:1.20.0")
	testImplementation("org.testcontainers:mariadb:1.20.0")
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

tasks.withType<Test> {
	useJUnitPlatform()
	systemProperty("spring.profiles.active", "test")
	systemProperty("testcontainers.host", "127.0.0.1")
	systemProperty("api.version", "1.44")
	environment("DOCKER_API_VERSION", "1.44")
	environment("TESTCONTAINERS_RYUK_DISABLED", "true")
	environment("TESTCONTAINERS_CONTAINER_STARTUP_TIMEOUT", "120")
	environment("TESTCONTAINERS_HOST_OVERRIDE", "127.0.0.1")
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
