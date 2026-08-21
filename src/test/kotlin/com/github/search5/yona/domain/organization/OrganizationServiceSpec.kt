package com.github.search5.yona.domain.organization

import com.github.search5.yona.AbstractIntegrationTest
import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.notification.NotificationEventRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleRepository
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

@Transactional
class OrganizationServiceSpec @Autowired constructor(
    private val organizationService: OrganizationService,
    private val organizationRepository: OrganizationRepository,
    private val organizationUserRepository: OrganizationUserRepository,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val projectRepository: ProjectRepository,
    private val notificationEventRepository: NotificationEventRepository,
    private val entityManager: EntityManager
) : AbstractIntegrationTest() {

    init {
        describe("OrganizationService 통합 테스트") {
            lateinit var admin: User
            lateinit var member: User
            lateinit var roleAdmin: Role
            lateinit var roleMember: Role

            beforeEach {
                projectRepository.deleteAll()
                organizationUserRepository.deleteAll()
                organizationRepository.deleteAll()
                userRepository.deleteAll()
                roleRepository.deleteAll()

                roleAdmin = roleRepository.save(Role(id = RoleType.ORG_ADMIN.roleType, name = "ORG_ADMIN"))
                roleMember = roleRepository.save(Role(id = RoleType.ORG_MEMBER.roleType, name = "ORG_MEMBER"))

                admin = userRepository.save(User(loginId = "admin-user", name = "조직관리자", email = "admin@yona.io"))
                member = userRepository.save(User(loginId = "member-user", name = "조직원", email = "member@yona.io"))
            }

            it("1. 조직 생성 및 초기 관리자 지정 검증") {
                // When - 조직 생성
                val org = organizationService.createOrganization("my-org", "설명", admin.id!!)

                // Then
                org.id shouldNotBe null
                org.name shouldBe "my-org"
                org.descr shouldBe "설명"

                val orgUser = organizationUserRepository.findByOrganizationIdAndUserId(org.id!!, admin.id!!).orElse(null)
                orgUser shouldNotBe null
                orgUser.role.id shouldBe RoleType.ORG_ADMIN.roleType
            }

            it("예약어를 조직 이름으로 사용하려 하면 예외가 발생해야 한다(P2-01)") {
                shouldThrow<IllegalArgumentException> {
                    organizationService.createOrganization("projects", "설명", admin.id!!)
                }
            }

            // yona models/Organization.java:42 @Constraints.Pattern(User.LOGIN_ID_PATTERN) 대응 (P1-108).
            it("형식에 맞지 않는(공백 포함) 조직 이름이면 예외가 발생해야 한다") {
                shouldThrow<IllegalArgumentException> {
                    organizationService.createOrganization("my org", "설명", admin.id!!)
                }
            }

            it("형식에 맞는(영문/숫자/하이픈) 조직 이름은 정상 생성되어야 한다") {
                val org = organizationService.createOrganization("my-valid-org123", "설명", admin.id!!)
                org.name shouldBe "my-valid-org123"
            }

            it("2. 조직 멤버 추가 검증") {
                val org = organizationService.createOrganization("my-org", "설명", admin.id!!)

                // When - 멤버 추가
                organizationService.addOrganizationMember(org.id!!, member.loginId, RoleType.ORG_MEMBER.roleType, admin.id!!)

                // Then
                val exists = organizationUserRepository.existsByOrganizationIdAndUserId(org.id!!, member.id!!)
                exists shouldBe true

                val orgUser = organizationUserRepository.findByOrganizationIdAndUserId(org.id!!, member.id!!).orElse(null)
                orgUser.role.id shouldBe RoleType.ORG_MEMBER.roleType
            }

            it("3. 조직 멤버 권한 변경 및 마지막 관리자 강등 제한 검증") {
                val org = organizationService.createOrganization("my-org", "설명", admin.id!!)
                organizationService.addOrganizationMember(org.id!!, member.loginId, RoleType.ORG_MEMBER.roleType, admin.id!!)

                // 마지막 관리자(ORG_ADMIN) 강등 시도 시 예외 발생 검증
                shouldThrow<IllegalArgumentException> {
                    organizationService.updateOrganizationMemberRole(org.id!!, admin.id!!, RoleType.ORG_MEMBER.roleType, admin.id!!)
                }

                // member를 관리자로 승격
                organizationService.updateOrganizationMemberRole(org.id!!, member.id!!, RoleType.ORG_ADMIN.roleType, admin.id!!)

                // 이제 admin은 마지막 관리자가 아니므로 강등 성공해야 함
                organizationService.updateOrganizationMemberRole(org.id!!, admin.id!!, RoleType.ORG_MEMBER.roleType, admin.id!!)

                val updatedAdmin = organizationUserRepository.findByOrganizationIdAndUserId(org.id!!, admin.id!!).orElse(null)
                updatedAdmin.role.id shouldBe RoleType.ORG_MEMBER.roleType
            }

            it("4. 조직 멤버 삭제 및 마지막 관리자 탈퇴 제한 검증") {
                val org = organizationService.createOrganization("my-org", "설명", admin.id!!)
                organizationService.addOrganizationMember(org.id!!, member.loginId, RoleType.ORG_MEMBER.roleType, admin.id!!)

                // 마지막 관리자 삭제 시도 시 예외 발생 검증
                shouldThrow<IllegalArgumentException> {
                    organizationService.removeOrganizationMember(org.id!!, admin.id!!, admin.id!!)
                }

                // 일반 멤버 삭제 시 정상 삭제되어야 함
                organizationService.removeOrganizationMember(org.id!!, member.id!!, admin.id!!)
                organizationUserRepository.existsByOrganizationIdAndUserId(org.id!!, member.id!!) shouldBe false
            }

            it("5. 조직 삭제 제약(하위 프로젝트 존재 여부) 검증") {
                val org = organizationService.createOrganization("my-org", "설명", admin.id!!)

                // 1) 하위 프로젝트가 존재할 때 삭제 시도 -> 실패 예외 발생해야 함
                val project = projectRepository.save(
                    Project(name = "org-project", owner = "admin-user", organization = org)
                )
                org.projects.add(project)
                organizationRepository.save(org)

                shouldThrow<IllegalArgumentException> {
                    organizationService.deleteOrganization(org.id!!, admin.id!!)
                }

                // 2) 하위 프로젝트 제거 후 삭제 시도 -> 성공해야 함
                org.projects.clear()
                organizationRepository.save(org)
                projectRepository.delete(project)

                organizationService.deleteOrganization(org.id!!, admin.id!!)
                organizationRepository.findById(org.id!!).isPresent shouldBe false
            }

            it("6. 게스트 계정은 조직 멤버로 추가할 수 없어야 한다 (P1-17)") {
                val org = organizationService.createOrganization("my-org", "설명", admin.id!!)
                val guest = userRepository.save(
                    User(loginId = "guest-user", name = "게스트", email = "guest@yona.io", isGuest = true)
                )

                shouldThrow<IllegalArgumentException> {
                    organizationService.addOrganizationMember(org.id!!, guest.loginId, RoleType.ORG_MEMBER.roleType, admin.id!!)
                }

                organizationUserRepository.existsByOrganizationIdAndUserId(org.id!!, guest.id!!) shouldBe false
            }

            // yona EnrollOrganizationApp.java 대응, Project P1-16과 동일 유형(P1-122). 이미 대기 중인
            // 가입 신청이 있는 유저가 재신청해도 알림이 중복 발행되지 않아야 한다.
            it("7. 이미 대기 중인 가입 신청이 있는 유저가 재신청해도 알림이 중복 발행되지 않아야 한다 (P1-122)") {
                val org = organizationService.createOrganization("my-org", "설명", admin.id!!)
                val applicant = userRepository.save(
                    User(loginId = "applicant-user", name = "신청자", email = "applicant@yona.io")
                )

                // Given - 최초 가입 신청
                organizationService.enroll(org.name, applicant.id!!)
                entityManager.flush()
                entityManager.clear()

                // When - 동일 유저가 다시 가입 신청(중복)
                organizationService.enroll(org.name, applicant.id!!)
                entityManager.flush()
                entityManager.clear()

                // Then - 대기 신청 목록엔 여전히 1건만 있고, 신청 알림도 1건만 발행돼야 한다
                val updatedApplicant = userRepository.findById(applicant.id!!).orElse(null)
                updatedApplicant.enrolledOrganizations.count { it.id == org.id } shouldBe 1

                val requestEvents = notificationEventRepository.findAll().filter {
                    it.resourceId == org.id.toString() &&
                        it.eventType == EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST &&
                        it.newValue == "REQUEST"
                }
                requestEvents.size shouldBe 1
            }

            // yona EnrollOrganizationApp.java:82,101-104 대응 (P1-123). 대기 중인 가입 신청이
            // 실제로 없으면 취소 알림을 발행하지 않고, 이미 정식 멤버라면 취소 자체를 거부해야 한다.
            it("8. 대기 중인 가입 신청이 없는 상태에서 취소를 호출하면 알림을 발행하지 않아야 한다") {
                val org = organizationService.createOrganization("my-org", "설명", admin.id!!)
                val bystander = userRepository.save(
                    User(loginId = "bystander-user", name = "구경꾼", email = "bystander@yona.io")
                )

                organizationService.cancelEnroll(org.name, bystander.id!!)
                entityManager.flush()
                entityManager.clear()

                val cancelEvents = notificationEventRepository.findAll().filter {
                    it.resourceId == org.id.toString() && it.eventType == EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST && it.newValue == "CANCEL"
                }
                cancelEvents.size shouldBe 0
            }

            it("9. 이미 정식 멤버인 유저가 가입 신청 취소를 호출하면 예외가 발생해야 한다") {
                val org = organizationService.createOrganization("my-org", "설명", admin.id!!)
                organizationService.addOrganizationMember(org.id!!, member.loginId, RoleType.ORG_MEMBER.roleType, admin.id!!)

                shouldThrow<IllegalArgumentException> {
                    organizationService.cancelEnroll(org.name, member.id!!)
                }
            }

            it("10. 대기 중인 가입 신청을 취소하면 목록에서 제거되고 취소 알림이 1건 발행돼야 한다") {
                val org = organizationService.createOrganization("my-org", "설명", admin.id!!)
                val applicant = userRepository.save(
                    User(loginId = "applicant-user2", name = "신청자2", email = "applicant2@yona.io")
                )
                organizationService.enroll(org.name, applicant.id!!)
                entityManager.flush()
                entityManager.clear()

                organizationService.cancelEnroll(org.name, applicant.id!!)
                entityManager.flush()
                entityManager.clear()

                val updatedApplicant = userRepository.findById(applicant.id!!).orElse(null)
                updatedApplicant.enrolledOrganizations.count { it.id == org.id } shouldBe 0

                val cancelEvents = notificationEventRepository.findAll().filter {
                    it.resourceId == org.id.toString() && it.eventType == EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST && it.newValue == "CANCEL"
                }
                cancelEvents.size shouldBe 1
            }
        }
    }
}
