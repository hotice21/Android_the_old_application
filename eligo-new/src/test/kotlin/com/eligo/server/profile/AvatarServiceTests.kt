package com.eligo.server.profile

import com.eligo.server.account.service.PhoneBindingReader
import com.eligo.server.account.vo.PhoneBindingView
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.file.entity.FileObjectEntity
import com.eligo.server.file.service.FileService
import com.eligo.server.file.vo.FileView
import com.eligo.server.profile.dto.UpdateAvatarRequest
import com.eligo.server.profile.entity.UserProfileEntity
import com.eligo.server.profile.mapper.InterestTagMapper
import com.eligo.server.profile.mapper.UserInterestTagMapper
import com.eligo.server.profile.mapper.UserProfileChangeLogMapper
import com.eligo.server.profile.mapper.UserProfileMapper
import com.eligo.server.profile.service.DefaultProfileService
import com.eligo.server.profile.service.NicknameContentValidator
import com.eligo.server.profile.service.ProfileCompletionUpdater
import com.eligo.server.profile.service.RegionCatalog
import com.eligo.server.security.SensitiveDataCodec
import com.eligo.server.security.UserPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.Mockito.`when`
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class AvatarServiceTests {
    private val OWNER = UserPrincipal(202L, "session")

    @Test
    fun activatingUsableOwnedFileUpdatesProfileInOneTransactionFlow() {
        val profiles = mock(UserProfileMapper::class.java)
        val tags = mock(InterestTagMapper::class.java)
        val userTags = mock(UserInterestTagMapper::class.java)
        val files = mock(FileService::class.java)
        val profile = profile()
        val file = FileObjectEntity()
        file.id = 77L
        file.version = 0

        val service = service(profiles, tags, userTags, files)
        `when`(files.requireUsableAvatar(202L, 77L)).thenReturn(file)
        `when`(profiles.lockByUserId(202L)).thenReturn(Optional.of(profile))
        `when`(profiles.updateAvatarFileId(202L, 77L, 0)).thenAnswer {
            profile.avatarFileId = 77L
            profile.version = 1
            1
        }
        `when`(profiles.findByUserId(202L)).thenReturn(Optional.of(profile))
        `when`(tags.findSelectedByUserId(202L)).thenReturn(listOf())
        `when`(userTags.countEnabledByUserId(202L)).thenReturn(0)
        `when`(files.getOwned(OWNER, 77L)).thenReturn(
            FileView(
                "77", "image/png", 68, "PUBLIC", "PASSED", "ACTIVE",
                null, "/api/v1/files/77/content"
            )
        )

        val result = service.updateAvatar(OWNER, UpdateAvatarRequest("77"))

        assertThat(result.avatar!!.fileId).isEqualTo("77")
        assertThat(result.avatar!!.url).isEqualTo("/api/v1/files/77/content")
        val order = inOrder(files, profiles)
        order.verify(files).requireUsableAvatar(202L, 77L)
        order.verify(profiles).lockByUserId(202L)
        order.verify(files).activateAvatar(file)
        order.verify(profiles).updateAvatarFileId(202L, 77L, 0)
        verify(files).getOwned(OWNER, 77L)
    }

    @Test
    fun rejectedInspectionCannotChangeAvatar() {
        val profiles = mock(UserProfileMapper::class.java)
        val files = mock(FileService::class.java)
        val service = service(
            profiles, mock(InterestTagMapper::class.java),
            mock(UserInterestTagMapper::class.java), files
        )
        `when`(files.requireUsableAvatar(202L, 77L))
            .thenThrow(BusinessException(AccountUserFileErrorCode.FILE_SECURITY_CHECK_FAILED))

        assertThatThrownBy { service.updateAvatar(OWNER, UpdateAvatarRequest("77")) }
            .isInstanceOfSatisfying(
                BusinessException::class.java
            ) { exception ->
                assertThat(exception.errorCode)
                    .isEqualTo(AccountUserFileErrorCode.FILE_SECURITY_CHECK_FAILED)
            }
        verify(profiles, never()).updateAvatarFileId(any<Long>(), any<Long>(), any<Int>())
    }

    private fun service(
        profiles: UserProfileMapper, tags: InterestTagMapper,
        userTags: UserInterestTagMapper, files: FileService
    ): DefaultProfileService {
        val phones = mock(PhoneBindingReader::class.java)
        `when`(phones.current(202L)).thenReturn(PhoneBindingView.unbound())
        return DefaultProfileService(
            profiles, tags, userTags,
            mock(UserProfileChangeLogMapper::class.java), mock(SensitiveDataCodec::class.java),
            mock(RegionCatalog::class.java), mock(NicknameContentValidator::class.java), phones,
            mock(ProfileCompletionUpdater::class.java), files,
            Clock.fixed(Instant.parse("2026-07-22T08:00:00Z"), ZoneOffset.UTC)
        )
    }

    private fun profile(): UserProfileEntity {
        val profile = UserProfileEntity()
        profile.userId = 202L
        profile.version = 0
        return profile
    }
}
