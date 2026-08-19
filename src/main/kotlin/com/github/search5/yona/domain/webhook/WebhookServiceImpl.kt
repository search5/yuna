package com.github.search5.yona.domain.webhook

import com.github.search5.yona.domain.enumeration.ResourceType
import com.github.search5.yona.domain.enumeration.WebhookType
import com.github.search5.yona.domain.project.Project
import com.github.search5.yona.domain.user.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class WebhookServiceImpl(
    private val webhookRepository: WebhookRepository,
    private val webhookThreadRepository: WebhookThreadRepository
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
        val textMessage = buildTextMessage(webhook.project?.name ?: "", eventType, sender, resource)

        return when (webhook.webhookType) {
            WebhookType.DETAIL_SLACK -> {
                val root = objectMapper.createObjectNode()
                root.put("text", textMessage)
                
                val attachments = objectMapper.createArrayNode()
                val attachNode = objectMapper.createObjectNode()
                
                // 리소스 세부 내용 바인딩
                val bodyText = when (resource) {
                    is com.github.search5.yona.domain.issue.Issue -> resource.body ?: ""
                    is com.github.search5.yona.domain.board.Posting -> resource.body ?: ""
                    is com.github.search5.yona.domain.issue.IssueComment -> resource.contents ?: ""
                    is com.github.search5.yona.domain.board.PostingComment -> resource.contents ?: ""
                    else -> ""
                }
                attachNode.put("text", bodyText)
                
                val fields = objectMapper.createArrayNode()
                // 예: 상태 필드 등 추가
                if (resource is com.github.search5.yona.domain.issue.Issue) {
                    val fieldNode = objectMapper.createObjectNode()
                    fieldNode.put("title", "State")
                    fieldNode.put("value", resource.state?.toString() ?: "OPEN")
                    fieldNode.put("short", true)
                    fields.add(fieldNode)
                }
                attachNode.set("fields", fields)
                attachments.add(attachNode)
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
    private fun buildPushPayload(webhook: Webhook, sender: User, pushed: PushedCommits): String {
        val objectMapper = tools.jackson.databind.ObjectMapper()
        val root = objectMapper.createObjectNode()

        val refNodes = objectMapper.createArrayNode()
        pushed.refNames.forEach { refNodes.add(it) }
        root.set("ref", refNodes)

        val commitNodes = objectMapper.createArrayNode()
        for (commit in pushed.commits) {
            val commitNode = objectMapper.createObjectNode()
            commitNode.put("id", commit.name)
            commitNode.put("message", commit.fullMessage)
            commitNode.put("timestamp", commit.authorIdent?.`when`?.toInstant()?.toString() ?: "")
            commitNode.put("url", "${webhook.project?.let { "/${it.owner}/${it.name}" } ?: ""}/commit/${commit.name}")

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
        root.set("sender", senderNode)

        val pusherNode = objectMapper.createObjectNode()
        pusherNode.put("name", sender.name)
        pusherNode.put("email", sender.email ?: "")
        root.set("pusher", pusherNode)

        val repositoryNode = objectMapper.createObjectNode()
        val project = webhook.project
        repositoryNode.put("id", project?.id ?: 0L)
        repositoryNode.put("name", project?.name ?: "")
        repositoryNode.put("owner", project?.owner ?: "")
        repositoryNode.put("html_url", project?.let { "/${it.owner}/${it.name}" } ?: "")
        repositoryNode.put("private", project?.projectScope != com.github.search5.yona.domain.project.ProjectScope.PUBLIC)
        root.set("repository", repositoryNode)

        return objectMapper.writeValueAsString(root)
    }

    private fun buildTextMessage(
        projectName: String,
        eventType: com.github.search5.yona.domain.enumeration.EventType,
        sender: User,
        resource: Any
    ): String {
        val actionMessage = when (eventType) {
            com.github.search5.yona.domain.enumeration.EventType.NEW_ISSUE -> "새 이슈를 등록했습니다"
            com.github.search5.yona.domain.enumeration.EventType.ISSUE_STATE_CHANGED -> "이슈 상태를 변경했습니다"
            com.github.search5.yona.domain.enumeration.EventType.NEW_POSTING -> "새 게시글을 작성했습니다"
            com.github.search5.yona.domain.enumeration.EventType.NEW_COMMENT -> "새 댓글을 등록했습니다"
            com.github.search5.yona.domain.enumeration.EventType.NEW_PULL_REQUEST -> "새 풀 리퀘스트를 생성했습니다"
            com.github.search5.yona.domain.enumeration.EventType.PULL_REQUEST_MERGED -> "풀 리퀘스트를 병합했습니다"
            com.github.search5.yona.domain.enumeration.EventType.PULL_REQUEST_STATE_CHANGED -> "풀 리퀘스트 상태를 변경했습니다"
            com.github.search5.yona.domain.enumeration.EventType.PULL_REQUEST_COMMIT_CHANGED -> "풀 리퀘스트에 커밋을 추가했습니다"
            com.github.search5.yona.domain.enumeration.EventType.PULL_REQUEST_REVIEW_STATE_CHANGED -> "풀 리퀘스트 리뷰 상태를 변경했습니다"
            com.github.search5.yona.domain.enumeration.EventType.NEW_COMMIT -> "커밋을 푸시했습니다"
            else -> "이벤트를 트리거했습니다"
        }

        val resourceInfo = when (resource) {
            is com.github.search5.yona.domain.issue.Issue -> "#${resource.number}: ${resource.title}"
            is com.github.search5.yona.domain.board.Posting -> "#${resource.number}: ${resource.title}"
            is com.github.search5.yona.domain.issue.IssueComment -> "의견: ${resource.contents?.take(30)}"
            is com.github.search5.yona.domain.board.PostingComment -> "의견: ${resource.contents?.take(30)}"
            is PushedCommits ->
                "${resource.commits.size}개의 커밋을 ${resource.refNames.firstOrNull() ?: ""} 브랜치로 푸시했습니다"
            is com.github.search5.yona.domain.pullrequest.PullRequest -> "#${resource.number}: ${resource.title}"
            else -> ""
        }

        return "[$projectName] ${sender.name}님이 $actionMessage. $resourceInfo"
    }

    private fun getResourceType(resource: Any): ResourceType {
        return when (resource) {
            is com.github.search5.yona.domain.issue.Issue -> ResourceType.ISSUE_POST
            is com.github.search5.yona.domain.board.Posting -> ResourceType.BOARD_POST
            is com.github.search5.yona.domain.issue.IssueComment -> ResourceType.ISSUE_COMMENT
            is com.github.search5.yona.domain.board.PostingComment -> ResourceType.NONISSUE_COMMENT
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
