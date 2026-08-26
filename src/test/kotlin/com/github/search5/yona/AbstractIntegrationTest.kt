package com.github.search5.yona

import io.kotest.core.spec.style.DescribeSpec
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.MSSQLServerContainer
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.cubrid.CubridContainer

// MSSQLServerContainer는 자기참조 제네릭(Self-bounded generic, `MSSQLServerContainer<SELF extends
// MSSQLServerContainer<SELF>>`)이라 Kotlin에서 타입 인자 없이 바로 쓸 수 없다 — 다른 컨테이너들처럼
// 구체 타입을 직접 만들어 SELF로 고정한다.
private class KMSSQLServerContainer(imageName: String) : MSSQLServerContainer<KMSSQLServerContainer>(imageName)

/**
 * yuna가 지원해야 하는 5개 DB(MariaDB/PostgreSQL/MySQL/SQL Server/CUBRID)를 전부 실제 도커
 * 컨테이너 기준으로 동일한 통합테스트 스위트(이 클래스를 상속하는 모든 Spec)로 검증하기 위해
 * 컨테이너 선택을 시스템 프로퍼티로 파라미터화했다.
 *
 * 실행: `./gradlew test -Dyona.it.db=postgres` (기본값은 mariadb). 값: mariadb|postgres|mysql|mssql|cubrid
 */
@SpringBootTest
@ActiveProfiles("test")
abstract class AbstractIntegrationTest : DescribeSpec() {

    companion object {
        private const val DB_PROPERTY = "yona.it.db"
        private val selectedDb = System.getProperty(DB_PROPERTY, "mariadb")

        private val container: JdbcDatabaseContainer<*> = when (selectedDb) {
            "postgres" -> PostgreSQLContainer("postgres:16")
                .withDatabaseName("yona")
                .withUsername("yona")
                .withPassword("yona_password")
            "mysql" -> MySQLContainer("mysql:8.4")
                .withDatabaseName("yona")
                .withUsername("yona")
                .withPassword("yona_password")
            // SQL Server 공식 이미지는 고정 계정(sa)/DB(master)만 지원해 withDatabaseName이 없다.
            "mssql" -> KMSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-latest")
                .acceptLicense()
            // CUBRID 공식 Testcontainers 모듈(org.cubrid:testcontainers-cubrid, CUBRID사 직접 관리).
            "cubrid" -> CubridContainer("cubrid/cubrid:11.4")
                .withDatabaseName("yona")
                .withUsername("yona")
                .withPassword("yona_password")
            else -> MariaDBContainer("mariadb:10.11")
                .withDatabaseName("yona")
                .withUsername("yona")
                .withPassword("yona_password")
        }

        init {
            container.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { container.jdbcUrl }
            registry.add("spring.datasource.username") { container.username }
            registry.add("spring.datasource.password") { container.password }
            registry.add("spring.datasource.driver-class-name") { container.driverClassName }
            // CUBRIDDialect는 hibernate-core가 아니라 hibernate-community-dialects에 있어
            // Hibernate가 JDBC 메타데이터만으로 자동 인식하지 못한다 — 명시적으로 지정해야 한다.
            if (selectedDb == "cubrid") {
                registry.add("spring.jpa.database-platform") { "org.hibernate.community.dialect.CUBRIDDialect" }
            }
        }
    }
}
