package com.eligo.server.file

import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.file.entity.FileObjectEntity
import com.eligo.server.file.mapper.FileObjectMapper
import com.eligo.server.file.service.DefaultFileService
import com.eligo.server.file.service.ImageInspectionService
import com.eligo.server.file.storage.FileStorage
import com.eligo.server.file.storage.StoredObject
import com.eligo.server.post.service.PostReadService
import com.eligo.server.security.UserPrincipal
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.imageio.ImageIO
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.ArgumentMatchers.matches
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockMultipartFile

class FileServiceTests {
    private val owner = UserPrincipal(202L, "session")
    private val files = mock<FileObjectMapper>()
    private val storage = mock<FileStorage>()
    private val postReads = mock<PostReadService>()
    private lateinit var service: DefaultFileService

    @BeforeEach
    fun setup() {
        val properties = FileStorageProperties()
        service = DefaultFileService(
            files, storage, ImageInspectionService(), properties, postReads,
            Clock.fixed(Instant.parse("2026-07-22T08:00:00Z"), ZoneOffset.UTC)
        )
    }

    @Test
    fun rejectsEmptyAndOversizeFiles() {
        assertCode(AccountUserFileErrorCode.UNSUPPORTED_FILE_TYPE) {
            service.uploadAvatarImage(owner, image("a.png", "image/png", ByteArray(0)))
        }
        val oversized = mock<MockMultipartFile>()
        whenever(oversized.isEmpty).thenReturn(false)
        whenever(oversized.contentType).thenReturn("image/png")
        whenever(oversized.originalFilename).thenReturn("a.png")
        whenever(oversized.size).thenReturn(10L * 1024 * 1024 + 1)
        assertCode(AccountUserFileErrorCode.FILE_TOO_LARGE) {
            service.uploadAvatarImage(owner, oversized)
        }
    }

    @Test
    fun rejectsSpoofedExtensionUnsupportedMediaAndBadMagic() {
        assertCode(AccountUserFileErrorCode.UNSUPPORTED_FILE_TYPE) {
            service.uploadAvatarImage(owner, image("a.jpg", "image/png", png()))
        }
        assertCode(AccountUserFileErrorCode.UNSUPPORTED_FILE_TYPE) {
            service.uploadAvatarImage(owner, image("a.gif", "image/gif", png()))
        }
        assertCode(AccountUserFileErrorCode.UNSUPPORTED_FILE_TYPE) {
            service.uploadAvatarImage(owner, image("a.png", "image/png", byteArrayOf(1, 2, 3)))
        }
    }

    @Test
    fun acceptsDecodedPngAndStoresOnlySafeDisplayFilename() {
        doAnswer { invocation ->
            val entity = invocation.getArgument<FileObjectEntity>(0)
            entity.id = 77L
            1
        }.whenever(files).insert(any<FileObjectEntity>())
        whenever(storage.save(any(), any(), any<Long>())).thenAnswer { invocation ->
            StoredObject(invocation.getArgument(0), invocation.getArgument(2))
        }

        val view = service.uploadAvatarImage(owner, image("../../portrait.png", "image/png", png()))

        assertThat(view.fileId).isEqualTo("77")
        assertThat(view.scanStatus).isEqualTo("PASSED")
        assertThat(view.lifecycleStatus).isEqualTo("TEMPORARY")
        assertThat(view.url).isNull()
        verify(storage).save(matches("avatars/[0-9a-f-]+\\.png"), any(), any<Long>())
        verify(files).insert(argThat<FileObjectEntity> { file ->
            file.originalFilename == "portrait.png" &&
                file.objectKey?.startsWith("avatars/") == true &&
                file.objectKey?.contains("..") == false &&
                LocalDateTime.parse("2026-07-22T08:00:00") == file.createdAt &&
                file.createdAt == file.updatedAt
        })
    }

