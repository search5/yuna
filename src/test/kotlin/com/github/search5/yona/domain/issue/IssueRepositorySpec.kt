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
        }
    }
}
