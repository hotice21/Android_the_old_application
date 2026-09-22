package com.eligo.server.activity

import java.util.function.Function

import com.eligo.server.account.service.AccountStateLockService
import com.eligo.server.activity.dto.PersonalActivityCreateRequest
import com.eligo.server.activity.entity.ActivityEntity
import com.eligo.server.activity.entity.ActivityCreateIdempotencyTombstoneEntity
import com.eligo.server.activity.mapper.ActivityCreateIdempotencyTombstoneMapper
import com.eligo.server.activity.mapper.ActivityLifecycleEventMapper
import com.eligo.server.activity.mapper.ActivityMapper
import com.eligo.server.activity.mapper.ActivityMediaMapper
import com.eligo.server.activity.service.DefaultActivityCommandService
import com.eligo.server.common.error.BusinessException
import com.eligo.server.file.entity.FileObjectEntity
import com.eligo.server.file.mapper.FileObjectMapper
import com.eligo.server.organization.mapper.OrganizationMapper
import com.eligo.server.participation.service.ParticipationActivityCancellationService
import com.eligo.server.profile.entity.UserProfileEntity
import com.eligo.server.profile.mapper.UserProfileMapper
import com.eligo.server.profile.service.ProfileCompletionReader
import com.eligo.server.profile.service.RegionCatalog
import com.eligo.server.security.SensitiveDataCodec
import com.eligo.server.security.UserPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class ActivityContactCommandServiceTests {

    private val activities = mock(ActivityMapper::class.java)
    private val tombstones = mock(ActivityCreateIdempotencyTombstoneMapper::class.java)
    private val media = mock(ActivityMediaMapper::class.java)
    private val events = mock(ActivityLifecycleEventMapper::class.java)
    private val organizations = mock(OrganizationMapper::class.java)
    private val profiles = mock(UserProfileMapper::class.java)
    private val files = mock(FileObjectMapper::class.java)
    private val completion = mock(ProfileCompletionReader::class.java)
    private val regions = mock(RegionCatalog::class.java)
    private val redis = mock(StringRedisTemplate::class.java)
    private val values = mock(ValueOperations::class.java) as ValueOperations<String, String>
    private val cancellation = mock(ParticipationActivityCancellationService::class.java)
    private val accountStates = mock(AccountStateLockService::class.java)
    private val codec = mock(SensitiveDataCodec::class.java)
    private lateinit var service: DefaultActivityCommandService

    @BeforeEach
    fun setUp() {
        `when`(completion.isCompleted(USER_ID)).thenReturn(true)
        `when`(tombstones.findByScopeAndKey(any<String>(), any<String>()))
            .thenReturn(Optional.empty<ActivityCreateIdempotencyTombstoneEntity>())
        `when`(media.findByActivityId(any<Long>())).thenReturn(listOf())
        `when`(profiles.findByUserId(USER_ID)).thenReturn(Optional.of(profile()))
        `when`(redis.opsForValue()).thenReturn(values)
        `when`(values.get(any<String>())).thenReturn(null)
        `when`(codec.encrypt("13800000000")).thenReturn("phone-cipher")
        `when`(codec.encrypt("organizer-wechat")).thenReturn("wechat-cipher")
        `when`(codec.decrypt("phone-cipher")).thenReturn("13800000000")
        `when`(codec.decrypt("wechat-cipher")).thenReturn("organizer-wechat")
        `when`(files.lockById(9001L)).thenReturn(Optional.of(qrFile()))
        `when`(files.activate(any<Long>(), any(), any<Int>())).thenReturn(1)
        doAnswer { invocation ->
            invocation.getArgument<ActivityEntity>(0).id = 7001L
            1
        }.`when`(activities).insert(any<ActivityEntity>())
        service = DefaultActivityCommandService(
            activities,
            tombstones,
            media,
            events,
            organizations,
            profiles,
            files,
            completion,
            regions,
            redis,
            cancellation,
            accountStates,
            codec,
            null,
            Clock.fixed(NOW, ZoneOffset.UTC)
        )
    }

    @Test
    fun storesGenderAsThreeValueCodeAndEncryptsContacts() {
        val saved = arrayOfNulls<ActivityEntity>(1)
        doAnswer { invocation ->
            saved[0] = invocation.getArgument(0)
            saved[0]!!.id = 7001L
            1
        }.`when`(activities).insert(any<ActivityEntity>())

        val result = service.createPersonal(
            PRINCIPAL,
            request("FEMALE", null),
            "contact-fields-1"
        )

        assertThat(saved[0]!!.registrationGender).isEqualTo(3)
        assertThat(saved[0]!!.organizerPhoneCiphertext)
            .isEqualTo("phone-cipher".toByteArray(StandardCharsets.UTF_8))
        assertThat(saved[0]!!.organizerWechatCiphertext)
            .isEqualTo("wechat-cipher".toByteArray(StandardCharsets.UTF_8))
        assertThat(saved[0]!!.organizerWechatQrFileId).isEqualTo(9001L)
        assertThat(result.view.registrationGender).isEqualTo("FEMALE")
        assertThat(result.view.organizerPhone).isEqualTo("13800000000")
        assertThat(result.view.organizerWechat).isEqualTo("organizer-wechat")
        assertThat(result.view.organizerWechatQr!!.url)
            .isEqualTo("/api/v1/activities/7001/organizer/wechat-qr")
        assertThat(result.view.refundPolicy).isNull()
    }

    @Test
    fun nonNullRefundPolicyIsRejectedBecausePaymentIsNotImplemented() {
        assertThatThrownBy {
            service.createPersonal(
                PRINCIPAL,
                request("UNLIMITED", "不支持退款"),
                "contact-fields-2"
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  (it as BusinessException).errorCode  })
            .isEqualTo(com.eligo.server.common.error.CommonErrorCode.VALIDATION_FAILED)
    }

    private fun request(gender: String, refundPolicy: String?): PersonalActivityCreateRequest {
        return PersonalActivityCreateRequest(
            title = "联系方式活动",
            categoryCode = "HIKING",
            mediaFileIds = listOf(),
            capacity = null,
            signupDetails = null,
            organizerMessage = null,
            registrationGender = gender,
            organizerPhone = "13800000000",
            organizerWechat = "organizer-wechat",
            organizerWechatQrFileId = "9001",
            refundPolicy = refundPolicy
        )
    }

    private fun profile(): UserProfileEntity {
        val profile = UserProfileEntity()
        profile.userId = USER_ID
        profile.nickname = "组织人"
        return profile
    }

    private fun qrFile(): FileObjectEntity {
        val file = FileObjectEntity()
        file.id = 9001L
        file.uploaderType = FileObjectEntity.UPLOADER_USER
        file.uploaderId = USER_ID
        file.purpose = FileObjectEntity.PURPOSE_ACTIVITY_CONTACT_QR
        file.accessLevel = FileObjectEntity.ACCESS_PRIVATE
        file.scanStatus = FileObjectEntity.SCAN_PASSED
        file.lifecycleStatus = FileObjectEntity.LIFECYCLE_TEMPORARY
        file.expiresAt = java.time.LocalDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC)
        file.version = 0
        return file
    }

    companion object {
        private const val USER_ID = 202L
        private val NOW = Instant.parse("2026-08-18T00:00:00Z")
        private val PRINCIPAL = UserPrincipal(USER_ID, "session")
    }
}
