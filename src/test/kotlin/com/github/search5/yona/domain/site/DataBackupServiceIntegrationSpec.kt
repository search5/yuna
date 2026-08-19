package com.github.search5.yona.domain.site

import com.github.search5.yona.AbstractIntegrationTest
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

/**
 * 실제 MariaDB(Testcontainers)를 대상으로 DataBackupService의 export→import
 * 전체 왕복이 실제 DB 메타데이터/FK 제약과 함께 정상 동작하는지 검증한다.
 * (순수 목 객체로는 "실제 DB 스키마/방언에서 동작하는가"를 검증할 수 없어 통합테스트로 작성)
 */
class DataBackupServiceIntegrationSpec @Autowired constructor(
    private val dataBackupService: DataBackupService,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val dataSource: DataSource
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private val jdbc: JdbcTemplate by lazy { JdbcTemplate(dataSource) }

    init {
        describe("DataBackupService export/import 왕복") {
            it("백업 시점 이후에 추가된 데이터는 해당 백업으로 복원하면 사라져야 한다") {
                // Given: 기준 시점 데이터
                val baseline = userRepository.save(
                    User(loginId = "backup-baseline-user", name = "기준사용자", email = "baseline@example.com")
                )
                projectRepository.save(
                    Project(name = "backup-baseline-project", owner = "backup-baseline-user", projectScope = ProjectScope.PUBLIC)
                )

                val backup = dataBackupService.exportAll()

                // When: 기준 시점 이후 데이터가 더 추가됨
                userRepository.save(
                    User(loginId = "backup-extra-user", name = "추가사용자", email = "extra@example.com")
                )

                val countBeforeRestore = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM n4user WHERE login_id IN ('backup-baseline-user', 'backup-extra-user')",
                    Int::class.java
                )
                countBeforeRestore shouldBe 2

                // 백업 시점으로 복원
                dataBackupService.importAll(backup)

                // Then: 백업 이후에 추가된 사용자는 사라지고, 기준 시점 사용자는 남아있어야 한다
                val baselineExists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM n4user WHERE login_id = 'backup-baseline-user'",
                    Int::class.java
                )
                val extraExists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM n4user WHERE login_id = 'backup-extra-user'",
                    Int::class.java
                )

                baselineExists shouldBe 1
                extraExists shouldBe 0

                val restoredProjectCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM project WHERE name = 'backup-baseline-project'",
                    Int::class.java
                )
                restoredProjectCount shouldBe 1
            }

            it("exportAll이 만든 백업에는 여러 테이블이 포함되어야 한다 (users/projects 전용이 아님)") {
                val backup = dataBackupService.exportAll()
                val json = String(backup, Charsets.UTF_8)

                // n4user/project 외에도 role, organization 같은 다른 테이블이 최소 포함돼야 한다
                (json.contains("\"role\"") || json.contains("\"n4user\"")) shouldBe true
                (json.contains("\"organization\"")) shouldBe true
            }
        }
    }
}
