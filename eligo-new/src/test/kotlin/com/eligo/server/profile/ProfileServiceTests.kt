package com.eligo.server.profile

import com.eligo.server.account.entity.UserPhoneBindingEntity
import com.eligo.server.account.mapper.UserPhoneBindingMapper
import com.eligo.server.account.service.PhoneBindingReader
import com.eligo.server.account.vo.PhoneBindingView
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.profile.dto.UpdateProfileRequest
import com.eligo.server.profile.entity.InterestTagEntity
import com.eligo.server.profile.entity.UserProfileChangeLogEntity
import com.eligo.server.profile.entity.UserProfileEntity
import com.eligo.server.profile.mapper.InterestTagMapper
import com.eligo.server.profile.mapper.UserInterestTagMapper
import com.eligo.server.profile.mapper.UserProfileChangeLogMapper
import com.eligo.server.profile.mapper.UserProfileMapper
import com.eligo.server.profile.service.ClasspathRegionCatalog
import com.eligo.server.profile.service.DefaultProfileCompletionUpdater
import com.eligo.server.profile.service.DefaultProfileService
import com.eligo.server.profile.service.NicknameContentValidator
import com.eligo.server.profile.service.RegionCatalog
import com.eligo.server.profile.vo.MyProfileView
import com.eligo.server.security.SensitiveDataCodec
import com.eligo.server.security.UserPrincipal
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.dao.DuplicateKeyException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.SQLIntegrityConstraintViolationException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.HexFormat
import java.util.Optional

class ProfileServiceTests {
    private val PRINCIPAL = UserPrincipal(202L, "session-a")
    private val CLOCK = Clock.fixed(Instant.parse("2026-07-22T08:00:00Z"), ZoneOffset.UTC)
    private val profiles = mock(UserProfileMapper::class.java)
    private val tags = mock(InterestTagMapper::class.java)
    private val userTags = mock(UserInterestTagMapper::class.java)
    private val phoneBindings = mock(UserPhoneBindingMapper::class.java)
    private val logs = mock(UserProfileChangeLogMapper::class.java)
    private val codec = mock(SensitiveDataCodec::class.java)
    private val regions = mock(RegionCatalog::class.java)
    private val nicknameContentValidator = mock(NicknameContentValidator::class.java)
    private val phoneBindingReader = mock(PhoneBindingReader::class.java)
    private lateinit var service: DefaultProfileService

    @BeforeEach
    fun setUp() {
        val profileCompletionUpdater =
            DefaultProfileCompletionUpdater(
                profiles, userTags, phoneBindings, CLOCK
            )
        service =
            DefaultProfileService(
                profiles,
                tags,
                userTags,
                logs,
                codec,
                regions,
                nicknameContentValidator,
                phoneBindingReader,
                profileCompletionUpdater,
                null,
                CLOCK
            )
        `when`(codec.encrypt(any())).thenAnswer { i -> "密文:" + i.getArgument<Any>(0) }
        `when`(codec.decrypt(any())).thenAnswer { i ->
            i.getArgument<String>(0).replaceFirst("^密文:".toRegex(), "")
        }
        `when`(codec.lookupHash(any<String>())).thenReturn(ByteArray(32))
        `when`(profiles.findUserIdByEmailLookupHash(any<ByteArray>()))
            .thenReturn(Optional.empty())
        `when`(regions.resolve("44", "4403", "440305")).thenReturn(
            RegionCatalog.Region("44", "广东省", "4403", "深圳市", "440305", "南山区")
        )
        `when`(tags.findSelectedByUserId(202L)).thenReturn(listOf())
        `when`(phoneBindingReader.current(202L)).thenReturn(PhoneBindingView.unbound())
    }

