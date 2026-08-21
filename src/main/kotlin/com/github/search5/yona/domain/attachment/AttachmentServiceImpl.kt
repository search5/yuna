package com.github.search5.yona.domain.attachment

import com.github.search5.yona.domain.enumeration.ResourceType
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant

@Service
@Transactional
class AttachmentServiceImpl(
    private val attachmentRepository: AttachmentRepository,
    @Value("\${yuna.upload.base-dir:/tmp/yuna/uploads}")
    private val baseDir: String
) : AttachmentService {

    override fun store(
        inputStream: InputStream,
        name: String,
        containerType: ResourceType,
        containerId: String,
        ownerLoginId: String
    ): Attachment {
        val uploadDir = File(baseDir)
        if (!uploadDir.exists()) {
            uploadDir.mkdirs()
        }

        val tempFile = File.createTempFile("yuna-upload-", null)
        val digest = MessageDigest.getInstance("SHA-256")
        var size: Long = 0

        FileOutputStream(tempFile).use { fos ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                fos.write(buffer, 0, bytesRead)
                digest.update(buffer, 0, bytesRead)
                size += bytesRead
            }
            fos.flush()
        }

        val hash = toHex(digest.digest())
        val targetFile = File(uploadDir, hash)

        if (!targetFile.exists()) {
            Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } else {
            tempFile.delete()
        }

        val mimeType = try {
            Files.probeContentType(targetFile.toPath())
        } catch (e: Exception) {
            "application/octet-stream"
        }

        val attachment = Attachment(
            name = name,
            hash = hash,
            containerType = containerType,
            containerId = containerId,
            mimeType = mimeType ?: "application/octet-stream",
            size = size,
            createdDate = Instant.now(),
            ownerLoginId = ownerLoginId
        )

        return attachmentRepository.save(attachment)
    }

    override fun getFile(attachment: Attachment): File {
        return File(baseDir, attachment.hash)
    }

    override fun delete(attachment: Attachment) {
        attachmentRepository.delete(attachment)
        // 동일한 해시를 참조하는 다른 파일 첨부 레코드가 없다면 디스크에서 삭제
        val remaining = attachmentRepository.findByHash(attachment.hash).filter { it.id != attachment.id }
        if (remaining.isEmpty()) {
            val file = File(baseDir, attachment.hash)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    override fun deleteAll(containerType: ResourceType, containerId: String) {
        val attachments = attachmentRepository.findByContainerTypeAndContainerId(containerType, containerId)
        for (attachment in attachments) {
            delete(attachment)
        }
    }

    override fun moveAll(
        fromType: ResourceType,
        fromId: String,
        toType: ResourceType,
        toId: String
    ): Int {
        val attachments = attachmentRepository.findByContainerTypeAndContainerId(fromType, fromId)
        for (attachment in attachments) {
            attachment.containerType = toType
            attachment.containerId = toId
        }
        attachmentRepository.saveAll(attachments)
        return attachments.size
    }

    override fun moveOnlySelected(
        fromType: ResourceType,
        fromId: String,
        toType: ResourceType,
        toId: String,
        selectedIds: List<Long>,
        moverLoginId: String
    ): Int {
        val attachments = attachmentRepository.findAllById(selectedIds)
        val validAttachments = attachments.filter {
            it.containerType == fromType && it.containerId == fromId && it.ownerLoginId == moverLoginId
        }
        for (attachment in validAttachments) {
            attachment.containerType = toType
            attachment.containerId = toId
        }
        attachmentRepository.saveAll(validAttachments)
        return validAttachments.size
    }

    private fun toHex(bytes: ByteArray): String {
        val formatter = StringBuilder()
        for (b in bytes) {
            formatter.append(String.format("%02x", b))
        }
        return formatter.toString()
    }
}
