package com.github.search5.yona.domain.notification

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface NotificationMailRepository : JpaRepository<NotificationMail, Long>
