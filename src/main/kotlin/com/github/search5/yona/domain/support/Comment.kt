package com.github.search5.yona.domain.support

import jakarta.persistence.*
import java.time.Instant

@MappedSuperclass
abstract class Comment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    // @Lob를 붙이지 않는다 — `columnDefinition = "TEXT"`만으로 이미 원하는 컬럼 타입이 나오는데,
    // Postgres + Hibernate 7.2.x 조합에서 String 필드에 @Lob를 얹으면 물리 매핑이 달라져(실측
    // 재현) LIKE 비교가 항상 매치 0건으로 조용히 실패한다(예외 없이 결과만 틀림 — 가장 위험한
    // 유형). MariaDB에서는 재현되지 않았다. TEXT 크기의 문자열 컬럼은 columnDefinition만으로
    // 충분하므로 @Lob 자체가 불필요했다.
    @Column(columnDefinition = "TEXT", nullable = false)
    var contents: String = "",

    var createdDate: Instant? = null,

    var authorId: Long? = null,
    var authorLoginId: String? = null,
    var authorName: String? = null,

    var projectId: Long? = null
)
