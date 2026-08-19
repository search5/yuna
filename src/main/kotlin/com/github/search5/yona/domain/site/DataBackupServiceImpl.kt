package com.github.search5.yona.domain.site

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import javax.sql.DataSource

/**
 * yona의 data/DataService.java + data/exchangers 하위 Exchanger 44개 대응.
 * yona는 테이블별 전용 Exchanger 클래스를 44개 손으로 나열해 유지하지만, yuna는
 * DB 메타데이터로 테이블 목록을 스스로 찾아내는 범용 방식을 택했다 — 엔티티가
 * 추가돼도 이 서비스는 수정할 필요가 없고, 기존 SiteApiController가 하드코딩했던
 * "users/projects 필드만 export" 문제(P0-07)를 테이블 단위로 완전히 해소한다.
 *
 * 복원 시 테이블 전체를 DELETE 후 백업 내용을 다시 INSERT한다(= 완전 교체).
 * yona는 원본 PK를 그대로 복원하는데, yuna도 auto-increment 컬럼을 포함해 백업된
 * 값 그대로 INSERT한다 — 신규 채번과 충돌하지 않으려면 복원 후 auto-increment
 * 시퀀스를 최댓값 이상으로 재설정해야 할 수 있다(운영 배포 가이드에 기술 필요,
 * docs/PARITY_BACKLOG.md 후속 항목 참고).
 */
@Service
class DataBackupServiceImpl(
    private val dataSource: DataSource,
    private val objectMapper: ObjectMapper
) : DataBackupService {

    private val logger = LoggerFactory.getLogger(DataBackupServiceImpl::class.java)
    private val jdbcTemplate = JdbcTemplate(dataSource)

    private enum class Dialect { MYSQL_COMPATIBLE, POSTGRES, OTHER }

    override fun exportAll(): ByteArray {
        val tables = listTables()
        val dump = LinkedHashMap<String, List<Map<String, Any?>>>()
        for (table in tables) {
            dump[table] = jdbcTemplate.queryForList("SELECT * FROM $table")
        }
        logger.info("데이터 백업 완료: ${tables.size}개 테이블")
        return objectMapper.writeValueAsBytes(dump)
    }

    @Transactional
    @Suppress("UNCHECKED_CAST")
    override fun importAll(bytes: ByteArray) {
        val dump = objectMapper.readValue(bytes, Map::class.java) as Map<String, List<Map<String, Any?>>>
        val dialect = detectDialect()

        setForeignKeyChecks(dialect, enabled = false)
        try {
            for ((table, rows) in dump) {
                jdbcTemplate.update("DELETE FROM $table")
                for (row in rows) {
                    insertRow(table, row)
                }
            }
            logger.info("데이터 복원 완료: ${dump.size}개 테이블")
        } finally {
            setForeignKeyChecks(dialect, enabled = true)
        }
    }

    private fun listTables(): List<String> {
        dataSource.connection.use { connection ->
            val meta = connection.metaData
            val tables = mutableListOf<String>()
            meta.getTables(connection.catalog, connection.schema, "%", arrayOf("TABLE")).use { rs ->
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"))
                }
            }
            return tables.sorted()
        }
    }

    private fun insertRow(table: String, row: Map<String, Any?>) {
        if (row.isEmpty()) {
            return
        }
        val columns = row.keys.toList()
        val placeholders = columns.joinToString(",") { "?" }
        val sql = "INSERT INTO $table (${columns.joinToString(",")}) VALUES ($placeholders)"
        jdbcTemplate.update(sql, *columns.map { row[it] }.toTypedArray())
    }

    private fun detectDialect(): Dialect {
        dataSource.connection.use { connection ->
            val product = connection.metaData.databaseProductName ?: ""
            return when {
                product.contains("MySQL", ignoreCase = true) || product.contains("MariaDB", ignoreCase = true) ->
                    Dialect.MYSQL_COMPATIBLE
                product.contains("PostgreSQL", ignoreCase = true) -> Dialect.POSTGRES
                else -> Dialect.OTHER
            }
        }
    }

    private fun setForeignKeyChecks(dialect: Dialect, enabled: Boolean) {
        when (dialect) {
            Dialect.MYSQL_COMPATIBLE -> jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = ${if (enabled) 1 else 0}")
            Dialect.POSTGRES -> jdbcTemplate.execute("SET session_replication_role = '${if (enabled) "origin" else "replica"}'")
            Dialect.OTHER -> logger.warn("알 수 없는 DB 방언이라 외래키 제약을 토글하지 않습니다")
        }
    }
}
