package com.github.search5.yona.domain.attachment

import com.github.search5.yona.domain.enumeration.ResourceType
import java.io.File
import java.io.InputStream

interface AttachmentService {
    fun store(
        inputStream: InputStream,
        name: String,
        containerType: ResourceType,
        containerId: String,
        ownerLoginId: String
    ): Attachment

    fun getFile(attachment: Attachment): File

    fun delete(attachment: Attachment)

    fun deleteAll(containerType: ResourceType, containerId: String)

    fun moveAll(
        fromType: ResourceType,
        fromId: String,
        toType: ResourceType,
        toId: String
    ): Int

    fun moveOnlySelected(
        fromType: ResourceType,
        fromId: String,
        toType: ResourceType,
        toId: String,
        selectedIds: List<Long>
    ): Int
}
