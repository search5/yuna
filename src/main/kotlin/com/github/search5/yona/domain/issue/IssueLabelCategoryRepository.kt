package com.github.search5.yona.domain.issue

import com.github.search5.yona.domain.project.Project
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface IssueLabelCategoryRepository : JpaRepository<IssueLabelCategory, Long> {
    fun findByProject(project: Project): List<IssueLabelCategory>
    fun findByProjectAndName(project: Project, name: String): IssueLabelCategory?
}