    @Test
    fun hidesOtherUsersFilesAndTreatsRepeatedTemporaryDeletionAsSuccess() {
        val other = file(1L, 203L, FileObjectEntity.SCAN_PASSED,
            FileObjectEntity.LIFECYCLE_TEMPORARY, null)
        whenever(files.findById(1L)).thenReturn(java.util.Optional.of(other))
        assertCode(CommonErrorCode.RESOURCE_NOT_FOUND) { service.getOwned(owner, 1L) }

        val deleted = file(2L, 202L, FileObjectEntity.SCAN_PASSED,
            FileObjectEntity.LIFECYCLE_DELETED, null)
        whenever(files.lockById(2L)).thenReturn(java.util.Optional.of(deleted))
        service.deleteTemporary(owner, 2L)
        verify(files, never()).markDeleted(any<Long>(), any(), any())
    }

    @Test
    fun rejectsExpiredFailedOrBoundFilesForAvatarAndDelete() {
        val expired = file(3L, 202L, FileObjectEntity.SCAN_PASSED,
            FileObjectEntity.LIFECYCLE_TEMPORARY, LocalDateTime.parse("2026-07-22T07:59:59"))
        whenever(files.lockById(3L)).thenReturn(java.util.Optional.of(expired))
        assertCode(AccountUserFileErrorCode.FILE_STATE_CONFLICT) { service.requireUsableAvatar(202L, 3L) }

        val failed = file(4L, 202L, FileObjectEntity.SCAN_FAILED,
            FileObjectEntity.LIFECYCLE_TEMPORARY, null)
        whenever(files.lockById(4L)).thenReturn(java.util.Optional.of(failed))
        assertCode(AccountUserFileErrorCode.FILE_STATE_CONFLICT) { service.requireUsableAvatar(202L, 4L) }

        val active = file(5L, 202L, FileObjectEntity.SCAN_PASSED,
            FileObjectEntity.LIFECYCLE_ACTIVE, null)
        whenever(files.lockById(5L)).thenReturn(java.util.Optional.of(active))
        assertCode(AccountUserFileErrorCode.FILE_STATE_CONFLICT) { service.deleteTemporary(owner, 5L) }
    }

    @Test
    fun expiredTemporaryFileHasNoUrlAndCannotBeOpened() {
        val expired = file(6L, 202L, FileObjectEntity.SCAN_PASSED,
            FileObjectEntity.LIFECYCLE_TEMPORARY, LocalDateTime.parse("2026-07-22T07:59:59"))
        whenever(files.findById(6L)).thenReturn(java.util.Optional.of(expired))

        assertThat(service.getOwned(owner, 6L).url).isNull()
        assertCode(CommonErrorCode.RESOURCE_NOT_FOUND) { service.openOwnedContent(owner, 6L) }
        verify(storage, never()).open(any())
    }

    @Test
    fun rejectedInspectionUsesPublishedContractName() {
        val rejected = file(7L, 202L, FileObjectEntity.SCAN_FAILED,
            FileObjectEntity.LIFECYCLE_TEMPORARY, LocalDateTime.parse("2026-07-22T09:00:00"))
        whenever(files.findById(7L)).thenReturn(java.util.Optional.of(rejected))

        assertThat(service.getOwned(owner, 7L).scanStatus).isEqualTo("REJECTED")
    }

