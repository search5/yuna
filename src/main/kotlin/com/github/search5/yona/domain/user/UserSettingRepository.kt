package com.github.search5.yona.domain.user

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface UserSettingRepository : JpaRepository<UserSetting, Long> {
    fun findByUserId(userId: Long): Optional<UserSetting>
}
