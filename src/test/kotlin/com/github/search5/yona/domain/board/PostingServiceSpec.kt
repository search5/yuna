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
import io.kotest.matchers.shouldNotBe
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

            it("본인이 작성한 글을 수정하고 알림 발송 옵션을 선택하지 않으면 알림이 발행되지 않아야 한다(P1-44)") {
                val author = userRepository.save(User(loginId = "writer3", name = "작성자3", email = "writer3@yona.io"))
                val project = projectRepository.save(Project(name = "board-project3", owner = "writer3"))
                val saved = postingService.createPosting(project.id!!, Posting(title = "원본", body = "원본 본문", project = project), author.id!!)
                notificationEventRepository.deleteAll()

                postingService.updatePosting(
                    projectId = project.id!!, number = saved.number!!,
                    title = "수정됨", body = "수정된 본문", notice = false, readme = false,
                    authorId = author.id!!, sendNotificationMail = false
                )

                notificationEventRepository.findAll().size shouldBe 0
            }

            it("본인이 작성한 글을 수정할 때 알림 발송 옵션을 선택하면 알림(POSTING_BODY_CHANGED)이 발행되어야 한다(P1-44)") {
                val author = userRepository.save(User(loginId = "writer4", name = "작성자4", email = "writer4@yona.io"))
                val project = projectRepository.save(Project(name = "board-project4", owner = "writer4"))
                val saved = postingService.createPosting(project.id!!, Posting(title = "원본", body = "원본 본문", project = project), author.id!!)
                notificationEventRepository.deleteAll()

                postingService.updatePosting(
                    projectId = project.id!!, number = saved.number!!,
                    title = "수정됨", body = "수정된 본문", notice = false, readme = false,
                    authorId = author.id!!, sendNotificationMail = true
                )

                val events = notificationEventRepository.findAll()
                events.size shouldBe 1
                events.first().eventType shouldBe EventType.POSTING_BODY_CHANGED
                events.first().oldValue shouldBe "원본 본문"
                events.first().newValue shouldBe "수정된 본문"
            }

            it("본문을 수정하면 변경 이력(history)이 기록되어야 한다(P2-02)") {
                val author = userRepository.save(User(loginId = "writer6", name = "작성자6", email = "writer6@yona.io"))
                val project = projectRepository.save(Project(name = "board-project6", owner = "writer6"))
                val saved = postingService.createPosting(project.id!!, Posting(title = "원본", body = "원본 본문", project = project), author.id!!)
                notificationEventRepository.deleteAll()

                val updated = postingService.updatePosting(
                    projectId = project.id!!, number = saved.number!!,
                    title = "수정됨", body = "수정된 본문", notice = false, readme = false,
                    authorId = author.id!!, sendNotificationMail = false
                )

                updated.history shouldNotBe null
                updated.history!!.contains("history-made-by") shouldBe true
                updated.history!!.contains("작성자6") shouldBe true
            }

            it("본문이 바뀌지 않으면 history를 기록하지 않아야 한다(P2-02)") {
                val author = userRepository.save(User(loginId = "writer7", name = "작성자7", email = "writer7@yona.io"))
                val project = projectRepository.save(Project(name = "board-project7", owner = "writer7"))
                val saved = postingService.createPosting(project.id!!, Posting(title = "원본", body = "동일 본문", project = project), author.id!!)
                notificationEventRepository.deleteAll()

                val updated = postingService.updatePosting(
                    projectId = project.id!!, number = saved.number!!,
                    title = "제목만 수정", body = "동일 본문", notice = false, readme = false,
                    authorId = author.id!!, sendNotificationMail = false
                )

                updated.history shouldBe null
            }

            it("본인이 작성하지 않은 글을 수정하면 알림 발송 옵션과 무관하게 항상 알림이 발행되어야 한다(P1-44)") {
                val author = userRepository.save(User(loginId = "writer5", name = "작성자5", email = "writer5@yona.io"))
                val editor = userRepository.save(User(loginId = "editor1", name = "편집자", email = "editor1@yona.io"))
                val project = projectRepository.save(Project(name = "board-project5", owner = "writer5"))
                val saved = postingService.createPosting(project.id!!, Posting(title = "원본", body = "원본 본문", project = project), author.id!!)
                notificationEventRepository.deleteAll()

                postingService.updatePosting(
                    projectId = project.id!!, number = saved.number!!,
                    title = "수정됨", body = "수정된 본문", notice = false, readme = false,
                    authorId = editor.id!!, sendNotificationMail = false
                )

                val events = notificationEventRepository.findAll()
                events.size shouldBe 1
                events.first().eventType shouldBe EventType.POSTING_BODY_CHANGED
                events.first().senderId shouldBe editor.id
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
