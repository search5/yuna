package com.github.search5.yona.domain.issue

import com.github.search5.yona.AbstractIntegrationTest
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.milestone.Milestone
import com.github.search5.yona.domain.milestone.MilestoneRepository
import com.github.search5.yona.domain.enumeration.State
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Transactional
class IssueRepositorySpec @Autowired constructor(
    private val issueRepository: IssueRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val milestoneRepository: MilestoneRepository
) : AbstractIntegrationTest() {

    init {
        describe("IssueRepository") {
            beforeEach {
                issueRepository.deleteAll()
                milestoneRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
            }

            it("이슈를 정상적으로 저장하고 조회할 수 있어야 한다") {
                // Given
                val author = userRepository.save(
                    User(loginId = "issue-author", name = "작성자", email = "author@yona.io")
                )
                val project = projectRepository.save(
                    Project(name = "issue-project", owner = "issue-owner")
                )
                val milestone = milestoneRepository.save(
                    Milestone(title = "v1.0 배포 마일스톤", project = project)
                )

                val issue = Issue(
                    title = "로그인 실패 버그 해결",
                    body = "특정 상황에서 로그인이 실패하는 현상입니다.",
                    project = project,
                    authorId = author.id,
                    authorLoginId = author.loginId,
                    authorName = author.name,
                    createdDate = Instant.now(),
                    state = State.OPEN,
                    milestone = milestone
                )

                // When
                val savedIssue = issueRepository.save(issue)

                // Then
                savedIssue.id shouldNotBe null
                
                val foundIssue = issueRepository.findById(savedIssue.id!!).orElse(null)
                foundIssue shouldNotBe null
                foundIssue.title shouldBe "로그인 실패 버그 해결"
                foundIssue.milestone?.title shouldBe "v1.0 배포 마일스톤"
                foundIssue.project.name shouldBe "issue-project"
                foundIssue.state shouldBe State.OPEN
            }

            // yona Search.java:112-127 issuesEL()의 "(Project && Keyword) || (Author && Keyword) ||
            // (Assignee && Keyword)" 대응 (P1-81).
            describe("searchIssues (P1-81, 프로젝트 접근권한과 무관한 본인 작성/담당 이슈 노출)") {
                it("접근 불가능한 프로젝트의 이슈라도 본인이 작성했으면 검색돼야 한다") {
                    val author = userRepository.save(
                        User(loginId = "search-author", name = "검색작성자", email = "search-author@yona.io")
                    )
                    val inaccessibleProject = projectRepository.save(
                        Project(name = "inaccessible-project", owner = "someone-else")
                    )
                    issueRepository.save(
                        Issue(
                            title = "권한 없는 프로젝트의 내 이슈", body = "본문", project = inaccessibleProject,
                            authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                            createdDate = Instant.now(), state = State.OPEN
                        )
                    )

                    // 접근 가능한 프로젝트 목록에는 이 프로젝트가 아예 없다.
                    val result = issueRepository.searchIssues(emptyList(), "%권한%", author.id, org.springframework.data.domain.PageRequest.of(0, 20))

                    result.content.size shouldBe 1
                    result.content.first().title shouldBe "권한 없는 프로젝트의 내 이슈"
                }

                it("접근 불가능한 프로젝트의 이슈라도 본인이 담당자면 검색돼야 한다") {
                    val author = userRepository.save(
                        User(loginId = "search-author2", name = "검색작성자2", email = "search-author2@yona.io")
                    )
                    val assigneeUser = userRepository.save(
                        User(loginId = "search-assignee", name = "검색담당자", email = "search-assignee@yona.io")
                    )
                    val inaccessibleProject = projectRepository.save(
                        Project(name = "inaccessible-project2", owner = "someone-else2")
                    )
                    issueRepository.save(
                        Issue(
                            title = "권한 없는 프로젝트의 담당 이슈", body = "본문", project = inaccessibleProject,
                            authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                            createdDate = Instant.now(), state = State.OPEN,
                            assignee = Assignee(user = assigneeUser, project = inaccessibleProject)
                        )
                    )

                    val result = issueRepository.searchIssues(emptyList(), "%담당%", assigneeUser.id, org.springframework.data.domain.PageRequest.of(0, 20))

                    result.content.size shouldBe 1
                    result.content.first().title shouldBe "권한 없는 프로젝트의 담당 이슈"
                }

                it("작성자도 담당자도 아니고 접근 가능한 프로젝트도 아니면 검색되지 않아야 한다") {
                    val author = userRepository.save(
                        User(loginId = "search-author3", name = "검색작성자3", email = "search-author3@yona.io")
                    )
                    val stranger = userRepository.save(
                        User(loginId = "search-stranger", name = "제3자", email = "search-stranger@yona.io")
                    )
                    val inaccessibleProject = projectRepository.save(
                        Project(name = "inaccessible-project3", owner = "someone-else3")
                    )
                    issueRepository.save(
                        Issue(
                            title = "완전히 무관한 이슈", body = "본문", project = inaccessibleProject,
                            authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                            createdDate = Instant.now(), state = State.OPEN
                        )
                    )

                    val result = issueRepository.searchIssues(emptyList(), "%무관%", stranger.id, org.springframework.data.domain.PageRequest.of(0, 20))

                    result.content.size shouldBe 0
                }

                it("userId가 null(비로그인)이면 접근 가능한 프로젝트로만 필터링돼야 한다") {
                    val author = userRepository.save(
                        User(loginId = "search-author4", name = "검색작성자4", email = "search-author4@yona.io")
                    )
                    val inaccessibleProject = projectRepository.save(
                        Project(name = "inaccessible-project4", owner = "someone-else4")
                    )
                    issueRepository.save(
                        Issue(
                            title = "비로그인 검색 테스트 이슈", body = "본문", project = inaccessibleProject,
                            authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                            createdDate = Instant.now(), state = State.OPEN
                        )
                    )

                    val result = issueRepository.searchIssues(emptyList(), "%비로그인%", null, org.springframework.data.domain.PageRequest.of(0, 20))

                    result.content.size shouldBe 0
                }
            }
        }
    }
}
