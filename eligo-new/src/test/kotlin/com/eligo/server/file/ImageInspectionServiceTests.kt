package com.eligo.server.file

import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.file.service.ImageInspectionService
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ImageInspectionServiceTests {
    private val service = ImageInspectionService()

    @Test
    fun rejectsDimensionsBeforeDecodingOversizedImage() {
        val image = BufferedImage(8193, 1, BufferedImage.TYPE_INT_RGB)
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        val png = output.toByteArray()

        assertThatThrownBy { service.inspect("image/png", png) }
            .isInstanceOfSatisfying(BusinessException::class.java) { exception ->
                assertThat(exception.errorCode).isEqualTo(AccountUserFileErrorCode.FILE_SECURITY_CHECK_FAILED)
            }
    }
}
