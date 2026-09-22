package com.eligo.server.file.service

import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.Arrays
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.stream.ImageInputStream
import org.springframework.stereotype.Service

@Service
class ImageInspectionService {
    fun inspect(contentType: String, bytes: ByteArray) {
        val jpeg = "image/jpeg" == contentType && startsWith(bytes, JPEG)
        val png = "image/png" == contentType && startsWith(bytes, PNG)
        if (!jpeg && !png) {
            throw BusinessException(AccountUserFileErrorCode.UNSUPPORTED_FILE_TYPE)
        }
        try {
            ByteArrayInputStream(bytes).use { raw ->
                val input = ImageIO.createImageInputStream(raw)
                if (input == null) {
                    throw failed()
                }
                val readers = ImageIO.getImageReaders(input)
                if (!readers.hasNext()) {
                    throw failed()
                }
                val reader = readers.next()
                try {
                    reader.setInput(input, true, true)
                    val width = reader.getWidth(0)
                    val height = reader.getHeight(0)
                    if (width <= 0 ||
                        height <= 0 ||
                        width > MAX_DIMENSION ||
                        height > MAX_DIMENSION ||
                        width.toLong() * height > MAX_PIXELS
                    ) {
                        throw failed()
                    }
                    if (reader.read(0) == null) {
                        throw failed()
                    }
                } finally {
                    reader.dispose()
                }
            }
        } catch (exception: IOException) {
            throw failed()
        } catch (exception: RuntimeException) {
            if (exception is BusinessException) {
                throw exception
            }
            throw failed()
        }
    }

    private fun startsWith(value: ByteArray, prefix: ByteArray): Boolean {
        return value.size >= prefix.size && Arrays.equals(Arrays.copyOf(value, prefix.size), prefix)
    }

    private fun failed(): BusinessException =
        BusinessException(AccountUserFileErrorCode.FILE_SECURITY_CHECK_FAILED)

    companion object {
        private val JPEG = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())
        private val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        private const val MAX_DIMENSION = 8192
        private const val MAX_PIXELS = 20_000_000L
    }
}
