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
        
        // yona IssueLabel.exists()(project+category+name 복합 유일성) 대응 (P1-54).
        val existing = issueLabelRepository.findByProjectAndCategoryAndName(project, category, name)
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

    // yona IssueLabelApp.updateCategory() 대응. 같은 프로젝트 내 다른 카테고리와 이름이 겹치면 거부한다.
    override fun updateCategory(categoryId: Long, name: String, isExclusive: Boolean): IssueLabelCategory {
        val category = issueLabelCategoryRepository.findById(categoryId)
            .orElseThrow { IllegalArgumentException("Category not found: $categoryId") }

        val duplicate = issueLabelCategoryRepository.findByProjectAndName(category.project, name)
        if (duplicate != null && duplicate.id != categoryId) {
            throw DuplicateLabelCategoryNameException("이미 존재하는 카테고리 이름입니다: $name")
        }

        category.name = name
        category.isExclusive = isExclusive

        return issueLabelCategoryRepository.save(category)
    }

    // yona IssueLabel.copyIssueLabels()/copyIssueLabel()/copyIssueLabelCategory() 대응.
    // 원본 프로젝트의 라벨을 대상 프로젝트로 복사하되, 같은 이름의 카테고리/라벨이 이미 있으면
    // 재사용(재생성하지 않음)한다. yona copyIssueLabelCategory()가 카테고리를 먼저 찾거나 만든 뒤
    // copyIssueLabel().exists()(project+category+name 복합 유일성, P1-54)로 중복을 판단하는 순서를
    // 그대로 따른다 — 카테고리를 먼저 정해야 그 카테고리 기준으로 중복 여부를 물을 수 있다.
    override fun copyLabels(fromProjectId: Long, toProjectId: Long): List<IssueLabel> {
        val fromProject = projectRepository.findById(fromProjectId)
            .orElseThrow { IllegalArgumentException("Project not found: $fromProjectId") }
        val toProject = projectRepository.findById(toProjectId)
            .orElseThrow { IllegalArgumentException("Project not found: $toProjectId") }

        val copiedLabels = mutableListOf<IssueLabel>()

        for (fromLabel in issueLabelRepository.findByProject(fromProject)) {
            val category = issueLabelCategoryRepository.findByProjectAndName(toProject, fromLabel.category.name)
                ?: issueLabelCategoryRepository.save(
                    IssueLabelCategory(
                        name = fromLabel.category.name,
                        isExclusive = fromLabel.category.isExclusive,
                        project = toProject
                    )
                )

            if (issueLabelRepository.findByProjectAndCategoryAndName(toProject, category, fromLabel.name) != null) {
                continue
            }

            val copiedLabel = issueLabelRepository.save(
                IssueLabel(
                    category = category,
                    color = fromLabel.color,
                    name = fromLabel.name,
                    project = toProject
                )
            )
            copiedLabels.add(copiedLabel)
        }

        return copiedLabels
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
