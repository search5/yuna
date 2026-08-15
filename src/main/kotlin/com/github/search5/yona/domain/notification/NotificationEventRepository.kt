package com.github.search5.yona.domain.notification

import com.github.search5.yona.domain.user.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface NotificationEventRepository : JpaRepository<NotificationEvent, Long> {
    @Query("select ne from NotificationEvent ne join ne.receivers r where r = :user order by ne.created desc")
    fun findByReceiver(@Param("user") user: User, pageable: Pageable): Page<NotificationEvent>
}
