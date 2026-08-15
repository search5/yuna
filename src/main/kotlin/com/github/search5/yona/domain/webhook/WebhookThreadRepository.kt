package com.github.search5.yona.domain.webhook

import com.github.search5.yona.domain.enumeration.ResourceType
import org.springframework.data.jpa.repository.JpaRepository

interface WebhookThreadRepository : JpaRepository<WebhookThread, Long> {
    fun findByWebhookIdAndResourceTypeAndResourceId(
        webhookId: Long,
        resourceType: ResourceType,
        resourceId: String
    ): WebhookThread?
}
