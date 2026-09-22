package com.eligo.server.security.attack

import com.eligo.server.common.web.GlobalExceptionHandler
import com.eligo.server.config.security.SecurityConfig
import com.eligo.server.security.AccountRestrictionFilter
import com.eligo.server.security.AccountRestrictionReader
import com.eligo.server.security.SessionAccessReader
import com.eligo.server.security.SessionAuthenticationFilter
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.Base64
import java.util.Date

/**
 * 漏洞测试：垂直越权 / 提权。
 *
 * 管理端路径 /api/v1/admin/ 下的所有接口必须：
 * - 匿名访问 → 401
 * - 普通用户（ROLE_USER）访问 → 403
 */
@WebMvcTest(AttackTestSupportController::class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(
    GlobalExceptionHandler::class,
    SecurityConfig::class,
    SessionAuthenticationFilter::class,
    AccountRestrictionFilter::class
)
class PrivilegeEscalationAttackTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var sessionAccessReader: SessionAccessReader

    @MockitoBean
    private lateinit var accountRestrictionReader: AccountRestrictionReader

    @Test
    fun adminEndpointAnonymousIsRejectedWith401() {
        mockMvc.perform(get("/api/v1/admin/users"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value(10100))
    }

    @Test
    fun adminEndpointAsRegularUserIsForbidden() {
        `when`(accountRestrictionReader.read(202L))
            .thenReturn(AccountRestrictionReader.State(1, true, listOf()))

        mockMvc.perform(
            get("/api/v1/admin/users")
                .header("Authorization", "Bearer ${validUserToken()}")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value(10101))
    }

    private fun validUserToken(): String {
        val claims = JWTClaimsSet.Builder()
            .issuer("eligo")
            .subject("202")
            .claim("sid", "session-a")
            .issueTime(Date.from(Instant.now().minusSeconds(30)))
            .expirationTime(Date.from(Instant.now().plusSeconds(900)))
            .build()
        val token = SignedJWT(JWSHeader(JWSAlgorithm.HS256), claims)
        token.sign(MACSigner(Base64.getDecoder().decode(SIGNING_KEY_BASE64)))
        return token.serialize()
    }

    companion object {
        private const val SIGNING_KEY_BASE64 =
            "IB8eHRwbGhkYFxYVFBMSERAPDg0MCwoJCAcGBQQDAgE="
    }
}
