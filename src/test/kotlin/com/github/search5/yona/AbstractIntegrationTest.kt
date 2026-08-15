package com.github.search5.yona

import io.kotest.core.spec.style.DescribeSpec
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@ActiveProfiles("test")
abstract class AbstractIntegrationTest : DescribeSpec() {

    companion object {
        private val mariaDB = MariaDBContainer("mariadb:10.11").apply {
            withDatabaseName("yona")
            withUsername("yona")
            withPassword("yona_password")
        }

        init {
            mariaDB.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { mariaDB.jdbcUrl }
            registry.add("spring.datasource.username") { mariaDB.username }
            registry.add("spring.datasource.password") { mariaDB.password }
            registry.add("spring.datasource.driver-class-name") { mariaDB.driverClassName }
        }
    }
}
