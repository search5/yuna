package com.github.search5.yona.domain.issue

import com.github.search5.yona.domain.project.ProjectRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class IssueLabelServiceImpl(
    private val issueLabelRepository: IssueLabelRepository,
    private val issueLabelCategoryRepository: IssueLabelCategoryRepository,
    private val projectRepository: ProjectRepository
) : IssueLabelService {

    @Transactional(readOnly = true)
    override fun getLabels(projectId: Long): List<IssueLabel> {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Project not found: $projectId") }
        return issueLabelRepository.findByProject(project)
    }

    @Transactional(readOnly = true)
    override fun getCategories(projectId: Long): List<IssueLabelCategory> {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Project not found: $projectId") }
        return issueLabelCategoryRepository.findByProject(project)
    }

    override fun createLabel(projectId: Long, categoryId: Long, name: String, color: String): IssueLabel {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Project not found: $projectId") }
        val category = issueLabelCategoryRepository.findById(categoryId)
            .orElseThrow { IllegalArgumentException("Category not found: $categoryId") }
        
        val existing = issueLabelRepository.findByProjectAndName(project, name)
        if (existing != null) {
            return existing
        }

        val label = IssueLabel(
            category = category,
            color = color,
            name = name,
            project = project
        )
        return issueLabelRepository.save(label)
    }

    override fun createCategory(projectId: Long, name: String, isExclusive: Boolean): IssueLabelCategory {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Project not found: $projectId") }
        
        val existing = issueLabelCategoryRepository.findByProjectAndName(project, name)
        if (existing != null) {
            return existing
        }

        val category = IssueLabelCategory(
            name = name,
            isExclusive = isExclusive,
            project = project
        )
        return issueLabelCategoryRepository.save(category)
    }

    // yona IssueLabelApp.update() 대응. newLabel()과 달리 이름/색상 중복 검사는 하지 않고 그대로 덮어쓴다.
    override fun updateLabel(labelId: Long, name: String, color: String, categoryId: Long): IssueLabel {
        val label = issueLabelRepository.findById(labelId)
            .orElseThrow { IllegalArgumentException("Label not found: $labelId") }
        val category = issueLabelCategoryRepository.findById(categoryId)
            .orElseThrow { IllegalArgumentException("Category not found: $categoryId") }

        label.name = name
        label.color = color
        label.category = category

        return issueLabelRepository.save(label)
    }

    override fun deleteLabel(labelId: Long) {
        issueLabelRepository.deleteIssueMappings(labelId)
        issueLabelRepository.deletePostingMappings(labelId)
        issueLabelRepository.deleteById(labelId)
    }

    override fun deleteCategory(categoryId: Long) {
        val category = issueLabelCategoryRepository.findById(categoryId)
            .orElseThrow { IllegalArgumentException("Category not found: $categoryId") }
        
        val labels = issueLabelRepository.findByProject(category.project)
            .filter { it.category.id == categoryId }
        
        labels.forEach { label ->
            label.id?.let { deleteLabel(it) }
        }
        
        issueLabelCategoryRepository.delete(category)
    }
}
