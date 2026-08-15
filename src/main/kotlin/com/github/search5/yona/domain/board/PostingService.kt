package com.github.search5.yona.domain.board

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface PostingService {
    fun getPostings(projectId: Long, pageable: Pageable): Page<Posting>
    fun getNotices(projectId: Long): List<Posting>
    fun getPosting(projectId: Long, number: Long): Posting?
    fun createPosting(projectId: Long, posting: Posting, authorId: Long): Posting
    fun updatePosting(
        projectId: Long,
        number: Long,
        title: String,
        body: String,
        notice: Boolean,
        readme: Boolean,
        authorId: Long
    ): Posting
    fun deletePosting(projectId: Long, number: Long, authorId: Long)
}
