package com.github.search5.yona.domain.issue

import com.github.search5.yona.domain.project.Project
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface IssueLabelRepository : JpaRepository<IssueLabel, Long> {
    fun findByProject(project: Project): List<IssueLabel>
    fun findByProjectAndName(project: Project, name: String): IssueLabel?

    @Modifying
    @Query(value = "delete from issue_issue_label where issue_label_id = :labelId", nativeQuery = true)
    fun deleteIssueMappings(@Param("labelId") labelId: Long)

    @Modifying
    @Query(value = "delete from posting_issue_label where issue_label_id = :labelId", nativeQuery = true)
    fun deletePostingMappings(@Param("labelId") labelId: Long)
}
