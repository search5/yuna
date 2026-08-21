package com.github.search5.yona.domain.pullrequest

import com.github.search5.yona.AbstractIntegrationTest
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.enumeration.State
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Transactional
class PullRequestRepositorySpec @Autowired constructor(
    private val pullRequestRepository: PullRequestRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    init {
        describe("PullRequestRepository") {
            beforeEach {
                pullRequestRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
            }

            it("풀 리퀘스트를 정상적으로 생성하고 조회할 수 있어야 한다") {
                // Given
                val contributor = userRepository.save(
                    User(loginId = "contrib", name = "기여자", email = "contrib@yona.io")
                )
                val receiver = userRepository.save(
                    User(loginId = "receive", name = "수신자", email = "receive@yona.io")
                )

                val projectA = projectRepository.save(
                    Project(name = "repo-a", owner = "owner-a")
                )
                val projectB = projectRepository.save(
                    Project(name = "repo-b", owner = "owner-b")
                )

                val pr = PullRequest(
                    title = "기능 개선 PR",
                    body = "새로운 기능을 추가했습니다.",
                    toProject = projectA,
                    fromProject = projectB,
                    toBranch = "main",
                    fromBranch = "feature-x",
                    contributor = contributor,
                    receiver = receiver,
                    created = Instant.now(),
                    state = State.OPEN
                )

                // When
                val savedPr = pullRequestRepository.save(pr)

                // Then
                savedPr.id shouldNotBe null
                
                val foundPr = pullRequestRepository.findById(savedPr.id!!).orElse(null)
                foundPr shouldNotBe null
                foundPr.title shouldBe "기능 개선 PR"
                foundPr.toProject.name shouldBe "repo-a"
                foundPr.fromProject.name shouldBe "repo-b"
                foundPr.contributor.loginId shouldBe "contrib"
                foundPr.state shouldBe State.OPEN
            }

            // yuna 자체 버그(P1-115): JPQL `A OR B AND C`는 `A OR (B AND C)`로 파싱되어 AND가 OR의
            // 두 번째 항에만 걸린다. `pr.state NOT IN (...)`가 toProject/toBranch 쪽에만 적용되고
            // fromProject/fromBranch 쪽은 상태와 무관하게 매칭돼, CLOSED/MERGED PR도 브랜치 삭제
            // 처리 대상(cleanupPullRequestsForDeletedBranches)에 잘못 포함될 수 있었다.
            it("findRelatedPullRequests는 fromProject/fromBranch로 매칭되는 CLOSED/MERGED PR을 제외해야 한다") {
                val contributor = userRepository.save(
                    User(loginId = "contrib2", name = "기여자2", email = "contrib2@yona.io")
                )
                val receiver = userRepository.save(
                    User(loginId = "receive2", name = "수신자2", email = "receive2@yona.io")
                )
                val fromProject = projectRepository.save(Project(name = "from-repo", owner = "owner-from"))
                val toProject = projectRepository.save(Project(name = "to-repo", owner = "owner-to"))

                val closedFromBranchPr = pullRequestRepository.save(
                    PullRequest(
                        title = "닫힌 PR(from 매칭)",
                        body = "closed",
                        toProject = toProject,
                        fromProject = fromProject,
                        toBranch = "main",
                        fromBranch = "feature-closed",
                        contributor = contributor,
                        receiver = receiver,
                        created = Instant.now(),
                        state = State.CLOSED
                    )
                )
                val openFromBranchPr = pullRequestRepository.save(
                    PullRequest(
                        title = "열린 PR(from 매칭)",
                        body = "open",
                        toProject = toProject,
                        fromProject = fromProject,
                        toBranch = "main",
                        fromBranch = "feature-closed",
                        contributor = contributor,
                        receiver = receiver,
                        created = Instant.now(),
                        state = State.OPEN
                    )
                )

                val related = pullRequestRepository.findRelatedPullRequests(fromProject, "feature-closed")

                related.map { it.id } shouldBe listOf(openFromBranchPr.id)
                related.map { it.id } shouldNotContain closedFromBranchPr.id
            }
        }
    }
}
