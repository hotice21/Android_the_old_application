package com.eligo.server.follow

import com.eligo.server.activity.vo.PublicImageView
import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.web.GlobalExceptionHandler
import com.eligo.server.config.security.SecurityConfig
import com.eligo.server.follow.controller.FollowController
import com.eligo.server.follow.service.FollowCommandService
import com.eligo.server.follow.service.FollowReadService
import com.eligo.server.follow.vo.FollowStateView
import com.eligo.server.follow.vo.FollowTargetSummaryView
import com.eligo.server.follow.vo.FollowerSummaryView
import com.eligo.server.follow.vo.PublicUserProfileView
import com.eligo.server.profile.vo.InterestTagView
import com.eligo.server.security.AccountRestrictionFilter
import com.eligo.server.security.AccountRestrictionReader
import com.eligo.server.security.SessionAccessReader
import com.eligo.server.security.SessionAuthenticationFilter
import com.eligo.server.security.UserPrincipal
import java.time.Instant
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(FollowController::class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(
    GlobalExceptionHandler::class,
    SecurityConfig::class,
    SessionAuthenticationFilter::class,
    AccountRestrictionFilter::class
)
class FollowControllerIntegrationTests {

    private val principal = UserPrincipal(202L, "session-m4")
    private val followedAt = Instant.parse("2026-08-16T01:02:03Z")

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var commands: FollowCommandService

    @MockitoBean
    lateinit var reads: FollowReadService

    @MockitoBean
    lateinit var sessionAccessReader: SessionAccessReader

    @MockitoBean
    lateinit var accountRestrictionReader: AccountRestrictionReader

    @Test
    fun exposesFourIdempotentFollowCommands() {
        whenever(commands.followUser(principal, 303L))
            .thenReturn(FollowStateView(true, followedAt))
        whenever(commands.unfollowUser(principal, 303L))
            .thenReturn(FollowStateView(false, null))
        whenever(commands.followOrganization(principal, 401L))
            .thenReturn(FollowStateView(true, followedAt))
        whenever(commands.unfollowOrganization(principal, 401L))
            .thenReturn(FollowStateView(false, null))

        mockMvc.perform(put("/api/v1/follows/users/303").with(authentication(auth())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.following").value(true))
            .andExpect(jsonPath("$.data.followedAt").value("2026-08-16T01:02:03Z"))
        mockMvc.perform(delete("/api/v1/follows/users/303").with(authentication(auth())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.following").value(false))
            .andExpect(jsonPath("$.data.followedAt").isEmpty)
        mockMvc.perform(put("/api/v1/follows/organizations/401").with(authentication(auth())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.following").value(true))
        mockMvc.perform(delete("/api/v1/follows/organizations/401").with(authentication(auth())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.following").value(false))
    }

    @Test
    fun exposesTwoFollowStateQueries() {
        whenever(commands.getUserState(principal, 303L))
            .thenReturn(FollowStateView(true, followedAt))
        whenever(commands.getOrganizationState(principal, 401L))
            .thenReturn(FollowStateView(false, null))

        mockMvc.perform(get("/api/v1/users/303/follow-state").with(authentication(auth())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.following").value(true))
        mockMvc.perform(get("/api/v1/organizations/401/follow-state").with(authentication(auth())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.following").value(false))
    }

    @Test
    fun exposesAuthenticatedFollowingAndFollowerLists() {
        whenever(
            reads.listFollowing(
                principal, "next", 10, "USER", "徒步", "RECENT"
            )
        ).thenReturn(
            CursorPage(
                listOf(
                    FollowTargetSummaryView(
                        "9001",
                        "USER",
                        "303",
                        "徒步者",
                        PublicImageView("501", "/api/v1/files/501/content"),
                        followedAt
                    )
                ),
                null,
                false
            )
        )
        whenever(reads.listFollowers(principal, null, 20, null, null))
            .thenReturn(
                CursorPage(
                    listOf(
                        FollowerSummaryView(
                            "8001", "304", "山友", null, followedAt
                        )
                    ),
                    null,
                    false
                )
            )

        mockMvc.perform(
            get("/api/v1/users/me/following")
                .param("cursor", "next")
                .param("limit", "10")
                .param("type", "USER")
                .param("q", "徒步")
                .param("sort", "RECENT")
                .with(authentication(auth()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].followId").value("9001"))
            .andExpect(jsonPath("$.data.items[0].targetType").value("USER"))
        mockMvc.perform(get("/api/v1/users/me/followers").with(authentication(auth())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].userId").value("304"))
    }

    @Test
    fun anonymousVisitorCanReadOnlyPublicUserProfile() {
        whenever(reads.getPublicUserProfile(303L))
            .thenReturn(
                PublicUserProfileView(
                    "303",
                    "徒步者",
                    PublicImageView("501", "/api/v1/files/501/content"),
                    "周末登山",
                    listOf(InterestTagView("701", "OUTDOOR", "户外", 1)),
                    3L,
                    2L
                )
            )

        mockMvc.perform(get("/api/v1/users/303/profile"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.userId").value("303"))
            .andExpect(jsonPath("$.data.interestTags[0].code").value("OUTDOOR"))
            .andExpect(jsonPath("$.data.followingCount").value(3))
            .andExpect(jsonPath("$.data.followerCount").value(2))
    }

    @Test
    fun anonymousFollowOperationsAndListsAreRejected() {
        mockMvc.perform(put("/api/v1/follows/users/303"))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/v1/users/303/follow-state"))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/v1/users/me/following"))
            .andExpect(status().isUnauthorized)
        verifyNoInteractions(commands)
    }

    private fun auth(): Authentication =
        UsernamePasswordAuthenticationToken(principal, "", emptyList())
}
