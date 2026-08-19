package com.github.search5.yona.domain.board

import com.github.search5.yona.AbstractIntegrationTest
import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.notification.NotificationEventRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

// yona BoardApp.newPost/deletePost 대응 (P1-18): 게시글 생성/삭제 시 알림 미발송 문제 검증
@Transactional
class PostingServiceSpec @Autowired constructor(
    private val postingService: PostingService,
    private val postingRepository: PostingRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val notificationEventRepository: NotificationEventRepository
) : AbstractIntegrationTest() {

    init {
        describe("PostingService 알림 발행 테스트 (P1-18)") {
            beforeEach {
                notificationEventRepository.deleteAll()
                postingRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
            }

            it("게시글을 새로 작성하면 신규 게시글(NEW_POSTING) 알림 이벤트가 발행되어야 한다") {
                val author = userRepository.save(User(loginId = "writer", name = "작성자", email = "writer@yona.io"))
                val project = projectRepository.save(Project(name = "board-project", owner = "writer"))

                val posting = Posting(
                    title = "공지사항입니다",
                    body = "본문입니다",
                    project = project
                )

                val saved = postingService.createPosting(project.id!!, posting, author.id!!)

                val events = notificationEventRepository.findAll()
                events.size shouldBe 1
                val event = events.first()
                event.eventType shouldBe EventType.NEW_POSTING
                event.resourceType shouldBe ResourceType.BOARD_POST
                event.resourceId shouldBe saved.id.toString()
                event.senderId shouldBe author.id
            }

            it("게시글을 삭제하면 삭제(RESOURCE_DELETED) 알림 이벤트가 발행되어야 한다") {
                val author = userRepository.save(User(loginId = "writer2", name = "작성자2", email = "writer2@yona.io"))
                val project = projectRepository.save(Project(name = "board-project2", owner = "writer2"))

                val posting = Posting(title = "삭제될 글", body = "본문", project = project)
                val saved = postingService.createPosting(project.id!!, posting, author.id!!)
                notificationEventRepository.deleteAll()

                postingService.deletePosting(project.id!!, saved.number!!, author.id!!)

                val events = notificationEventRepository.findAll()
                events.size shouldBe 1
                val event = events.first()
                event.eventType shouldBe EventType.RESOURCE_DELETED
                event.resourceType shouldBe ResourceType.BOARD_POST
                event.resourceId shouldBe saved.id.toString()
                event.senderId shouldBe author.id
            }
        }
    }
}
