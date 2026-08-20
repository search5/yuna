package com.github.search5.yona.config.security

import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.enumeration.ResourceType

object AccessControl {

    /**
     * Checks if a user has a permission to read a project.
     */
    fun isAllowedToReadProject(user: User?, project: Project): Boolean {
        // public 프로젝트는 비로그인 사용자나 게스트가 아니라면 기본적으로 읽기 가능
        if (project.isPublic) {
            return user == null || !user.isGuest
        }

        if (user == null || user.isGuest || user.loginId == "") {
            return false
        }

        return user.isSiteManager
            || isOrganizationAdmin(project, user)
            || user.isMemberOf(project)
            || isAllowedIfGroupMember(project, user)
    }

    /**
     * Checks if a user has a permission to create a resource of the given
     * type in the given project.
     */
    fun isProjectResourceCreatable(user: User?, project: Project, resourceType: ResourceType): Boolean {
        if (user == null || user.isGuest || user.loginId == "") {
            return false
        }

        // Site manager, Group admin, Project members can create anything.
        if (user.isSiteManager
            || isOrganizationAdmin(project, user)
            || user.isMemberOf(project)
            || isAllowedIfGroupMember(project, user)
        ) {
            return true
        }

        // If the project is not public, nonmembers cannot create anything.
        if (!project.isPublic) {
            return false
        }

        // If the project is public, login users can create issues and posts.
        return when (resourceType) {
            ResourceType.ISSUE_POST,
            ResourceType.BOARD_POST,
            ResourceType.ISSUE_COMMENT,
            ResourceType.NONISSUE_COMMENT,
            ResourceType.FORK,
            ResourceType.COMMIT_COMMENT,
            ResourceType.REVIEW_COMMENT -> true
            else -> false
        }
    }

    /**
     * Checks if a user has a permission to update a project resource like an issue.
     */
    fun isAllowedToUpdateIssue(user: User?, project: Project, authorLoginId: String?): Boolean {
        if (user == null || user.isGuest || user.loginId == "") {
            return false
        }
        if (user.isSiteManager
            || isOrganizationAdmin(project, user)
            || user.isManagerOf(project)
            || user.isMemberOf(project)
            || (authorLoginId != null && user.loginId == authorLoginId)
        ) {
            return true
        }
        return false
    }

    /**
     * Checks if a user has a permission to update a posting.
     */
    fun isAllowedToUpdatePosting(user: User?, project: Project, authorLoginId: String?): Boolean {
        if (user == null || user.isGuest || user.loginId == "") {
            return false
        }
        if (user.isSiteManager
            || isOrganizationAdmin(project, user)
            || user.isManagerOf(project)
            || user.isMemberOf(project)
            || (authorLoginId != null && user.loginId == authorLoginId)
        ) {
            return true
        }
        return false
    }

    /**
     * Checks if a user has a permission to update a milestone.
     */
    fun isAllowedToUpdateMilestone(user: User?, project: Project): Boolean {
        if (user == null || user.isGuest || user.loginId == "") {
            return false
        }
        if (user.isSiteManager
            || isOrganizationAdmin(project, user)
            || user.isManagerOf(project)
            || user.isMemberOf(project)
        ) {
            return true
        }
        return false
    }

    private fun isOrganizationAdmin(project: Project, user: User): Boolean {
        val organization = project.organization ?: return false
        return organization.organizationUsers.any {
            it.user.id == user.id && it.role.id == com.github.search5.yona.domain.role.RoleType.ORG_ADMIN.roleType
        }
    }

    // yona AccessControl.java:90-94 isAllowedIfGroupMember() 대응 (P1-57). 프로젝트 직접 멤버가
    // 아니어도 조직(그룹) 소속이면 PUBLIC/PROTECTED 프로젝트에 한해 권한을 준다 — web 컨트롤러들의
    // checkReadPermission/checkWritePermission이 이 규칙을 호출할 수 있도록 공개.
    fun isAllowedIfGroupMember(project: Project, user: User): Boolean {
        val organization = project.organization ?: return false
        val hasGroup = project.organization != null
        val isPublicOrProtected = project.isPublic || project.isProtected

        if (hasGroup && isPublicOrProtected) {
            return organization.organizationUsers.any {
                it.user.id == user.id && (
                    it.role.id == com.github.search5.yona.domain.role.RoleType.ORG_MEMBER.roleType ||
                    it.role.id == com.github.search5.yona.domain.role.RoleType.ORG_ADMIN.roleType
                )
            }
        }
        return false
    }
}
