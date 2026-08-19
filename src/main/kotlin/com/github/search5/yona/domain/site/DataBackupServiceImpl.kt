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
 * 값 그대로 INSERT한다 — 신규 채번과 충돌하지 않도록 테이블마다 복원 직후
 * auto-increment/시퀀스를 백업된 최댓값+1로 재설정한다(`resetAutoIncrement`, P1-33).
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
                resetAutoIncrement(table, dialect, rows)
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

    // yuna 자체 설계 이슈 대응 (P1-33): 백업된 PK를 명시적으로 그대로 INSERT하므로,
    // 이후 신규 행이 auto-increment/시퀀스로 채번될 때 이미 복원된 PK와 충돌할 수 있다.
    // MySQL/MariaDB의 AUTO_INCREMENT는 명시적 INSERT 값을 보고 스스로 다음 채번을 올려주지만,
    // PostgreSQL의 시퀀스는 그렇지 않아(nextval()이 실제 행 데이터와 완전히 무관하게 관리됨)
    // 복원 직후 첫 신규 insert가 PK 충돌로 실패할 수 있다(P1-34에서 Postgres 통합테스트로 실측 확인).
    // id 컬럼이 없는 조인 테이블 등은 그냥 스킵한다.
    private fun resetAutoIncrement(table: String, dialect: Dialect, rows: List<Map<String, Any?>>) {
        val maxId = rows.mapNotNull { (it["id"] as? Number)?.toLong() }.maxOrNull() ?: return

        when (dialect) {
            Dialect.MYSQL_COMPATIBLE -> {
                // 실측상 MariaDB는 이미 명시적 INSERT 값을 보고 자동으로 채번을 올리지만,
                // 방어적으로 명시 재설정해 다른 MySQL 계열/설정에서도 안전하게 만든다.
                jdbcTemplate.execute("ALTER TABLE $table AUTO_INCREMENT = ${maxId + 1}")
            }
            Dialect.POSTGRES -> {
                val sequenceName = jdbcTemplate.queryForObject(
                    "SELECT pg_get_serial_sequence(?, 'id')",
                    String::class.java,
                    table
                )
                if (sequenceName != null) {
                    jdbcTemplate.queryForObject("SELECT setval(?, ?, true)", Long::class.java, sequenceName, maxId)
                }
            }
            Dialect.OTHER -> logger.warn("알 수 없는 DB 방언이라 $table 의 auto-increment/시퀀스를 재설정하지 않습니다")
        }
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
