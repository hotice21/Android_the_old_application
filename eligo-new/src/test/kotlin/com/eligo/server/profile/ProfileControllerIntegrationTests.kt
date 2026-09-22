package com.eligo.server.profile

import com.eligo.server.account.service.PhoneBindingReader
import com.eligo.server.account.vo.PhoneBindingView
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.web.GlobalExceptionHandler
import com.eligo.server.config.security.SecurityConfig
import com.eligo.server.profile.controller.ProfileController
import com.eligo.server.profile.dto.UpdateProfileRequest
import com.eligo.server.profile.entity.UserProfileEntity
import com.eligo.server.profile.mapper.InterestTagMapper
import com.eligo.server.profile.mapper.UserInterestTagMapper
import com.eligo.server.profile.mapper.UserProfileChangeLogMapper
import com.eligo.server.profile.mapper.UserProfileMapper
import com.eligo.server.profile.service.DefaultProfileService
import com.eligo.server.profile.service.NicknameContentValidator
import com.eligo.server.profile.service.ProfileCompletionUpdater
import com.eligo.server.profile.service.ProfileService
import com.eligo.server.profile.service.RegionCatalog
import com.eligo.server.profile.vo.InterestTagView
import com.eligo.server.profile.vo.MyProfileView
import com.eligo.server.security.AccountRestrictionFilter
import com.eligo.server.security.AccountRestrictionReader
import com.eligo.server.security.SessionAccessReader
import com.eligo.server.security.SessionAuthenticationFilter
import com.eligo.server.security.UserPrincipal
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.LocalDate
import java.util.Optional

