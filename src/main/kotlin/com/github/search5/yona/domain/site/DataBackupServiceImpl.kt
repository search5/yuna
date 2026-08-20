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
 * auto-increment/시퀀스를 재설정한다.
 *
 * yona `DefaultExchanger.exportData()`/`importSequence()`(P2-07) 대응 — export 시점에
 * 실제 DB가 갖고 있던 auto-increment/시퀀스의 "다음 값"(`INFORMATION_SCHEMA.TABLES.AUTO_INCREMENT`)을
 * 그대로 캡처해뒀다가 복원 시 그 값으로 되돌린다. 백업된 행들의 `max(id)+1`을 복원 시점에
 * 재계산하는 방식(이전 구현)은 export 이전에 이미 삭제된 행으로 생긴 시퀀스 갭을 없애버려
 * 그 ID가 재사용될 수 있었는데, 이 방식은 yona처럼 갭을 그대로 보존한다.
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
        val dialect = detectDialect()
        val dump = LinkedHashMap<String, List<Map<String, Any?>>>()
        val sequences = LinkedHashMap<String, Long>()
        for (table in tables) {
            dump[table] = jdbcTemplate.queryForList("SELECT * FROM $table")
            nextSequenceValue(table, dialect)?.let { sequences[table] = it }
        }
        logger.info("데이터 백업 완료: ${tables.size}개 테이블")
        return objectMapper.writeValueAsBytes(mapOf("tables" to dump, "sequences" to sequences))
    }

    @Transactional
    @Suppress("UNCHECKED_CAST")
    override fun importAll(bytes: ByteArray) {
        val root = objectMapper.readValue(bytes, Map::class.java) as Map<String, Any?>
        val dump = root["tables"] as Map<String, List<Map<String, Any?>>>
        val sequences = (root["sequences"] as? Map<String, Any?>)
            ?.mapValues { (_, value) -> (value as Number).toLong() }
            ?: emptyMap()
        val dialect = detectDialect()

        setForeignKeyChecks(dialect, enabled = false)
        try {
            for ((table, rows) in dump) {
                jdbcTemplate.update("DELETE FROM $table")
                for (row in rows) {
                    insertRow(table, row)
                }
                sequences[table]?.let { restoreSequence(table, dialect, it) }
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

    // yona DefaultExchanger.exportData()의 hasSequence() 분기 대응 (P2-07) — export 시점에
    // 실제 DB가 다음으로 배정할 auto-increment/시퀀스 값을 그대로 조회해둔다. id 컬럼이
    // 없는 조인 테이블 등은 조회 결과가 null이라 자연히 스킵된다(yona의 hasSequence()=false와
    // 동일한 효과를 하드코딩된 테이블 목록 없이 얻는다).
    private fun nextSequenceValue(table: String, dialect: Dialect): Long? {
        return when (dialect) {
            Dialect.MYSQL_COMPATIBLE -> {
                val catalog = dataSource.connection.use { it.catalog }
                jdbcTemplate.queryForObject(
                    "SELECT AUTO_INCREMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?",
                    java.lang.Long::class.java,
                    catalog,
                    table
                )?.toLong()
            }
            Dialect.POSTGRES -> {
                val sequenceName = jdbcTemplate.queryForObject(
                    "SELECT pg_get_serial_sequence(?, 'id')", String::class.java, table
                ) ?: return null
                jdbcTemplate.queryForObject(
                    "SELECT CASE WHEN is_called THEN last_value + 1 ELSE last_value END FROM $sequenceName",
                    java.lang.Long::class.java
                )?.toLong()
            }
            Dialect.OTHER -> null
        }
    }

    // yona DefaultExchanger.importSequence() 대응 (P2-07) — export 시점에 캡처해둔 "다음 값"을
    // 그대로 복원한다(백업된 행들의 max(id)+1을 재계산하지 않음 — 그러면 export 이전에 이미
    // 삭제된 행으로 생긴 시퀀스 갭이 사라져 그 ID가 재사용될 수 있다).
    private fun restoreSequence(table: String, dialect: Dialect, nextValue: Long) {
        when (dialect) {
            Dialect.MYSQL_COMPATIBLE -> {
                jdbcTemplate.execute("ALTER TABLE $table AUTO_INCREMENT = $nextValue")
            }
            Dialect.POSTGRES -> {
                val sequenceName = jdbcTemplate.queryForObject(
                    "SELECT pg_get_serial_sequence(?, 'id')",
                    String::class.java,
                    table
                )
                if (sequenceName != null) {
                    // setval(seq, nextValue, false): is_called=false로 설정해, 다음 nextval() 호출이
                    // nextValue-1이 아니라 정확히 nextValue를 반환하도록 한다.
                    jdbcTemplate.queryForObject("SELECT setval(?, ?, false)", Long::class.java, sequenceName, nextValue)
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
