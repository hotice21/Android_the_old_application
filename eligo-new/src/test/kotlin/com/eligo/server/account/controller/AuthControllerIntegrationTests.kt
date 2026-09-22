package com.eligo.server.account.controller

import com.eligo.server.account.service.AuthService
import com.eligo.server.account.vo.LoginResponse
import com.eligo.server.common.web.GlobalExceptionHandler
import com.eligo.server.config.security.SecurityConfig
import com.eligo.server.security.UserPrincipal
import com.eligo.server.security.AccountRestrictionFilter
import com.eligo.server.security.AccountRestrictionReader
import com.eligo.server.security.SessionAccessReader
import com.eligo.server.security.SessionAuthenticationFilter
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
import org.mockito.kotlin.any
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AuthController::class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(GlobalExceptionHandler::class, SecurityConfig::class,
        SessionAuthenticationFilter::class, AccountRestrictionFilter::class)
class AuthControllerIntegrationTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var authService: AuthService

    @MockitoBean
    private lateinit var sessionAccessReader: SessionAccessReader

    @MockitoBean
    private lateinit var accountRestrictionReader: AccountRestrictionReader

    @Test
    fun wechatLoginValidatesRequestAndReturnsUnifiedResponse() {
        `when`(authService.login(any())).thenReturn(loginResponse())

        mockMvc.perform(post("/api/v1/auth/wechat-login")
                        .contentType("application/json")
                        .content("""
                                {"wechatCode":"code","installationId":"installation-0001","deviceName":"微信设备","platform":"WECHAT_MINIPROGRAM","appVersion":"2.3.0"}
                                """))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value("202"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
    }

    @Test
    fun invalidWechatLoginRequestReturnsValidationError() {
        mockMvc.perform(post("/api/v1/auth/wechat-login")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value(10001))
    }

    @Test
    fun refreshReturnsRotatedTokens() {
        `when`(authService.refresh(any())).thenReturn(loginResponse())

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"old-token\",\"installationId\":\"installation-0001\"}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
    }

    @Test
    fun logoutUsesCurrentPrincipal() {
        val principal = UserPrincipal(202L, "session-a")
        `when`(accountRestrictionReader.read(202L))
            .thenReturn(AccountRestrictionReader.State(1, true, listOf()))
        SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(principal, "", listOf())

        try {
            mockMvc.perform(post("/api/v1/auth/logout")
                            .with(authentication(UsernamePasswordAuthenticationToken(principal, "", listOf()))))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.data").doesNotExist())
        } finally {
            SecurityContextHolder.clearContext()
        }

        verify(authService).logout(principal)
    }

    private fun loginResponse(): LoginResponse {
        return LoginResponse(
                "202", "ACTIVE", "401", "access-token", Instant.parse("2026-07-21T09:00:00Z"),
                "refresh-token", Instant.parse("2026-08-20T08:30:00Z"), false, listOf()
        )
    }
}