@WebMvcTest(ProfileController::class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(
    GlobalExceptionHandler::class, SecurityConfig::class,
    SessionAuthenticationFilter::class, AccountRestrictionFilter::class
)
class ProfileControllerIntegrationTests {
    private val PRINCIPAL = UserPrincipal(202L, "session-a")

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var profileService: ProfileService

    @MockitoBean
    private lateinit var sessionAccessReader: SessionAccessReader

    @MockitoBean
    private lateinit var accountRestrictionReader: AccountRestrictionReader

    @Test
    fun readsPrivateProfileWithCompleteBirthDate() {
        `when`(profileService.getMyProfile(PRINCIPAL)).thenReturn(profile())
        mockMvc.perform(get("/api/v1/users/me/profile").with(authentication(auth())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.userId").value("202"))
            .andExpect(jsonPath("$.data.birthDate").value("2000-01-02"))
            .andExpect(jsonPath("$.data.region.districtName").value("南山区"))
    }

    @Test
    fun updatesProfileUsingCodesOnly() {
        `when`(profileService.updateProfile(eq(PRINCIPAL), any())).thenReturn(profile())
        mockMvc.perform(
            put("/api/v1/users/me/profile").with(authentication(auth()))
                .contentType("application/json")
                .content(
                    """
                    {"nickname":"小艾","birthDate":"2000-01-02","gender":"FEMALE",
                     "provinceCode":"44","cityCode":"4403","districtCode":"440305",
                     "email":"xiaoyi@example.com","bio":null}
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.email").value("xiaoyi@example.com"))
            .andExpect(jsonPath("$.data.region.provinceName").value("广东省"))
    }

    @Test
    fun emailMayBeOmittedOverHttp() {
        `when`(profileService.updateProfile(eq(PRINCIPAL), any())).thenReturn(profile())

        mockMvc.perform(
            put("/api/v1/users/me/profile").with(authentication(auth()))
                .contentType("application/json")
                .content(
                    """
                    {"nickname":"小艾","birthDate":"2000-01-02","gender":"FEMALE",
                     "provinceCode":"44","cityCode":"4403","districtCode":"440305",
                     "bio":null}
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)

        verify(profileService)
            .updateProfile(
                eq(PRINCIPAL),
                argThat { request -> request.email == null }
            )
    }

    @Test
    fun existingNicknameMayBeOmittedOverHttp() {
        val profiles = mock(UserProfileMapper::class.java)
        val tags = mock(InterestTagMapper::class.java)
        val userTags = mock(UserInterestTagMapper::class.java)
        val logs = mock(UserProfileChangeLogMapper::class.java)
        val codec = mock(com.eligo.server.security.SensitiveDataCodec::class.java)
        val regions = mock(RegionCatalog::class.java)
        val nicknameContentValidator =
            mock(NicknameContentValidator::class.java)
        val phoneBindingReader = mock(PhoneBindingReader::class.java)
        val actualService =
            DefaultProfileService(
                profiles,
                tags,
                userTags,
                logs,
                codec,
                regions,
                nicknameContentValidator,
                phoneBindingReader,
                mock(ProfileCompletionUpdater::class.java),
                null,
                Clock.systemUTC()
            )
        val current = profileEntity()

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
                org.mockito.ArgumentMatchers.isNull(),
                eq(0)
            )
        ).thenReturn(1)
        `when`(profiles.findByUserId(202L)).thenReturn(Optional.of(current))
        `when`(profiles.findUserIdByEmailLookupHash(any<ByteArray>()))
            .thenReturn(Optional.empty())
        `when`(tags.findSelectedByUserId(202L)).thenReturn(listOf())
        `when`(codec.encrypt("2000-01-02")).thenReturn("密文:2000-01-02")
        `when`(codec.encrypt("xiaoyi@example.com")).thenReturn("密文:xiaoyi@example.com")
        `when`(codec.lookupHash("xiaoyi@example.com")).thenReturn(ByteArray(32))
        `when`(codec.decrypt("密文:2000-01-02")).thenReturn("2000-01-02")
        `when`(codec.decrypt("密文:xiaoyi@example.com")).thenReturn("xiaoyi@example.com")
        `when`(regions.resolve("44", "4403", "440305"))
            .thenReturn(
                RegionCatalog.Region(
                    "44", "广东省", "4403", "深圳市", "440305", "南山区"
                )
            )
        `when`(phoneBindingReader.current(202L)).thenReturn(PhoneBindingView.unbound())
        `when`(profileService.updateProfile(eq(PRINCIPAL), any()))
            .thenAnswer { invocation ->
                actualService.updateProfile(
                    PRINCIPAL,
                    invocation.getArgument(1, UpdateProfileRequest::class.java)
                )
            }

        mockMvc.perform(
            put("/api/v1/users/me/profile").with(authentication(auth()))
                .contentType("application/json")
                .content(
                    """
                    {"birthDate":"2000-01-02","gender":"FEMALE",
                     "provinceCode":"44","cityCode":"4403","districtCode":"440305",
                     "email":"xiaoyi@example.com","bio":null}
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.nickname").value("小艾"))

        verify(nicknameContentValidator, never()).validate(any())
    }

    @Test
    fun profileBusinessLengthIsCheckedAfterTrimming() {
        `when`(profileService.updateProfile(eq(PRINCIPAL), any())).thenReturn(profile())
        val nickname = "昵".repeat(20)
        val bio = "简".repeat(200)

        mockMvc.perform(
            put("/api/v1/users/me/profile").with(authentication(auth()))
                .contentType("application/json")
                .content(
                    """
                    {"nickname":"  %s  ","birthDate":"2000-01-02","gender":"FEMALE",
                     "provinceCode":"44","cityCode":"4403","districtCode":"440305",
                     "email":"xiaoyi@example.com","bio":"  %s  "}
                    """.trimIndent().format(nickname, bio)
                )
        )
            .andExpect(status().isOk)

        verify(profileService).updateProfile(
            eq(PRINCIPAL),
            argThat { request ->
                request.nickname == "  $nickname  " &&
                    request.bio == "  $bio  "
            }
        )
    }

    @Test
    fun invalidProfileReturnsValidationError() {
        mockMvc.perform(
            put("/api/v1/users/me/profile").with(authentication(auth()))
                .contentType("application/json").content("{}")
        )
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value(10001))
    }

    @Test
    fun nicknameLimitReturnsStableCode() {
        `when`(profileService.updateNickname(PRINCIPAL, "新昵称"))
            .thenThrow(BusinessException(AccountUserFileErrorCode.NICKNAME_UPDATE_TOO_FREQUENT))
        mockMvc.perform(
            patch("/api/v1/users/me/nickname").with(authentication(auth()))
                .contentType("application/json").content("""{"nickname":"新昵称"}""")
        )
            .andExpect(status().isTooManyRequests).andExpect(jsonPath("$.code").value(11202))
    }

    @Test
    fun listsEnabledInterestsAndReplacesSelection() {
        `when`(profileService.listEnabledInterests()).thenReturn(
            listOf(
                InterestTagView("11", "OUTDOOR", "户外", 10)
            )
        )
        `when`(profileService.replaceInterests(PRINCIPAL, listOf(11L, 12L))).thenReturn(profile())

        mockMvc.perform(get("/api/v1/interest-tags").with(authentication(auth())))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.items[0].interestTagId").value("11"))
        mockMvc.perform(
            put("/api/v1/users/me/interests").with(authentication(auth()))
                .contentType("application/json")
                .content("""{"interestTagIds":["11","12"]}""")
        )
            .andExpect(status().isOk).andExpect(jsonPath("$.data.userId").value("202"))
        verify(profileService).replaceInterests(PRINCIPAL, listOf(11L, 12L))
    }

    @Test
    fun interestTransportAllowsDuplicateBeyondBusinessLimit() {
        val body = StringBuilder("""{"interestTagIds":[""")
        for (id in 1..20) {
            if (id > 1) {
                body.append(',')
            }
            body.append('"').append(id).append('"')
        }
        body.append(""","20"]}""")

        `when`(profileService.replaceInterests(eq(PRINCIPAL), any())).thenReturn(profile())

        mockMvc.perform(
            put("/api/v1/users/me/interests").with(authentication(auth()))
                .contentType("application/json")
                .content(body.toString())
        )
            .andExpect(status().isOk)

        verify(profileService).replaceInterests(
            PRINCIPAL,
            listOf(
                1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L,
                11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L, 20L
            )
        )
    }

    private fun auth(): org.springframework.security.core.Authentication {
        return UsernamePasswordAuthenticationToken(PRINCIPAL, "", listOf())
    }

    private fun profileEntity(): UserProfileEntity {
        val profile = UserProfileEntity()
        profile.userId = 202L
        profile.nickname = "小艾"
        profile.birthDateCiphertext =
            "密文:2000-01-02".toByteArray(StandardCharsets.UTF_8)
        profile.emailCiphertext =
            "密文:xiaoyi@example.com".toByteArray(StandardCharsets.UTF_8)
        profile.emailLookupHash = ByteArray(32)
        profile.genderCode = 2
        profile.provinceCode = "44"
        profile.provinceName = "广东省"
        profile.cityCode = "4403"
        profile.cityName = "深圳市"
        profile.districtCode = "440305"
        profile.districtName = "南山区"
        profile.version = 0
        return profile
    }

    private fun profile(): MyProfileView {
        return MyProfileView(
            "202", "小艾", null, LocalDate.parse("2000-01-02"), "FEMALE",
            MyProfileView.RegionView("44", "广东省", "4403", "深圳市", "440305", "南山区"),
            "xiaoyi@example.com", null, listOf(),
            MyProfileView.PhoneBindingView(false, null), false, null, null
        )
    }

    @Test
    fun myProfileRequiresAuthentication() {
        mockMvc.perform(get("/api/v1/users/me/profile"))
            .andExpect(status().isUnauthorized)
    }
}
