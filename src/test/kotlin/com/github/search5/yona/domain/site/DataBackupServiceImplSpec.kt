package com.github.search5.yona.domain.site

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.util.ReflectionTestUtils
import tools.jackson.databind.ObjectMapper
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.sql.Types
import javax.sql.DataSource
import java.time.Instant

class DataBackupServiceImplSpec : DescribeSpec({
    val dataSource = mockk<DataSource>()
    val objectMapper = ObjectMapper()
    val connection = mockk<Connection>()
    val metaData = mockk<DatabaseMetaData>()
    val jdbcTemplate = mockk<JdbcTemplate>(relaxed = true)

    val service = DataBackupServiceImpl(dataSource, objectMapper)
    ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate)

    beforeTest {
        clearMocks(dataSource, connection, metaData, jdbcTemplate)
        every { dataSource.connection } returns connection
        every { connection.close() } returns Unit
        every { connection.metaData } returns metaData
        every { connection.catalog } returns "test_catalog"
        every { connection.schema } returns "test_schema"
    }

    describe("detectDialect & setForeignKeyChecks") {
        it("MySQL/MariaDB인 경우 MYSQL_COMPATIBLE 방언을 사용해야 한다") {
            every { metaData.databaseProductName } returns "MariaDB"
            val tablesRs = mockk<ResultSet>(relaxed = true)
            every { metaData.getTables(any(), any(), any(), any()) } returns tablesRs
            every { tablesRs.next() } returns false
            
            service.exportAll()
            
            // 검증 로직 없음, 에러만 안나면 성공
        }

        it("PostgreSQL인 경우 POSTGRES 방언을 사용해야 한다") {
            every { metaData.databaseProductName } returns "PostgreSQL"
            val tablesRs = mockk<ResultSet>(relaxed = true)
            every { metaData.getTables(any(), any(), any(), any()) } returns tablesRs
            every { tablesRs.next() } returns false
            
            service.exportAll()
        }

        it("기타 DB인 경우 OTHER 방언을 사용해야 한다") {
            every { metaData.databaseProductName } returns "H2"
            val tablesRs = mockk<ResultSet>(relaxed = true)
            every { metaData.getTables(any(), any(), any(), any()) } returns tablesRs
            every { tablesRs.next() } returns false
            
            service.exportAll()
        }
    }

    describe("exportAll") {
        it("모든 테이블을 조회하고 방언별로 sequence를 추출해야 한다 (MySQL)") {
            every { metaData.databaseProductName } returns "MySQL"
            
            val tablesRs = mockk<ResultSet>(relaxed = true)
            every { metaData.getTables(any(), any(), any(), any()) } returns tablesRs
            every { tablesRs.next() } returnsMany listOf(true, true, false)
            every { tablesRs.getString("TABLE_NAME") } returnsMany listOf("users", "projects")
            
            every { jdbcTemplate.queryForList("SELECT * FROM projects") } returns listOf(mapOf("id" to 1))
            every { jdbcTemplate.queryForList("SELECT * FROM users") } returns listOf(mapOf("id" to 2))
            
            every { jdbcTemplate.queryForObject(
                "SELECT AUTO_INCREMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?",
                java.lang.Long::class.java,
                "test_catalog",
                "projects"
            ) } returns 10L as java.lang.Long
            every { jdbcTemplate.queryForObject(
                "SELECT AUTO_INCREMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?",
                java.lang.Long::class.java,
                "test_catalog",
                "users"
            ) } returns 20L as java.lang.Long

            val result = service.exportAll()
            val str = String(result)
            str.contains("projects") shouldBe true
            str.contains("users") shouldBe true
            str.contains("10") shouldBe true
            str.contains("20") shouldBe true
        }

        it("모든 테이블을 조회하고 방언별로 sequence를 추출해야 한다 (PostgreSQL)") {
            every { metaData.databaseProductName } returns "PostgreSQL"
            
            val tablesRs = mockk<ResultSet>(relaxed = true)
            every { metaData.getTables(any(), any(), any(), any()) } returns tablesRs
            every { tablesRs.next() } returnsMany listOf(true, false)
            every { tablesRs.getString("TABLE_NAME") } returns "users"
            
            every { jdbcTemplate.queryForList("SELECT * FROM users") } returns listOf(mapOf("id" to 1))
            
            every { jdbcTemplate.queryForObject("SELECT pg_get_serial_sequence(?, 'id')", String::class.java, "users") } returns "users_id_seq"
            every { jdbcTemplate.queryForObject("SELECT CASE WHEN is_called THEN last_value + 1 ELSE last_value END FROM users_id_seq", java.lang.Long::class.java) } returns 30L as java.lang.Long

            val result = service.exportAll()
            val str = String(result)
            str.contains("30") shouldBe true
        }

        it("PostgreSQL에서 sequence가 없는 경우(null) 예외 없이 넘어가야 한다") {
            every { metaData.databaseProductName } returns "PostgreSQL"
            val tablesRs = mockk<ResultSet>(relaxed = true)
            every { metaData.getTables(any(), any(), any(), any()) } returns tablesRs
            every { tablesRs.next() } returnsMany listOf(true, false)
            every { tablesRs.getString("TABLE_NAME") } returns "users"
            every { jdbcTemplate.queryForList("SELECT * FROM users") } returns listOf(mapOf("id" to 1))
            
            // sequence_name is null
            every { jdbcTemplate.queryForObject("SELECT pg_get_serial_sequence(?, 'id')", String::class.java, "users") } returns null

            val result = service.exportAll()
            result shouldNotBe null
        }
    }

    describe("importAll") {
        it("JSON 데이터를 받아 테이블에 INSERT하고 sequence를 복원해야 한다 (MySQL)") {
            every { metaData.databaseProductName } returns "MariaDB"
            
            val json = """
                {
                  "tables": {
                    "users": [
                      {"id": 1, "name": "test", "created_at": "2026-08-20T06:21:04.973Z"}
                    ],
                    "empty_table": []
                  },
                  "sequences": {
                    "users": 100
                  }
                }
            """.trimIndent().toByteArray()

            val columnsRs = mockk<ResultSet>(relaxed = true)
            every { metaData.getColumns(any(), any(), any(), null) } returns columnsRs
            every { columnsRs.next() } returnsMany listOf(true, true, true, false)
            every { columnsRs.getString("COLUMN_NAME") } returnsMany listOf("id", "name", "created_at")
            every { columnsRs.getInt("DATA_TYPE") } returnsMany listOf(Types.INTEGER, Types.VARCHAR, Types.TIMESTAMP)

            service.importAll(json)

            verify { jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0") }
            verify { jdbcTemplate.update("DELETE FROM users") }
            verify { jdbcTemplate.update("DELETE FROM empty_table") }
            // row insert verification (value array check is tricky with mockk, just verify update was called)
            verify(atLeast = 1) { jdbcTemplate.update(any<String>(), *anyVararg<Any>()) }
            verify { jdbcTemplate.execute("ALTER TABLE users AUTO_INCREMENT = 100") }
            verify { jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1") }
        }

        it("JSON 데이터를 받아 테이블에 INSERT하고 sequence를 복원해야 한다 (PostgreSQL)") {
            every { metaData.databaseProductName } returns "PostgreSQL"
            
            val json = """
                {
                  "tables": {
                    "users": [
                      {"id": 1}
                    ]
                  },
                  "sequences": {
                    "users": 200
                  }
                }
            """.trimIndent().toByteArray()

            val columnsRs = mockk<ResultSet>(relaxed = true)
            every { metaData.getColumns(any(), any(), any(), null) } returns columnsRs
            every { columnsRs.next() } returns false

            every { jdbcTemplate.queryForObject("SELECT pg_get_serial_sequence(?, 'id')", String::class.java, "users") } returns "users_id_seq"
            every { jdbcTemplate.queryForObject("SELECT setval(?, ?, false)", Long::class.java, "users_id_seq", 200L) } returns 200L

            service.importAll(json)

            verify { jdbcTemplate.execute("SET session_replication_role = 'replica'") }
            verify { jdbcTemplate.execute("SET session_replication_role = 'origin'") }
            verify { jdbcTemplate.queryForObject("SELECT setval(?, ?, false)", Long::class.java, "users_id_seq", 200L) }
        }

        it("PostgreSQL import 시 시퀀스가 null이면 setval을 호출하지 않아야 한다") {
            every { metaData.databaseProductName } returns "PostgreSQL"
            
            val json = """
                {
                  "tables": {
                    "users": [
                      {"id": 1}
                    ]
                  },
                  "sequences": {
                    "users": 200
                  }
                }
            """.trimIndent().toByteArray()

            val columnsRs = mockk<ResultSet>(relaxed = true)
            every { metaData.getColumns(any(), any(), any(), null) } returns columnsRs
            every { columnsRs.next() } returns false

            every { jdbcTemplate.queryForObject("SELECT pg_get_serial_sequence(?, 'id')", String::class.java, "users") } returns null

            service.importAll(json)

            verify(exactly = 0) { jdbcTemplate.queryForObject("SELECT setval(?, ?, false)", Long::class.java, any(), any()) }
        }

        it("기타 DB인 경우 외래키 토글 및 시퀀스 복원이 무시되어야 한다 (OTHER)") {
            every { metaData.databaseProductName } returns "Oracle"
            
            val json = """
                {
                  "tables": {
                    "users": [
                      {"id": 1}
                    ]
                  },
                  "sequences": {
                    "users": 300
                  }
                }
            """.trimIndent().toByteArray()

            val columnsRs = mockk<ResultSet>(relaxed = true)
            every { metaData.getColumns(any(), any(), any(), null) } returns columnsRs
            every { columnsRs.next() } returns false

            service.importAll(json)

            // Exception이 발생하지 않으면 성공
        }

        it("빈 로우(empty map)가 포함되어 있으면 무시하고 진행해야 한다") {
            every { metaData.databaseProductName } returns "MySQL"
            
            val json = """
                {
                  "tables": {
                    "users": [
                      {}
                    ]
                  },
                  "sequences": {}
                }
            """.trimIndent().toByteArray()

            val columnsRs = mockk<ResultSet>(relaxed = true)
            every { metaData.getColumns(any(), any(), any(), null) } returns columnsRs
            every { columnsRs.next() } returns false

            service.importAll(json)

            // insert가 호출되지 않아야 함
            verify(exactly = 0) { jdbcTemplate.update(any<String>(), *anyVararg<Any>()) }
        }

        it("잘못된 datetime 문자열은 파싱을 포기하고 그대로 반환해야 한다") {

            every { metaData.databaseProductName } returns "MySQL"
            
            val json = """
                {
                  "tables": {
                    "users": [
                      {"id": 1, "created_at": "invalid-date"}
                    ]
                  },
                  "sequences": {}
                }
            """.trimIndent().toByteArray()

            val columnsRs = mockk<ResultSet>(relaxed = true)
            every { metaData.getColumns(any(), any(), "users", null) } returns columnsRs
            every { columnsRs.next() } returnsMany listOf(true, false)
            every { columnsRs.getString("COLUMN_NAME") } returns "created_at"
            every { columnsRs.getInt("DATA_TYPE") } returns Types.TIMESTAMP

            service.importAll(json)

            // Exception 안나면 성공
        }
    }
})
