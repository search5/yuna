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
    // relaxed=true: 대부분의 테스트는 리소스 링크와 무관하므로 stub하지 않은 호출은 null(링크 없음)로
    // 흘러가게 두고, 링크 자체를 검증하는 테스트에서만 개별적으로 every {}를 재정의한다.
    val notificationUrlResolver = mockk<com.github.search5.yona.domain.notification.NotificationUrlResolver>(relaxed = true)
    val webhookThreadRecorder = mockk<WebhookThreadRecorder>(relaxed = true)

    val webhookService = WebhookServiceImpl(
        webhookRepository, webhookThreadRepository, "https://yona.example.com", notificationUrlResolver, webhookThreadRecorder
    )

    beforeTest {
        io.mockk.clearMocks(webhookRepository, webhookThreadRepository, projectRepository, notificationUrlResolver, webhookThreadRecorder)
    }

    describe("WebhookService 비즈니스 테스트") {
        val project = Project(id = 1L, name = "test-project", owner = "owner", overview = "테스트 프로젝트 설명")
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

            // yona Webhook.java의 buildSenderJSON()/buildRepositoryJSON()/buildJSONFromCommit() 필드
            // 4곳(site_admin/overview/절대 URL/timestamp 포맷) 대응 (P2-08).
            it("sender.site_admin/repository.overview/절대 URL/timestamp 포맷까지 legacy와 일치해야 한다") {
                val jsonWebhook = Webhook(
                    id = 31L, project = project, payloadUrl = "http://localhost:8080/hook",
                    gitPush = true, webhookType = WebhookType.JSON
                )
                val siteAdminSender = User(
                    id = 6L, loginId = "admin", name = "관리자", email = "admin@yona.io",
                    state = com.github.search5.yona.domain.user.UserState.SITE_ADMIN
                )
                val commit = testCommit("admin commit", authorName = "admin", authorEmail = "admin@yona.io")
                val pushed = PushedCommits(listOf(commit), listOf("refs/heads/master"))

                val payload = webhookService.buildPayload(jsonWebhook, EventType.NEW_COMMIT, siteAdminSender, pushed)
                val json = tools.jackson.databind.ObjectMapper().readTree(payload)

                json.get("sender").get("site_admin").asBoolean() shouldBe true
                json.get("repository").get("overview").asText() shouldBe "테스트 프로젝트 설명"
                json.get("repository").get("html_url").asText() shouldBe "https://yona.example.com/owner/test-project"
                json.get("commits").get(0).get("url").asText() shouldBe
                    "https://yona.example.com/owner/test-project/commit/${commit.name}"

                val expectedTimestamp = commit.authorIdent.`when`.toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'hh:mm:ssZ"))
                json.get("commits").get(0).get("timestamp").asText() shouldBe expectedTimestamp
            }

            it("일반 사용자가 push하면 sender.site_admin은 false여야 한다") {
                val jsonWebhook = Webhook(
                    id = 32L, project = project, payloadUrl = "http://localhost:8080/hook",
                    gitPush = true, webhookType = WebhookType.JSON
                )
                val normalSender = User(id = 7L, loginId = "gildong", name = "홍길동", email = "gildong@yona.io")
                val commit = testCommit("normal commit")
                val pushed = PushedCommits(listOf(commit), listOf("refs/heads/master"))

                val payload = webhookService.buildPayload(jsonWebhook, EventType.NEW_COMMIT, normalSender, pushed)
                val json = tools.jackson.databind.ObjectMapper().readTree(payload)

                json.get("sender").get("site_admin").asBoolean() shouldBe false
            }
        }

        // yona Webhook.java:182-192 buildRequestMessage() 대응 (P1-132) — 텍스트 메시지에 리소스 링크가
        // 전혀 없던 것을 Slack 링크 문법(" <url|text>")으로 붙이도록 수정.
        describe("buildPayload - 텍스트 메시지 리소스 링크 (P1-132)") {
            it("SIMPLE 웹훅은 텍스트 메시지 끝에 이슈 링크를 붙여야 한다") {
                val simpleWebhook = Webhook(
                    id = 40L, project = project, payloadUrl = "http://localhost:8080/hook",
                    gitPush = true, webhookType = WebhookType.SIMPLE
                )
                val sender = User(id = 2L, loginId = "sender", name = "송신자")
                val issue = com.github.search5.yona.domain.issue.Issue(
                    id = 100L, title = "웹훅 테스트 이슈", body = "내용", project = project, number = 10
                )
                every {
                    notificationUrlResolver.getUrl(ResourceType.ISSUE_POST, "100")
                } returns "https://yona.example.com/owner/test-project/issue/10"

                val payload = webhookService.buildPayload(simpleWebhook, EventType.NEW_ISSUE, sender, issue)
                val json = tools.jackson.databind.ObjectMapper().readTree(payload)

                json.get("text").asText() shouldBe
                    "[test-project] 송신자님이 새 이슈를 등록했습니다. <https://yona.example.com/owner/test-project/issue/10|#10: 웹훅 테스트 이슈>"
            }

            it("DETAIL_SLACK 웹훅은 링크 텍스트의 '>'를 '&gt;'로 이스케이프해야 한다") {
                val slackWebhook = Webhook(
                    id = 41L, project = project, payloadUrl = "http://localhost:8080/hook",
                    gitPush = true, webhookType = WebhookType.DETAIL_SLACK
                )
                val sender = User(id = 2L, loginId = "sender", name = "송신자")
                val issue = com.github.search5.yona.domain.issue.Issue(
                    id = 101L, title = "A > B 비교", body = "내용", project = project, number = 11
                )
                every {
                    notificationUrlResolver.getUrl(ResourceType.ISSUE_POST, "101")
                } returns "https://yona.example.com/owner/test-project/issue/11"

                val payload = webhookService.buildPayload(slackWebhook, EventType.NEW_ISSUE, sender, issue)
                val json = tools.jackson.databind.ObjectMapper().readTree(payload)

                json.get("text").asText() shouldBe
                    "[test-project] 송신자님이 새 이슈를 등록했습니다. <https://yona.example.com/owner/test-project/issue/11|#11: A &gt; B 비교>"
            }

            it("이슈 댓글은 댓글 자신이 아니라 부모 이슈의 #번호: 제목을 링크 텍스트로 써야 한다") {
                val simpleWebhook = Webhook(
                    id = 42L, project = project, payloadUrl = "http://localhost:8080/hook",
                    gitPush = true, webhookType = WebhookType.SIMPLE
                )
                val sender = User(id = 2L, loginId = "sender", name = "송신자")
                val parentIssue = com.github.search5.yona.domain.issue.Issue(
                    id = 200L, title = "부모 이슈", body = "내용", project = project, number = 20
                )
                val comment = com.github.search5.yona.domain.issue.IssueComment(
                    id = 300L, contents = "댓글 내용입니다", issue = parentIssue
                )
                every {
                    notificationUrlResolver.getUrl(ResourceType.ISSUE_COMMENT, "300")
                } returns "https://yona.example.com/owner/test-project/issue/20#comment-300"

                val payload = webhookService.buildPayload(simpleWebhook, EventType.NEW_COMMENT, sender, comment)
                val json = tools.jackson.databind.ObjectMapper().readTree(payload)

                json.get("text").asText() shouldBe
                    "[test-project] 송신자님이 새 댓글을 등록했습니다. <https://yona.example.com/owner/test-project/issue/20#comment-300|#20: 부모 이슈>"
            }

            it("리소스 URL을 찾지 못하면 링크 없이 본문 텍스트만 반환해야 한다") {
                val simpleWebhook = Webhook(
                    id = 43L, project = project, payloadUrl = "http://localhost:8080/hook",
                    gitPush = true, webhookType = WebhookType.SIMPLE
                )
                val sender = User(id = 2L, loginId = "sender", name = "송신자")
                val issue = com.github.search5.yona.domain.issue.Issue(
                    id = 102L, title = "삭제된 이슈", body = "내용", project = project, number = 12
                )
                every { notificationUrlResolver.getUrl(ResourceType.ISSUE_POST, "102") } returns null

                val payload = webhookService.buildPayload(simpleWebhook, EventType.NEW_ISSUE, sender, issue)
                val json = tools.jackson.databind.ObjectMapper().readTree(payload)

                json.get("text").asText() shouldBe "[test-project] 송신자님이 새 이슈를 등록했습니다."
            }
        }

        // yona Webhook.java:284-298 buildIssueDetails() / :502-515 buildJsonWithPullReqtuestDetails() 대응
        // (P1-133) — DETAIL_SLACK attachment의 이슈 필드(마일스톤/담당자/상태)가 "State" 하나로 축소돼
        // 있었고, PR attachment는 아예 미지원이었다.
        describe("buildPayload - DETAIL_SLACK attachment 필드 (P1-133)") {
            it("이슈는 마일스톤(있을 때만)/담당자/상태 필드를 모두 포함해야 한다") {
                val slackWebhook = Webhook(
                    id = 50L, project = project, payloadUrl = "http://localhost:8080/hook",
                    gitPush = true, webhookType = WebhookType.DETAIL_SLACK
                )
                val sender = User(id = 2L, loginId = "sender", name = "송신자")
                val assigneeUser = User(id = 8L, loginId = "assignee", name = "담당자이름")
                val milestone = com.github.search5.yona.domain.milestone.Milestone(id = 500L, title = "1.0 릴리즈", project = project)
                val issue = com.github.search5.yona.domain.issue.Issue(
                    id = 103L, title = "필드 테스트 이슈", body = "이슈 본문", project = project, number = 13,
                    milestone = milestone,
                    assignee = com.github.search5.yona.domain.issue.Assignee(id = 1L, user = assigneeUser, project = project)
                )

                val payload = webhookService.buildPayload(slackWebhook, EventType.NEW_ISSUE, sender, issue)
                val json = tools.jackson.databind.ObjectMapper().readTree(payload)
                val fields = json.get("attachments").get(0).get("fields")

                json.get("attachments").get(0).get("text").asText() shouldBe "이슈 본문"
                fields.get(0).get("title").asText() shouldBe "마일 스톤 변경"
                fields.get(0).get("value").asText() shouldBe "1.0 릴리즈"
                fields.get(1).get("title").asText() shouldBe ""
                fields.get(1).get("value").asText() shouldBe "담당자이름"
                fields.get(2).get("title").asText() shouldBe "상태"
                fields.get(2).get("value").asText() shouldBe issue.state.toString()
            }

            it("마일스톤이 없는 이슈는 마일스톤 필드 없이 담당자/상태 필드만 포함해야 한다") {
                val slackWebhook = Webhook(
                    id = 51L, project = project, payloadUrl = "http://localhost:8080/hook",
                    gitPush = true, webhookType = WebhookType.DETAIL_SLACK
                )
                val sender = User(id = 2L, loginId = "sender", name = "송신자")
                val issue = com.github.search5.yona.domain.issue.Issue(
                    id = 104L, title = "마일스톤 없는 이슈", body = "본문", project = project, number = 14
                )

                val payload = webhookService.buildPayload(slackWebhook, EventType.NEW_ISSUE, sender, issue)
                val json = tools.jackson.databind.ObjectMapper().readTree(payload)
                val fields = json.get("attachments").get(0).get("fields")

                fields.size() shouldBe 2
                fields.get(0).get("title").asText() shouldBe ""
                fields.get(1).get("title").asText() shouldBe "상태"
            }

            it("풀 리퀘스트는 보낸사람/보낸브랜치/받는브랜치 필드를 포함해야 한다 (yona 원본은 미지원이었음)") {
                val slackWebhook = Webhook(
                    id = 52L, project = project, payloadUrl = "http://localhost:8080/hook",
                    gitPush = true, webhookType = WebhookType.DETAIL_SLACK
                )
                val sender = User(id = 2L, loginId = "sender", name = "송신자")
                val contributor = User(id = 9L, loginId = "contributor", name = "기여자")
                val pullRequest = com.github.search5.yona.domain.pullrequest.PullRequest(
                    id = 60L, title = "PR 제목", body = "PR 본문",
                    toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature/x",
                    contributor = contributor, number = 3
                )

                val payload = webhookService.buildPayload(slackWebhook, EventType.NEW_PULL_REQUEST, sender, pullRequest)
                val json = tools.jackson.databind.ObjectMapper().readTree(payload)
                val attachment = json.get("attachments").get(0)
                val fields = attachment.get("fields")

                attachment.get("text").asText() shouldBe "PR 본문"
                fields.get(0).get("title").asText() shouldBe "보낸 사람"
                fields.get(0).get("value").asText() shouldBe "기여자"
                fields.get(1).get("title").asText() shouldBe "코드 보내는 곳"
                fields.get(1).get("value").asText() shouldBe "feature/x"
                fields.get(2).get("title").asText() shouldBe "코드 받을 곳"
                fields.get(2).get("value").asText() shouldBe "master"
            }

            // CommitComment는 yona Webhook.java에 대응 오버로드 자체가 없는 yuna 전용 리소스라, 링크나
            // 필드를 새로 만들어 붙이지 않아야 한다(레거시에 없는 동작 추가 금지).
            it("CommitComment는 링크나 attachment 필드를 새로 만들지 않아야 한다") {
                val slackWebhook = Webhook(
                    id = 53L, project = project, payloadUrl = "http://localhost:8080/hook",
                    gitPush = true, webhookType = WebhookType.DETAIL_SLACK
                )
                val sender = User(id = 2L, loginId = "sender", name = "송신자")
                val commitComment = com.github.search5.yona.domain.pullrequest.CommitComment(
                    id = 70L, project = project, contents = "커밋 댓글 내용"
                )

                val payload = webhookService.buildPayload(slackWebhook, EventType.NEW_COMMENT, sender, commitComment)
                val json = tools.jackson.databind.ObjectMapper().readTree(payload)

                json.get("text").asText() shouldBe "[test-project] 송신자님이 새 댓글을 등록했습니다."
                json.get("attachments").get(0).get("text").asText() shouldBe ""
                json.get("attachments").get(0).get("fields").size() shouldBe 0
            }

            // yona Webhook.java:299-317 Posting 오버로드에는 DETAIL_SLACK 전용 분기가 없어(다른
            // 타입과 달리) SLACK 웹훅이어도 attachments 없이 텍스트만 보낸다 (P2-36).
            it("Posting(게시글)은 SLACK 웹훅이어도 attachments 없이 텍스트만 보내야 한다") {
                val slackWebhook = Webhook(
                    id = 54L, project = project, payloadUrl = "http://localhost:8080/hook",
                    gitPush = true, webhookType = WebhookType.DETAIL_SLACK
                )
                val sender = User(id = 2L, loginId = "sender", name = "송신자")
                val posting = com.github.search5.yona.domain.board.Posting(
                    id = 80L, title = "게시글 제목", body = "게시글 본문", project = project, number = 4
                )

                val payload = webhookService.buildPayload(slackWebhook, EventType.NEW_POSTING, sender, posting)
                val json = tools.jackson.databind.ObjectMapper().readTree(payload)

                json.has("attachments") shouldBe false
            }
        }

        // yona Webhook.java:643-648 — Hangout Chat 응답의 thread.name을 파싱해 WebhookThread로
        // 저장하는 쓰기 경로가 yuna에 전혀 없던 것(P1-143)을 추가.
        describe("recordHangoutThreadIfNeeded (P1-143)") {
            it("DETAIL_HANGOUT_CHAT 웹훅이고 응답에 thread.name이 있으면 저장을 요청해야 한다") {
                val hangoutWebhook = Webhook(
                    id = 60L, project = project, payloadUrl = "http://localhost:8080/hook",
                    webhookType = WebhookType.DETAIL_HANGOUT_CHAT
                )
                val issue = com.github.search5.yona.domain.issue.Issue(
                    id = 105L, title = "스레드 테스트 이슈", body = "내용", project = project, number = 15
                )
                val responseBody = """{"thread":{"name":"spaces/AAA/threads/BBB"}}"""

                webhookService.recordHangoutThreadIfNeeded(hangoutWebhook, issue, responseBody)

                verify(exactly = 1) {
                    webhookThreadRecorder.recordThreadIfAbsent(60L, ResourceType.ISSUE_POST, "105", "spaces/AAA/threads/BBB")
                }
            }

            it("DETAIL_HANGOUT_CHAT이 아닌 웹훅이면 저장을 요청하지 않아야 한다") {
                val slackWebhook = Webhook(
                    id = 61L, project = project, payloadUrl = "http://localhost:8080/hook",
                    webhookType = WebhookType.DETAIL_SLACK
                )
                val issue = com.github.search5.yona.domain.issue.Issue(
                    id = 106L, title = "이슈", body = "내용", project = project, number = 16
                )
                val responseBody = """{"thread":{"name":"spaces/AAA/threads/BBB"}}"""

                webhookService.recordHangoutThreadIfNeeded(slackWebhook, issue, responseBody)

                verify(exactly = 0) { webhookThreadRecorder.recordThreadIfAbsent(any(), any(), any(), any()) }
            }

            it("응답 본문이 JSON이 아니면 예외 없이 저장을 요청하지 않아야 한다") {
                val hangoutWebhook = Webhook(
                    id = 62L, project = project, webhookType = WebhookType.DETAIL_HANGOUT_CHAT
                )
                val issue = com.github.search5.yona.domain.issue.Issue(
                    id = 107L, title = "이슈", body = "내용", project = project, number = 17
                )

                webhookService.recordHangoutThreadIfNeeded(hangoutWebhook, issue, "not a json")

                verify(exactly = 0) { webhookThreadRecorder.recordThreadIfAbsent(any(), any(), any(), any()) }
            }
        }

        // yona Webhook.java:346 eventComment.getParent().asResource() / :480 eventPullRequest.asResource()
        // 대응 (P1-134) — 댓글 이벤트의 Hangout 스레드 키는 댓글 자신이 아니라 부모 리소스 기준이어야
        // 같은 이슈/게시글/PR에 달리는 댓글들이 한 스레드로 묶인다.
        describe("Hangout Chat 스레드 키는 댓글 자신이 아니라 부모 리소스를 써야 한다 (P1-134)") {
            val hangoutWebhook = Webhook(
                id = 70L, project = project, payloadUrl = "http://localhost:8080/hook",
                webhookType = WebhookType.DETAIL_HANGOUT_CHAT
            )
            val sender = User(id = 2L, loginId = "sender", name = "송신자")

            it("이슈 댓글은 부모 이슈(ISSUE_POST)를 스레드 키로 조회/저장해야 한다") {
                val parentIssue = com.github.search5.yona.domain.issue.Issue(
                    id = 200L, title = "부모 이슈", body = "내용", project = project, number = 20
                )
                val comment = com.github.search5.yona.domain.issue.IssueComment(
                    id = 300L, contents = "댓글", issue = parentIssue
                )
                every {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(70L, ResourceType.ISSUE_POST, "200")
                } returns null

                webhookService.buildPayload(hangoutWebhook, EventType.NEW_COMMENT, sender, comment)
                verify(exactly = 1) {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(70L, ResourceType.ISSUE_POST, "200")
                }
                verify(exactly = 0) {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(70L, ResourceType.ISSUE_COMMENT, "300")
                }

                webhookService.recordHangoutThreadIfNeeded(
                    hangoutWebhook, comment, """{"thread":{"name":"spaces/AAA/threads/BBB"}}"""
                )
                verify(exactly = 1) {
                    webhookThreadRecorder.recordThreadIfAbsent(70L, ResourceType.ISSUE_POST, "200", "spaces/AAA/threads/BBB")
                }
            }

            it("PR 리뷰 댓글은 부모 풀 리퀘스트(PULL_REQUEST)를 스레드 키로 조회/저장해야 한다") {
                val contributor = User(id = 9L, loginId = "contributor", name = "기여자")
                val pullRequest = com.github.search5.yona.domain.pullrequest.PullRequest(
                    id = 60L, title = "PR", body = "본문",
                    toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature/x",
                    contributor = contributor, number = 3
                )
                val thread = com.github.search5.yona.domain.pullrequest.CodeCommentThread(
                    id = 400L, pullRequest = pullRequest, project = project
                )
                val reviewComment = com.github.search5.yona.domain.pullrequest.ReviewComment(
                    id = 500L, contents = "리뷰 댓글", thread = thread
                )
                every {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(70L, ResourceType.PULL_REQUEST, "60")
                } returns null

                webhookService.buildPayload(hangoutWebhook, EventType.NEW_REVIEW_COMMENT, sender, reviewComment)
                verify(exactly = 1) {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(70L, ResourceType.PULL_REQUEST, "60")
                }

                webhookService.recordHangoutThreadIfNeeded(
                    hangoutWebhook, reviewComment, """{"thread":{"name":"spaces/AAA/threads/CCC"}}"""
                )
                verify(exactly = 1) {
                    webhookThreadRecorder.recordThreadIfAbsent(70L, ResourceType.PULL_REQUEST, "60", "spaces/AAA/threads/CCC")
                }
            }

            // CommitComment는 yona Webhook.java에 대응 이벤트 자체가 없어(P2-18) 부모 매핑 규칙이
            // 없다 — 레거시에 없는 동작을 새로 추가하지 않도록 자기 자신의 키를 그대로 써야 한다.
            it("CommitComment는 부모 매핑 규칙이 없어 자기 자신의 키(COMMIT_COMMENT)를 그대로 써야 한다") {
                val commitComment = com.github.search5.yona.domain.pullrequest.CommitComment(
                    id = 600L, project = project, contents = "커밋 댓글"
                )
                every {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(70L, ResourceType.COMMIT_COMMENT, "600")
                } returns null

                webhookService.buildPayload(hangoutWebhook, EventType.NEW_COMMENT, sender, commitComment)
                verify(exactly = 1) {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(70L, ResourceType.COMMIT_COMMENT, "600")
                }
            }
        }
    }
})