    @Test
    fun privateProfileReadsRealPhoneBindingWithoutChangingCompletion() {
        val current = profile(false)
        `when`(profiles.findByUserId(202L)).thenReturn(Optional.of(current))
        `when`(phoneBindingReader.current(202L))
            .thenReturn(
                PhoneBindingView(
                    true,
                    "86",
                    "138****1234",
                    Instant.parse("2026-07-22T08:00:00Z")
                )
            )

        val result = service.getMyProfile(PRINCIPAL)

        assertThat(result.phoneBinding!!.bound).isTrue()
        assertThat(result.phoneBinding!!.maskedPhone).isEqualTo("138****1234")
        assertThat(result.profileCompleted).isFalse()
    }

    @Test
    fun profileBirthDateIsEncryptedAtRestAndPrivateViewDecryptsIt() {
        val current = profile(false)
        current.nickname = null
        `when`(profiles.lockByUserId(202L)).thenReturn(Optional.of(current))
        `when`(
            profiles.updateBasicProfile(
                eq(202L), eq("小艾"), any(), any(), any(), eq(2),
                eq("44"), eq("广东省"), eq("4403"), eq("深圳市"),
                eq("440305"), eq("南山区"), eq("喜欢徒步"), eq(0)
            )
        ).thenReturn(1)
        `when`(profiles.findByUserId(202L)).thenReturn(Optional.of(profile(false)))

        assertThat(service.updateProfile(PRINCIPAL, request("小艾")).birthDate)
            .isEqualTo(LocalDate.parse("2000-01-02"))
        verify(codec).encrypt("2000-01-02")
        verify(codec).decrypt("密文:2000-01-02")
        verify(nicknameContentValidator).validate("小艾")
    }

    @Test
    fun emailIsNormalizedBeforeEncryptionHashingAndOwnerEcho() {
        val current = profile(false)
        `when`(profiles.lockByUserId(202L)).thenReturn(Optional.of(current))
        `when`(codec.lookupHash("user@example.com")).thenReturn(ByteArray(32))
        `when`(
            profiles.updateBasicProfile(
                any<Long>(), any(), any(), any(), any(), any<Int>(), any(), any(),
                any(), any(), any(), any(), any(), any<Int>()
            )
        ).thenReturn(1)
        `when`(profiles.findByUserId(202L)).thenReturn(Optional.of(profile(false)))

        val result =
            service.updateProfile(PRINCIPAL, request(null, " User@Example.COM "))

        verify(codec).encrypt("user@example.com")
        verify(codec).lookupHash("user@example.com")
        assertThat(result.email).isEqualTo("user@example.com")
    }

    @Test
    fun blankEmailClearsEncryptedValueAndLookupHash() {
        val current = profile(false)
        val stored = profile(false)
        stored.emailCiphertext = null
        stored.emailLookupHash = null
        `when`(profiles.lockByUserId(202L)).thenReturn(Optional.of(current))
        `when`(
            profiles.updateBasicProfile(
                eq(202L),
                eq("小艾"),
                any(),
                isNull(),
                isNull(),
                eq(2),
                eq("44"),
                eq("广东省"),
                eq("4403"),
                eq("深圳市"),
                eq("440305"),
                eq("南山区"),
                eq("喜欢徒步"),
                eq(0)
            )
        ).thenReturn(1)
        `when`(profiles.findByUserId(202L)).thenReturn(Optional.of(stored))

        val result = service.updateProfile(PRINCIPAL, request(null, ""))

        assertThat(result.email).isNull()
        verify(codec, never()).lookupHash(any<String>())
    }

    @Test
    fun emailUniqueIndexRaceReturnsStableBusinessCode() {
        val current = profile(false)
        `when`(profiles.lockByUserId(202L)).thenReturn(Optional.of(current))
        `when`(
            profiles.updateBasicProfile(
                any<Long>(), any(), any(), any(), any(), any<Int>(), any(), any(),
                any(), any(), any(), any(), any(), any<Int>()
            )
        ).thenThrow(
            DuplicateKeyException(
                "重复键",
                SQLIntegrityConstraintViolationException(
                    "Duplicate entry for key 'user_profiles.uk_profile_email_lookup_hash'",
                    "23000",
                    1062
                )
            )
        )

        assertThatThrownBy {
            service.updateProfile(
                PRINCIPAL, request(null, "user@example.com")
            )
        }.isInstanceOfSatisfying(
            BusinessException::class.java
        ) { error ->
            assertThat(error.errorCode.code).isEqualTo(11203)
        }
    }

