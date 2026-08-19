package com.github.search5.yona.domain.webhook

import com.github.search5.yona.domain.enumeration.EventType
import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.enumeration.WebhookType
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.project.ProjectRepository
import com.github.search5.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository
import org.eclipse.jgit.lib.CommitBuilder
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.TreeFormatter
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import java.util.Optional

private fun testCommit(message: String, authorName: String = "tester", authorEmail: String = "tester@yona.io"): RevCommit {
    val repo = InMemoryRepository(DfsRepositoryDescription())
    val inserter = repo.newObjectInserter()
    val blobId = inserter.insert(Constants.OBJ_BLOB, "content".toByteArray())
    val tree = TreeFormatter()
    tree.append("file.txt", FileMode.REGULAR_FILE, blobId)
    val treeId = inserter.insert(tree)
    val commitBuilder = CommitBuilder()
    commitBuilder.setTreeId(treeId)
    val ident = PersonIdent(authorName, authorEmail)
    commitBuilder.author = ident
    commitBuilder.committer = ident
    commitBuilder.message = message
    val commitId = inserter.insert(commitBuilder)
    inserter.flush()
    return RevWalk(repo).use { it.parseCommit(commitId) }
}

class WebhookServiceSpec : DescribeSpec({
    val webhookRepository = mockk<WebhookRepository>()
    val webhookThreadRepository = mockk<WebhookThreadRepository>()
    val projectRepository = mockk<ProjectRepository>()

    val webhookService = WebhookServiceImpl(webhookRepository, webhookThreadRepository)

    beforeTest {
        io.mockk.clearMocks(webhookRepository, webhookThreadRepository, projectRepository)
    }

    describe("WebhookService 비즈니스 테스트") {
        val project = Project(id = 1L, name = "test-project", owner = "owner")
        val webhook = Webhook(
            id = 10L,
            project = project,
            payloadUrl = "http://localhost:8080/hook",
            secret = "mysecret",
            gitPush = true,
            webhookType = WebhookType.SIMPLE
        )

        describe("createWebhook") {
            it("전달된 정보로 Webhook 엔티티를 생성하고 저장한다") {
                every { webhookRepository.save(any()) } returns webhook

                val created = webhookService.createWebhook(
                    project = project,
                    payloadUrl = "http://localhost:8080/hook",
                    secret = "mysecret",
                    gitPush = true,
                    webhookType = WebhookType.SIMPLE
                )

                created.payloadUrl shouldBe "http://localhost:8080/hook"
                created.secret shouldBe "mysecret"
                created.gitPush shouldBe true
                verify(exactly = 1) { webhookRepository.save(any()) }
            }
        }

        describe("deleteWebhook") {
            it("주어진 ID의 Webhook 엔티티를 레포지토리에서 삭제한다") {
                every { webhookRepository.findById(10L) } returns Optional.of(webhook)
                every { webhookRepository.delete(webhook) } returns Unit

                webhookService.deleteWebhook(10L)

                verify(exactly = 1) { webhookRepository.delete(webhook) }
            }
        }

        describe("findByProject") {
            it("프로젝트 ID에 해당하는 등록된 웹훅 목록을 반환한다") {
                every { webhookRepository.findByProjectId(1L) } returns listOf(webhook)

                val list = webhookService.findByProject(1L)

                list.size shouldBe 1
                list[0].payloadUrl shouldBe "http://localhost:8080/hook"
            }
        }

        describe("sendWebhook") {
            it("프로젝트에 등록된 웹훅을 찾아 이벤트를 전송한다") {
                every { webhookRepository.findByProjectId(1L) } returns listOf(webhook)
                
                // mock eventUser
                val sender = User(id = 2L, loginId = "sender", name = "송신자")
                val issue = com.github.search5.yona.domain.issue.Issue(
                    id = 100L,
                    title = "웹훅 테스트 이슈",
                    body = "내용",
                    project = project,
                    number = 10,
                    authorId = sender.id,
                    authorLoginId = sender.loginId,
                    authorName = sender.name
                )

                // sendWebhook 호출 시 내부적으로 repository 조회 및 http 발송 처리가 일어남.
                // 비동기로 HttpClient 호출이 전개되나, Mocking 환경 하에서 예외 없이 로직이 흘러가는지 검증.
                webhookService.sendWebhook(
                    project = project,
                    eventType = com.github.search5.yona.domain.enumeration.EventType.NEW_ISSUE,
                    sender = sender,
                    resource = issue
                )

                verify(exactly = 1) { webhookRepository.findByProjectId(1L) }
            }
        }

        describe("shouldDeliverToWebhook (gitPush 필터 정책)") {
            fun webhookOf(gitPush: Boolean, type: WebhookType) = Webhook(
                id = 20L, project = project, payloadUrl = "http://localhost:8080/hook",
                gitPush = gitPush, webhookType = type
            )

            it("push(NEW_COMMIT)가 아닌 이벤트는 gitPush 설정과 무관하게 항상 전송되어야 한다") {
                val gitPushOnlyWebhook = webhookOf(gitPush = true, type = WebhookType.SIMPLE)

                webhookService.shouldDeliverToWebhook(gitPushOnlyWebhook, EventType.NEW_ISSUE) shouldBe true
                webhookService.shouldDeliverToWebhook(gitPushOnlyWebhook, EventType.NEW_COMMENT) shouldBe true
                webhookService.shouldDeliverToWebhook(gitPushOnlyWebhook, EventType.NEW_POSTING) shouldBe true
                webhookService.shouldDeliverToWebhook(gitPushOnlyWebhook, EventType.NEW_PULL_REQUEST) shouldBe true
            }

            it("gitPush=false인 SIMPLE/SLACK 웹훅은 NEW_COMMIT 이벤트를 받지 않아야 한다") {
                val noGitPushWebhook = webhookOf(gitPush = false, type = WebhookType.SIMPLE)

                webhookService.shouldDeliverToWebhook(noGitPushWebhook, EventType.NEW_COMMIT) shouldBe false
            }

            it("gitPush=true인 웹훅은 NEW_COMMIT 이벤트를 받아야 한다") {
                val gitPushWebhook = webhookOf(gitPush = true, type = WebhookType.SIMPLE)

                webhookService.shouldDeliverToWebhook(gitPushWebhook, EventType.NEW_COMMIT) shouldBe true
            }

            it("JSON 포맷 웹훅은 gitPush 설정과 무관하게 NEW_COMMIT 이벤트를 항상 받아야 한다") {
                val jsonWebhookNoGitPush = webhookOf(gitPush = false, type = WebhookType.JSON)

                webhookService.shouldDeliverToWebhook(jsonWebhookNoGitPush, EventType.NEW_COMMIT) shouldBe true
            }
        }

        // yona Webhook.java의 push용 buildRequestBody(commits, refNames, sender) 대응 (P2-04).
        describe("buildPayload - JSON 포맷 push 페이로드에 커밋 목록이 포함돼야 한다") {
            it("PushedCommits 리소스면 ref/commits/head_commit/sender/pusher/repository를 포함해야 한다") {
                val jsonWebhook = Webhook(
                    id = 30L, project = project, payloadUrl = "http://localhost:8080/hook",
                    gitPush = true, webhookType = WebhookType.JSON
                )
                val sender = User(id = 5L, loginId = "gildong", name = "홍길동", email = "gildong@yona.io")
                val commit = testCommit("fix bug", authorName = "gildong", authorEmail = "gildong@yona.io")
                val pushed = PushedCommits(listOf(commit), listOf("refs/heads/master"))

                val payload = webhookService.buildPayload(jsonWebhook, EventType.NEW_COMMIT, sender, pushed)
                val json = tools.jackson.databind.ObjectMapper().readTree(payload)

                json.get("ref").get(0).asText() shouldBe "refs/heads/master"
                json.get("commits").size() shouldBe 1
                json.get("commits").get(0).get("id").asText() shouldBe commit.name
                json.get("commits").get(0).get("message").asText() shouldBe "fix bug"
                json.get("commits").get(0).get("author").get("name").asText() shouldBe "gildong"
                json.get("head_commit").get("id").asText() shouldBe commit.name
                json.get("sender").get("login").asText() shouldBe "gildong"
                json.get("pusher").get("email").asText() shouldBe "gildong@yona.io"
                json.get("repository").get("name").asText() shouldBe "test-project"
            }
        }
    }
})
