package com.eligo.server.security.attack

import com.eligo.server.common.web.GlobalExceptionHandler
import com.eligo.server.config.security.SecurityConfig
import com.eligo.server.security.AccountRestrictionReader
import com.eligo.server.security.SessionAccessReader
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 测试模式旁路验证：
 *
 * 当 `eligo.security.enabled=false` 时：
 * - 受保护端点匿名可访问；
 * - 请求以指定的测试用户身份（id=202, session=dev-session-xyz）执行。
 */
@WebMvcTest(AttackTestSupportController::class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "eligo.security.enabled=false",
        "eligo.security.dev-user-id=202",
        "eligo.security.dev-session-key=dev-session-xyz"
    ]
)
@Import(GlobalExceptionHandler::class, SecurityConfig::class)
class DevModeSecurityBypassTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    // SecurityConfig declares the filters as @Bean methods, so their reader deps must exist
    @MockitoBean
    private lateinit var sessionAccessReader: SessionAccessReader

    @MockitoBean
    private lateinit var accountRestrictionReader: AccountRestrictionReader

    @Test
    fun protectedEndpointAccessibleWithoutTokenAsDevUser() {
        mockMvc.perform(get("/test-support/attack/protected"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data").value("202:dev-session-xyz"))
    }

    @Test
    fun garbageBearerTokenDoesNotBlockRequestInDevMode() {
        mockMvc.perform(
            get("/test-support/attack/protected")
                .header("Authorization", "Bearer garbage")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data").value("202:dev-session-xyz"))
    }
}
