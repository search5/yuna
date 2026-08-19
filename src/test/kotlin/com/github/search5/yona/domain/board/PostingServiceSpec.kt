package com.github.search5.yona.domain.board

import com.github.search5.yona.AbstractIntegrationTest
import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.notification.NotificationEventRepository
import com.github.search5.yona.domain.notification.NotificationMailRepository
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.project.ProjectScope
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.user.UserRepository
import com.github.search5.yona.domain.watch.Watch
import com.github.search5.yona.domain.watch.WatchRepository
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
    private val notificationEventRepository: NotificationEventRepository,
    private val notificationMailRepository: NotificationMailRepository,
    private val watchRepository: WatchRepository
) : AbstractIntegrationTest() {

    init {
        describe("PostingService 알림 발행 테스트 (P1-18)") {
            // NotificationEventRecorder(P1-27)가 저장 시 NotificationMail 마커도 함께 만들므로
            // (OneToOne), notification_event를 지우기 전에 반드시 notification_mail을 먼저 지워야
            // FK/영속성 컨텍스트 불일치가 나지 않는다.
            fun resetNotifications() {
                notificationMailRepository.deleteAll()
                notificationEventRepository.deleteAll()
            }

            beforeEach {
                watchRepository.deleteAll()
                resetNotifications()
                postingRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
            }

            it("게시글을 새로 작성하면 신규 게시글(NEW_POSTING) 알림 이벤트가 발행되어야 한다") {
                val author = userRepository.save(User(loginId = "writer", name = "작성자", email = "writer@yona.io"))
                val project = projectRepository.save(Project(name = "board-project", owner = "writer", projectScope = ProjectScope.PUBLIC))
                // NotificationEventRecorder(P1-27)는 legacy NotificationEvent.add()와 동일하게 수신자가
                // 없으면 저장하지 않으므로(receivers.isEmpty()), 작성자 본인 외에 실제로 알림을 받을
                // 프로젝트 감시자를 한 명 둔다.
                val watcher = userRepository.save(User(loginId = "watcher", name = "감시자", email = "watcher@yona.io"))
                watchRepository.save(Watch(user = watcher, resourceType = ResourceType.PROJECT, resourceId = project.id.toString()))

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
                resetNotifications()

                postingService.updatePosting(
                    projectId = project.id!!, number = saved.number!!,
                    title = "수정됨", body = "수정된 본문", notice = false, readme = false,
                    authorId = author.id!!, sendNotificationMail = false
                )

                notificationEventRepository.findAll().size shouldBe 0
            }

            it("본인이 작성한 글을 수정할 때 알림 발송 옵션을 선택하면 알림(POSTING_BODY_CHANGED)이 발행되어야 한다(P1-44)") {
                val author = userRepository.save(User(loginId = "writer4", name = "작성자4", email = "writer4@yona.io"))
                val project = projectRepository.save(Project(name = "board-project4", owner = "writer4", projectScope = ProjectScope.PUBLIC))
                val watcher = userRepository.save(User(loginId = "watcher4", name = "감시자4", email = "watcher4@yona.io"))
                watchRepository.save(Watch(user = watcher, resourceType = ResourceType.PROJECT, resourceId = project.id.toString()))
                val saved = postingService.createPosting(project.id!!, Posting(title = "원본", body = "원본 본문", project = project), author.id!!)
                resetNotifications()

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
                resetNotifications()

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
                resetNotifications()

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
                val project = projectRepository.save(Project(name = "board-project5", owner = "writer5", projectScope = ProjectScope.PUBLIC))
                // 편집자(=수신자에서 제외되는 본인)가 아닌 실제 수신자가 될 프로젝트 감시자를 둔다.
                val watcher = userRepository.save(User(loginId = "watcher5", name = "감시자5", email = "watcher5@yona.io"))
                watchRepository.save(Watch(user = watcher, resourceType = ResourceType.PROJECT, resourceId = project.id.toString()))
                val saved = postingService.createPosting(project.id!!, Posting(title = "원본", body = "원본 본문", project = project), author.id!!)
                resetNotifications()

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
                val project = projectRepository.save(Project(name = "board-project2", owner = "writer2", projectScope = ProjectScope.PUBLIC))
                val watcher = userRepository.save(User(loginId = "watcher2", name = "감시자2", email = "watcher2@yona.io"))
                watchRepository.save(Watch(user = watcher, resourceType = ResourceType.PROJECT, resourceId = project.id.toString()))

                val posting = Posting(title = "삭제될 글", body = "본문", project = project)
                val saved = postingService.createPosting(project.id!!, posting, author.id!!)
                resetNotifications()

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