    @Test
    fun emailAlreadyUsedByAnotherUserIsRejectedBeforeUpdate() {
        val current = profile(false)
        val lookupHash = ByteArray(32)
        `when`(profiles.lockByUserId(202L)).thenReturn(Optional.of(current))
        `when`(codec.lookupHash("used@example.com")).thenReturn(lookupHash)
        `when`(profiles.findUserIdByEmailLookupHash(lookupHash))
            .thenReturn(Optional.of(303L))

        assertThatThrownBy {
            service.updateProfile(
                PRINCIPAL, request(null, "used@example.com")
            )
        }.isInstanceOfSatisfying(
            BusinessException::class.java
        ) { error ->
            assertThat(error.errorCode)
                .isEqualTo(AccountUserFileErrorCode.EMAIL_ALREADY_USED)
        }
        verify(profiles, never()).updateBasicProfile(
            any<Long>(), any(), any(), any(), any(), any<Int>(),
            any(), any(), any(), any(), any(), any(), any(), any<Int>()
        )
    }

    @Test
    fun firstProfileNicknameIsValidatedAfterTrimming() {
        val current = profile(false)
        current.nickname = null
        `when`(profiles.lockByUserId(202L)).thenReturn(Optional.of(current))

        assertThatThrownBy { service.updateProfile(PRINCIPAL, request(" a ")) }
            .isInstanceOfSatisfying(
                BusinessException::class.java
            ) { error ->
                assertThat(error.errorCode)
                    .isEqualTo(CommonErrorCode.VALIDATION_FAILED)
            }
        verify(profiles, never()).updateBasicProfile(
            any<Long>(), any(), any(), any(), any(), any<Int>(), any(), any(),
            any(), any(), any(), any(), any(), any<Int>()
        )
    }

    @Test
    fun firstProfileNicknameRejectsInternalControlCharacters() {
        val current = profile(false)
        current.nickname = null
        `when`(profiles.lockByUserId(202L)).thenReturn(Optional.of(current))

        assertThatThrownBy { service.updateProfile(PRINCIPAL, request("小\n艾")) }
            .isInstanceOfSatisfying(
                BusinessException::class.java
            ) { error ->
                assertThat(error.errorCode)
                    .isEqualTo(CommonErrorCode.VALIDATION_FAILED)
            }
        verify(profiles, never()).updateBasicProfile(
            any<Long>(), any(), any(), any(), any(), any<Int>(), any(), any(),
            any(), any(), any(), any(), any(), any<Int>()
        )
    }

    @Test
    fun existingNicknameMayBeOmittedWhenUpdatingOtherProfileFields() {
        val current = profile(false)
        `when`(profiles.lockByUserId(202L)).thenReturn(Optional.of(current))
        `when`(
            profiles.updateBasicProfile(
                eq(202L),
                eq("小艾"),
                any(),
                any(),
                any(),
                eq(2),
                eq("44"),
                eq("广东省"),
                eq("4403"),
                eq("深圳市"),
                eq("440305"),
                eq("南山区"),
                eq("喜欢徒步"),
                eq(0)
            )
        ).thenReturn(1)
        `when`(profiles.findByUserId(202L)).thenReturn(Optional.of(profile(false)))

        assertThatCode { service.updateProfile(PRINCIPAL, request(null)) }
            .doesNotThrowAnyException()

        verify(nicknameContentValidator, never()).validate(any())
        verify(profiles).updateBasicProfile(
            eq(202L),
            eq("小艾"),
            any(),
            any(),
            any(),
            eq(2),
            eq("44"),
            eq("广东省"),
            eq("4403"),
            eq("深圳市"),
            eq("440305"),
            eq("南山区"),
            eq("喜欢徒步"),
            eq(0)
        )
    }

