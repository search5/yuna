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

    // yona Attachment.java:75-85 findBy(Attachment) 대응 (P2-24) — 동일 컨테이너에 동일 이름·내용으로
    // 재업로드된 첨부는 새 행을 만들지 않고 기존 행을 재사용(dedup)하기 위한 조회.
    fun findFirstByNameAndHashAndContainerTypeAndContainerId(
        name: String,
        hash: String,
        containerType: ResourceType,
        containerId: String
    ): Attachment?
}

