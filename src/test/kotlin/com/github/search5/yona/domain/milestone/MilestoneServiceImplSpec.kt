package com.github.search5.yona.domain.milestone

import com.github.search5.yona.domain.attachment.AttachmentService
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.watch.WatchService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Optional

class MilestoneServiceImplSpec : DescribeSpec({
    val milestoneRepository = mockk<MilestoneRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val issueRepository = mockk<IssueRepository>()
    val attachmentService = mockk<AttachmentService>(relaxed = true)
    val watchService = mockk<WatchService>(relaxed = true)
    val service = MilestoneServiceImpl(milestoneRepository, projectRepository, issueRepository, attachmentService, watchService)

    // yona models/resource/ResourcePersistAdapter.java의 postDelete()(deleteRelatedWatch/
    // deleteRelatedUnwatch) 대응 (P1-147).
    describe("MilestoneServiceImpl.deleteMilestone") {
        it("마일스톤 삭제 시 첨부파일과 Watch/Unwatch가 함께 정리되어야 한다") {
            val milestone = Milestone(id = 5L, title = "mile", project = Project(id = 1L, name = "proj", owner = "owner"))
            every { milestoneRepository.findById(5L) } returns Optional.of(milestone)
            every { issueRepository.removeMilestoneFromIssues(milestone) } returns Unit
            every { milestoneRepository.delete(milestone) } returns Unit

            service.deleteMilestone(5L)

            verify { attachmentService.deleteAll(ResourceType.MILESTONE, "5") }
            verify { watchService.deleteAll(ResourceType.MILESTONE, "5") }
            verify { milestoneRepository.delete(milestone) }
        }

        it("존재하지 않는 마일스톤이면 예외가 발생해야 한다") {
            every { milestoneRepository.findById(999L) } returns Optional.empty()

            shouldThrow<IllegalArgumentException> {
                service.deleteMilestone(999L)
            }
        }
    }
})