    @Test
    fun ordinaryProfileUpdateCannotChangeExistingNickname() {
        `when`(profiles.lockByUserId(202L)).thenReturn(Optional.of(profile(false)))
        assertThatThrownBy { service.updateProfile(PRINCIPAL, request("另一个昵称")) }
            .isInstanceOfSatisfying(BusinessException::class.java) { e ->
                assertThat(e.errorCode).isEqualTo(CommonErrorCode.CONFLICT)
            }
        verify(profiles, never()).updateBasicProfile(
            any<Long>(), any(), any(), any(), any(), any<Int>(),
            any(), any(), any(), any(), any(), any(), any(), any<Int>()
        )
    }

    @Test
    fun firstNicknameDoesNotConsumeAllowance() {
        val current = profile(false)
        current.nickname = null
        `when`(profiles.lockByUserId(202L)).thenReturn(Optional.of(current))
        `when`(profiles.setFirstNickname(202L, "初始昵称", 0)).thenReturn(1)
        val saved = profile(false)
        saved.nickname = "初始昵称"
        `when`(profiles.findByUserId(202L)).thenReturn(Optional.of(saved))

        val result = service.updateNickname(PRINCIPAL, " 初始昵称 ")
        assertThat(result.nicknameChangedAt).isNull()
        assertThat(result.nextNicknameChangeAt).isNull()
        verify(logs, never()).insert(any<UserProfileChangeLogEntity>())
        verify(nicknameContentValidator).validate("初始昵称")
    }

    @Test
    fun thirtyDayConditionalUpdateRejectsConcurrentLoser() {
        val current = profile(false)
        current.nickname = "旧昵称"
        current.nicknameChangedAt = LocalDateTime.parse("2026-06-01T08:00:00")
        current.version = 7
        `when`(profiles.lockByUserId(202L)).thenReturn(Optional.of(current))
        `when`(
            profiles.updateNicknameWithLimit(
                202L, "新昵称",
                LocalDateTime.parse("2026-07-22T08:00:00"),
                LocalDateTime.parse("2026-06-22T08:00:00"), 7
            )
        ).thenReturn(0)

        assertThatThrownBy { service.updateNickname(PRINCIPAL, "新昵称") }
            .isInstanceOfSatisfying(BusinessException::class.java) { e ->
                assertThat(e.errorCode).isEqualTo(AccountUserFileErrorCode.NICKNAME_UPDATE_TOO_FREQUENT)
            }
        verify(logs, never()).insert(any<UserProfileChangeLogEntity>())
    }

    @Test
    fun successfulNicknameChangeEncryptsAppendOnlyAudit() {
        val current = profile(false)
        current.nickname = "旧昵称"
        current.nicknameChangedAt = LocalDateTime.parse("2026-06-01T08:00:00")
        current.version = 7
        `when`(profiles.lockByUserId(202L)).thenReturn(Optional.of(current))
        `when`(
            profiles.updateNicknameWithLimit(
                any<Long>(), any(), any(), any(), any<Int>()
            )
        ).thenReturn(1)
        val saved = profile(false)
        saved.nickname = "新昵称"
        saved.nicknameChangedAt = LocalDateTime.parse("2026-07-22T08:00:00")
        `when`(profiles.findByUserId(202L)).thenReturn(Optional.of(saved))

        assertThat(service.updateNickname(PRINCIPAL, "新昵称").nextNicknameChangeAt)
            .isEqualTo(Instant.parse("2026-08-21T08:00:00Z"))
        verify(codec).encrypt("旧昵称")
        verify(codec).encrypt("新昵称")
        verify(logs).insert(any<UserProfileChangeLogEntity>())
    }

    @Test
    fun interestsAreDeduplicatedValidatedThenAtomicallyReplaced() {
        `when`(profiles.lockByUserId(202L)).thenReturn(Optional.of(profile(false)))
        `when`(tags.findEnabledByIds(listOf(11L, 12L))).thenReturn(listOf(tag(11L), tag(12L)))
        `when`(profiles.findByUserId(202L)).thenReturn(Optional.of(profile(false)))
        service.replaceInterests(PRINCIPAL, listOf(11L, 12L, 11L))

        val order = inOrder(profiles, userTags)
        order.verify(profiles).lockByUserId(202L)
        order.verify(userTags).deleteByUserId(202L)
        order.verify(userTags).insertSelection(202L, 11L, LocalDateTime.parse("2026-07-22T08:00:00"))
        order.verify(userTags).insertSelection(202L, 12L, LocalDateTime.parse("2026-07-22T08:00:00"))
    }

