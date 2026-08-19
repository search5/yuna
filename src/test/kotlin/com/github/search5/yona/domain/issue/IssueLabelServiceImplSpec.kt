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
})
