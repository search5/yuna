package com.github.search5.yona.domain.support

import jakarta.persistence.EntityManager
import jakarta.persistence.TypedQuery
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class StatisticsServiceImplSpec : DescribeSpec({
    describe("StatisticsServiceImpl") {
        it("getUserStatistics가 정상적으로 모든 통계 쿼리를 실행하고 반환해야 한다") {
            val entityManager = mockk<EntityManager>()
            val service = StatisticsServiceImpl(entityManager)
            
            val query = mockk<TypedQuery<java.lang.Long>>()
            every { entityManager.createQuery(any<String>(), java.lang.Long::class.java) } returns query
            every { query.setParameter(any<String>(), any()) } returns query
            every { query.singleResult } answers { 10L as java.lang.Long }

            val response = service.getUserStatistics(1L)
            
            response.issue shouldBe 10L
            response.posting shouldBe 10L
            response.assignedIssue shouldBe 10L
            response.issueComment shouldBe 10L
            response.postingComment shouldBe 10L
            response.issueVoter shouldBe 10L
            response.issueCommentVoter shouldBe 10L
            
            verify(exactly = 7) { entityManager.createQuery(any<String>(), java.lang.Long::class.java) }
        }
    }
})
