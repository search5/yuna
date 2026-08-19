package com.github.search5.yona.domain.watch

import com.github.search5.yona.AbstractIntegrationTest
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.project.ProjectUser
import com.github.search5.yona.domain.project.ProjectUserRepository
import com.github.search5.yona.domain.role.Role
import com.github.search5.yona.domain.role.RoleRepository
import com.github.search5.yona.domain.role.RoleType
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import com.github.search5.yona.domain.issue.IssueService
import com.github.search5.yona.domain.comment.CommentService
import com.github.search5.yona.domain.notification.NotificationEventRepository
import com.github.search5.yona.domain.enumeration.EventType

@org.springframework.transaction.annotation.Transactional
class WatchServiceSpec @Autowired constructor(
    private val watchService: WatchService,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository,
    private val issueRepository: IssueRepository,
    private val watchRepository: WatchRepository,
    private val unwatchRepository: UnwatchRepository,
    private val issueService: IssueService,
    private val commentService: CommentService,
    private val notificationEventRepository: NotificationEventRepository
) : AbstractIntegrationTest() {

    init {
        describe("WatchService 통합 테스트") {
            // 테스트용 데이터 준비
            lateinit var user1: User
            lateinit var user2: User
            lateinit var user3: User
            lateinit var project: Project
            lateinit var issue: Issue

            beforeEach {
                notificationEventRepository.deleteAll()
                watchRepository.deleteAll()
                unwatchRepository.deleteAll()
                issueRepository.deleteAll()
                projectUserRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()

                user1 = userRepository.save(User(loginId = "user1", name = "사용자1", email = "user1@example.com"))
                user2 = userRepository.save(User(loginId = "user2", name = "사용자2", email = "user2@example.com"))
                user3 = userRepository.save(User(loginId = "user3", name = "사용자3", email = "user3@example.com"))
                // 이 스펙의 기존 시나리오들은 프로젝트 멤버십과 무관한 "감시 매커니즘" 자체를 검증하므로
                // PUBLIC 프로젝트를 사용해 P1-21의 allowedWatchersOnly 권한 필터에 영향받지 않게 한다.
                // 권한 필터 자체는 아래 별도 describe 블록에서 PRIVATE 프로젝트로 명시적으로 검증한다.
                project = projectRepository.save(Project(name = "test-project", owner = "user1", projectScope = ProjectScope.PUBLIC))
                issue = issueRepository.save(
                    Issue(
                        title = "테스트 이슈",
                        body = "내용",
                        project = project,
                        authorId = user1.id,
                        authorLoginId = user1.loginId,
                        authorName = user1.name
                    )
                )
            }

            it("기본 감시 및 감시 취소 기능 동작 검증") {
                // Given
                val resourceType = ResourceType.ISSUE_POST
                val resourceId = issue.id.toString()

                // When & Then - 초기에는 감시하지 않음
                watchService.isWatching(user2, resourceType, resourceId) shouldBe false

                // When - 감시 시작
                watchService.watch(user2, resourceType, resourceId)
                // Then
                watchService.isWatching(user2, resourceType, resourceId) shouldBe true

                // When - 감시 해제
                watchService.unwatch(user2, resourceType, resourceId)
                // Then
                watchService.isWatching(user2, resourceType, resourceId) shouldBe false
            }

            it("프로젝트 감시자와 이슈 감시자를 조합한 실제 감시자(findActualWatchers) 산출 검증") {
                // Given
                val pResource = ResourceType.PROJECT
                val pResourceId = project.id.toString()
                val iResource = ResourceType.ISSUE_POST
                val iResourceId = issue.id.toString()

                // user2 가 프로젝트를 감시함
                watchService.watch(user2, pResource, pResourceId)

                // When & Then - 프로젝트 감시자는 이슈 감시자에 자동으로 포함되어야 함
                val watchers1 = watchService.findActualWatchers(
                    baseWatchers = setOf(user1), // 작성자 user1
                    resourceType = iResource,
                    resourceId = iResourceId,
                    projectId = project.id
                )
                watchers1.map { it.loginId }.toSet() shouldBe setOf("user1", "user2")

                // user2 가 이슈에 대해서는 감시를 해제(unwatch)함
                watchService.unwatch(user2, iResource, iResourceId)

                // Then - 이슈 감시에서 user2 가 제외되어야 함
                val watchers2 = watchService.findActualWatchers(
                    baseWatchers = setOf(user1),
                    resourceType = iResource,
                    resourceId = iResourceId,
                    projectId = project.id
                )
                watchers2.map { it.loginId }.toSet() shouldBe setOf("user1")
            }

            it("이슈 및 댓글 생성 시 알림 대상자(receivers) 계산 통합 연동 검증") {
                // Given
                val pResource = ResourceType.PROJECT
                val pResourceId = project.id.toString()

                // user2 가 프로젝트를 감시함
                watchService.watch(user2, pResource, pResourceId)

                // When - user1 이 신규 이슈 등록
                val newIssue = issueService.createIssue(
                    Issue(
                        title = "신규 TDD 이슈",
                        body = "TDD 내용",
                        project = project
                    ),
                    author = user1,
                    assigneeUser = null,
                    milestoneId = null,
                    labelIds = null
                )

                // Then - 신규 이슈 등록 알림 이벤트의 수신자에 user2(프로젝트 감시자)가 포함되어야 함 (user1 은 작성자이자 발송자이므로 제외)
                val events = notificationEventRepository.findAll()
                val issueEvent = events.find { ev -> ev.eventType == EventType.NEW_ISSUE }
                issueEvent shouldNotBe null
                issueEvent!!.receivers.map { u -> u.loginId }.toSet() shouldBe setOf("user2")

                // Given - user3 이 해당 이슈 감시, user2 는 이슈 감시 해제(unwatch)
                watchService.watch(user3, ResourceType.ISSUE_POST, newIssue.id.toString())
                watchService.unwatch(user2, ResourceType.ISSUE_POST, newIssue.id.toString())

                // When - user1 이 댓글을 작성함
                commentService.createIssueComment(
                    issueId = newIssue.id!!,
                    contents = "새로운 댓글 등록",
                    author = user1,
                    parentCommentId = null
                )

                // Then - 댓글 등록 알림 이벤트의 수신자에 user3만 포함되어야 함 (user2는 unwatch 했고, user1은 발송자이므로 제외)
                val updatedEvents = notificationEventRepository.findAll()
                val commentEvent = updatedEvents.find { ev -> ev.eventType == EventType.NEW_COMMENT }
                commentEvent shouldNotBe null
                commentEvent!!.receivers.map { u -> u.loginId }.toSet() shouldBe setOf("user3")
            }

            describe("allowedWatchersOnly 권한 필터링 (P1-21, yona Watch.findActualWatchers 대응)") {
                it("allowedWatchersOnly=true이면 비공개 프로젝트에 접근 권한이 없는 감시자는 실제 감시자에서 제외되어야 한다") {
                    val managerRole = roleRepository.findById(RoleType.MANAGER.roleType)
                        .orElseGet { roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER")) }

                    val privateProject = projectRepository.save(
                        Project(name = "private-project", owner = "user1", projectScope = ProjectScope.PRIVATE)
                    )
                    projectUserRepository.save(ProjectUser(project = privateProject, user = user1, role = managerRole))

                    val privateIssue = issueRepository.save(
                        Issue(
                            title = "비공개 이슈",
                            body = "내용",
                            project = privateProject,
                            authorId = user1.id,
                            authorLoginId = user1.loginId,
                            authorName = user1.name
                        )
                    )

                    // user2(프로젝트 멤버 아님)와 user3(프로젝트 멤버)이 둘 다 이슈를 감시
                    projectUserRepository.save(ProjectUser(project = privateProject, user = user3, role = managerRole))
                    watchService.watch(user2, ResourceType.ISSUE_POST, privateIssue.id.toString())
                    watchService.watch(user3, ResourceType.ISSUE_POST, privateIssue.id.toString())

                    val watchers = watchService.findActualWatchers(
                        baseWatchers = emptySet(),
                        resourceType = ResourceType.ISSUE_POST,
                        resourceId = privateIssue.id.toString(),
                        projectId = privateProject.id,
                        allowedWatchersOnly = true
                    )

                    watchers.map { it.loginId }.toSet() shouldBe setOf("user3")
                }

                it("allowedWatchersOnly=false이면 권한 필터를 건너뛰고 감시자 전원을 반환해야 한다") {
                    val managerRole = roleRepository.findById(RoleType.MANAGER.roleType)
                        .orElseGet { roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER")) }

                    val privateProject = projectRepository.save(
                        Project(name = "private-project-2", owner = "user1", projectScope = ProjectScope.PRIVATE)
                    )
                    projectUserRepository.save(ProjectUser(project = privateProject, user = user1, role = managerRole))

                    val privateIssue = issueRepository.save(
                        Issue(
                            title = "비공개 이슈2",
                            body = "내용",
                            project = privateProject,
                            authorId = user1.id,
                            authorLoginId = user1.loginId,
                            authorName = user1.name
                        )
                    )

                    watchService.watch(user2, ResourceType.ISSUE_POST, privateIssue.id.toString())

                    val watchers = watchService.findActualWatchers(
                        baseWatchers = emptySet(),
                        resourceType = ResourceType.ISSUE_POST,
                        resourceId = privateIssue.id.toString(),
                        projectId = privateProject.id,
                        allowedWatchersOnly = false
                    )

                    watchers.map { it.loginId }.toSet() shouldBe setOf("user2")
                }
            }
        }
    }
}