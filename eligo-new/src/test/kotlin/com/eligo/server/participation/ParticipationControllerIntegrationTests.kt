package com.eligo.server.participation

import com.eligo.server.activity.vo.ActivityOwnerSummaryView
import com.eligo.server.activity.vo.PublicActivitySummaryView
import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.web.GlobalExceptionHandler
import com.eligo.server.config.security.SecurityConfig
import com.eligo.server.participation.controller.ParticipationController
import com.eligo.server.participation.service.ParticipationCommandService
import com.eligo.server.participation.service.ParticipationReadService
import com.eligo.server.participation.vo.ActivityParticipantSummaryView
import com.eligo.server.participation.vo.ActivityParticipationView
import com.eligo.server.participation.vo.MyParticipationView
import com.eligo.server.security.AccountRestrictionFilter
import com.eligo.server.security.AccountRestrictionReader
import com.eligo.server.security.SessionAccessReader
import com.eligo.server.security.SessionAuthenticationFilter
import com.eligo.server.security.UserPrincipal
import java.math.BigDecimal
import java.time.Instant
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@WebMvcTest(ParticipationController::class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(
    GlobalExceptionHandler::class,
    SecurityConfig::class,
    SessionAuthenticationFilter::class,
    AccountRestrictionFilter::class
)
class ParticipationControllerIntegrationTests {

    private val principal = UserPrincipal(202L, "session-m3")

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var commandService: ParticipationCommandService

    @MockitoBean
    lateinit var readService: ParticipationReadService

    @MockitoBean
    lateinit var sessionAccessReader: SessionAccessReader

    @MockitoBean
    lateinit var accountRestrictionReader: AccountRestrictionReader

    @Test
    fun authenticatedUserCanJoinAndCancel() {
        whenever(commandService.join(principal, 1001L)).thenReturn(participation("ACTIVE"))
        whenever(commandService.cancel(principal, 1001L)).thenReturn(participation("CANCELLED"))

        mockMvc.perform(put("/api/v1/activities/1001/participation")
            .with(authentication(auth())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.userId").value("202"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))

        mockMvc.perform(delete("/api/v1/activities/1001/participation")
            .with(authentication(auth())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("CANCELLED"))
    }

    @Test
    fun ownerCanRemoveParticipant() {
        whenever(commandService.remove(principal, 1001L, 303L))
            .thenReturn(participation("TERMINATED"))

        mockMvc.perform(delete("/api/v1/activities/1001/participants/303")
            .with(authentication(auth())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.terminationReason")
                .value("REMOVED_BY_OWNER"))
    }

    @Test
    fun authenticatedUserCanReadParticipantsAndOwnHistory() {
        whenever(readService.listParticipants(principal, 1001L, null, 20))
            .thenReturn(CursorPage(listOf(participant()), null, false))
        whenever(readService.listMyParticipations(principal, null, 20, null, null))
            .thenReturn(CursorPage(listOf(myParticipation()), null, false))

        mockMvc.perform(get("/api/v1/activities/1001/participants")
            .with(authentication(auth())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].userId").value("303"))
            .andExpect(jsonPath("$.data.items[0].nickname").value("参与者"))

        mockMvc.perform(get("/api/v1/users/me/participations")
            .with(authentication(auth())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].participationId").value("8001"))
            .andExpect(jsonPath("$.data.items[0].activity.activityId").value("1001"))
            .andExpect(jsonPath("$.data.items[0].activity.latitude")
                .value(22.5430960))
            .andExpect(jsonPath("$.data.items[0].activity.longitude")
                .value(114.0578650))
    }

    @Test
    fun anonymousParticipationEndpointsAreRejected() {
        mockMvc.perform(put("/api/v1/activities/1001/participation"))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/v1/activities/1001/participants"))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/v1/users/me/participations"))
            .andExpect(status().isUnauthorized)
        verifyNoInteractions(commandService, readService)
    }

    private fun participation(status: String): ActivityParticipationView {
        return ActivityParticipationView(
            "8001", "1001", "202", status,
            Instant.parse("2026-08-09T00:00:00Z"),
            if ("CANCELLED" == status)
                Instant.parse("2026-08-09T01:00:00Z") else null,
            if ("TERMINATED" == status)
                Instant.parse("2026-08-09T01:00:00Z") else null,
            if ("TERMINATED" == status) "REMOVED_BY_OWNER" else null,
            if ("ACTIVE" == status) 1 else 0)
    }

    private fun participant(): ActivityParticipantSummaryView {
        return ActivityParticipantSummaryView(
            "303", "参与者", null,
            Instant.parse("2026-08-09T00:00:00Z"))
    }

    private fun myParticipation(): MyParticipationView {
        return MyParticipationView(
            "8001", "202", "ACTIVE",
            Instant.parse("2026-08-09T00:00:00Z"),
            null, null, null,
            PublicActivitySummaryView(
                "1001", "PUBLISHED", "测试活动", "HIKING", null,
                ActivityOwnerSummaryView("USER", "303", "发起者", null),
                Instant.parse("2026-08-09T03:00:00Z"),
                Instant.parse("2026-08-09T04:00:00Z"),
                "440305", "活动地址",
                BigDecimal("22.5430960"),
                BigDecimal("114.0578650"),
                20, 1, "OPEN", "FREE"))
    }

    private fun auth(): Authentication {
        return UsernamePasswordAuthenticationToken(principal, "", emptyList())
    }
}
