package com.github.search5.yona.domain.support

import org.apache.tika.Tika
import org.apache.tika.metadata.Metadata
import org.apache.tika.metadata.TikaCoreProperties
import org.mozilla.universalchardet.UniversalDetector
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset

object FileUtil {

    fun detectCharset(bytes: ByteArray): String {
        val detector = UniversalDetector(null)
        var offset = 0

        do {
            val blockSize = Math.min(4096, bytes.size - offset)
            detector.handleData(bytes, offset, blockSize)
            offset += blockSize
        } while (offset < bytes.size)
        detector.dataEnd()

        return detector.detectedCharset ?: "UTF-8"
    }

    fun detectCharset(inputStream: InputStream): String {
        val detector = UniversalDetector(null)
        val buf = ByteArray(4096)
        var nRead: Int

        while (inputStream.read(buf).also { nRead = it } > 0 && !detector.isDone) {
            detector.handleData(buf, 0, nRead)
        }
        detector.dataEnd()

        return detector.detectedCharset ?: "UTF-8"
    }

    // yona FileUtil.java:113-142 detectMediaType() 대응 (P2-25). Apache Tika로 실제 파일 내용(매직
    // 바이트)을 콘텐츠 기반으로 감지한다 — 원본 파일명(name)은 힌트로만 쓰이고, 확장자가 없는
    // 해시 파일명이어도 정확히 감지된다(JDK Files.probeContentType()은 사실상 확장자 기반이라
    // 해시 파일명에서 거의 항상 감지에 실패한다).
    fun detectMediaType(file: File, name: String): String {
        val metadata = Metadata()
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name)
        val mediaType = file.inputStream().use { input ->
            Tika().detector.detect(BufferedInputStream(input), metadata)
        }

        return when {
            mediaType.type.lowercase() == "text" -> {
                val charset = file.inputStream().use { detectCharset(it) }
                "$mediaType; charset=${Charset.forName(charset).name()}"
            }
            // Tika가 ogg 비디오를 audio/ogg로 오판하는 것을 보정 (yona FileUtil.java:132-136 동일 대응).
            mediaType.toString() == "audio/ogg" && name.substringAfterLast('.', "").lowercase() == "ogv" ->
                "video/ogg"
            else -> mediaType.toString()
        }
    }
}
