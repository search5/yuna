package com.github.search5.yona.domain.user

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface UserRepository : JpaRepository<User, Long> {
    fun findByLoginId(loginId: String): Optional<User>
    fun findByEmail(email: String): Optional<User>
    fun findByToken(token: String): Optional<User>
    fun findByState(state: UserState): List<User>

    @Query("""
        SELECT u FROM User u 
        WHERE u.state IN (com.github.search5.yona.domain.user.UserState.ACTIVE, com.github.search5.yona.domain.user.UserState.SITE_ADMIN) 
          AND (u.name LIKE :keyword 
               OR u.loginId LIKE :keyword
               OR u.englishName LIKE :keyword)
     """)
    fun searchUsers(@Param("keyword") keyword: String, pageable: Pageable): Page<User>

    @Query("""
        SELECT COUNT(u) FROM User u 
        WHERE u.state IN (com.github.search5.yona.domain.user.UserState.ACTIVE, com.github.search5.yona.domain.user.UserState.SITE_ADMIN) 
          AND (u.name LIKE :keyword 
               OR u.loginId LIKE :keyword
               OR u.englishName LIKE :keyword)
    """)
    fun countSearchUsers(@Param("keyword") keyword: String): Int

    @Query("""
        SELECT u FROM User u 
        WHERE u.state = :state 
          AND (u.name LIKE :query 
               OR u.loginId LIKE :query)
    """)
    fun findUsersForAdmin(@Param("state") state: UserState, @Param("query") query: String, pageable: Pageable): Page<User>

    @Query("""
        SELECT COUNT(u) FROM User u 
        WHERE u.state = :state 
          AND (u.name LIKE :query 
               OR u.loginId LIKE :query)
    """)
    fun countUsersForAdmin(@Param("state") state: UserState, @Param("query") query: String): Int
}
