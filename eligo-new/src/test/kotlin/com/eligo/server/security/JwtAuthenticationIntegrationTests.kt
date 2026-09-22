package com.eligo.server.security

import com.eligo.server.common.api.Result
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.Base64
import java.util.Date

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(JwtAuthenticationIntegrationTests.TestController::class)
class JwtAuthenticationIntegrationTests {

    private val SIGNING_KEY_BASE64 = "IB8eHRwbGhkYFxYVFBMSERAPDg0MCwoJCAcGBQQDAgE="

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var tokenService: JwtTokenService

    @Test
    fun missingAccessTokenReturnsPublicAuthenticationRequiredError() {
        mockMvc.perform(get("/test-support/jwt-principal"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value(10100))
    }

    @Test
    fun invalidAccessTokenReturnsAccountModuleInvalidTokenError() {
        mockMvc.perform(
            get("/test-support/jwt-principal").header("Authorization", "Bearer malformed")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value(11003))
            .andExpect(jsonPath("$.message").value("访问令牌无效或过期"))
    }

    @Test
    fun expiredAccessTokenReturnsAccountModuleInvalidTokenError() {
        mockMvc.perform(
            get("/test-support/jwt-principal").header(
                "Authorization",
                "Bearer " + signedAccessToken("42", "session-key-123", Instant.now().minusSeconds(60))
            )
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value(11003))
    }

    @Test
    fun nonNumericSubjectReturnsAccountModuleInvalidTokenError() {
        assertInvalidAccessToken(signedAccessToken("not-a-number", "session-key-123", Instant.now().plusSeconds(300)))
    }

    @Test
    fun numericSubjectReturnsAccountModuleInvalidTokenError() {
        assertInvalidAccessToken(signedAccessToken(42L, "session-key-123", Instant.now().plusSeconds(300)))
    }

    @Test
    fun nonPositiveSubjectReturnsAccountModuleInvalidTokenError() {
        assertInvalidAccessToken(signedAccessToken("0", "session-key-123", Instant.now().plusSeconds(300)))
    }

    @Test
    fun missingSessionKeyReturnsAccountModuleInvalidTokenError() {
        assertInvalidAccessToken(signedAccessToken("42", null, Instant.now().plusSeconds(300)))
    }

    @Test
    fun numericSessionKeyReturnsAccountModuleInvalidTokenError() {
        assertInvalidAccessToken(signedAccessToken("42", 12345L, Instant.now().plusSeconds(300)))
    }

    @Test
    fun blankSessionKeyReturnsAccountModuleInvalidTokenError() {
        assertInvalidAccessToken(signedAccessToken("42", "  ", Instant.now().plusSeconds(300)))
    }

    @Test
    fun validAccessTokenCreatesUserPrincipalFromSubjectAndSessionKey() {
        val accessToken = tokenService.issueTokenPair(UserPrincipal(42L, "session-key-123")).accessToken

        mockMvc.perform(
            get("/test-support/jwt-principal").header("Authorization", "Bearer $accessToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data").value("42:session-key-123"))
    }

    private fun assertInvalidAccessToken(accessToken: String) {
        mockMvc.perform(
            get("/test-support/jwt-principal").header("Authorization", "Bearer $accessToken")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value(11003))
    }

    private fun signedAccessToken(subject: Any?, sessionKey: Any?, expiresAt: Instant): String {
        val issuedAt = Instant.now().minusSeconds(120)
        val claims = JWTClaimsSet.Builder()
            .issuer("eligo")
            .claim("sub", subject)
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(expiresAt))
        if (sessionKey != null) {
            claims.claim("sid", sessionKey)
        }
        val token = SignedJWT(JWSHeader(JWSAlgorithm.HS256), claims.build())
        token.sign(MACSigner(Base64.getDecoder().decode(SIGNING_KEY_BASE64)))
        return token.serialize()
    }

    @RestController
    internal class TestController {

        @GetMapping("/test-support/jwt-principal")
        fun principal(@AuthenticationPrincipal principal: UserPrincipal): Result<String> {
            return Result.success(principal.userId.toString() + ":" + principal.sessionKey)
        }
    }
}
