package com.eligo.server.security

import com.eligo.server.account.service.AccountDataService
import com.eligo.server.account.service.AuthService
import com.eligo.server.account.service.SessionService
import com.eligo.server.activity.service.ActivityCommandService
import com.eligo.server.activity.service.ActivityReadService
import com.eligo.server.activity.vo.ActivityMapView
import com.eligo.server.agreement.service.AgreementService
import com.eligo.server.agreement.vo.AgreementConsentView
import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.api.Result
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.config.security.SecurityConfig
import com.eligo.server.favorite.service.ActivityFavoriteService
import com.eligo.server.comment.service.ActivityCommentService
import com.eligo.server.file.service.FileService
import com.eligo.server.file.vo.FileView
import com.eligo.server.follow.service.FollowCommandService
import com.eligo.server.follow.service.FollowReadService
import com.eligo.server.follow.vo.FollowStateView
import com.eligo.server.follow.vo.PublicUserProfileView
import com.eligo.server.organization.service.OrganizationAccessService
import com.eligo.server.organization.vo.PublicOrganizationDetailView
import com.eligo.server.post.service.PostCommandService
import com.eligo.server.post.service.PostReadService
import com.eligo.server.post.vo.PublicPostView
import com.eligo.server.profile.service.ProfileService
import com.eligo.server.recommendation.service.RecommendedFeedService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.OptionalLong

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SecurityConfig::class, AccountRestrictionIntegrationTests.TestController::class)
class AccountRestrictionIntegrationTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jwtTokenService: JwtTokenService

    @MockitoBean
    private lateinit var sessionAccessReader: SessionAccessReader

    @MockitoBean
    private lateinit var restrictionReader: AccountRestrictionReader

    @MockitoBean
    private lateinit var agreementService: AgreementService

    @MockitoBean
    private lateinit var authService: AuthService

    @MockitoBean
    private lateinit var accountDataService: AccountDataService

    @MockitoBean
    private lateinit var sessionService: SessionService

    @MockitoBean
    private lateinit var profileService: ProfileService

    @MockitoBean
    private lateinit var fileService: FileService

    @MockitoBean
    private lateinit var activityCommandService: ActivityCommandService

    @MockitoBean
    private lateinit var activityReadService: ActivityReadService

    @MockitoBean
    private lateinit var followCommandService: FollowCommandService

    @MockitoBean
    private lateinit var followReadService: FollowReadService

    @MockitoBean
    private lateinit var activityFavoriteService: ActivityFavoriteService

    @MockitoBean
    private lateinit var activityCommentService: ActivityCommentService

    @MockitoBean
    private lateinit var organizationAccessService: OrganizationAccessService

    @MockitoBean
    private lateinit var postCommandService: PostCommandService

    @MockitoBean
    private lateinit var postReadService: PostReadService

    @MockitoBean
    private lateinit var recommendedFeedService: RecommendedFeedService

    private lateinit var accessToken: String

    @BeforeEach
    fun setUp() {
        accessToken = jwtTokenService.issueTokenPair(UserPrincipal(202L, "session-a")).accessToken
        `when`(sessionAccessReader.requireActive(any<String>(), any<Long>()))
            .thenReturn(SessionAccessState(401L, 301L, 1))
        `when`(agreementService.current(OptionalLong.of(202L), Optional.empty())).thenReturn(listOf())
        `when`(agreementService.consent(any<UserPrincipal>(), eq(101L)))
            .thenReturn(
                AgreementConsentView(
                    "101", Instant.parse("2026-07-21T08:00:00Z"), false
                )
            )
        `when`(sessionService.listActive(any<UserPrincipal>())).thenReturn(listOf())
        `when`(accountDataService.currentDeactivation(any<UserPrincipal>())).thenReturn(Optional.empty())
        `when`(recommendedFeedService.list(any(), any(), eq(20)))
            .thenReturn(CursorPage(listOf<PublicPostView>(), null, false))
        val deactivation = com.eligo.server.account.vo.DeactivationView(
            "501", "WAITING", Instant.parse("2026-07-23T08:00:00Z"),
            Instant.parse("2026-07-30T08:00:00Z"), true
        )
        `when`(accountDataService.requestDeactivationOutcome(any<UserPrincipal>(), any()))
            .thenReturn(
                com.eligo.server.account.service.DeactivationRequestOutcome(
                    deactivation, true
                )
            )
    }

    @Test
    fun deactivationPendingAccountIsRestrictedBeforeAgreementAndProfileChecks() {
        `when`(restrictionReader.read(202L))
            .thenReturn(AccountRestrictionReader.State(2, false, listOf(101L)))

        perform(post("/test-support/ordinary-action"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value(11501))
    }

    @Test
    fun requiredAgreementIsRestrictedBeforeIncompleteProfile() {
        `when`(restrictionReader.read(202L))
            .thenReturn(AccountRestrictionReader.State(1, false, listOf(101L)))

        perform(post("/test-support/ordinary-action"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value(11301))
    }

    @Test
    fun invalidSessionStopsBeforeAgreementAndProfileRestrictions() {
        doThrow(
            BusinessException(
                AccountUserFileErrorCode.LOGIN_SESSION_INVALID
            )
        ).`when`(sessionAccessReader).requireActive("session-a", 202L)

        perform(post("/test-support/ordinary-action"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value(11005))

        verify(restrictionReader, never()).read(202L)
    }

    @Test
    fun incompleteProfileRestrictsOrdinaryBusiness() {
        `when`(restrictionReader.read(202L))
            .thenReturn(AccountRestrictionReader.State(1, false, listOf()))

        perform(post("/test-support/ordinary-action"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value(11201))
    }

    @Test
    fun incompleteProfileAllowsPublicationCommandToReachActivityService() {
        `when`(restrictionReader.read(202L))
            .thenReturn(AccountRestrictionReader.State(1, false, listOf()))

        perform(put("/api/v1/activities/1001/publication"))
            .andExpect(status().isOk)

        verify(activityCommandService).publish(any<UserPrincipal>(), eq(1001L))
    }

    @Test
    fun incompleteProfileAllowsPublicActivityMapRead() {
        `when`(restrictionReader.read(202L))
            .thenReturn(AccountRestrictionReader.State(1, false, listOf()))
        `when`(
            activityReadService.listActivitiesOnMap(
                BigDecimal("22.5"),
                BigDecimal("22.6"),
                BigDecimal("113.9"),
                BigDecimal("114.2"),
                null, 100
            )
        ).thenReturn(ActivityMapView(listOf(), false))

        perform(
            get("/api/v1/activities/map")
                .queryParam("minLatitude", "22.5")
                .queryParam("maxLatitude", "22.6")
                .queryParam("minLongitude", "113.9")
                .queryParam("maxLongitude", "114.2")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items").isArray)
    }

    fun incompleteProfileAllowsIdempotentFollowCommandsAndStateQueries() {
        `when`(restrictionReader.read(202L))
            .thenReturn(AccountRestrictionReader.State(1, false, listOf()))
        `when`(followCommandService.followUser(any<UserPrincipal>(), eq(303L)))
            .thenReturn(FollowStateView(true, Instant.parse("2026-08-16T01:02:03Z")))
        `when`(followCommandService.unfollowUser(any<UserPrincipal>(), eq(303L)))
            .thenReturn(FollowStateView(false, null))
        `when`(followCommandService.getUserState(any<UserPrincipal>(), eq(303L)))
            .thenReturn(FollowStateView(true, Instant.parse("2026-08-16T01:02:03Z")))

        perform(put("/api/v1/follows/users/303"))
            .andExpect(status().isOk)
        perform(delete("/api/v1/follows/users/303"))
            .andExpect(status().isOk)
        perform(get("/api/v1/users/303/follow-state"))
            .andExpect(status().isOk)

        verify(followCommandService).followUser(any<UserPrincipal>(), eq(303L))
        verify(followCommandService).unfollowUser(any<UserPrincipal>(), eq(303L))
        verify(followCommandService).getUserState(any<UserPrincipal>(), eq(303L))
    }

    @Test
    fun incompleteProfileCanReadPublicUserProfile() {
        `when`(restrictionReader.read(202L))
            .thenReturn(AccountRestrictionReader.State(1, false, listOf()))
        `when`(followReadService.getPublicUserProfile(303L))
            .thenReturn(
                PublicUserProfileView(
                    "303", "徒步者", null, null, listOf(), 3L, 2L
                )
            )

        perform(get("/api/v1/users/303/profile"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.userId").value("303"))
    }

    @Test
    fun incompleteProfileCanReadPublicOrganization() {
        `when`(restrictionReader.read(202L))
            .thenReturn(AccountRestrictionReader.State(1, false, listOf()))
        `when`(organizationAccessService.getPublicOrganization(401L))
            .thenReturn(
                PublicOrganizationDetailView(
                    "401", "徒步企业", null, null,
                    PublicOrganizationDetailView.RegionView(null, null, null, null, null, null),
                    null, 4L
                )
            )

        perform(get("/api/v1/organizations/401"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.organizationId").value("401"))
            .andExpect(jsonPath("$.data.followerCount").value(4))
    }

    @Test
    fun incompleteProfileCanBrowseAndReachPostCleanupOrIdempotencyRules() {
        `when`(restrictionReader.read(202L))
            .thenReturn(AccountRestrictionReader.State(1, false, listOf()))

        perform(get("/api/v1/posts")).andExpect(status().isOk)
        perform(get("/api/v1/posts/7001")).andExpect(status().isOk)
        perform(get("/api/v1/users/303/posts")).andExpect(status().isOk)
        perform(get("/api/v1/organizations/401/posts")).andExpect(status().isOk)
        perform(get("/api/v1/feed/following")).andExpect(status().isOk)
        perform(get("/api/v1/feed/recommended")).andExpect(status().isOk)
        perform(get("/api/v1/users/me/managed-posts")).andExpect(status().isOk)
        perform(put("/api/v1/posts/7001/publication")).andExpect(status().isOk)
        perform(delete("/api/v1/posts/7001")).andExpect(status().isOk)
        perform(delete("/api/v1/activities/301/favorite"))
            .andExpect(status().isOk)
        perform(get("/api/v1/activities/301/comments"))
            .andExpect(status().isOk)
        perform(delete("/api/v1/activities/301/comments/501"))
            .andExpect(status().isOk)

        verify(postCommandService).publish(any<UserPrincipal>(), eq(7001L))
        verify(postCommandService).delete(any<UserPrincipal>(), eq(7001L))
        verify(activityFavoriteService).unfavorite(
            any<UserPrincipal>(), eq(301L)
        )
        verify(activityCommentService).delete(
            any<UserPrincipal>(), eq(301L), eq(501L)
        )

        perform(put("/api/v1/activities/301/favorite"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value(11201))
        verify(activityFavoriteService, never()).favorite(
            any<UserPrincipal>(), eq(301L)
        )

        perform(
            post("/api/v1/activities/301/comments")
                .header("Idempotency-Key", "activity-comment")
                .contentType("application/json")
                .content("""{"content":"出发前集合"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value(11201))
        verify(activityCommentService, never()).create(
            any<UserPrincipal>(), eq(301L), any(), any<String>()
        )

        perform(
            post("/api/v1/organizations/401/posts")
                .header("Idempotency-Key", "organization-draft")
                .contentType("application/json")
                .content("""{"title":"草稿","mediaFileIds":[]}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value(11201))
        verify(postCommandService, never()).createOrganization(
            any<UserPrincipal>(), eq(401L), any(), any<String>()
        )
    }

    @Test
    fun incompleteProfileLetsEnterpriseReplaceResolveVisibilityBeforeProfile() {
        `when`(restrictionReader.read(202L))
            .thenReturn(AccountRestrictionReader.State(1, false, listOf()))
        doThrow(
            BusinessException(
                CommonErrorCode.RESOURCE_NOT_FOUND
            )
        ).`when`(postCommandService).replaceOrganization(
            any<UserPrincipal>(), eq(401L), eq(7001L), any()
        )
        doThrow(
            BusinessException(
                CommonErrorCode.RESOURCE_NOT_FOUND
            )
        ).`when`(postCommandService).replaceOrganization(
            any<UserPrincipal>(), eq(401L), eq(7002L), any()
        )
        doThrow(
            BusinessException(
                AccountUserFileErrorCode.PROFILE_INCOMPLETE
            )
        ).`when`(postCommandService).replaceOrganization(
            any<UserPrincipal>(), eq(401L), eq(7003L), any()
        )

        val body = """{"version":0,"title":"草稿","visibility":"PUBLIC","mediaFileIds":[]}"""
        perform(
            put("/api/v1/organizations/401/posts/7001")
                .contentType("application/json").content(body)
        )
            .andExpect(status().isNotFound)
        perform(
            put("/api/v1/organizations/401/posts/7002")
                .contentType("application/json").content(body)
        )
            .andExpect(status().isNotFound)
        perform(
            put("/api/v1/organizations/401/posts/7003")
                .contentType("application/json").content(body)
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value(11201))

        verify(postCommandService).replaceOrganization(
            any<UserPrincipal>(), eq(401L), eq(7001L), any()
        )
        verify(postCommandService).replaceOrganization(
            any<UserPrincipal>(), eq(401L), eq(7002L), any()
        )
        verify(postCommandService).replaceOrganization(
            any<UserPrincipal>(), eq(401L), eq(7003L), any()
        )
    }

    @Test
    fun forcedAgreementAllowsAgreementAndLogoutPaths() {
        `when`(restrictionReader.read(202L))
            .thenReturn(AccountRestrictionReader.State(1, false, listOf(101L)))

        perform(post("/api/v1/agreements/101/consents"))
            .andExpect(status().isOk)
        perform(post("/api/v1/auth/logout"))
            .andExpect(status().isOk)
    }

    @Test
    fun deactivationPendingAllowsOnlyStatusCancellationExportAndLogout() {
        `when`(restrictionReader.read(202L))
            .thenReturn(AccountRestrictionReader.State(2, false, listOf(101L)))
        val existing = com.eligo.server.account.vo.DeactivationView(
            "501", "WAITING", Instant.parse("2026-07-23T08:00:00Z"),
            Instant.parse("2026-07-30T08:00:00Z"), true
        )
        `when`(accountDataService.currentDeactivation(any<UserPrincipal>()))
            .thenReturn(Optional.of(existing))
        `when`(accountDataService.requestDeactivationOutcome(any<UserPrincipal>(), any()))
            .thenReturn(
                com.eligo.server.account.service.DeactivationRequestOutcome(
                    existing, false
                )
            )

        perform(
            post("/api/v1/account/deactivation")
                .contentType("application/json")
                .content("""{"wechatCode":"fresh-code","confirmed":true}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.requestId").value("501"))
        verify(accountDataService).requestDeactivationOutcome(any<UserPrincipal>(), any())

        perform(get("/api/v1/account/deactivation"))
            .andExpect(status().isOk)
        perform(delete("/api/v1/account/deactivation"))
            .andExpect(status().isOk)
        perform(post("/api/v1/account/data-exports"))
            .andExpect(status().isCreated)
        perform(post("/api/v1/auth/logout"))
            .andExpect(status().isOk)
    }

    @Test
    fun deactivationPendingOnlyDownloadsOwnedValidExportResult() {
        `when`(restrictionReader.read(202L))
            .thenReturn(AccountRestrictionReader.State(2, false, listOf(101L)))
        `when`(restrictionReader.canDownloadExport(202L, 801L)).thenReturn(true)
        `when`(fileService.openContent(any<UserPrincipal>(), eq(801L)))
            .thenReturn(
                FileService.FileContent(
                    java.io.ByteArrayInputStream(byteArrayOf(1, 2, 3)),
                    "application/zip",
                    "private, max-age=60"
                )
            )

        perform(get("/api/v1/files/801/content"))
            .andExpect(status().isOk)
            .andExpect(content().contentType("application/zip"))
        perform(get("/api/v1/files/802/content"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value(11501))

        verify(fileService).openContent(any<UserPrincipal>(), eq(801L))
        verify(fileService, never()).openContent(any<UserPrincipal>(), eq(802L))
    }

    @Test
    fun incompleteProfileAllowsStageTwoAccountAgreementProfileAvatarAndDeactivationPaths() {
        `when`(restrictionReader.read(202L))
            .thenReturn(AccountRestrictionReader.State(1, false, listOf()))

        perform(get("/api/v1/account/sessions")).andExpect(status().isOk)
        perform(get("/api/v1/agreements/current")).andExpect(status().isOk)
        perform(
            put("/api/v1/users/me/profile")
                .contentType("application/json")
                .content(
                    """{"nickname":"小艾","birthDate":"2000-01-02","gender":"FEMALE",""" +
                        """"provinceCode":"44","cityCode":"4403","districtCode":"440305","email":"xiaoai@example.com"}"""
                )
        )
            .andExpect(status().isOk)
        perform(
            put("/api/v1/users/me/avatar")
                .contentType("application/json")
                .content("""{"fileId":"77"}""")
        ).andExpect(status().isOk)
        `when`(fileService.uploadImage(any<UserPrincipal>(), any(), eq("AVATAR")))
            .thenReturn(
                FileView(
                    "77", "image/png", 1, "PUBLIC", "PASSED",
                    "TEMPORARY", Instant.parse("2026-07-23T08:00:00Z"), null
                )
            )
        mockMvc.perform(
            multipart("/api/v1/files/images")
                .file(MockMultipartFile("file", "avatar.png", "image/png", byteArrayOf(1)))
                .param("purpose", "AVATAR")
                .header("Authorization", "Bearer $accessToken")
        )
            .andExpect(status().isCreated)
        perform(
            post("/api/v1/account/deactivation")
                .contentType("application/json")
                .content("""{"wechatCode":"fresh-code","confirmed":true}""")
        )
            .andExpect(status().isCreated)
        perform(post("/api/v1/auth/logout")).andExpect(status().isOk)
    }

    @Test
    fun databaseReaderCombinesAccountProfileAndPendingAgreementFacts() {
        val jdbcTemplate = mock(JdbcTemplate::class.java)
        `when`(jdbcTemplate.queryForObject(any<String>(), eq(Int::class.javaObjectType), eq(202L))).thenReturn(1)
        `when`(jdbcTemplate.queryForObject(any<String>(), eq(Boolean::class.javaObjectType), eq(202L))).thenReturn(false)
        `when`(jdbcTemplate.queryForList(any<String>(), eq(Long::class.javaObjectType), eq(202L))).thenReturn(listOf(101L, 102L))

        val state = DatabaseAccountRestrictionReader(jdbcTemplate).read(202L)

        org.assertj.core.api.Assertions.assertThat(state)
            .isEqualTo(AccountRestrictionReader.State(1, false, listOf(101L, 102L)))
        verify(jdbcTemplate).queryForObject(
            org.mockito.ArgumentMatchers.contains("users"), eq(Int::class.javaObjectType), eq(202L)
        )
        verify(jdbcTemplate).queryForObject(
            org.mockito.ArgumentMatchers.contains("user_profiles"), eq(Boolean::class.javaObjectType), eq(202L)
        )
        verify(jdbcTemplate).queryForList(
            org.mockito.ArgumentMatchers.contains("agreement_consents"), eq(Long::class.javaObjectType), eq(202L)
        )
    }

    @Test
    fun databaseReaderOnlyAllowsCurrentOwnedCompletedExportResult() {
        val jdbcTemplate = mock(JdbcTemplate::class.java)
        `when`(
            jdbcTemplate.queryForObject(
                org.mockito.ArgumentMatchers.contains("user_data_requests"),
                eq(Boolean::class.javaObjectType), eq(202L), eq(801L)
            )
        ).thenReturn(true)

        org.assertj.core.api.Assertions.assertThat(
            DatabaseAccountRestrictionReader(jdbcTemplate)
                .canDownloadExport(202L, 801L)
        ).isTrue()
    }

    private fun perform(
        request: MockHttpServletRequestBuilder
    ) = mockMvc.perform(request.header("Authorization", "Bearer $accessToken"))

    @RestController
    internal class TestController {
        @PostMapping("/test-support/ordinary-action")
        fun ordinaryAction(): Result<Void> = Result.success()
    }
}