    @Test
    fun physicalDeletionRunsOnlyAfterTransactionCommit() {
        val temporary = file(8L, 202L, FileObjectEntity.SCAN_PASSED,
            FileObjectEntity.LIFECYCLE_TEMPORARY, LocalDateTime.parse("2026-07-22T09:00:00"))
        whenever(files.lockById(8L)).thenReturn(java.util.Optional.of(temporary))
        whenever(files.markDeleted(8L, LocalDateTime.parse("2026-07-22T08:00:00"), 0)).thenReturn(1)

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization()
        try {
            service.deleteTemporary(owner, 8L)
            verify(storage, never()).delete(any())
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                .forEach { it.afterCommit() }
            verify(storage).delete("avatars/a.png")
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun acceptsDecodedJpegMagicAndContent() {
        doAnswer { invocation ->
            invocation.getArgument<FileObjectEntity>(0).id = 78L
            1
        }.whenever(files).insert(any<FileObjectEntity>())
        whenever(storage.save(any(), any(), any<Long>())).thenAnswer { invocation ->
            StoredObject(invocation.getArgument(0), invocation.getArgument(2))
        }
        assertThat(service.uploadAvatarImage(owner, image("portrait.jpg", "image/jpeg", jpeg())).scanStatus)
            .isEqualTo("PASSED")
    }

    @Test
    fun storesActivityAndPostPurposeInSeparateTemporaryImageNamespaces() {
        doAnswer { invocation ->
            val entity = invocation.getArgument<FileObjectEntity>(0)
            entity.id = if (entity.purpose == FileObjectEntity.PURPOSE_ACTIVITY) 79L else 80L
            1
        }.whenever(files).insert(any<FileObjectEntity>())
        whenever(storage.save(any(), any(), any<Long>())).thenAnswer { invocation ->
            StoredObject(invocation.getArgument(0), invocation.getArgument(2))
        }

        val activity = service.uploadImage(owner, image("activity.png", "image/png", png()),
            FileObjectEntity.PURPOSE_ACTIVITY)
        val post = service.uploadImage(owner, image("post.png", "image/png", png()),
            FileObjectEntity.PURPOSE_POST)

        assertThat(activity.purpose).isEqualTo("ACTIVITY")
        assertThat(post.purpose).isEqualTo("POST")
        verify(files).insert(argThat<FileObjectEntity> { file ->
            "ACTIVITY" == file.purpose && file.objectKey?.startsWith("activities/") == true
        })
        verify(files).insert(argThat<FileObjectEntity> { file ->
            "POST" == file.purpose && file.objectKey?.startsWith("posts/") == true
        })
    }

    @Test
    fun rejectsUnknownImagePurpose() {
        assertCode(CommonErrorCode.VALIDATION_FAILED) {
            service.uploadImage(owner, image("a.png", "image/png", png()), "UNKNOWN")
        }
    }

    @Test
    fun allowsAnonymousPublicActiveContentAndOwnerTemporaryPreview() {
        val active = file(9L, 202L, FileObjectEntity.SCAN_PASSED,
            FileObjectEntity.LIFECYCLE_ACTIVE, null)
        active.purpose = FileObjectEntity.PURPOSE_AVATAR
        whenever(files.findById(9L)).thenReturn(java.util.Optional.of(active))
        whenever(storage.open("avatars/a.png")).thenReturn(ByteArrayInputStream(byteArrayOf(1, 2, 3)))

        val publicContent = service.openContent(null, 9L)

        assertThat(publicContent.contentType).isEqualTo("image/png")
        assertThat(publicContent.cacheControl).isEqualTo("public, max-age=300")
        assertThat(publicContent.input.readAllBytes()).containsExactly(1, 2, 3)

        val temporary = file(10L, 202L, FileObjectEntity.SCAN_PASSED,
            FileObjectEntity.LIFECYCLE_TEMPORARY, LocalDateTime.parse("2026-07-22T09:00:00"))
        temporary.purpose = FileObjectEntity.PURPOSE_POST
        whenever(files.findById(10L)).thenReturn(java.util.Optional.of(temporary))
        whenever(storage.open("avatars/a.png")).thenReturn(ByteArrayInputStream(byteArrayOf(4)))

        val privateContent = service.openContent(owner, 10L)

        assertThat(privateContent.cacheControl).isEqualTo("no-store")
        assertThat(privateContent.input.readAllBytes()).containsExactly(4)
    }

    @Test
    fun hidesActivityContentFromAnonymousUntilPublicActivityReferencesIt() {
        val activityImage = file(13L, 202L, FileObjectEntity.SCAN_PASSED,
            FileObjectEntity.LIFECYCLE_ACTIVE, null)
        activityImage.purpose = FileObjectEntity.PURPOSE_ACTIVITY
        whenever(files.findById(13L)).thenReturn(java.util.Optional.of(activityImage))
        whenever(storage.open("avatars/a.png")).thenReturn(ByteArrayInputStream(byteArrayOf(5)))

        assertCode(CommonErrorCode.RESOURCE_NOT_FOUND) { service.openContent(null, 13L) }

        val ownerContent = service.openContent(owner, 13L)
        assertThat(ownerContent.cacheControl).isEqualTo("private, max-age=60")
        assertThat(ownerContent.input.readAllBytes()).containsExactly(5)
    }

    @Test
    fun allowsAnonymousActivityContentReferencedByPublicActivity() {
        val activityImage = file(14L, 202L, FileObjectEntity.SCAN_PASSED,
            FileObjectEntity.LIFECYCLE_ACTIVE, null)
        activityImage.purpose = FileObjectEntity.PURPOSE_ACTIVITY
        whenever(files.findById(14L)).thenReturn(java.util.Optional.of(activityImage))
        whenever(files.existsPublicActivityReference(14L)).thenReturn(true)
        whenever(storage.open("avatars/a.png")).thenReturn(ByteArrayInputStream(byteArrayOf(6)))

        val publicContent = service.openContent(null, 14L)

        assertThat(publicContent.cacheControl).isEqualTo("no-store")
        assertThat(publicContent.input.readAllBytes()).containsExactly(6)
    }

    @Test
    fun activePostMediaRequiresPostVisibilityAndIsNeverCached() {
        val postImage = file(15L, 202L, FileObjectEntity.SCAN_PASSED,
            FileObjectEntity.LIFECYCLE_ACTIVE, null)
        postImage.purpose = FileObjectEntity.PURPOSE_POST
        whenever(files.findById(15L)).thenReturn(java.util.Optional.of(postImage))
        whenever(storage.open("avatars/a.png"))
            .thenReturn(ByteArrayInputStream(byteArrayOf(7)))

        assertCode(CommonErrorCode.RESOURCE_NOT_FOUND) { service.openContent(null, 15L) }
        assertCode(CommonErrorCode.RESOURCE_NOT_FOUND) { service.openContent(owner, 15L) }
        verify(storage, never()).open(any())

        whenever(postReads.canReadMedia(null, 15L)).thenReturn(true)
        val publicContent = service.openContent(null, 15L)

        assertThat(publicContent.cacheControl).isEqualTo("no-store")
        assertThat(publicContent.input.readAllBytes()).containsExactly(7)
    }

    @Test
    fun hidesTemporaryContentFromAnonymousAndFailedContentFromEveryone() {
        val temporary = file(11L, 202L, FileObjectEntity.SCAN_PASSED,
            FileObjectEntity.LIFECYCLE_TEMPORARY, LocalDateTime.parse("2026-07-22T09:00:00"))
        whenever(files.findById(11L)).thenReturn(java.util.Optional.of(temporary))
        assertCode(CommonErrorCode.RESOURCE_NOT_FOUND) { service.openContent(null, 11L) }

        val failed = file(12L, 202L, FileObjectEntity.SCAN_FAILED,
            FileObjectEntity.LIFECYCLE_ACTIVE, null)
        whenever(files.findById(12L)).thenReturn(java.util.Optional.of(failed))
        assertCode(CommonErrorCode.RESOURCE_NOT_FOUND) { service.openContent(owner, 12L) }
    }

    private fun image(name: String, type: String, value: ByteArray): MockMultipartFile =
        MockMultipartFile("file", name, type, value)

    private fun png(): ByteArray {
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return output.toByteArray()
    }

    private fun jpeg(): ByteArray {
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "jpeg", output)
        return output.toByteArray()
    }

    private fun file(
        id: Long, uploader: Long, scan: Int, lifecycle: Int,
        expiresAt: LocalDateTime?
    ): FileObjectEntity {
        val entity = FileObjectEntity()
        entity.id = id
        entity.uploaderType = FileObjectEntity.UPLOADER_USER
        entity.uploaderId = uploader
        entity.accessLevel = FileObjectEntity.ACCESS_PUBLIC
        entity.purpose = FileObjectEntity.PURPOSE_AVATAR
        entity.scanStatus = scan
        entity.lifecycleStatus = lifecycle
        entity.expiresAt = expiresAt
        entity.version = 0
        entity.objectKey = "avatars/a.png"
        entity.contentType = "image/png"
        entity.sizeBytes = 68L
        return entity
    }

    private fun assertCode(code: com.eligo.server.common.error.ErrorCode, call: () -> Unit) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException::class.java) { exception ->
            assertThat(exception.errorCode).isEqualTo(code)
        }
    }
}