    @Test
    fun interestLimitIsAppliedAfterDeduplication() {
        val rawIds = ArrayList<Long>()
        for (id in 1L..20L) {
            rawIds.add(id)
        }
        rawIds.add(20L)
        val uniqueIds = rawIds.subList(0, 20)
        val enabledTags = uniqueIds.map { tag(it) }

        `when`(profiles.lockByUserId(202L)).thenReturn(Optional.of(profile(false)))
        `when`(tags.findEnabledByIds(uniqueIds)).thenReturn(enabledTags)
        `when`(profiles.findByUserId(202L)).thenReturn(Optional.of(profile(false)))

        service.replaceInterests(PRINCIPAL, rawIds)

        verify(tags).findEnabledByIds(uniqueIds)
        verify(userTags, times(20))
            .insertSelection(eq(202L), any<Long>(), eq(LocalDateTime.parse("2026-07-22T08:00:00")))
    }

    @Test
    fun disabledTagIsRejectedBeforeReplacement() {
        `when`(profiles.lockByUserId(202L)).thenReturn(Optional.of(profile(false)))
        `when`(tags.findEnabledByIds(listOf(11L, 99L))).thenReturn(listOf(tag(11L)))
        assertThatThrownBy { service.replaceInterests(PRINCIPAL, listOf(11L, 99L)) }
            .isInstanceOfSatisfying(BusinessException::class.java) { e ->
                assertThat(e.errorCode).isEqualTo(CommonErrorCode.VALIDATION_FAILED)
            }
        verify(userTags, never()).deleteByUserId(any<Long>())
    }

    @Test
    fun completionDoesNotRequireEmail() {
        val current = profile(false)
        current.emailCiphertext = null
        current.emailLookupHash = null
        `when`(profiles.lockByUserId(202L)).thenReturn(Optional.of(current))
        `when`(userTags.countEnabledByUserId(202L)).thenReturn(1)
        `when`(phoneBindings.findActiveByUserId(202L))
            .thenReturn(Optional.of(UserPhoneBindingEntity()))
        `when`(
            profiles.updateCompletedAt(
                202L, LocalDateTime.parse("2026-07-22T08:00:00"), 0
            )
        ).thenReturn(1)

        assertThat(service.recalculateCompletion(202L)).isTrue()
        verify(profiles)
            .updateCompletedAt(
                202L, LocalDateTime.parse("2026-07-22T08:00:00"), 0
            )
    }

    @Test
    fun completionDoesNotRequireAvatarButDoesRequireActivePhone() {
        val current = profile(false)
        `when`(profiles.lockByUserId(202L)).thenReturn(Optional.of(current))
        `when`(userTags.countEnabledByUserId(202L)).thenReturn(1)
        `when`(phoneBindings.findActiveByUserId(202L))
            .thenReturn(Optional.of(UserPhoneBindingEntity()))
        `when`(phoneBindingReader.current(202L))
            .thenReturn(
                PhoneBindingView(
                    true, "86", "138****1234", Instant.parse("2026-07-22T08:00:00Z")
                )
            )
        `when`(profiles.updateCompletedAt(any<Long>(), anyOrNull(), any<Int>())).thenReturn(1)

        assertThat(service.recalculateCompletion(202L)).isTrue()
        verify(profiles).updateCompletedAt(
            202L, LocalDateTime.parse("2026-07-22T08:00:00"), 0
        )

        current.completedAt = LocalDateTime.parse("2026-07-22T08:00:00")
        current.version = 1
        `when`(phoneBindings.findActiveByUserId(202L)).thenReturn(Optional.empty())
        `when`(phoneBindingReader.current(202L)).thenReturn(PhoneBindingView.unbound())

        assertThat(service.recalculateCompletion(202L)).isFalse()
        verify(profiles).updateCompletedAt(202L, null, 1)
    }

