package com.github.search5.yona.domain.support

import org.mozilla.universalchardet.UniversalDetector
import java.io.InputStream

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
}
