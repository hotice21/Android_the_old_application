package com.eligo.server.file

import com.eligo.server.file.entity.FileObjectEntity
import com.eligo.server.file.mapper.FileObjectMapper
import com.eligo.server.file.service.DefaultFileService
import com.eligo.server.file.service.ImageInspectionService
import com.eligo.server.file.storage.FileStorage
import com.eligo.server.file.storage.StoredObject
import com.eligo.server.post.service.PostReadService
import com.eligo.server.security.UserPrincipal
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.imageio.ImageIO
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockMultipartFile

class FileContactQrPurposeTests {

    @Test
    fun acceptsPrivateActivityContactQrPurposeAndUsesPrivateNamespace() {
        val files = mock<FileObjectMapper>()
        val storage = mock<FileStorage>()
        val properties = FileStorageProperties()
        val service = DefaultFileService(
            files,
            storage,
            ImageInspectionService(),
            properties,
            mock<PostReadService>(),
            Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC)
        )

        doAnswer { invocation ->
            invocation.getArgument<FileObjectEntity>(0).id = 9001L
            1
        }.whenever(files).insert(any<FileObjectEntity>())
        whenever(storage.save(any(), any(), any<Long>())).thenAnswer { invocation ->
            StoredObject(invocation.getArgument(0), invocation.getArgument(2))
        }

        val result = service.uploadImage(
            UserPrincipal(202L, "session"),
            MockMultipartFile("file", "qr.png", "image/png", png()),
            FileObjectEntity.PURPOSE_ACTIVITY_CONTACT_QR
        )

        assertThat(result.purpose).isEqualTo("ACTIVITY_CONTACT_QR")
        verify(files).insert(org.mockito.kotlin.argThat<FileObjectEntity> { file ->
            file.purpose == "ACTIVITY_CONTACT_QR" &&
                file.objectKey?.startsWith("activity-contact-qrs/") == true &&
                file.accessLevel == FileObjectEntity.ACCESS_PRIVATE
        })
    }

    private fun png(): ByteArray {
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return output.toByteArray()
    }
}
