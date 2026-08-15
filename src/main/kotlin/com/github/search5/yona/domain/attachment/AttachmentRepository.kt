package com.github.search5.yona.domain.attachment

import com.github.search5.yona.domain.enumeration.ResourceType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface AttachmentRepository : JpaRepository<Attachment, Long> {
    fun findByContainerTypeAndContainerId(containerType: ResourceType, containerId: String): List<Attachment>
    fun countByContainerTypeAndContainerId(containerType: ResourceType, containerId: String): Int
    fun existsByHash(hash: String): Boolean
    fun findByHash(hash: String): List<Attachment>
    fun findByOwnerLoginId(ownerLoginId: String): List<Attachment>
    fun findByOwnerLoginId(ownerLoginId: String, pageable: Pageable): Page<Attachment>
    fun findByOwnerLoginIdAndNameContainingIgnoreCase(
        ownerLoginId: String,
        name: String,
        pageable: Pageable
    ): Page<Attachment>
    fun findByContainerTypeAndCreatedDateBefore(containerType: ResourceType, createdDate: Instant): List<Attachment>
}

