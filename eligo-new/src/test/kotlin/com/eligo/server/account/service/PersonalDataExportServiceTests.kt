package com.eligo.server.account.service

import com.eligo.server.account.entity.UserEntity
import com.eligo.server.account.mapper.AccountSecurityEventMapper
import com.eligo.server.account.mapper.UserMapper
import com.eligo.server.account.mapper.UserPhoneBindingMapper
import com.eligo.server.agreement.mapper.AgreementConsentMapper
import com.eligo.server.file.FileStorageProperties
import com.eligo.server.file.entity.FileObjectEntity
import com.eligo.server.file.mapper.FileObjectMapper
import com.eligo.server.file.storage.FileStorage
import com.eligo.server.follow.entity.OrganizationFollowEntity
import com.eligo.server.follow.entity.UserFollowEntity
import com.eligo.server.follow.mapper.OrganizationFollowMapper
import com.eligo.server.follow.mapper.UserFollowMapper
import com.eligo.server.post.entity.PostEntity
import com.eligo.server.post.mapper.PostMapper
import com.eligo.server.profile.mapper.UserInterestTagMapper
import com.eligo.server.profile.mapper.UserProfileMapper
import com.eligo.server.security.SensitiveDataCodec
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.LocalDateTime
import java.util.Optional
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipInputStream
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import tools.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class PersonalDataExportServiceTests {
    @Test
    fun createsPrivateTwentyFourHourZipWithoutInternalSecurityFields() {
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
        val following = UserFollowEntity()
        following.id = 901L
        following.followerUserId = 202L
        following.followedUserId = 303L
        following.followedAt = LocalDateTime.parse("2026-08-16T08:00:00")
        val organization = OrganizationFollowEntity()
        organization.id = 902L
        organization.followerUserId = 202L
        organization.organizationId = 401L
        organization.followedAt = LocalDateTime.parse("2026-08-16T08:01:00")
        val personal = PostEntity()
        personal.id = 7001L
        personal.authorUserId = 202L
        personal.status = PostEntity.STATUS_DRAFT
        personal.visibility = PostEntity.VISIBILITY_PRIVATE
        personal.title = "本人动态"
        `when`(userFollows.findFollowingExportPage(202L, null, 100))
            .thenReturn(listOf(following))
        `when`(userFollows.findFollowerExportPage(202L, null, 100)).thenReturn(listOf())
        `when`(organizationFollows.findExportPage(202L, null, 100))
            .thenReturn(listOf(organization))
        `when`(posts.findPersonalExportPage(202L, null, 100)).thenReturn(listOf(personal))
        `when`(files.findExportableByUserId(202L, 21)).thenReturn(listOf())
        `when`(files.findExportablePersonalPostMedia(202L, 21)).thenReturn(listOf())
        `when`(files.insert(any<FileObjectEntity>())).thenAnswer {
            it.getArgument(0, FileObjectEntity::class.java).id = 801L
            1
        }
        val content = AtomicReference<ByteArray>()
        `when`(storage.save(any(), any<InputStream>(), any<Long>())).thenAnswer { invocation ->
            content.set(invocation.getArgument(1, InputStream::class.java).readAllBytes())
            null
        }
        val properties = FileStorageProperties()
        val service = PersonalDataExportService(users, profiles, interests, consents,
                phones, security, userFollows, organizationFollows, posts, files, storage, properties,
                mock(SensitiveDataCodec::class.java), PhoneMasker(), ObjectMapper())
        val expires = LocalDateTime.parse("2026-07-24T08:00:00")

        val result = service.create(202L, expires)

        assertThat(result.fileId).isEqualTo(801L)
        val metadata = ArgumentCaptor.forClass(FileObjectEntity::class.java)
        verify(files).insert(metadata.capture())
        assertThat(metadata.value.accessLevel).isEqualTo(2)
        assertThat(metadata.value.contentType).isEqualTo("application/zip")
        assertThat(metadata.value.expiresAt).isEqualTo(expires)
        ZipInputStream(ByteArrayInputStream(content.get())).use { zip ->
            assertThat(zip.nextEntry.name).isEqualTo("personal-data.json")
            val json = String(zip.readAllBytes())
            assertThat(json).contains("profile", "interests", "agreementConsents", "phone", "securityEvents", "files",
                    "followingUsers", "followingOrganizations", "followers", "posts", "本人动态")
            assertThat(json).doesNotContain("ipLookupHash", "detailJson")
        }
    }
}
