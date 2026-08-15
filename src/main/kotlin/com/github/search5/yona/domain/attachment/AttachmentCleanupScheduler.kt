package com.github.search5.yona.domain.attachment

import com.github.search5.yona.domain.enumeration.ResourceType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class AttachmentCleanupScheduler(
    private val attachmentRepository: AttachmentRepository,
    private val attachmentService: AttachmentService,
    @Value("\${yuna.attachment.temporary-keep-alive-ms:86400000}")
    private val keepAliveMillis: Long
) {
    private val log = LoggerFactory.getLogger(AttachmentCleanupScheduler::class.java)

    @Scheduled(cron = "0 0 * * * *")
    fun cleanupTemporaryFiles() {
        log.info("Starting cleanup of temporary attachment files...")
        val threshold = Instant.now().minusMillis(keepAliveMillis)
        val temporaryAttachments = attachmentRepository.findByContainerTypeAndCreatedDateBefore(
            ResourceType.USER,
            threshold
        )
        
        var deletedCount = 0
        for (attachment in temporaryAttachments) {
            try {
                attachmentService.delete(attachment)
                deletedCount++
            } catch (e: Exception) {
                log.error("Failed to delete temporary attachment: ${attachment.id}", e)
            }
        }
        log.info("Temporary attachment cleanup completed. Deleted $deletedCount of ${temporaryAttachments.size} files.")
    }
}
