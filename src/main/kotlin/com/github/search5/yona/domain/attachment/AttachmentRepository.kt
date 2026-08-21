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
    // yona Attachment.java:456-458 cleanupTemporaryUploadFilesWithSchedule()의 .ge("createdDate", ...)
    // 대응 (P2-26) — "오래된" 파일이 아니라 "최근(threshold 이후)" 파일을 대상으로 하는 게 맞는지
    // 의심스러운 비교 방향이지만, 사용자 지시로 레거시를 그대로 포팅한다. 백로그 P2-26 참고.
    fun findByContainerTypeAndCreatedDateGreaterThanEqual(containerType: ResourceType, createdDate: Instant): List<Attachment>

    // yona Attachment.java:75-85 findBy(Attachment) 대응 (P2-24) — 동일 컨테이너에 동일 이름·내용으로
    // 재업로드된 첨부는 새 행을 만들지 않고 기존 행을 재사용(dedup)하기 위한 조회.
    fun findFirstByNameAndHashAndContainerTypeAndContainerId(
        name: String,
        hash: String,
        containerType: ResourceType,
        containerId: String
    ): Attachment?
}

