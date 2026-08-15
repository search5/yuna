package com.github.search5.yona.domain.organization

interface OrganizationService {
    fun findByName(name: String): Organization?
    fun createOrganization(organization: Organization): Organization
    fun isNameExist(name: String): Boolean

    /**
     * 조직 신규 생성
     */
    fun createOrganization(name: String, descr: String?, creatorId: Long): Organization

    /**
     * 조직 정보 및 설정 변경
     */
    fun updateOrganizationSettings(orgId: Long, name: String, descr: String?, updaterId: Long)

    /**
     * 조직 멤버 추가
     */
    fun addOrganizationMember(orgId: Long, userLoginId: String, roleId: Long, updaterId: Long)

    /**
     * 조직 멤버 삭제
     */
    fun removeOrganizationMember(orgId: Long, userId: Long, removerId: Long)

    /**
     * 조직 멤버 권한 변경
     */
    fun updateOrganizationMemberRole(orgId: Long, userId: Long, newRoleId: Long, updaterId: Long)

    /**
     * 조직 삭제
     */
    fun deleteOrganization(orgId: Long, deleterId: Long)
    /**
     * 조직 멤버 가입 신청
     */
    fun enroll(orgName: String, userId: Long)

    /**
     * 조직 멤버 가입 신청 취소
     */
    fun cancelEnroll(orgName: String, userId: Long)
}

