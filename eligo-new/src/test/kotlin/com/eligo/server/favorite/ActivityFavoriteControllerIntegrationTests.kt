package com.eligo.server.favorite

import com.eligo.server.activity.vo.ActivityOwnerSummaryView
import com.eligo.server.activity.vo.PublicActivitySummaryView
import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.web.GlobalExceptionHandler
import com.eligo.server.config.security.SecurityConfig
import com.eligo.server.favorite.controller.ActivityFavoriteController
import com.eligo.server.favorite.service.ActivityFavoriteService
import com.eligo.server.favorite.vo.FavoriteActivityView
import com.eligo.server.favorite.vo.FavoriteStateView
import com.eligo.server.security.AccountRestrictionFilter
import com.eligo.server.security.AccountRestrictionReader
import com.eligo.server.security.SessionAccessReader
import com.eligo.server.security.SessionAuthenticationFilter
import com.eligo.server.security.UserPrincipal
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions
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
import java.time.Instant

@WebMvcTest(ActivityFavoriteController::class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(
    GlobalExceptionHandler::class,
    SecurityConfig::class,
    SessionAuthenticationFilter::class,
    AccountRestrictionFilter::class
)
class ActivityFavoriteControllerIntegrationTests {

    private val principal = UserPrincipal(202L, "favorite-session")
    private val now = Instant.parse("2026-08-22T08:00:00Z")

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var favorites: ActivityFavoriteService

    @MockitoBean
    private lateinit var sessionAccessReader: SessionAccessReader

    @MockitoBean
    private lateinit var accountRestrictionReader: AccountRestrictionReader

    @Test
    fun exposesIdempotentFavoriteCommandsAndState() {
        whenever(favorites.favorite(principal, 301L))
            .thenReturn(FavoriteStateView(true, now))
        whenever(favorites.getState(principal, 301L))
            .thenReturn(FavoriteStateView(true, now))
        whenever(favorites.unfavorite(principal, 301L))
            .thenReturn(FavoriteStateView(false, null))

        mockMvc.perform(
            put("/api/v1/activities/301/favorite")
                .with(authentication(auth()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.favorited").value(true))
            .andExpect(jsonPath("$.data.favoritedAt").value("2026-08-22T08:00:00Z"))
        mockMvc.perform(
            get("/api/v1/activities/301/favorite")
                .with(authentication(auth()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.favorited").value(true))
        mockMvc.perform(
            delete("/api/v1/activities/301/favorite")
                .with(authentication(auth()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.favorited").value(false))
    }

    @Test
    fun exposesPersonalFavoritePage() {
        val item = FavoriteActivityView(
            now,
            PublicActivitySummaryView(
                "301", "PUBLISHED", "徒步", "OUTDOOR", null,
                ActivityOwnerSummaryView("USER", "202", "组织者", null),
                now, now.plusSeconds(3600), "440100", "地址",
                null, null, null, 0, "OPEN", "FREE", "集合点",
                null, null, listOf("周末")
            )
        )
        whenever(favorites.listMine(principal, "next", 10))
            .thenReturn(CursorPage(listOf(item), null, false))

        mockMvc.perform(
            get("/api/v1/users/me/favorite-activities")
                .param("cursor", "next")
                .param("limit", "10")
                .with(authentication(auth()))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].favoritedAt").value("2026-08-22T08:00:00Z"))
            .andExpect(jsonPath("$.data.items[0].activity.activityId").value("301"))
            .andExpect(jsonPath("$.data.items[0].activity.topics[0]").value("周末"))
    }

    @Test
    fun anonymousFavoriteEndpointsAreRejected() {
        mockMvc.perform(put("/api/v1/activities/301/favorite"))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/v1/activities/301/favorite"))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/v1/users/me/favorite-activities"))
            .andExpect(status().isUnauthorized)
        verifyNoInteractions(favorites)
    }

    private fun auth(): Authentication {
        return UsernamePasswordAuthenticationToken(principal, "", listOf())
    }
}
