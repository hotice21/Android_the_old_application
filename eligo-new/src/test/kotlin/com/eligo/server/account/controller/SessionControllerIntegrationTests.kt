package com.eligo.server.account.controller

import com.eligo.server.account.service.SessionService
import com.eligo.server.account.vo.SessionView
import com.eligo.server.common.web.GlobalExceptionHandler
import com.eligo.server.config.security.SecurityConfig
import com.eligo.server.security.UserPrincipal
import com.eligo.server.security.AccountRestrictionFilter
import com.eligo.server.security.AccountRestrictionReader
import com.eligo.server.security.SessionAccessReader
import com.eligo.server.security.SessionAuthenticationFilter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import java.time.Instant
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(SessionController::class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(GlobalExceptionHandler::class, SecurityConfig::class,
        SessionAuthenticationFilter::class, AccountRestrictionFilter::class)
class SessionControllerIntegrationTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var sessionService: SessionService

    @MockitoBean
    private lateinit var sessionAccessReader: SessionAccessReader

    @MockitoBean
    private lateinit var accountRestrictionReader: AccountRestrictionReader

    private val principal = UserPrincipal(202L, "session-a")

    @BeforeEach
    fun setPrincipal() {
        SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(principal, "", listOf())
        `when`(accountRestrictionReader.read(202L))
            .thenReturn(AccountRestrictionReader.State(1, true, listOf()))
    }

    @AfterEach
    fun clearPrincipal() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun listsActiveSessionsWithoutSensitiveFields() {
        `when`(sessionService.listActive(principal)).thenReturn(listOf(SessionView(
                "401", "微信设备", "WECHAT_MINIPROGRAM", "18.0", "2.3.0", "CN-44",
                Instant.parse("2026-07-21T08:30:00Z"), Instant.parse("2026-08-20T08:30:00Z"), true
        )))

        mockMvc.perform(get("/api/v1/account/sessions")
                        .with(authentication(UsernamePasswordAuthenticationToken(principal, "", listOf()))))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.items[0].sessionId").value("401"))
                .andExpect(jsonPath("$.data.items[0].current").value(true))
                .andExpect(jsonPath("$.data.items[0].refreshTokenHash").doesNotExist())
    }

    @Test
    fun revokesAnotherOwnedSession() {
        mockMvc.perform(delete("/api/v1/account/sessions/402")
                        .with(authentication(UsernamePasswordAuthenticationToken(principal, "", listOf()))))
                .andExpect(status().isOk)

        verify(sessionService).revokeOther(principal, 402L)
    }
}
