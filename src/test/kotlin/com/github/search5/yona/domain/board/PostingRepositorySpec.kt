package com.github.search5.yona.domain.board

import com.github.search5.yona.AbstractIntegrationTest
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Transactional
class PostingRepositorySpec @Autowired constructor(
    private val postingRepository: PostingRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    init {
        describe("PostingRepository") {
            beforeEach {
                postingRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
            }

            it("게시판 포스팅을 정상적으로 저장하고 조회할 수 있어야 한다") {
                // Given
                val author = userRepository.save(
                    User(loginId = "posting-author", name = "작성자", email = "author@yona.io")
                )
                val project = projectRepository.save(
                    Project(name = "board-project", owner = "board-owner")
                )

                val posting = Posting(
                    title = "프로젝트 첫 공지사항",
                    body = "팀원 여러분 반갑습니다.",
                    project = project,
                    authorId = author.id,
                    authorLoginId = author.loginId,
                    authorName = author.name,
                    createdDate = Instant.now(),
                    notice = true
                )

                // When
                val savedPosting = postingRepository.save(posting)

                // Then
                savedPosting.id shouldNotBe null
                
                val foundPosting = postingRepository.findById(savedPosting.id!!).orElse(null)
                foundPosting shouldNotBe null
                foundPosting.title shouldBe "프로젝트 첫 공지사항"
                foundPosting.notice shouldBe true
                foundPosting.project.name shouldBe "board-project"
            }
        }
    }
}
