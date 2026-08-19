package com.github.search5.yona.domain.issue

interface IssueLabelService {
    fun getLabels(projectId: Long): List<IssueLabel>
    fun getCategories(projectId: Long): List<IssueLabelCategory>
    fun createLabel(projectId: Long, categoryId: Long, name: String, color: String): IssueLabel
    fun createCategory(projectId: Long, name: String, isExclusive: Boolean): IssueLabelCategory
    fun updateLabel(labelId: Long, name: String, color: String, categoryId: Long): IssueLabel
    fun deleteLabel(labelId: Long)
    fun deleteCategory(categoryId: Long)
}
