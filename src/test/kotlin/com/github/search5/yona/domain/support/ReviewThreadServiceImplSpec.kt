package com.github.search5.yona.domain.support

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.pullrequest.CommentThread
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.lang.Long as JLong
import jakarta.persistence.EntityManager
import jakarta.persistence.TypedQuery
import org.springframework.data.domain.PageRequest

class ReviewThreadServiceImplSpec : DescribeSpec({
    describe("ReviewThreadServiceImpl") {
        it("getReviewThreads (Pageable) - 조건에 따라 JPQL을 생성하고 결과를 반환해야 한다") {
            val entityManager = mockk<EntityManager>()
            val service = ReviewThreadServiceImpl(entityManager)
            val project = mockk<Project>()
            val condition = ReviewSearchCondition(
                state = "open",
                authorId = 1L,
                participantId = 2L,
                filter = "test",
                orderBy = "createdDate",
                orderDir = "desc"
            )
            val pageable = PageRequest.of(0, 10)

            val query = mockk<TypedQuery<CommentThread>>(relaxed = true)
            every { entityManager.createQuery(any<String>(), CommentThread::class.java) } returns query
            every { query.resultList } returns listOf()

            val countQuery = mockk<TypedQuery<JLong>>(relaxed = true)
            every { entityManager.createQuery(any<String>(), JLong::class.java) } returns countQuery
            every { countQuery.singleResult } answers { 0L as JLong }

            val page = service.getReviewThreads(project, condition, pageable)
            page.totalElements shouldBe 0L
        }
        
        it("getReviewThreads (List) - 조건에 따라 결과를 반환해야 한다") {
            val entityManager = mockk<EntityManager>()
            val service = ReviewThreadServiceImpl(entityManager)
            val project = mockk<Project>()
            val condition = ReviewSearchCondition(
                state = "invalid_state",
                authorId = null,
                participantId = null,
                filter = "",
                orderDir = "asc"
            )

            val query = mockk<TypedQuery<CommentThread>>(relaxed = true)
            every { entityManager.createQuery(any<String>(), CommentThread::class.java) } returns query
            every { query.resultList } returns listOf()

            val list = service.getReviewThreads(project, condition)
            list.size shouldBe 0
        }
    }
})
