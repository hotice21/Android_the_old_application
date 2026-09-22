package com.eligo.server.account.controller

import com.eligo.server.account.service.AccountDataService
import com.eligo.server.account.vo.SecurityEventView
import com.eligo.server.common.api.CursorPage
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(SecurityEventController::class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(GlobalExceptionHandler::class, SecurityConfig::class,
        SessionAuthenticationFilter::class, AccountRestrictionFilter::class)
class SecurityEventControllerIntegrationTests {
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
    fun listsWithDefaultLimitAndDoesNotExposeInternalDetails() {
        val item = SecurityEventView("901", "NEW_INSTALLATION_LOGIN", "LOW",
                "微信小程序", "440300", Instant.parse("2026-07-23T08:00:00Z"))
        `when`(accountDataService.securityEvents(principal, null, 20))
            .thenReturn(CursorPage(listOf(item), "next", true))

        mockMvc.perform(get("/api/v1/account/security-events").with(authentication(authToken())))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.items[0].eventId").value("901"))
                .andExpect(jsonPath("$.data.items[0].deviceName").value("微信小程序"))
                .andExpect(jsonPath("$.data.items[0].ipLookupHash").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].detailJson").doesNotExist())
        verify(accountDataService).securityEvents(principal, null, 20)
    }

    @Test
    fun rejectsLimitAboveOneHundred() {
        mockMvc.perform(get("/api/v1/account/security-events?limit=101")
                        .with(authentication(authToken())))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value(10001))
    }
}
