package com.github.search5.yona.domain.project

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface RecentProjectRepository : JpaRepository<RecentProject, Long> {
    fun findByUserId(userId: Long): List<RecentProject>
    fun findByUserIdOrderByVisitedDateDesc(userId: Long): List<RecentProject>
    fun findByUserIdAndProjectId(userId: Long, projectId: Long): Optional<RecentProject>
    fun deleteByUserIdAndProjectId(userId: Long, projectId: Long)
}
