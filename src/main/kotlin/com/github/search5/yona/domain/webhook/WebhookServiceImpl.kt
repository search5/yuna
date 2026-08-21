package com.github.search5.yona.domain.webhook

import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.enumeration.WebhookType
import com.github.search5.yona.domain.notification.NotificationUrlResolver
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.user.User
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Service
@Transactional
class WebhookServiceImpl(
    private val webhookRepository: WebhookRepository,
    private val webhookThreadRepository: WebhookThreadRepository,
    // yona Webhook.java:178 getBaseUrl()(스킴+호스트) 대응 (P2-08). NotificationUrlResolver가
    // 이미 동일 목적으로 쓰는 설정값을 그대로 재사용한다.
    @Value("\${yuna.base-url:}")
    private val baseUrl: String,
    // yona Webhook.java:182-192 buildRequestMessage()(리소스 링크) 대응 (P1-132) — 이슈/게시글/PR/댓글
    // URL 계산 로직을 새로 만들지 않고, 알림메일 경로에서 이미 쓰는 것과 동일한 리졸버를 재사용한다.
    private val notificationUrlResolver: NotificationUrlResolver
) : WebhookService {

    @Transactional(readOnly = true)
    override fun findByProject(projectId: Long): List<Webhook> {
        return webhookRepository.findByProjectId(projectId)
    }

    override fun createWebhook(
        project: Project,
        payloadUrl: String,
        secret: String?,
        gitPush: Boolean,
        webhookType: WebhookType
    ): Webhook {
        val webhook = Webhook(
            project = project,
            payloadUrl = payloadUrl,
            secret = secret,
            gitPush = gitPush,
            webhookType = webhookType,
            createdAt = Instant.now()
        )
        return webhookRepository.save(webhook)
    }

    override fun deleteWebhook(id: Long) {
        val webhook = webhookRepository.findById(id).orElse(null)
        if (webhook != null) {
            webhookRepository.delete(webhook)
        }
    }

    override fun sendWebhook(
        project: Project,
        eventType: com.github.search5.yona.domain.enumeration.EventType,
        sender: User,
        resource: Any
    ) {
        val webhooks = webhookRepository.findByProjectId(project.id ?: return)
        if (webhooks.isEmpty()) return

        for (webhook in webhooks) {
            if (!shouldDeliverToWebhook(webhook, eventType)) {
                continue
            }

            val payload = buildPayload(webhook, eventType, sender, resource)
            sendRequestAsync(webhook.payloadUrl, webhook.secret, payload)
        }
    }

    /**
     * gitPush 플래그는 "push(NEW_COMMIT) 이벤트를 보낼지"만 결정한다.
     * 이슈/게시글/댓글/PR 등 push가 아닌 이벤트는 이 플래그와 무관하게 항상 전송된다.
     * 단, JSON 포맷 웹훅은 gitPush 설정과 무관하게 push 이벤트를 항상 받는다(yona 원본 동작).
     */
    internal fun shouldDeliverToWebhook(
        webhook: Webhook,
        eventType: com.github.search5.yona.domain.enumeration.EventType
    ): Boolean {
        if (eventType != com.github.search5.yona.domain.enumeration.EventType.NEW_COMMIT) {
            return true
        }
        return webhook.gitPush || webhook.webhookType == WebhookType.JSON
    }

    internal fun buildPayload(
        webhook: Webhook,
        eventType: com.github.search5.yona.domain.enumeration.EventType,
        sender: User,
        resource: Any
    ): String {
        val objectMapper = tools.jackson.databind.ObjectMapper()
        val textMessage = buildTextMessage(webhook, eventType, sender, resource)

        return when (webhook.webhookType) {
            WebhookType.DETAIL_SLACK -> {
                val root = objectMapper.createObjectNode()
                root.put("text", textMessage)

                val attachments = objectMapper.createArrayNode()
                attachments.add(buildAttachmentJSON(objectMapper, resource))
                root.set("attachments", attachments)

                objectMapper.writeValueAsString(root)
            }
            WebhookType.DETAIL_HANGOUT_CHAT -> {
                val root = objectMapper.createObjectNode()
                root.put("text", textMessage)
                
                // 스레드 지원
                val resType = getResourceType(resource)
                val resId = getResourceId(resource)
                if (resType != ResourceType.NOT_A_RESOURCE && resId.isNotBlank()) {
                    val webhookThread = webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(
                        webhook.id ?: 0L,
                        resType,
                        resId
                    )
                    if (webhookThread != null) {
                        val threadNode = objectMapper.createObjectNode()
                        threadNode.put("name", webhookThread.threadId)
                        root.set("thread", threadNode)
                    }
                }
                objectMapper.writeValueAsString(root)
            }
            WebhookType.JSON -> {
                if (resource is PushedCommits) {
                    buildPushPayload(webhook, sender, resource)
                } else {
                    // Raw JSON 포맷
                    val root = objectMapper.createObjectNode()
                    root.put("event", eventType.name)
                    root.put("sender", sender.name ?: "")
                    root.put("project", webhook.project?.name ?: "")
                    root.put("resourceId", getResourceId(resource))
                    root.put("resourceType", getResourceType(resource).name)
                    objectMapper.writeValueAsString(root)
                }
            }
            else -> {
                // WebhookType.SIMPLE
                val root = objectMapper.createObjectNode()
                root.put("text", textMessage)
                objectMapper.writeValueAsString(root)
            }
        }
    }

    // yona Webhook.java의 push용 buildRequestBody(commits, refNames, sender) 대응 (P2-04).
    // 커밋 목록이 빠진 채 event/sender/project만 담겨있던 단순 JSON 대신, GitHub 웹훅과 유사한
    // ref/commits/head_commit/sender/pusher/repository 구조로 구성한다.
    // yona Webhook.java:178 getBaseUrl() 대응 (P2-08) — RouteUtil.getUrl(project)(상대경로)에 붙는
    // 스킴+호스트. NotificationUrlResolver가 쓰는 것과 동일한 yuna.base-url 설정을 재사용한다.
    private fun projectUrl(project: Project?): String =
        project?.let { "$baseUrl/${it.owner}/${it.name}" } ?: baseUrl

    // yona Webhook.java:713-714 buildJSONFromCommit()의
    // new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ssZ") 대응 (P2-08) — 문자열 포맷까지 그대로 재현한다.
    private val commitTimestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'hh:mm:ssZ")

    // yona Webhook.java:284-298 buildIssueDetails() / :502-515 buildJsonWithPullReqtuestDetails() /
    // :376-384 buildCommentDetails() / :545-552 buildAttachmentJSON() 대응 (P1-133) — DETAIL_SLACK
    // attachment의 fields를 리소스 타입별로 yona와 동일하게 구성한다. Issue는 마일스톤(있을 때만)+담당자+상태,
    // PullRequest는 보낸사람+보낸브랜치+받는브랜치(yona 원본은 완전히 미지원이었음), 그 외(댓글 등)는
    // yona도 fields 없이 본문만 담는다. yona는 color를 `Play.application().configuration().getString(
    // "slack." + eventType, "")`로 조회하나 yuna에는 이 설정 자체가 없어(기본값도 항상 "") 빈 문자열로 고정.
    private fun buildAttachmentJSON(objectMapper: tools.jackson.databind.ObjectMapper, resource: Any): tools.jackson.databind.node.ObjectNode {
        val fields = objectMapper.createArrayNode()
        val text: String

        when (resource) {
            is com.github.search5.yona.domain.issue.Issue -> {
                text = resource.body ?: ""
                resource.milestone?.let {
                    fields.add(buildTitleValueJSON(objectMapper, "마일 스톤 변경", it.title, true))
                }
                fields.add(buildTitleValueJSON(objectMapper, "", resource.assignee?.user?.name ?: "", true))
                fields.add(buildTitleValueJSON(objectMapper, "상태", resource.state.toString(), true))
            }
            is com.github.search5.yona.domain.board.Posting -> text = resource.body ?: ""
            is com.github.search5.yona.domain.pullrequest.PullRequest -> {
                text = resource.body ?: ""
                fields.add(buildTitleValueJSON(objectMapper, "보낸 사람", resource.contributor.name, false))
                fields.add(buildTitleValueJSON(objectMapper, "코드 보내는 곳", resource.fromBranch, true))
                fields.add(buildTitleValueJSON(objectMapper, "코드 받을 곳", resource.toBranch, true))
            }
            is com.github.search5.yona.domain.issue.IssueComment -> text = resource.contents
            is com.github.search5.yona.domain.board.PostingComment -> text = resource.contents
            // yona Webhook.java:476-478 — 리뷰 댓글(Pull Request Comment) 이벤트의 DETAIL_SLACK
            // attachment는 댓글 자신이 아니라 buildJsonWithPullReqtuestDetails(eventPullRequest, ...)를
            // 그대로 재사용해 부모 풀 리퀘스트의 본문+필드(보낸사람/보낸브랜치/받는브랜치)를 담는다.
            is com.github.search5.yona.domain.pullrequest.ReviewComment -> {
                val pullRequest = resource.thread?.pullRequest
                text = pullRequest?.body ?: ""
                if (pullRequest != null) {
                    fields.add(buildTitleValueJSON(objectMapper, "보낸 사람", pullRequest.contributor.name, false))
                    fields.add(buildTitleValueJSON(objectMapper, "코드 보내는 곳", pullRequest.fromBranch, true))
                    fields.add(buildTitleValueJSON(objectMapper, "코드 받을 곳", pullRequest.toBranch, true))
                }
            }
            // CommitComment는 yona Webhook.java에 대응하는 오버로드 자체가 없는 yuna 전용 리소스라
            // (P2-18) else 분기(본문/필드 없음)로 떨어지도록 그대로 둔다(레거시에 없는 동작 추가 금지).
            else -> text = ""
        }

        val attachmentNode = objectMapper.createObjectNode()
        attachmentNode.put("text", text)
        attachmentNode.set("fields", fields)
        attachmentNode.put("color", "")
        return attachmentNode
    }

    private fun buildTitleValueJSON(
        objectMapper: tools.jackson.databind.ObjectMapper,
        title: String,
        value: String,
        shorten: Boolean
    ): tools.jackson.databind.node.ObjectNode {
        val titleJSON = objectMapper.createObjectNode()
        titleJSON.put("title", title)
        titleJSON.put("value", value)
        titleJSON.put("short", shorten)
        return titleJSON
    }

    private fun buildPushPayload(webhook: Webhook, sender: User, pushed: PushedCommits): String {
        val objectMapper = tools.jackson.databind.ObjectMapper()
        val root = objectMapper.createObjectNode()
        val project = webhook.project

        val refNodes = objectMapper.createArrayNode()
        pushed.refNames.forEach { refNodes.add(it) }
        root.set("ref", refNodes)

        val commitNodes = objectMapper.createArrayNode()
        for (commit in pushed.commits) {
            val commitNode = objectMapper.createObjectNode()
            commitNode.put("id", commit.name)
            commitNode.put("message", commit.fullMessage)
            val authorInstant = commit.authorIdent?.`when`?.toInstant()
            commitNode.put(
                "timestamp",
                authorInstant?.atZone(ZoneId.systemDefault())?.format(commitTimestampFormatter) ?: ""
            )
            commitNode.put("url", "${projectUrl(project)}/commit/${commit.name}")

            val authorNode = objectMapper.createObjectNode()
            authorNode.put("name", commit.authorIdent?.name ?: "")
            authorNode.put("email", commit.authorIdent?.emailAddress ?: "")
            commitNode.set("author", authorNode)

            val committerNode = objectMapper.createObjectNode()
            committerNode.put("name", commit.committerIdent?.name ?: "")
            committerNode.put("email", commit.committerIdent?.emailAddress ?: "")
            commitNode.set("committer", committerNode)

            commitNodes.add(commitNode)
        }
        root.set("commits", commitNodes)
        if (commitNodes.size() > 0) {
            root.set("head_commit", commitNodes.get(0))
        }

        val senderNode = objectMapper.createObjectNode()
        senderNode.put("login", sender.loginId)
        senderNode.put("id", sender.id ?: 0L)
        senderNode.put("avatar_url", sender.avatarUrl)
        senderNode.put("type", "User")
        // yona Webhook.java:560 buildSenderJSON()의 site_admin 대응 (P2-08).
        senderNode.put("site_admin", sender.isSiteManager)
        root.set("sender", senderNode)

        val pusherNode = objectMapper.createObjectNode()
        pusherNode.put("name", sender.name)
        pusherNode.put("email", sender.email ?: "")
        root.set("pusher", pusherNode)

        val repositoryNode = objectMapper.createObjectNode()
        repositoryNode.put("id", project?.id ?: 0L)
        repositoryNode.put("name", project?.name ?: "")
        repositoryNode.put("owner", project?.owner ?: "")
        repositoryNode.put("html_url", projectUrl(project))
        // yona Webhook.java:577 buildRepositoryJSON()의 overview(프로젝트 설명) 대응 (P2-08).
        repositoryNode.put("overview", project?.overview ?: "")
        repositoryNode.put("private", project?.projectScope != com.github.search5.yona.domain.project.ProjectScope.PUBLIC)
        root.set("repository", repositoryNode)

        return objectMapper.writeValueAsString(root)
    }

    private fun buildTextMessage(
        webhook: Webhook,
        eventType: com.github.search5.yona.domain.enumeration.EventType,
        sender: User,
        resource: Any
    ): String {
        val projectName = webhook.project?.name ?: ""
        val actionMessage = when (eventType) {
            com.github.search5.yona.domain.enumeration.EventType.NEW_ISSUE -> "새 이슈를 등록했습니다"
            com.github.search5.yona.domain.enumeration.EventType.ISSUE_STATE_CHANGED -> "이슈 상태를 변경했습니다"
            com.github.search5.yona.domain.enumeration.EventType.NEW_POSTING -> "새 게시글을 작성했습니다"
            com.github.search5.yona.domain.enumeration.EventType.NEW_COMMENT -> "새 댓글을 등록했습니다"
            com.github.search5.yona.domain.enumeration.EventType.NEW_REVIEW_COMMENT -> "새 리뷰 댓글을 등록했습니다"
            com.github.search5.yona.domain.enumeration.EventType.NEW_PULL_REQUEST -> "새 풀 리퀘스트를 생성했습니다"
            com.github.search5.yona.domain.enumeration.EventType.PULL_REQUEST_MERGED -> "풀 리퀘스트를 병합했습니다"
            com.github.search5.yona.domain.enumeration.EventType.PULL_REQUEST_STATE_CHANGED -> "풀 리퀘스트 상태를 변경했습니다"
            com.github.search5.yona.domain.enumeration.EventType.PULL_REQUEST_COMMIT_CHANGED -> "풀 리퀘스트에 커밋을 추가했습니다"
            com.github.search5.yona.domain.enumeration.EventType.PULL_REQUEST_REVIEW_STATE_CHANGED -> "풀 리퀘스트 리뷰 상태를 변경했습니다"
            com.github.search5.yona.domain.enumeration.EventType.NEW_COMMIT -> "커밋을 푸시했습니다"
            else -> "이벤트를 트리거했습니다"
        }

        // PushedCommits는 yona도 buildRequestMessage()(리소스 링크)를 쓰지 않는 별도 경로
        // (Webhook.java:668 buildRequestBody(commits, refNames, sender, title))라 링크 없이 그대로 유지.
        if (resource is PushedCommits) {
            val resourceInfo = "${resource.commits.size}개의 커밋을 ${resource.refNames.firstOrNull() ?: ""} 브랜치로 푸시했습니다"
            return "[$projectName] ${sender.name}님이 $actionMessage. $resourceInfo"
        }

        return "[$projectName] ${sender.name}님이 $actionMessage.${buildResourceLink(webhook, resource)}"
    }

    private fun buildResourceLink(webhook: Webhook, resource: Any): String {
        val linkText = when (resource) {
            is com.github.search5.yona.domain.issue.Issue -> "#${resource.number}: ${resource.title}"
            is com.github.search5.yona.domain.board.Posting -> "#${resource.number}: ${resource.title}"
            is com.github.search5.yona.domain.issue.IssueComment ->
                "#${resource.issue.number}: ${resource.issue.title}"
            is com.github.search5.yona.domain.board.PostingComment ->
                "#${resource.posting.number}: ${resource.posting.title}"
            // yona Webhook.java:493-499 buildRequestBody(PullRequest, ReviewComment) — 링크 텍스트는
            // 리뷰 댓글 자신이 아니라 부모 풀 리퀘스트의 "#번호: 제목". 부모를 못 찾으면(비정상 상태)
            // yona에 대응 분기가 없으므로 링크를 만들지 않는다.
            is com.github.search5.yona.domain.pullrequest.ReviewComment ->
                resource.thread?.pullRequest?.let { "#${it.number}: ${it.title}" }
            // CommitComment는 yona Webhook.java에 대응하는 오버로드 자체가 없는 yuna 전용 리소스라
            // (P2-18) 링크를 만들지 않는다(레거시에 없는 동작 추가 금지).
            is com.github.search5.yona.domain.pullrequest.PullRequest -> "#${resource.number}: ${resource.title}"
            else -> null
        } ?: return ""

        val url = notificationUrlResolver.getUrl(getResourceType(resource), getResourceId(resource)) ?: return ""

        val escapedText = if (webhook.webhookType == WebhookType.DETAIL_SLACK) {
            linkText.replace(">", "&gt;")
        } else {
            linkText
        }
        return " <$url|$escapedText>"
    }

    private fun getResourceType(resource: Any): ResourceType {
        return when (resource) {
            is com.github.search5.yona.domain.issue.Issue -> ResourceType.ISSUE_POST
            is com.github.search5.yona.domain.board.Posting -> ResourceType.BOARD_POST
            is com.github.search5.yona.domain.issue.IssueComment -> ResourceType.ISSUE_COMMENT
            is com.github.search5.yona.domain.board.PostingComment -> ResourceType.NONISSUE_COMMENT
            is com.github.search5.yona.domain.pullrequest.ReviewComment -> ResourceType.REVIEW_COMMENT
            is com.github.search5.yona.domain.pullrequest.CommitComment -> ResourceType.COMMIT_COMMENT
            is PushedCommits -> ResourceType.COMMIT
            is com.github.search5.yona.domain.pullrequest.PullRequest -> ResourceType.PULL_REQUEST
            else -> ResourceType.NOT_A_RESOURCE
        }
    }

    private fun getResourceId(resource: Any): String {
        return when (resource) {
            is com.github.search5.yona.domain.issue.Issue -> resource.id?.toString() ?: ""
            is com.github.search5.yona.domain.board.Posting -> resource.id?.toString() ?: ""
            is com.github.search5.yona.domain.issue.IssueComment -> resource.id?.toString() ?: ""
            is com.github.search5.yona.domain.board.PostingComment -> resource.id?.toString() ?: ""
            is com.github.search5.yona.domain.pullrequest.ReviewComment -> resource.id?.toString() ?: ""
            is com.github.search5.yona.domain.pullrequest.CommitComment -> resource.id?.toString() ?: ""
            is PushedCommits -> resource.commits.firstOrNull()?.name ?: ""
            is com.github.search5.yona.domain.pullrequest.PullRequest -> resource.id?.toString() ?: ""
            else -> ""
        }
    }

    private fun sendRequestAsync(payloadUrl: String, secret: String?, payload: String) {
        try {
            val httpClient = java.net.http.HttpClient.newBuilder().build()
            val requestBuilder = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(payloadUrl))
                .header("Content-Type", "application/json")
                .header("User-Agent", "Yobi-Hookshot")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload))

            if (!secret.isNullOrBlank()) {
                requestBuilder.header("Authorization", "token $secret")
            }

            val request = requestBuilder.build()
            httpClient.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofString())
                .thenAccept { response ->
                    val statusCode = response.statusCode()
                    if (statusCode < 200 || statusCode >= 300) {
                        println("[Webhook] HTTP 전송 실패: $statusCode - ${response.body()}")
                    }
                }
                .exceptionally { ex ->
                    println("[Webhook] 전송 중 예외 발생: ${ex.message}")
                    null
                }
        } catch (e: Exception) {
            println("[Webhook] HTTP 클라이언트 생성 중 오류: ${e.message}")
        }
    }
}
