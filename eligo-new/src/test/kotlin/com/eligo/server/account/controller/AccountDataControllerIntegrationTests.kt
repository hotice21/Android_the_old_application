package com.eligo.server.account.controller

import com.eligo.server.account.service.AccountDataService
import com.eligo.server.account.vo.DataExportView
import com.eligo.server.account.vo.DownloadUrlView
import com.eligo.server.common.web.GlobalExceptionHandler
import com.eligo.server.config.security.SecurityConfig
import com.eligo.server.security.AccountRestrictionFilter
import com.eligo.server.security.AccountRestrictionReader
import com.eligo.server.security.SessionAccessReader
import com.eligo.server.security.SessionAuthenticationFilter
import com.eligo.server.security.UserPrincipal
import java.time.Instant
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.eq
import org.mockito.kotlin.anyOrNull
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AccountDataController::class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(GlobalExceptionHandler::class, SecurityConfig::class,
        SessionAuthenticationFilter::class, AccountRestrictionFilter::class)
class AccountDataControllerIntegrationTests {
    @Autowired private lateinit var mockMvc: MockMvc
    @MockitoBean private lateinit var accountDataService: AccountDataService
    @MockitoBean private lateinit var sessionAccessReader: SessionAccessReader
    @MockitoBean private lateinit var accountRestrictionReader: AccountRestrictionReader
    private val principal = UserPrincipal(202L, "session-a")

    private fun authToken(): UsernamePasswordAuthenticationToken {
        `when`(accountRestrictionReader.read(202L))
            .thenReturn(AccountRestrictionReader.State(1, true, listOf()))
        return UsernamePasswordAuthenticationToken(principal, "", listOf())
    }

    @Test
    fun deactivationUsesAtomicOutcomeForCreatedAndRepeatedStatus() {
        val view = com.eligo.server.account.vo.DeactivationView(
                "501", "WAITING", Instant.parse("2026-07-23T08:00:00Z"),
                Instant.parse("2026-07-30T08:00:00Z"), true)
        `when`(accountDataService.requestDeactivationOutcome(
                eq(principal), anyOrNull()))
            .thenReturn(com.eligo.server.account.service.DeactivationRequestOutcome(view, true))
            .thenReturn(com.eligo.server.account.service.DeactivationRequestOutcome(view, false))

        val request = post("/api/v1/account/deactivation")
                .with(authentication(authToken()))
                .contentType("application/json")
                .content("{\"wechatCode\":\"fresh-code\",\"confirmed\":true}")
        mockMvc.perform(request).andExpect(status().isCreated)
        mockMvc.perform(post("/api/v1/account/deactivation")
                        .with(authentication(authToken()))
                        .contentType("application/json")
                        .content("{\"wechatCode\":\"fresh-code\",\"confirmed\":true}"))
                .andExpect(status().isOk)
        org.mockito.Mockito.verify(accountDataService, org.mockito.Mockito.never())
                .currentDeactivation(principal)
    }

    @Test
    fun createsExportWithHttp201() {
        `when`(accountDataService.requestExport(principal)).thenReturn(DataExportView(
                "701", "REQUESTED", Instant.parse("2026-07-23T08:00:00Z"), null, null, null))
        mockMvc.perform(post("/api/v1/account/data-exports").with(authentication(authToken())))
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.data.requestId").value("701"))
                .andExpect(jsonPath("$.data.status").value("REQUESTED"))
        verify(accountDataService).requestExport(principal)
    }

    @Test
    fun returnsOwnedExportStatusAndProtectedDownloadUrl() {
        val expiresAt = Instant.parse("2026-07-24T08:00:00Z")
        `when`(accountDataService.exportStatus(principal, 701L)).thenReturn(DataExportView(
                "701", "COMPLETED", Instant.parse("2026-07-23T08:00:00Z"),
                Instant.parse("2026-07-23T08:10:00Z"), expiresAt, null))
        `when`(accountDataService.exportDownloadUrl(principal, 701L))
            .thenReturn(DownloadUrlView("/api/v1/files/801/content", expiresAt))

        mockMvc.perform(get("/api/v1/account/data-exports/701").with(authentication(authToken())))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
        mockMvc.perform(get("/api/v1/account/data-exports/701/download-url")
                        .with(authentication(authToken())))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.downloadUrl").value("/api/v1/files/801/content"))
    }
}
