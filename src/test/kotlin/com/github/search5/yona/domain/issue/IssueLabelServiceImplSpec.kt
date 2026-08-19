package com.github.search5.yona.domain.issue

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.Optional

class IssueLabelServiceImplSpec : DescribeSpec({
    val issueLabelRepository = mockk<IssueLabelRepository>()
    val issueLabelCategoryRepository = mockk<IssueLabelCategoryRepository>()
    val projectRepository = mockk<ProjectRepository>()

    val service = IssueLabelServiceImpl(issueLabelRepository, issueLabelCategoryRepository, projectRepository)

    val project = Project(id = 1L, name = "TestProject", owner = "gildong")
    val oldCategory = IssueLabelCategory(id = 10L, name = "기존 카테고리", isExclusive = false, project = project)
    val newCategory = IssueLabelCategory(id = 11L, name = "새 카테고리", isExclusive = false, project = project)

    beforeTest {
        clearMocks(issueLabelRepository, issueLabelCategoryRepository, projectRepository, answers = false)
    }

    describe("IssueLabelServiceImpl.updateLabel") {
        it("라벨의 이름/색상/카테고리를 변경 후 저장해야 한다") {
            val label = IssueLabel(id = 100L, category = oldCategory, color = "#111111", name = "옛 이름", project = project)
            every { issueLabelRepository.findById(100L) } returns Optional.of(label)
            every { issueLabelCategoryRepository.findById(11L) } returns Optional.of(newCategory)

            val captured = slot<IssueLabel>()
            every { issueLabelRepository.save(capture(captured)) } answers { firstArg() }

            val result = service.updateLabel(labelId = 100L, name = "새 이름", color = "#ffffff", categoryId = 11L)

            captured.captured.name shouldBe "새 이름"
            captured.captured.color shouldBe "#ffffff"
            captured.captured.category shouldBe newCategory
            result.name shouldBe "새 이름"
        }

        it("존재하지 않는 라벨 id면 IllegalArgumentException을 던져야 한다") {
            every { issueLabelRepository.findById(999L) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                service.updateLabel(labelId = 999L, name = "새 이름", color = "#ffffff", categoryId = 11L)
            }

            verify(exactly = 0) { issueLabelRepository.save(any()) }
        }

        it("존재하지 않는 카테고리 id면 IllegalArgumentException을 던져야 한다") {
            val label = IssueLabel(id = 100L, category = oldCategory, color = "#111111", name = "옛 이름", project = project)
            every { issueLabelRepository.findById(100L) } returns Optional.of(label)
            every { issueLabelCategoryRepository.findById(999L) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                service.updateLabel(labelId = 100L, name = "새 이름", color = "#ffffff", categoryId = 999L)
            }

            verify(exactly = 0) { issueLabelRepository.save(any()) }
        }
    }

    describe("IssueLabelServiceImpl.updateCategory") {
        it("카테고리의 이름/exclusive 여부를 변경 후 저장해야 한다") {
            every { issueLabelCategoryRepository.findById(10L) } returns Optional.of(oldCategory)
            every { issueLabelCategoryRepository.findByProjectAndName(project, "변경된 이름") } returns null

            val captured = slot<IssueLabelCategory>()
            every { issueLabelCategoryRepository.save(capture(captured)) } answers { firstArg() }

            val result = service.updateCategory(categoryId = 10L, name = "변경된 이름", isExclusive = true)

            captured.captured.name shouldBe "변경된 이름"
            captured.captured.isExclusive shouldBe true
            result.name shouldBe "변경된 이름"
        }

        it("존재하지 않는 카테고리 id면 IllegalArgumentException을 던져야 한다") {
            every { issueLabelCategoryRepository.findById(999L) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                service.updateCategory(categoryId = 999L, name = "새 이름", isExclusive = false)
            }

            verify(exactly = 0) { issueLabelCategoryRepository.save(any()) }
        }

        it("같은 프로젝트에 동일한 이름의 다른 카테고리가 있으면 DuplicateLabelCategoryNameException을 던져야 한다") {
            val otherCategory = IssueLabelCategory(id = 12L, name = "중복 이름", isExclusive = false, project = project)
            every { issueLabelCategoryRepository.findById(10L) } returns Optional.of(oldCategory)
            every { issueLabelCategoryRepository.findByProjectAndName(project, "중복 이름") } returns otherCategory

            shouldThrow<DuplicateLabelCategoryNameException> {
                service.updateCategory(categoryId = 10L, name = "중복 이름", isExclusive = false)
            }

            verify(exactly = 0) { issueLabelCategoryRepository.save(any()) }
        }

        it("자기 자신과 이름이 같으면(변경 없음) 중복으로 취급하지 않아야 한다") {
            val sameNameAsSelf = IssueLabelCategory(id = 10L, name = "기존 카테고리", isExclusive = false, project = project)
            every { issueLabelCategoryRepository.findById(10L) } returns Optional.of(sameNameAsSelf)
            every { issueLabelCategoryRepository.findByProjectAndName(project, "기존 카테고리") } returns sameNameAsSelf
            every { issueLabelCategoryRepository.save(any()) } answers { firstArg() }

            val result = service.updateCategory(categoryId = 10L, name = "기존 카테고리", isExclusive = true)

            result.isExclusive shouldBe true
        }
    }
})
