package com.github.search5.yona.domain.support

import com.github.search5.yona.web.UserStatisticsResponse

interface StatisticsService {
    fun getUserStatistics(userId: Long): UserStatisticsResponse
}
