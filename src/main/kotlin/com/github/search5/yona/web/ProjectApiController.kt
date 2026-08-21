package com.github.search5.yona.web

import com.github.search5.yona.config.security.AccessControl
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUser
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.role.RoleRepository
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.vcs.RepositoryService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Locale

// yona controllers/api/ProjectApi.java의 newProject() 대응 (P2-45). legacy 경로는
// `-_-api/v1/owners/:owner/projects`(별도의 외부연동용 API 네임스페이스, yuna에는 아직 이식되지
// 않은 네임스페이스)이지만, yuna는 이미 `/api/projects/...`를 이 REST API 네임스페이스로 통일해
// 써왔으므로(ProjectController.kt의 search/update/delete/transfer/fork 등) 같은 컨벤션을 따른다 —
// 프로젝트 생성 로직 자체는 legacy 그대로, URL 스킴만 yuna 컨벤션으로 아키텍처 번역.
@RestController
class ProjectApiController(
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val organizationRepository: OrganizationRepository,
    private val roleRepository: RoleRepository,
    private val repositoryService: RepositoryService,
    private val accessControl: AccessControl
) {
    private val logger = LoggerFactory.getLogger(ProjectApiController::class.java)

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    // yona ProjectApi.java:111-161 newProject() 대응 (P2-45).
    @PostMapping("/api/projects/{owner}")
    fun newProject(
        @PathVariable owner: String,
        @RequestBody request: NewProjectApiRequest,
        authentication: Authentication?
    ): ResponseEntity<*> {
        val currentUser = getLoginUser(authentication)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build<Any>()

        // yona ProjectApi.java:120-123 "!currentUser.isSiteManager()" 대응.
        if (!currentUser.isSiteManager) {
            return ResponseEntity.badRequest()
                .body(mapOf("message" to "User creation with api is allowed by Site admin only."))
        }

        // yona ProjectApi.java:125-131 대응 — 중복 시 legacy는 실제 HTTP 상태는 400(badRequest)이고
        // JSON 바디 안의 "status" 필드에만 409를 적어 넣는다(원문 그대로의 불일치, 의도적 "정정" 금지).
        val existed = projectRepository.findByOwnerAndName(owner, request.projectName).orElse(null)
        if (existed != null) {
            return ResponseEntity.badRequest().body(
                mapOf(
                    "status" to 409,
                    "reason" to "Conflict",
                    "project" to createdProjectNode(existed)
                )
            )
        }

        val organization = organizationRepository.findByName(owner).orElse(null)

        // yona ProjectApi.java:133-138 대응 — isGlobalResourceCreatable(P2-34에서 이식) + owner가
        // 기존 조직명이면 그 조직 admin만 허용.
        if (!accessControl.isGlobalResourceCreatable(currentUser) ||
            (organization != null && !accessControl.isOrganizationAdmin(organization, currentUser))
        ) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapOf("message" to "'${currentUser.name}' has no permission"))
        }

        val project = Project().apply {
            this.owner = owner
            this.name = request.projectName
            this.overview = request.projectDescription ?: ""
            this.vcs = request.projectVcs ?: "GIT"
            this.createdDate = parseProjectCreatedDate(request.projectCreatedDate)
            this.projectScope = parseProjectScope(request.projectScope)
            this.siteurl = "http://localhost:9000/${request.projectName}"
            // yona ProjectApi.java:148-150 "Organization.isNameExist(owner)면 project.organization
            // 연동" 대응.
            if (organization != null) {
                this.organization = organization
            }
            // yona ProjectApi.java:230-243 saveMenuSettingsToDefault() 대응 — 전체 메뉴 활성화가
            // Project 엔티티의 isCodeEnabled 등 기본값(전부 true)과 이미 동일해 별도 호출이 필요 없다.
        }
        val savedProject = projectRepository.save(project)

        // yona ProjectApi.java:152 "ProjectUser.assignRole(User.SITE_MANAGER_ID, ..., SITEMANAGER)"
        // 대응 — SITE_MANAGER_ID는 legacy가 "DB의 1번 유저가 사이트매니저"라고 가정하는 하드코딩된
        // 상수(User.java:64 `SITE_MANAGER_ID = 1L`)로, yuna는 사이트매니저 여부를 User.isSiteManager
        // 상태값으로 판단해(id 하드코딩 관례 자체가 없음) 이 상수에 대응하는 값이 없다. 이 메서드에
        // 진입하려면 currentUser가 이미 사이트매니저임이 검증됐으므로(위 isSiteManager 체크), 그
        // 역할(SITEMANAGER)을 실제로 API를 호출한 사이트매니저 본인에게 부여하는 것이 legacy의 의도
        // (API로 만든 프로젝트에 사이트 차원의 소유권 role을 남긴다)를 그대로 보존하는 아키텍처 번역이다.
        roleRepository.findById(RoleType.SITEMANAGER.roleType).ifPresent { role ->
            val projectUser = ProjectUser(project = savedProject, user = currentUser, role = role)
            projectUserRepository.save(projectUser)
            savedProject.projectUsers.add(projectUser)
        }

        // yona ProjectApi.java:153 "RepositoryService.createRepository(project)" 대응.
        repositoryService.getRepository(savedProject).create()

        addProjectMembers(request.members, savedProject)

        return ResponseEntity.status(HttpStatus.CREATED).body(createdProjectNode(savedProject))
    }

    // yona ProjectApi.java:163-179 parseProjectScope() 대응.
    private fun parseProjectScope(scope: String?): ProjectScope {
        return when (scope) {
            "PRIVATE" -> ProjectScope.PRIVATE
            "PUBLIC" -> ProjectScope.PUBLIC
            "PROTECTED" -> ProjectScope.PROTECTED
            else -> ProjectScope.PRIVATE
        }
    }

    // yona ProjectApi.java:322-325 getDateString()/IssueApi.java:723-735 parseDateString()의
    // 역함수 대응 — 두 메서드가 공유하는 포맷("yyyy-MM-dd a hh:mm:ss Z", Locale.ENGLISH)을 그대로
    // 재사용해 exports()가 만든 날짜 문자열을 다시 파싱할 수 있게 한다. 파싱 실패 시 legacy도 null을
    // 반환(에러 응답 없이 조용히 무시)한다.
    private fun parseProjectCreatedDate(dateString: String?): Instant? {
        if (dateString == null) return null
        return try {
            SimpleDateFormat("yyyy-MM-dd a hh:mm:ss Z", Locale.ENGLISH).parse(dateString).toInstant()
        } catch (e: Exception) {
            null
        }
    }

    // yona ProjectApi.java:189-210 addProjectMembers() 대응.
    private fun addProjectMembers(members: List<NewProjectApiMember>?, project: Project) {
        if (members == null) return

        members.forEach { memberReq ->
            val member = userRepository.findByEmail(memberReq.email).orElse(null) ?: return@forEach

            val roleType = when (memberReq.role.lowercase()) {
                "member" -> RoleType.MEMBER
                "manager" -> RoleType.MANAGER
                else -> {
                    logger.warn("Unknown role type: ${memberReq.email}")
                    return@forEach
                }
            }
            val role = roleRepository.findById(roleType.roleType).orElse(null) ?: return@forEach

            val existing = projectUserRepository.findByProjectIdAndUserId(project.id!!, member.id!!).orElse(null)
            if (existing != null) {
                existing.role = role
                projectUserRepository.save(existing)
            } else {
                projectUserRepository.save(ProjectUser(project = project, user = member, role = role))
            }
        }

        // yona Project.java:637-655 cleanEnrolledUsers() 대응 — 이 프로젝트에 대해 이미 "가입 신청"을
        // 해둔 사용자가 여기서 멤버로 추가되면 그 신청을 자동 수락 처리하고 알림을 보낸다. newProject()는
        // 항상 새로 만드는 프로젝트라 이 시점엔 project.enrolledUsers가 구조적으로 항상 비어있어(방금
        // 막 생성돼 아직 아무도 가입 신청을 할 수 없었음) 실질적으로 항상 아무 일도 하지 않는 no-op이므로
        // 포팅하지 않는다(레거시에 없는 동작을 추가하는 게 아니라, 레거시에서도 이 호출 지점 한정으로는
        // 절대 발동할 수 없는 코드라 이식 대상에서 제외 — 기존 프로젝트에 멤버를 추가하는 다른 경로가
        // 있다면 그 경로에서는 별도로 검토가 필요할 수 있음).
    }

    // yona ProjectApi.java:220-228 createdProjectNode() 대응.
    private fun createdProjectNode(project: Project): Map<String, Any?> {
        return mapOf(
            "id" to project.id,
            "owner" to project.owner,
            "name" to project.name,
            "overview" to project.overview,
            "vcs" to project.vcs
        )
    }
}

// yona ProjectApi.java newProject()가 소비하는 JSON 스키마(exports()가 생산하는 것과 동일한 필드명)
// 대응 — projectName만 필수이고 나머지는 legacy와 동일하게 전부 선택값이다.
data class NewProjectApiRequest(
    val projectName: String,
    val projectDescription: String? = null,
    val projectVcs: String? = null,
    val projectCreatedDate: String? = null,
    val projectScope: String? = null,
    val members: List<NewProjectApiMember>? = null
)

data class NewProjectApiMember(
    val email: String,
    val role: String
)
