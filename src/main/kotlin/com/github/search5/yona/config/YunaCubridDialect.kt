package com.github.search5.yona.config

import org.hibernate.community.dialect.CUBRIDDialect
import org.hibernate.dialect.TimeZoneSupport
import org.hibernate.type.SqlTypes
import java.sql.Types

/**
 * CUBRID 지원용 커스텀 방언. org.hibernate.community.dialect.CUBRIDDialect를 그대로 쓰면
 * CUBRID JDBC 드라이버(11.3.2.0053)와 실제로 안 맞는 지점이 두 군데 있어 우회한다(둘 다 실측
 * 재현 — 방언은 지원한다고 광고하지만 드라이버가 그 바인딩을 못 받는 유형의 결함).
 *
 * 1. BOOLEAN을 CUBRID의 `bit` 타입으로 매핑하는데(getPreferredSqlTypeCodeForBoolean() ==
 *    Types.BIT), 드라이버가 이 bit 바인드 파라미터를 받아들이지 못해 "Cannot coerce host var
 *    to type bit"로 매번 INSERT/UPDATE가 실패한다(CUBRID 커뮤니티 Q&A에도 보고된 결함).
 *    bit 대신 smallint(0/1)로 매핑해 우회한다.
 * 2. getTimeZoneSupport()가 NATIVE라 Instant 컬럼이 datetimetz 타입 + OffsetDateTime 기반
 *    바인딩(TimestampUtcAsOffsetDateTimeJdbcType)으로 매핑되는데, 드라이버의
 *    PreparedStatement.setObject()가 이 바인딩을 거부한다(IllegalArgumentException, 메시지
 *    없음). NONE으로 낮춰 평범한 java.sql.Timestamp 기반 바인딩(TIMESTAMP)을 쓰게 한다.
 */
class YunaCubridDialect : CUBRIDDialect() {
    override fun getPreferredSqlTypeCodeForBoolean(): Int = Types.SMALLINT

    override fun columnType(sqlTypeCode: Int): String {
        if (sqlTypeCode == SqlTypes.BOOLEAN) {
            return "smallint"
        }
        return super.columnType(sqlTypeCode)
    }

    override fun getTimeZoneSupport(): TimeZoneSupport = TimeZoneSupport.NONE
}