    @Test
    fun regionResourceHasUniqueThreeLevelsAndRepresentativePaths() {
        val mapper = ObjectMapper()
        val root: JsonNode
        javaClass.getResourceAsStream("/regions/cn-regions.json").use { `in` ->
            assertThat(`in`).isNotNull()
            root = mapper.readTree(`in`)
        }
        assertThat(root.path("sourceVersion").asText()).isEqualTo("2025.251231.260403")
        val codes = HashSet<String>()
        root.path("regions").forEach { p ->
            assertThat(codes.add(p.path("code").asText())).isTrue()
            assertThat(p.path("children").isArray).isTrue()
            p.path("children").forEach { c ->
                assertThat(codes.add(c.path("code").asText())).isTrue()
                assertThat(c.path("children").isArray).isTrue()
                c.path("children").forEach { d ->
                    assertThat(codes.add(d.path("code").asText())).isTrue()
                    assertThat(d.has("children")).isFalse()
                }
            }
        }
        val catalog = ClasspathRegionCatalog(mapper)
        assertThat(catalog.resolve("44", "4403", "440305").districtName).isEqualTo("南山区")
        assertThat(catalog.resolve("11", "1101", "110101").districtName).isEqualTo("东城区")
        assertThat(catalog.resolve("46", "469001", "469001000").districtName).isEqualTo("五指山市")
        assertThat(catalog.resolve("71", "7101", "710101").districtName).isEqualTo("中正区")
        assertThat(catalog.resolve("81", "8100", "810101000").districtName).isEqualTo("中西区")
        assertThat(catalog.resolve("82", "8200", "820101000").districtName).isEqualTo("花地玛堂区")
        assertThatThrownBy { catalog.resolve("44", "1101", "110101") }.isInstanceOf(BusinessException::class.java)
    }

    @Test
    fun regionReadmeMatchesNormalizedDigestAndLockedInput() {
        val json: ByteArray
        val readme: String
        javaClass.getResourceAsStream("/regions/cn-regions.json").use { `in` ->
            assertThat(`in`).isNotNull()
            json = `in`!!.readAllBytes()
        }
        javaClass.getResourceAsStream("/regions/README.md").use { `in` ->
            assertThat(`in`).isNotNull()
            readme = String(`in`!!.readAllBytes(), StandardCharsets.UTF_8)
        }
        val digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json))
        assertThat(readme).contains(digest)
            .contains("0b181b4105c32b2631b1c8c8654859f10684c3748b188ebe08f342291dec1169")
    }

    private fun request(nickname: String?): UpdateProfileRequest {
        return request(nickname, "xiaoyi@example.com")
    }

    private fun request(nickname: String?, email: String?): UpdateProfileRequest {
        return UpdateProfileRequest(
            nickname, LocalDate.parse("2000-01-02"),
            "FEMALE", "44", "4403", "440305", email, "喜欢徒步"
        )
    }

    private fun profile(avatar: Boolean): UserProfileEntity {
        val p = UserProfileEntity()
        p.userId = 202L
        p.nickname = "小艾"
        p.avatarFileId = if (avatar) 901L else null
        p.birthDateCiphertext = "密文:2000-01-02".toByteArray(StandardCharsets.UTF_8)
        p.emailCiphertext = "密文:user@example.com".toByteArray(StandardCharsets.UTF_8)
        p.emailLookupHash = ByteArray(32)
        p.genderCode = 2
        p.provinceCode = "44"
        p.provinceName = "广东省"
        p.cityCode = "4403"
        p.cityName = "深圳市"
        p.districtCode = "440305"
        p.districtName = "南山区"
        p.version = 0
        return p
    }

    private fun tag(id: Long): InterestTagEntity {
        val t = InterestTagEntity()
        t.id = id
        t.tagCode = "TAG_$id"
        t.tagName = "标签$id"
        t.sortOrder = id.toInt()
        t.status = 1
        return t
    }
}
