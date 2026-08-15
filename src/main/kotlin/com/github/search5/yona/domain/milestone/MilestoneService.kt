package com.github.search5.yona.domain.milestone

import com.github.search5.yona.domain.enumeration.State
import java.time.Instant

interface MilestoneService {
    fun getMilestones(projectId: Long, state: State): List<Milestone>
    fun getMilestone(milestoneId: Long): Milestone?
    fun createMilestone(projectId: Long, milestone: Milestone): Milestone
    fun updateMilestone(
        milestoneId: Long,
        title: String,
        contents: String?,
        dueDate: Instant?,
        state: State
    ): Milestone
    fun deleteMilestone(milestoneId: Long)
}
