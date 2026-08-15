package com.github.search5.yona.domain.support

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.pullrequest.CommentThread
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ReviewThreadService {
    fun getReviewThreads(project: Project, condition: ReviewSearchCondition, pageable: Pageable): Page<CommentThread>
    fun getReviewThreads(project: Project, condition: ReviewSearchCondition): List<CommentThread>
    fun countReviewThreads(project: Project, condition: ReviewSearchCondition): Long
}
