package com.eligo.server.security.attack

import com.eligo.server.activity.controller.ActivityController
import com.eligo.server.activity.service.ActivityCommandService
import com.eligo.server.activity.service.ActivityReadService
import com.eligo.server.common.web.GlobalExceptionHandler
import com.eligo.server.config.security.SecurityConfig
import com.eligo.server.file.controller.FileController
import com.eligo.server.file.service.FileService
import com.eligo.server.security.AccountRestrictionFilter
import com.eligo.server.security.AccountRestrictionReader
import com.eligo.server.security.SessionAccessReader
import com.eligo.server.security.SessionAuthenticationFilter
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 漏洞测试：未认证访问。
 *
 * 受保护的端点在缺失/伪造令牌时必须返回 401，且错误信息采用统一错误码，
 * 不能出现 500、空白页或匿名放行。
 */
@WebMvcTest(
    controllers = [
        ActivityController::class,
        FileController::class,
        AttackTestSupportController::class
    ]
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(
    GlobalExceptionHandler::class,
    SecurityConfig::class,
    SessionAuthenticationFilter::class,
    AccountRestrictionFilter::class
)
class UnauthenticatedAccessAttackTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var sessionAccessReader: SessionAccessReader

    @MockitoBean
    private lateinit var accountRestrictionReader: AccountRestrictionReader

    @MockitoBean
    private lateinit var readService: ActivityReadService

    @MockitoBean
    private lateinit var commandService: ActivityCommandService

    @MockitoBean
    private lateinit var fileService: FileService

    @Test
    fun protectedUserEndpointWithoutTokenReturns401() {
        mockMvc.perform(get("/api/v1/users/me/managed-activities"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value(10100))
    }

    @Test
    fun protectedFileUploadWithoutTokenReturns401() {
        mockMvc.perform(post("/api/v1/files/images"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value(10100))
    }

    @Test
    fun attackSupportEndpointWithoutTokenReturns401() {
        mockMvc.perform(get("/test-support/attack/protected"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value(10100))
    }

    @Test
    fun malformedBearerTokenReturnsInvalidTokenError() {
        mockMvc.perform(
            get("/test-support/attack/protected")
                .header("Authorization", "Bearer not-a-jwt")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value(11003))
    }

    @Test
    fun emptyBearerTokenReturnsInvalidTokenError() {
        // 畸形 Bearer 头必须由标准过滤器干净地返回 401/11003，而不是 500
        mockMvc.perform(
            get("/test-support/attack/protected")
                .header("Authorization", "Bearer ")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value(11003))
    }
}
