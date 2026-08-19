package com.github.search5.yona.domain.issue

interface IssueLabelService {
    fun getLabels(projectId: Long): List<IssueLabel>
    fun getCategories(projectId: Long): List<IssueLabelCategory>
    fun createLabel(projectId: Long, categoryId: Long, name: String, color: String): IssueLabel
    fun createCategory(projectId: Long, name: String, isExclusive: Boolean): IssueLabelCategory
    fun updateLabel(labelId: Long, name: String, color: String, categoryId: Long): IssueLabel
    fun updateCategory(categoryId: Long, name: String, isExclusive: Boolean): IssueLabelCategory
    fun copyLabels(fromProjectId: Long, toProjectId: Long): List<IssueLabel>
    fun deleteLabel(labelId: Long)
    fun deleteCategory(categoryId: Long)
}

// yona IssueLabelApp.updateCategory()의 동일 프로젝트 내 이름 중복 검사 대응.
class DuplicateLabelCategoryNameException(message: String) : RuntimeException(message)
