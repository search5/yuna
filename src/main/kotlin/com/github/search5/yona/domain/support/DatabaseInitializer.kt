package com.github.search5.yona.domain.support

import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleRepository
import com.github.search5.yona.domain.role.RoleType
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class DatabaseInitializer(
    private val roleRepository: RoleRepository
) : CommandLineRunner {

    @Transactional
    override fun run(vararg args: String) {
        if (roleRepository.count() == 0L) {
            val roles = RoleType.entries.map { roleType ->
                Role(
                    id = roleType.roleType,
                    name = roleType.getLowerCasedName(),
                    active = true
                )
            }
            roleRepository.saveAll(roles)
            println("=== 초기화: 기본 Role 데이터 적재 완료 ===")
        }
    }
}
