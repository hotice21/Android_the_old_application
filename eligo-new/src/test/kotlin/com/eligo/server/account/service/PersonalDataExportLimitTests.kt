package com.eligo.server.account.service

import com.eligo.server.account.entity.AccountSecurityEventEntity
import com.eligo.server.account.entity.UserEntity
import com.eligo.server.account.mapper.AccountSecurityEventMapper
import com.eligo.server.account.mapper.UserMapper
import com.eligo.server.account.mapper.UserPhoneBindingMapper
import com.eligo.server.agreement.mapper.AgreementConsentMapper
import com.eligo.server.file.FileStorageProperties
import com.eligo.server.file.entity.FileObjectEntity
import com.eligo.server.file.mapper.FileObjectMapper
import com.eligo.server.file.storage.FileStorage
import com.eligo.server.follow.mapper.OrganizationFollowMapper
import com.eligo.server.follow.mapper.UserFollowMapper
import com.eligo.server.post.mapper.PostMapper
import com.eligo.server.profile.mapper.UserInterestTagMapper
import com.eligo.server.profile.mapper.UserProfileMapper
import com.eligo.server.security.SensitiveDataCodec
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.HashSet
import java.util.Optional
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.mockito.kotlin.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class PersonalDataExportLimitTests {
    @Test
    fun rejectsExportWhenFileCountExceedsConfiguredLimit() {
        val fixture = Fixture()
        fixture.properties.export.maxFiles = 1
        `when`(fixture.files.findExportableByUserId(202L, 2))
            .thenReturn(listOf(fixture.file(1L, 1L), fixture.file(2L, 1L)))

        assertThatThrownBy { fixture.service().create(202L, fixture.expiresAt) }
            .isInstanceOf(PersonalDataExportService.ExportLimitExceededException::class.java)
    }

    @Test
    fun rejectsExportWhenActualStreamBytesExceedConfiguredLimit() {
        val fixture = Fixture()
        fixture.properties.export.maxTotalBytes = 4
        val file = fixture.file(1L, 4L)
        `when`(fixture.files.findExportableByUserId(202L, 21)).thenReturn(listOf(file))
        `when`(fixture.storage.open(file.objectKey!!))
            .thenReturn(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)))

        assertThatThrownBy { fixture.service().create(202L, fixture.expiresAt) }
            .isInstanceOf(PersonalDataExportService.ExportLimitExceededException::class.java)
    }

    @Test
    fun rejectsOversizedStructuredJsonAndCleansAllArtifacts() {
        val fixture = Fixture()
        fixture.properties.export.maxTotalBytes = 10L * 1024 * 1024
        val event = AccountSecurityEventEntity()
        event.id = 901L
        event.eventType = "LOGIN"
        event.severity = 1
        event.regionCode = "x".repeat(5 * 1024 * 1024)
        event.occurredAt = LocalDateTime.parse("2026-07-23T07:00:00")
        `when`(fixture.security.findExportPage(202L, null, null, 100)).thenReturn(listOf(event))
        val before = temporaryExportFiles()

        assertThatThrownBy { fixture.service().create(202L, fixture.expiresAt) }
            .isInstanceOf(PersonalDataExportService.ExportLimitExceededException::class.java)

        assertThat(temporaryExportFiles()).isEqualTo(before)
        verify(fixture.storage, never()).save(any(), any<InputStream>(), any<Long>())
        verify(fixture.files, never()).insert(any<FileObjectEntity>())
        verify(fixture.storage).delete(org.mockito.ArgumentMatchers.startsWith("exports/"))
    }

    @Test
    fun countsStructuredJsonAndAttachmentsAgainstOneTotalLimit() {
        val fixture = Fixture()
        fixture.properties.export.maxTotalBytes = 300
        val file = fixture.file(1L, 200L)
        `when`(fixture.files.findExportableByUserId(202L, 21)).thenReturn(listOf(file))
        `when`(fixture.storage.open(file.objectKey!!))
            .thenReturn(ByteArrayInputStream(ByteArray(200)))

        assertThatThrownBy { fixture.service().create(202L, fixture.expiresAt) }
            .isInstanceOf(PersonalDataExportService.ExportLimitExceededException::class.java)

        verify(fixture.storage, never()).save(any(), any<InputStream>(), any<Long>())
        verify(fixture.files, never()).insert(any<FileObjectEntity>())
        verify(fixture.storage).delete(org.mockito.ArgumentMatchers.startsWith("exports/"))
    }

    private fun temporaryExportFiles(): Set<Path> {
        val root = Path.of(System.getProperty("java.io.tmpdir"))
        Files.list(root).use { paths ->
            return paths.filter { path ->
                path.fileName.toString().startsWith("eligo-personal-data-")
            }.collect({ HashSet<Path>() }, { set, path -> set.add(path) }, { a, b -> a.addAll(b) })
        }
    }

    private class Fixture {
        val users = mock(UserMapper::class.java)
        val profiles = mock(UserProfileMapper::class.java)
        val interests = mock(UserInterestTagMapper::class.java)
        val consents = mock(AgreementConsentMapper::class.java)
        val phones = mock(UserPhoneBindingMapper::class.java)
        val security = mock(AccountSecurityEventMapper::class.java)
        val userFollows = mock(UserFollowMapper::class.java)
        val organizationFollows = mock(OrganizationFollowMapper::class.java)
        val posts = mock(PostMapper::class.java)
        val files = mock(FileObjectMapper::class.java)
        val storage = mock(FileStorage::class.java)
        val properties = FileStorageProperties()
        val expiresAt = LocalDateTime.parse("2026-07-24T08:00:00")

        init {
            val user = UserEntity()
            user.id = 202L
            user.status = 1
            user.createdAt = LocalDateTime.parse("2026-07-20T08:00:00")
            `when`(users.selectById(202L)).thenReturn(user)
            `when`(profiles.findByUserId(202L)).thenReturn(Optional.empty())
            `when`(interests.findAllByUserId(202L)).thenReturn(listOf())
            `when`(consents.findExportPage(202L, null, 100)).thenReturn(listOf())
            `when`(phones.findActiveByUserId(202L)).thenReturn(Optional.empty())
            `when`(security.findExportPage(202L, null, null, 100)).thenReturn(listOf())
            `when`(files.findExportableByUserId(202L, 21)).thenReturn(listOf())
            `when`(files.findExportablePersonalPostMedia(202L, 21)).thenReturn(listOf())
        }

        fun service(): PersonalDataExportService {
            return PersonalDataExportService(users, profiles, interests, consents,
                    phones, security, userFollows, organizationFollows, posts,
                    files, storage, properties,
                    mock(SensitiveDataCodec::class.java), PhoneMasker(), ObjectMapper())
        }

        fun file(id: Long, size: Long): FileObjectEntity {
            val file = FileObjectEntity()
            file.id = id
            file.objectKey = "avatars/7b5e7bb8-38b9-40d2-99bd-e07fd4dc0053.jpg"
            file.originalFilename = "avatar.jpg"
            file.contentType = "image/jpeg"
            file.sizeBytes = size
            return file
        }
    }
}
