package com.eligo.server.security

import com.eligo.server.common.api.Result
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SessionAuthenticationIntegrationTests.TestController::class)
class SessionAuthenticationIntegrationTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jwtTokenService: JwtTokenService

    @MockitoBean
    private lateinit var sessionAccessReader: SessionAccessReader

    @Test
    fun validJwtRequiresDatabaseSessionState() {
        val accessToken = jwtTokenService.issueTokenPair(UserPrincipal(42L, "session-a")).accessToken

        mockMvc.perform(
            get("/test-support/session-principal")
                .header("Authorization", "Bearer $accessToken")
        )
            .andExpect(status().isOk)

        verify(sessionAccessReader).requireActive("session-a", 42L)
    }

    @Test
    fun revokedSessionReturnsSessionInvalidError() {
        doThrow(BusinessException(AccountUserFileErrorCode.LOGIN_SESSION_INVALID))
            .`when`(sessionAccessReader).requireActive("session-a", 42L)
        val accessToken = jwtTokenService.issueTokenPair(UserPrincipal(42L, "session-a")).accessToken

        mockMvc.perform(
            get("/test-support/session-principal")
                .header("Authorization", "Bearer $accessToken")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value(11005))
    }

    @Test
    fun lowercaseBearerStillRequiresDatabaseSessionState() {
        doThrow(BusinessException(AccountUserFileErrorCode.LOGIN_SESSION_INVALID))
            .`when`(sessionAccessReader).requireActive("session-a", 42L)
        val accessToken = jwtTokenService.issueTokenPair(UserPrincipal(42L, "session-a")).accessToken

        mockMvc.perform(
            get("/test-support/session-principal")
                .header("authorization", "bearer $accessToken")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value(11005))

        verify(sessionAccessReader).requireActive("session-a", 42L)
    }

    @Test
    fun securityDisabledAccountReturnsPublicAccessDeniedError() {
        doThrow(BusinessException(CommonErrorCode.ACCESS_DENIED))
            .`when`(sessionAccessReader).requireActive("session-a", 42L)
        val accessToken = jwtTokenService.issueTokenPair(UserPrincipal(42L, "session-a")).accessToken

        mockMvc.perform(
            get("/test-support/session-principal")
                .header("Authorization", "Bearer $accessToken")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value(10101))
    }

    @Test
    fun simulatedIdentitySkipsDatabaseSessionRead() {
        mockMvc.perform(get("/test-support/session-principal").with(user("test-user")))
            .andExpect(status().isOk)
    }

    @RestController
    internal class TestController {

        @GetMapping("/test-support/session-principal")
        fun principal(@AuthenticationPrincipal principal: Any): Result<String> {
            return Result.success(principal.toString())
        }
    }
}
