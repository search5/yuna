package com.github.search5.yona.domain.event

import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.issue.Issue
import com.github.search5.yona.domain.issue.IssueEvent
import com.github.search5.yona.domain.issue.IssueEventRepository
import com.github.search5.yona.domain.issue.IssueRepository
import com.github.search5.yona.domain.notification.NotificationEvent
import com.github.search5.yona.domain.notification.NotificationEventRepository
import com.github.search5.yona.domain.project.GitService
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.user.User
import com.github.search5.yona.domain.watch.WatchService
import com.github.search5.yona.domain.webhook.WebhookService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository
import org.eclipse.jgit.lib.CommitBuilder
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.TreeFormatter
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk

private fun testCommit(message: String): RevCommit {
    val repo = InMemoryRepository(DfsRepositoryDescription())
    val inserter = repo.newObjectInserter()
    val blobId = inserter.insert(Constants.OBJ_BLOB, "content".toByteArray())
    val tree = TreeFormatter()
    tree.append("file.txt", FileMode.REGULAR_FILE, blobId)
    val treeId = inserter.insert(tree)
    val commitBuilder = CommitBuilder()
    commitBuilder.setTreeId(treeId)
    val ident = PersonIdent("tester", "tester@yona.io")
    commitBuilder.author = ident
    commitBuilder.committer = ident
    commitBuilder.message = message
    val commitId = inserter.insert(commitBuilder)
    inserter.flush()
    return RevWalk(repo).use { it.parseCommit(commitId) }
}

class GitPostReceiveEventListenerSpec : DescribeSpec({
    val gitService = mockk<GitService>()
    val notificationEventRepository = mockk<NotificationEventRepository>(relaxed = true)
    val issueRepository = mockk<IssueRepository>()
    val issueEventRepository = mockk<IssueEventRepository>()
    val webhookService = mockk<WebhookService>(relaxed = true)
    val watchService = mockk<WatchService>()
    val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    val listener = GitPostReceiveEventListener(
        gitService, notificationEventRepository, issueRepository, issueEventRepository, webhookService,
        watchService, eventPublisher
    )

    val project = Project(id = 1L, name = "yona-project", owner = "gildong")
    val sender = User(id = 9L, loginId = "gildong", name = "길동", email = "gildong@example.com")

    beforeTest {
        io.mockk.clearMocks(issueRepository, issueEventRepository, webhookService, notificationEventRepository, watchService, eventPublisher, answers = false)
        every {
            watchService.findActualWatchers(
                baseWatchers = emptySet(),
                resourceType = com.github.search5.yona.domain.enumeration.ResourceType.PROJECT,
                resourceId = "1",
                projectId = 1L,
                eventType = EventType.NEW_COMMIT
            )
        } returns emptySet()
    }

    describe("GitPostReceiveEventListener.recordReferredIssues") {
        it("커밋 메시지가 언급한 이슈가 프로젝트에 존재하면 IssueEvent를 기록해야 한다") {
            val issue = Issue(id = 100L, title = "버그", body = "...", project = project, number = 42L)
            every { issueRepository.findByProjectAndNumber(project, 42L) } returns issue
            val savedSlot = slot<IssueEvent>()
            every { issueEventRepository.save(capture(savedSlot)) } answers { firstArg() }

            listener.recordReferredIssues("fix #42 bug", "abc123commit", project, sender)

            savedSlot.captured.issue shouldBe issue
            savedSlot.captured.senderLoginId shouldBe "gildong"
            savedSlot.captured.senderEmail shouldBe "gildong@example.com"
            savedSlot.captured.newValue shouldBe "abc123commit"
            savedSlot.captured.eventType shouldBe com.github.search5.yona.domain.enumeration.EventType.ISSUE_REFERRED_FROM_COMMIT
        }

        it("언급된 이슈 번호가 프로젝트에 존재하지 않으면 조용히 스킵해야 한다") {
            every { issueRepository.findByProjectAndNumber(project, 999L) } returns null

            listener.recordReferredIssues("close #999", "def456", project, sender)

            verify(exactly = 0) { issueEventRepository.save(any()) }
        }

        it("커밋 메시지에 이슈 참조가 없으면 아무 것도 저장하지 않아야 한다") {
            listener.recordReferredIssues("just a regular commit", "ghi789", project, sender)

            verify(exactly = 0) { issueRepository.findByProjectAndNumber(any(), any()) }
            verify(exactly = 0) { issueEventRepository.save(any()) }
        }

        it("커밋 메시지에 여러 이슈가 언급되면 각각에 대해 IssueEvent를 기록해야 한다") {
            val issue1 = Issue(id = 1L, title = "A", body = "", project = project, number = 1L)
            val issue2 = Issue(id = 2L, title = "B", body = "", project = project, number = 2L)
            every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue1
            every { issueRepository.findByProjectAndNumber(project, 2L) } returns issue2
            every { issueEventRepository.save(any()) } answers { firstArg() }

            listener.recordReferredIssues("fixes #1 and #2", "jkl012", project, sender)

            verify(exactly = 2) { issueEventRepository.save(any()) }
        }
    }

    describe("GitPostReceiveEventListener.processCommitsNotification (P1-25, yona Webhook.sendRequestToPayloadUrl(commits,...) 대응)") {
        it("push된 커밋이 있으면 NotificationEvent를 저장하고 NEW_COMMIT 웹훅을 발송해야 한다") {
            val commit = testCommit("fix bug")
            val savedNotification = slot<NotificationEvent>()
            every { notificationEventRepository.save(capture(savedNotification)) } answers { firstArg() }

            listener.processCommitsNotification(listOf(commit), listOf("refs/heads/master"), project, sender)

            savedNotification.captured.eventType shouldBe EventType.NEW_COMMIT
            savedNotification.captured.senderId shouldBe sender.id

            verify(exactly = 1) {
                webhookService.sendWebhook(project, EventType.NEW_COMMIT, sender, any())
            }
        }

        it("push된 커밋이 없으면 알림도 웹훅도 발생시키지 않아야 한다") {
            listener.processCommitsNotification(emptyList(), listOf("refs/heads/master"), project, sender)

            verify(exactly = 0) { notificationEventRepository.save(any()) }
            verify(exactly = 0) { webhookService.sendWebhook(any(), any(), any(), any()) }
        }

        it("push된 커밋이 있으면 프로젝트 워처를 수신자로 계산해 NotificationEvent를 publish해야 한다 (P1-46)") {
            val watcher = User(id = 20L, loginId = "watcher1", name = "워처")
            every {
                watchService.findActualWatchers(
                    baseWatchers = emptySet(),
                    resourceType = com.github.search5.yona.domain.enumeration.ResourceType.PROJECT,
                    resourceId = "1",
                    projectId = 1L,
                    eventType = EventType.NEW_COMMIT
                )
            } returns setOf(watcher, sender)

            val commit = testCommit("fix bug")
            val savedNotification = slot<NotificationEvent>()
            every { notificationEventRepository.save(capture(savedNotification)) } answers { firstArg() }

            listener.processCommitsNotification(listOf(commit), listOf("refs/heads/master"), project, sender)

            // 워처 목록에 발신자 본인이 섞여 있어도 자기 자신에게는 알림을 보내지 않는다(기존 다른 리스너들과 동일한 관례)
            savedNotification.captured.receivers shouldBe setOf(watcher)
            verify(exactly = 1) { eventPublisher.publishEvent(savedNotification.captured) }
        }
    }
})
