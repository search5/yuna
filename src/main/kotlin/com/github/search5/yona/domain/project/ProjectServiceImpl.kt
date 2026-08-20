package com.github.search5.yona.domain.project

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import com.github.search5.yona.domain.vcs.RepositoryService
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.role.RoleRepository
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.organization.OrganizationRepository
import com.github.search5.yona.domain.organization.OrganizationUserRepository
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Optional

@Service
@Transactional(readOnly = true)
class ProjectServiceImpl(
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val repositoryService: RepositoryService,
    private val userRepository: UserRepository,
    private val projectTransferRepository: ProjectTransferRepository,
    private val roleRepository: RoleRepository,
    private val organizationRepository: OrganizationRepository,
    private val organizationUserRepository: OrganizationUserRepository,
    private val labelRepository: LabelRepository
) : ProjectService {

    // yona Project.findByOwnerAndProjectName()의 예전 위치(previousOwnerLoginId/previousName) 폴백
    // 대응 (P1-76) — 프로젝트가 이전/개명된 뒤에도 이 서비스 메서드를 쓰는 모든 호출부(SVN/Git
    // 인가 필터 등)가 자동으로 예전 owner/name도 계속 찾을 수 있다.
    override fun findByOwnerAndName(owner: String, name: String): Project? {
        return projectRepository.findByOwnerAndNameOrPreviousPlace(owner, name).orElse(null)
    }

    override fun findProjectsByOwner(owner: String): List<Project> {
        return projectRepository.findByOwner(owner)
    }

    @Transactional
    override fun createProject(project: Project, creator: User): Project {
        if (exists(project.owner ?: "", project.name)) {
            throw IllegalArgumentException("Already exists project name: ${project.owner}/${project.name}")
        }
        project.createdDate = Instant.now()
        project.siteurl = "http://localhost:9000/${project.name}"
        val savedProject = projectRepository.save(project)
        roleRepository.findById(RoleType.MANAGER.roleType).ifPresent { managerRole ->
            val projectUser = ProjectUser(
                project = savedProject,
                user = creator,
                role = managerRole
            )
            projectUserRepository.save(projectUser)
            savedProject.projectUsers.add(projectUser)
        }
        return savedProject
    }

    override fun exists(owner: String, name: String): Boolean {
        return projectRepository.findByOwnerAndName(owner, name).isPresent
    }

    override fun isMember(projectId: Long, loginId: String): Boolean {
        val project = projectRepository.findById(projectId).orElse(null) ?: return false
        val user = userRepository.findByLoginId(loginId).orElse(null)
        if (user != null && user.isSiteManager) return true
        if (project.owner == loginId) return true
        return projectUserRepository.existsByProjectIdAndUserLoginId(projectId, loginId)
    }

    @Transactional
    override fun updateProject(projectId: Long, param: UpdateProjectParam): Project {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("프로젝트를 찾을 수 없습니다.") }
        project.overview = param.overview
        project.projectScope = param.projectScope
        project.isCodeAccessibleMemberOnly = param.isCodeAccessibleMemberOnly
        project.isUsingReviewerCount = param.isUsingReviewerCount
        project.defaultReviewerCount = param.defaultReviewerCount
        
        project.isCodeEnabled = param.isCodeEnabled
        project.isIssueEnabled = param.isIssueEnabled
        project.isPullRequestEnabled = param.isPullRequestEnabled
        project.isReviewEnabled = param.isReviewEnabled
        project.isMilestoneEnabled = param.isMilestoneEnabled
        project.isBoardEnabled = param.isBoardEnabled

        if (!param.defaultBranch.isNullOrBlank()) {
            try {
                val repository = repositoryService.getRepository(project)
                repository.setDefaultBranch("refs/heads/${param.defaultBranch}")
            } catch (e: Exception) {
                // 저장소 기본 브랜치 세팅 에러 방어
            }
        }

        return projectRepository.save(project)
    }

    @Transactional
    override fun deleteProject(projectId: Long) {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("프로젝트를 찾을 수 없습니다.") }
        
        // 연관 멤버 삭제
        val members = projectUserRepository.findByProjectId(projectId)
        projectUserRepository.deleteAll(members)

        projectRepository.delete(project)
    }

    @Transactional
    override fun requestNewTransfer(projectId: Long, senderId: Long, destination: String): ProjectTransfer {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Project not found") }
        val sender = userRepository.findById(senderId)
            .orElseThrow { IllegalArgumentException("Sender not found") }

        // destination 적격성 검증 (유저 로그인 ID 또는 조직 이름)
        val destUser = userRepository.findByLoginId(destination)
        val destOrg = projectRepository.findByOwner(destination) // 기존에 조직 등으로 존재하거나 owner로 식별 가능한지
        
        val key = (1..50).map { (('a'..'z') + ('A'..'Z') + ('0'..'9')).random() }.joinToString("")
        // yona Project.newProjectName(destination, name) 대응 (P1-72) — 목적지에 이미 동명
        // 프로젝트가 있으면 name-1, name-2...로 충돌이 없을 때까지 자동으로 뒤에 숫자를 붙인다.
        val newProjName = resolveNewProjectName(destination, project.name)

        val existing = projectTransferRepository.findByProjectAndSenderAndDestination(project, sender, destination)
        return if (existing.isPresent) {
            val pt = existing.get()
            pt.requested = Instant.now()
            pt.confirmKey = key
            projectTransferRepository.save(pt)
        } else {
            val pt = ProjectTransfer(
                project = project,
                sender = sender,
                destination = destination,
                confirmKey = key,
                newProjectName = newProjName,
                requested = Instant.now()
            )
            projectTransferRepository.save(pt)
        }
    }

    // yona Project.recordRenameOrTransferHistoryIfLastChangePassed24HoursFrom() 대응 (P1-76).
    private fun recordRenameOrTransferHistoryIfLastChangePassed24HoursFrom(
        project: Project,
        currentOwner: String,
        currentName: String
    ) {
        val lastChanged = project.previousNameChangedTime
        val isFirstOrPassed24Hours = lastChanged == null ||
            lastChanged.isBefore(Instant.now().minusSeconds(24 * 3600))
        if (isFirstOrPassed24Hours) {
            project.previousNameChangedTime = Instant.now()
            project.previousName = currentName
            project.previousOwnerLoginId = currentOwner
        }
    }

    // yona Project.newProjectName(loginId, projectName) 대응 (P1-72).
    private fun resolveNewProjectName(destination: String, name: String): String {
        if (!projectRepository.findByOwnerAndName(destination, name).isPresent) {
            return name
        }
        var i = 1
        while (true) {
            val candidate = "$name-$i"
            if (!projectRepository.findByOwnerAndName(destination, candidate).isPresent) {
                return candidate
            }
            i++
        }
    }

    @Transactional
    override fun acceptTransfer(transferId: Long, confirmKey: String, acceptorId: Long) {
        val limit = Instant.now().minusSeconds(86400) // 24시간 전 유효
        val pt = projectTransferRepository.findByIdAndAcceptedAndRequestedAfter(transferId, false, limit)
            .orElseThrow { IllegalArgumentException("Invalid or expired transfer request") }

        if (pt.confirmKey != confirmKey) {
            throw IllegalArgumentException("Confirm key mismatch")
        }

        val acceptor = userRepository.findById(acceptorId)
            .orElseThrow { IllegalArgumentException("Acceptor not found") }
        if (!isAuthorizedToAcceptTransfer(pt.destination, acceptor)) {
            throw IllegalArgumentException("이 프로젝트 이전을 수락할 권한이 없습니다.")
        }

        val project = pt.project
        val originalOwner = project.owner ?: ""
        val originalName = project.name
        val newOwner = pt.destination
        val newName = pt.newProjectName
        val senderId = pt.sender.id!!

        // yona Project.recordRenameOrTransferHistoryIfLastChangePassed24HoursFrom() 대응 (P1-76) —
        // 마지막 이전/개명 기록으로부터 24시간이 지났을 때만(또는 최초일 때만) 예전 위치를 갱신한다.
        // 짧은 시간 내 연속 이전이 일어나도 "예전 위치" 포인터가 계속 최신으로만 덮어써지지 않도록
        // 방지하는 legacy의 의도를 그대로 재현.
        recordRenameOrTransferHistoryIfLastChangePassed24HoursFrom(project, originalOwner, originalName)

        // 물리 저장소 폴더명 이동
        val baseDir = if (project.vcs?.uppercase() == "SUBVERSION" || project.vcs?.uppercase() == "SVN") {
            "/tmp/yuna/svn" // 기본경로 활용
        } else {
            "/tmp/yuna/git"
        }
        val sourceDir = File(baseDir, "$originalOwner/$originalName.git")
        val targetDir = File(baseDir, "$newOwner/$newName.git")
        if (sourceDir.exists()) {
            targetDir.parentFile.mkdirs()
            sourceDir.renameTo(targetDir)
        }

        // DB 메타데이터 변경 반영
        project.owner = newOwner
        project.name = newName
        // yona ProjectApp.acceptTransfer()의 "project.organization = newOwnerOrg 또는 null" 대응
        // (P1-73) — 목적지가 조직이면 그 조직으로, 개인이면 null로 명시적으로 갱신한다.
        project.organization = organizationRepository.findByName(newOwner).orElse(null)
        projectRepository.save(project)

        // 권한(Role) 변경 처리
        // 1. 보낸 사람이 MANAGER였다면 MEMBER로 강등
        val senderProjectUser = projectUserRepository.findByProjectIdAndUserId(project.id!!, senderId).orElse(null)
        if (senderProjectUser != null && senderProjectUser.role.id == RoleType.MANAGER.roleType) {
            val memberRole = roleRepository.findById(RoleType.MEMBER.roleType)
                .orElseThrow { IllegalStateException("MEMBER role not found") }
            senderProjectUser.role = memberRole
            projectUserRepository.save(senderProjectUser)
        }

        // 2. 이관 목적지(destination) 사용자가 존재한다면 MANAGER 권한 부여
        val newOwnerUser = userRepository.findByLoginId(newOwner).orElse(null)
        if (newOwnerUser != null) {
            val newOwnerProjectUser = projectUserRepository.findByProjectIdAndUserId(project.id!!, newOwnerUser.id!!).orElse(null)
            val managerRole = roleRepository.findById(RoleType.MANAGER.roleType)
                .orElseThrow { IllegalStateException("MANAGER role not found") }
            if (newOwnerProjectUser != null) {
                newOwnerProjectUser.role = managerRole
                projectUserRepository.save(newOwnerProjectUser)
            } else {
                val projectUser = ProjectUser(
                    project = project,
                    user = newOwnerUser,
                    role = managerRole
                )
                projectUserRepository.save(projectUser)
            }
        }

        // yona ProjectApp.disableProjectTransferLink()의 ProjectTransfer.deleteExisting(project,
        // pt.sender, pt.destination) 대응 (P1-74) — 실제 쿼리 조건이 pt 자신과 동일한
        // (project, sender, destination) 3중 키라, "완료된 이관 요청을 DB에서 삭제"하는 게 실제
        // 동작이다(accepted=true로 남겨두지 않음). in-memory 상의 pt.accepted=true 대입은 yona에서도
        // 삭제 직전에만 존재하는 값이라(영속 안 됨) 그대로 재현하되, 영속화는 save가 아니라 delete로 한다.
        pt.accepted = true
        projectTransferRepository.delete(pt)
    }

    private fun isAuthorizedToAcceptTransfer(destination: String, acceptor: User): Boolean {
        if (acceptor.loginId == destination) {
            return true
        }
        val organization = organizationRepository.findByName(destination).orElse(null) ?: return false
        val orgUser = organizationUserRepository
            .findByOrganizationIdAndUserId(organization.id!!, acceptor.id!!)
            .orElse(null) ?: return false
        return orgUser.role.id == RoleType.ORG_ADMIN.roleType
    }

    @Transactional
    override fun forkProject(
        projectId: Long,
        forkerId: Long,
        destinationOwner: String,
        destinationName: String
    ): Project {
        val original = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Original project not found") }
        val forker = userRepository.findById(forkerId)
            .orElseThrow { IllegalArgumentException("Forker user not found") }

        val destOwner = if (destinationOwner.isNotBlank()) destinationOwner else forker.loginId
        val destName = if (destinationName.isNotBlank()) destinationName else original.name

        // 포크 대상 껍데기 프로젝트 엔티티 복제 생성
        val forked = Project(
            name = destName,
            overview = original.overview,
            vcs = original.vcs,
            siteurl = "http://localhost:9000/$destName",
            owner = destOwner,
            projectScope = original.projectScope,
            originalProject = original
        )
        val savedFork = projectRepository.save(forked)

        // 포크 유저를 자식 프로젝트의 MANAGER 멤버로 매핑
        val managerRole = roleRepository.findById(RoleType.MANAGER.roleType)
            .orElseThrow { IllegalStateException("MANAGER role not found") }
        val projectUser = ProjectUser(
            project = savedFork,
            user = forker,
            role = managerRole
        )
        projectUserRepository.save(projectUser)

        // 물리 Bare 깃 저장소를 하드링크(Hard Link) 방식으로 무복사 복제
        val baseDir = if (original.vcs?.uppercase() == "SUBVERSION" || original.vcs?.uppercase() == "SVN") {
            "/tmp/yuna/svn"
        } else {
            "/tmp/yuna/git"
        }
        val sourceDir = File(baseDir, "${original.owner}/${original.name}.git")
        val targetDir = File(baseDir, "$destOwner/$destName.git")

        if (sourceDir.exists()) {
            cloneHardLinkedRepository(sourceDir, targetDir)
        }

        return savedFork
    }

    /**
     * 원본 저장소를 하드링크 기반으로 무복사 복제합니다.
     * 
     * [제약 사항 및 한계 상황]:
     * - 이 기능은 파일 시스템 수준의 하드링크(Hard Link)를 생성하므로, 원본 디렉토리와 대상 디렉토리가
     *   물리적으로 동일한 디스크 파티션/볼륨에 위치해야만 정상 작동합니다.
     * - 서로 다른 볼륨(Cross-device) 간 포크 시에는 java.nio.file.FileSystemException (EXDEV)이 발생하게 되며,
     *   이 예외에 대한 별도의 런타임 물리 복사(Files.copy) 폴백 처리는 의도적으로 생략되었습니다.
     */
    private fun cloneHardLinkedRepository(source: File, target: File) {
        if (!target.exists()) {
            target.mkdirs()
        }
        source.listFiles()?.forEach { file ->
            val targetFile = File(target, file.name)
            if (file.isDirectory) {
                cloneHardLinkedRepository(file, targetFile)
            } else {
                Files.createLink(targetFile.toPath(), file.toPath())
            }
        }
    }

    @Transactional
    override fun changeVCS(projectId: Long): Project {
        val project = projectRepository.findById(projectId).orElseThrow { IllegalArgumentException("Project not found") }

        for (fork in project.forkingProjects) {
            fork.originalProject = null
            projectRepository.save(fork)
        }
        project.forkingProjects.clear()

        try {
            repositoryService.getRepository(project).delete()
        } catch (e: Exception) {
            // ignore
        }

        val currentVcs = project.vcs?.uppercase() ?: "GIT"
        project.vcs = if (currentVcs == "GIT") "SUBVERSION" else "GIT"

        repositoryService.getRepository(project).create()

        return projectRepository.save(project)
    }

    override fun getProjectLabels(projectId: Long): Set<Label> {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("프로젝트를 찾을 수 없습니다.") }
        return project.labels
    }

    @Transactional
    override fun attachLabel(projectId: Long, category: String?, name: String): AttachLabelResult {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("프로젝트를 찾을 수 없습니다.") }
        val resolvedCategory = category ?: "Label"

        var label = labelRepository.findByCategoryAndName(resolvedCategory, name).orElse(null)
        val isCreated = label == null
        if (label == null) {
            label = labelRepository.save(Label(category = resolvedCategory, name = name))
        }

        if (project.labels.any { it.id == label.id }) {
            // yona Project.attachLabel(): 이미 붙어있으면 아무 것도 하지 않고 false를 반환한다.
            return AttachLabelResult(label, isCreated, isAttached = false)
        }

        project.labels.add(label)
        projectRepository.save(project)
        return AttachLabelResult(label, isCreated, isAttached = true)
    }

    @Transactional
    override fun detachLabel(projectId: Long, labelId: Long): Boolean {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("프로젝트를 찾을 수 없습니다.") }
        val label = labelRepository.findById(labelId).orElse(null) ?: return false

        project.labels.remove(label)
        projectRepository.save(project)

        // yona Label.delete(project) 이후 Project.detachLabel(): 라벨을 참조하는 프로젝트가
        // 더 이상 없으면(0개) 이 전역 라벨 자체를 삭제한다.
        if (projectRepository.countByLabelsId(labelId) == 0L) {
            labelRepository.delete(label)
        }
        return true
    }
}

