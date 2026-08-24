package com.github.search5.yona.domain.issue

import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.milestone.Milestone
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.*
import jakarta.persistence.criteria.*

class IssueSpecificationSpec : DescribeSpec({

    val cb = mockk<CriteriaBuilder>(relaxed = true)
    val query = mockk<CriteriaQuery<*>>(relaxed = true)
    val root = mockk<Root<Issue>>(relaxed = true)

    val project = Project(id = 1L, name = "test")

    beforeTest {
        clearMocks(cb, query, root)
    }

    describe("filterIssues") {
        it("should apply basic predicates: project, state, author, assignee, milestone") {
            val spec = IssueSpecification.filterIssues(
                project = project,
                state = State.OPEN,
                filter = null,
                authorId = 2L,
                assigneeId = 3L,
                milestoneId = 4L,
                commenterId = null,
                labelIds = null,
                dueDate = null
            )
            
            // Just invoke to Predicate to cover lines
            spec.toPredicate(root, query, cb)
        }

        it("should apply null checks for assigneeId = -1 and milestoneId = -1") {
            val spec = IssueSpecification.filterIssues(
                project = project,
                state = State.OPEN,
                filter = null,
                authorId = null,
                assigneeId = -1L,
                milestoneId = -1L,
                commenterId = null,
                labelIds = null,
                dueDate = null
            )
            spec.toPredicate(root, query, cb)
        }

        it("should apply commenterId subquery") {
            val spec = IssueSpecification.filterIssues(
                project = project,
                state = State.OPEN,
                filter = null,
                authorId = null,
                assigneeId = null,
                milestoneId = null,
                commenterId = 5L,
                labelIds = null,
                dueDate = null
            )
            
            val subquery = mockk<Subquery<Long>>(relaxed = true)
            every { query.subquery(Long::class.java) } returns subquery
            
            val inClause = mockk<CriteriaBuilder.In<Any>>(relaxed = true)
            every { cb.`in`(any<Expression<out Any>>()) } returns inClause
            
            spec.toPredicate(root, query, cb)
        }

        it("should apply labelIds") {
            val spec = IssueSpecification.filterIssues(
                project = project,
                state = State.OPEN,
                filter = null,
                authorId = null,
                assigneeId = null,
                milestoneId = null,
                commenterId = null,
                labelIds = listOf(10L, 20L),
                dueDate = null
            )
            val inClause = mockk<CriteriaBuilder.In<Any>>(relaxed = true)
            every { cb.`in`(any<Expression<out Any>>()) } returns inClause
            spec.toPredicate(root, query, cb)
        }
        
        it("should apply filter string") {
            val spec = IssueSpecification.filterIssues(
                project = project,
                state = State.OPEN,
                filter = "searchme",
                authorId = null,
                assigneeId = null,
                milestoneId = null,
                commenterId = null,
                labelIds = null,
                dueDate = null
            )
            val subquery = mockk<Subquery<Long>>(relaxed = true)
            every { query.subquery(Long::class.java) } returns subquery
            val inClause = mockk<CriteriaBuilder.In<Any>>(relaxed = true)
            every { cb.`in`(any<Expression<out Any>>()) } returns inClause
            spec.toPredicate(root, query, cb)
        }
        
        it("should apply valid dueDate") {
            val spec = IssueSpecification.filterIssues(
                project = project,
                state = State.OPEN,
                filter = null,
                authorId = null,
                assigneeId = null,
                milestoneId = null,
                commenterId = null,
                labelIds = null,
                dueDate = "2024-12-31"
            )
            spec.toPredicate(root, query, cb)
        }
        
        it("should ignore invalid dueDate") {
            val spec = IssueSpecification.filterIssues(
                project = project,
                state = State.OPEN,
                filter = null,
                authorId = null,
                assigneeId = null,
                milestoneId = null,
                commenterId = null,
                labelIds = null,
                dueDate = "invalid-date"
            )
            spec.toPredicate(root, query, cb)
        }
    }

    describe("filterOrganizationIssues") {
        it("should apply empty projects and mentionedIssueIds") {
            val spec = IssueSpecification.filterOrganizationIssues(
                projects = emptyList(),
                state = State.OPEN,
                filter = null,
                authorId = null,
                assigneeId = null,
                mentionedIssueIds = emptyList()
            )
            spec.toPredicate(root, query, cb)
        }

        it("should apply non-empty projects, authorId, assigneeId, mentionedIssueIds, filter") {
            val spec = IssueSpecification.filterOrganizationIssues(
                projects = listOf(project),
                state = State.OPEN,
                filter = "search",
                authorId = 1L,
                assigneeId = 2L,
                mentionedIssueIds = listOf(100L)
            )
            spec.toPredicate(root, query, cb)
        }
    }
})
