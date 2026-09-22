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
import com.nimbusds.jwt.PlainJWT
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.Test
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
 * 漏洞测试：JWT 伪造 / 篡改。
 *
 * 包括：错误签名密钥、none 算法、错误签发方、缺失关键声明、过期令牌。
 * 所有伪造令牌必须返回 401 / 11003，不能被接受。
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
class JwtForgeryAttackTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var sessionAccessReader: SessionAccessReader

    @MockitoBean
    private lateinit var accountRestrictionReader: AccountRestrictionReader

    @Test
    fun tokenSignedWithAttackerKeyIsRejected() {
        val attackerKey = ByteArray(32) { (it + 7).toByte() }
        assertRejected(signedToken(attackerKey, "eligo", "202", "session-a"))
    }

    @Test
    fun unsignedNoneAlgorithmTokenIsRejected() {
        val claims = baseClaims("202", "session-a").build()
        assertRejected(PlainJWT(claims).serialize())
    }

    @Test
    fun tokenWithWrongIssuerIsRejected() {
        assertRejected(signedToken(SIGNING_KEY, "attacker-issuer", "202", "session-a"))
    }

    @Test
    fun tokenWithoutSessionIdIsRejected() {
        assertRejected(signedToken(SIGNING_KEY, "eligo", "202", null))
    }

    @Test
    fun tokenWithoutSubjectIsRejected() {
        assertRejected(signedToken(SIGNING_KEY, "eligo", null, "session-a"))
    }

    @Test
    fun expiredTokenIsRejected() {
        assertRejected(
            signedToken(
                SIGNING_KEY, "eligo", "202", "session-a",
                Instant.now().minusSeconds(3600)
            )
        )
    }

    @Test
    fun tokenWithFutureIssuedAtButValidExpiryIsAccepted() {
        // 实测：Spring 的 JwtTimestampValidator 只校验 exp / nbf，不校验 iat。
        // 记录该真实行为：未来 iat 但 exp 仍有效的令牌会被接受（安全性由 exp 保证）。
        val claims = JWTClaimsSet.Builder()
            .issuer("eligo")
            .subject("202")
            .claim("sid", "session-a")
            .issueTime(Date.from(Instant.now().plusSeconds(3600)))
            .expirationTime(Date.from(Instant.now().plusSeconds(7200)))
            .build()
        mockMvc.perform(
            get("/test-support/attack/protected")
                .header("Authorization", "Bearer ${serializeSigned(claims)}")
        )
            .andExpect(status().isOk)
    }

    private fun assertRejected(token: String) {
        mockMvc.perform(
            get("/test-support/attack/protected")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value(11003))
    }

    private fun signedToken(
        signingKey: ByteArray,
        issuer: String,
        subject: String?,
        sessionKey: String?,
        expiresAt: Instant = Instant.now().plusSeconds(900)
    ): String {
        val builder = baseClaims(subject, sessionKey, expiresAt)
            .issuer(issuer)
        return serializeSigned(builder.build(), signingKey)
    }

    private fun serializeSigned(
        claims: JWTClaimsSet,
        signingKey: ByteArray = SIGNING_KEY
    ): String {
        val token = SignedJWT(JWSHeader(JWSAlgorithm.HS256), claims)
        token.sign(MACSigner(signingKey))
        return token.serialize()
    }

    private fun baseClaims(
        subject: String?,
        sessionKey: String?,
        expiresAt: Instant = Instant.now().plusSeconds(900)
    ): JWTClaimsSet.Builder {
        val claims = JWTClaimsSet.Builder()
            .issueTime(Date.from(Instant.now().minusSeconds(60)))
            .expirationTime(Date.from(expiresAt))
        if (subject != null) {
            claims.subject(subject)
        }
        if (sessionKey != null) {
            claims.claim("sid", sessionKey)
        }
        return claims
    }

    companion object {
        private val SIGNING_KEY: ByteArray =
            Base64.getDecoder().decode("IB8eHRwbGhkYFxYVFBMSERAPDg0MCwoJCAcGBQQDAgE=")
    }
}
