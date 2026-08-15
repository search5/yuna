plugins {
	kotlin("jvm") version "2.2.21"
	kotlin("plugin.spring") version "2.2.21"
	id("org.springframework.boot") version "4.0.6"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "2.2.21"
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

	// Apache Commons Lang3
	implementation("org.apache.commons:commons-lang3:3.14.0")

	// JExcelAPI (Legacy Yona Excel support)
	implementation("net.sourceforge.jexcelapi:jxl:2.6.12")

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
}
