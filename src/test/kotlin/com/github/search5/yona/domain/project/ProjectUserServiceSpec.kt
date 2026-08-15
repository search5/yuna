package com.github.search5.yona.domain.project

import com.github.search5.yona.AbstractIntegrationTest
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleRepository
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

@Transactional
class ProjectUserServiceSpec @Autowired constructor(
    private val projectUserService: ProjectUserService,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val entityManager: EntityManager
) : AbstractIntegrationTest() {

    init {
        describe("ProjectUserService 통합 테스트") {
            lateinit var manager: User
            lateinit var member1: User
            lateinit var applicant: User
            lateinit var project: Project
            lateinit var roleManager: Role
            lateinit var roleMember: Role

            beforeEach {
                projectUserRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
                roleRepository.deleteAll()

                // 역할 등록
                roleManager = roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER"))
                roleMember = roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))

                // 유저 등록
                manager = userRepository.save(User(loginId = "manager-user", name = "관리자", email = "manager@yona.io"))
                member1 = userRepository.save(User(loginId = "member-user", name = "멤버1", email = "member@yona.io"))
                applicant = userRepository.save(User(loginId = "apply-user", name = "신청자", email = "apply@yona.io"))

                // 프로젝트 등록 (소유자를 manager 유저로 지정)
                project = projectRepository.save(
                    Project(name = "auth-project", owner = "manager-user")
                )

                // 관리자 멤버 관계 등록
                projectUserRepository.save(ProjectUser(project = project, user = manager, role = roleManager))
            }

            it("1. 가입 신청 및 대기 신청 정상 등록 검증") {
                // When - 가입 신청
                projectUserService.enroll(project.id!!, applicant.id!!)
                entityManager.flush()
                entityManager.clear()

                // Then
                val updatedApplicant = userRepository.findById(applicant.id!!).orElse(null)
                updatedApplicant.enrolledProjects.map { it.id } shouldContain project.id

                val updatedProject = projectRepository.findById(project.id!!).orElse(null)
                updatedProject.enrolledUsers.map { it.id } shouldContain applicant.id
            }

            it("2. 가입 신청 승인 검증") {
                // Given - 가입 신청
                projectUserService.enroll(project.id!!, applicant.id!!)
                entityManager.flush()
                entityManager.clear()

                // When - 가입 승인
                projectUserService.acceptMemberRequest(project.id!!, applicant.id!!, manager.id!!)
                entityManager.flush()
                entityManager.clear()

                // Then
                val projectMembers = projectUserRepository.findByProjectId(project.id!!)
                projectMembers.any { it.user.id == applicant.id && it.role.id == RoleType.MEMBER.roleType } shouldBe true

                val updatedApplicant = userRepository.findById(applicant.id!!).orElse(null)
                updatedApplicant.enrolledProjects.map { it.id } shouldNotContain project.id
            }

            it("3. 멤버 권한 변경 및 소유자 강등 금지 검증") {
                // Given - member1 등록
                projectUserRepository.save(ProjectUser(project = project, user = member1, role = roleMember))

                // When - member1 권한을 MANAGER로 승급
                projectUserService.updateMemberRole(project.id!!, member1.id!!, RoleType.MANAGER.roleType, manager.id!!)

                // Then
                val updatedMember = projectUserRepository.findByProjectIdAndUserId(project.id!!, member1.id!!).orElse(null)
                updatedMember shouldNotBe null
                updatedMember.role.id shouldBe RoleType.MANAGER.roleType

                // 소유자(owner) 강등 시도 시 예외 발생 검증
                shouldThrow<IllegalArgumentException> {
                    projectUserService.updateMemberRole(project.id!!, manager.id!!, RoleType.MEMBER.roleType, manager.id!!)
                }
            }

            it("4. 멤버 삭제 및 소유자 탈퇴 금지 검증") {
                // Given - member1 등록
                projectUserRepository.save(ProjectUser(project = project, user = member1, role = roleMember))

                // When - member1 삭제
                projectUserService.removeMember(project.id!!, member1.id!!, manager.id!!)

                // Then
                val memberExist = projectUserRepository.existsByProjectIdAndUserId(project.id!!, member1.id!!)
                memberExist shouldBe false

                // 소유자 탈퇴 시도 시 예외 발생 검증
                shouldThrow<IllegalArgumentException> {
                    projectUserService.removeMember(project.id!!, manager.id!!, manager.id!!)
                }
            }
        }
    }
}
