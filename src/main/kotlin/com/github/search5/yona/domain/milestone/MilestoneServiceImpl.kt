package com.github.search5.yona.domain.milestone

import com.github.search5.yona.domain.enumeration.State
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.project.ProjectRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import com.github.search5.yona.domain.attachment.AttachmentService
import com.github.search5.yona.domain.enumeration.ResourceType

@Service
@Transactional(readOnly = true)
class MilestoneServiceImpl(
    private val milestoneRepository: MilestoneRepository,
    private val projectRepository: ProjectRepository,
    private val issueRepository: IssueRepository,
    private val attachmentService: AttachmentService
) : MilestoneService {

    override fun getMilestones(projectId: Long, state: State): List<Milestone> {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("프로젝트를 찾을 수 없습니다.") }
        
        return if (state == State.ALL) {
            milestoneRepository.findByProject(project)
        } else {
            milestoneRepository.findByProjectAndState(project, state)
        }
    }

    override fun getMilestone(milestoneId: Long): Milestone? {
        return milestoneRepository.findById(milestoneId).orElse(null)
    }

    @Transactional
    override fun createMilestone(projectId: Long, milestone: Milestone): Milestone {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("프로젝트를 찾을 수 없습니다.") }

        val existing = milestoneRepository.findByProjectAndTitle(project, milestone.title)
        if (existing != null) {
            throw IllegalArgumentException("이미 동일한 제목의 마일스톤이 존재합니다.")
        }

        milestone.project = project
        milestone.state = State.OPEN
        return milestoneRepository.save(milestone)
    }

    @Transactional
    override fun updateMilestone(
        milestoneId: Long,
        title: String,
        contents: String?,
        dueDate: Instant?,
        state: State
    ): Milestone {
        val milestone = milestoneRepository.findById(milestoneId)
            .orElseThrow { IllegalArgumentException("마일스톤을 찾을 수 없습니다.") }

        val existing = milestoneRepository.findByProjectAndTitle(milestone.project, title.trim())
        if (existing != null && existing.id != milestoneId) {
            throw IllegalArgumentException("이미 동일한 제목의 마일스톤이 존재합니다.")
        }

        milestone.title = title.trim()
        milestone.contents = contents
        milestone.dueDate = dueDate
        milestone.state = state

        return milestoneRepository.save(milestone)
    }

    @Transactional
    override fun deleteMilestone(milestoneId: Long) {
        val milestone = milestoneRepository.findById(milestoneId)
            .orElseThrow { IllegalArgumentException("마일스톤을 찾을 수 없습니다.") }
        
        // 연관 관계 일괄 끊기
        issueRepository.removeMilestoneFromIssues(milestone)
        
        attachmentService.deleteAll(ResourceType.MILESTONE, milestone.id.toString())
        milestoneRepository.delete(milestone)
    }
}
