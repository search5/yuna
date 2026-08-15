package com.github.search5.yona.domain.project

import com.github.search5.yona.domain.user.User

interface ProjectService {
    fun findByOwnerAndName(owner: String, name: String): Project?
    fun findProjectsByOwner(owner: String): List<Project>
    fun createProject(project: Project, creator: User): Project
    fun exists(owner: String, name: String): Boolean
    fun isMember(projectId: Long, loginId: String): Boolean
    fun updateProject(projectId: Long, param: UpdateProjectParam): Project
    fun deleteProject(projectId: Long)
    fun requestNewTransfer(projectId: Long, senderId: Long, destination: String): ProjectTransfer
    fun acceptTransfer(transferId: Long, confirmKey: String, acceptorId: Long)
    fun forkProject(projectId: Long, forkerId: Long, destinationOwner: String = "", destinationName: String = ""): Project
    fun changeVCS(projectId: Long): Project
}

data class UpdateProjectParam(
    val overview: String,
    val projectScope: ProjectScope,
    val isCodeAccessibleMemberOnly: Boolean,
    val isUsingReviewerCount: Boolean,
    val defaultReviewerCount: Int,
    val defaultBranch: String?,
    val isCodeEnabled: Boolean,
    val isIssueEnabled: Boolean,
    val isPullRequestEnabled: Boolean,
    val isReviewEnabled: Boolean,
    val isMilestoneEnabled: Boolean,
    val isBoardEnabled: Boolean
)

