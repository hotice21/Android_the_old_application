package com.eligo.server.security

import com.eligo.server.config.security.JwtProperties
import com.nimbusds.jwt.SignedJWT
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.Base64

class JwtTokenServiceTests {

    private val SIGNING_KEY = Base64.getEncoder().encodeToString(
        byteArrayOf(
            32, 31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 21, 20, 19, 18, 17,
            16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1
        )
    )

    private val sensitiveDataCodec = AesGcmSensitiveDataCodec(
        SensitiveDataProperties(SIGNING_KEY, SIGNING_KEY)
    )
    private val tokenService = JwtTokenService(
        JwtProperties("eligo", Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofSeconds(30), SIGNING_KEY),
        sensitiveDataCodec
    )

    @Test
    fun issueTokenPairCreatesHs256AccessTokenWithOnlyRequiredClaims() {
        val tokenPair = tokenService.issueTokenPair(UserPrincipal(42L, "session-key-123"))
        val token = SignedJWT.parse(tokenPair.accessToken)

        assertThat(token.header.algorithm.name).isEqualTo("HS256")
        assertThat(token.jwtClaimsSet.issuer).isEqualTo("eligo")
        assertThat(token.jwtClaimsSet.subject).isEqualTo("42")
        assertThat(token.jwtClaimsSet.getStringClaim("sid")).isEqualTo("session-key-123")
        assertThat(token.jwtClaimsSet.claims).containsOnlyKeys("iss", "sub", "sid", "iat", "exp")
        assertThat(tokenPair.accessTokenExpiresAt).isAfter(Instant.now())
        assertThat(tokenPair.refreshToken).isNotBlank().isNotEqualTo(tokenPair.accessToken)
        assertThat(tokenService.refreshTokenHash(tokenPair.refreshToken)).hasSize(32)
    }

    @Test
    fun refreshTokenHashUsesDedicatedDomainAndRemainsStable() {
        val rawRefreshToken = "same-value"

        assertThat(tokenService.refreshTokenHash(rawRefreshToken))
            .isEqualTo(tokenService.refreshTokenHash(rawRefreshToken))
            .isNotEqualTo(sensitiveDataCodec.lookupHash(rawRefreshToken))
    }

    @Test
    fun issueTokenPairUsesSecureRandomRefreshToken() {
        val first = tokenService.issueTokenPair(UserPrincipal(42L, "session-key-123"))
        val second = tokenService.issueTokenPair(UserPrincipal(42L, "session-key-123"))

        assertThat(first.refreshToken).isNotEqualTo(second.refreshToken)
        assertThat(tokenService.refreshTokenHash(first.refreshToken))
            .isEqualTo(tokenService.refreshTokenHash(first.refreshToken))
    }

    @Test
    fun versionedRefreshTokenCanBeParsedWithoutExposingSessionKey() {
        val tokenPair = tokenService.issueTokenPair(UserPrincipal(42L, "session-key-123"), 7)

        assertThat(tokenPair.refreshToken).doesNotContain("session-key-123")
        assertThat(tokenService.parseRefreshToken(tokenPair.refreshToken))
            .isEqualTo(JwtTokenService.RefreshTokenClaims("session-key-123", 7))
    }

    @Test
    fun malformedOrTamperedRefreshTokenReturnsFixedSafeError() {
        val token = tokenService.issueTokenPair(UserPrincipal(42L, "session-key-123"), 1).refreshToken
        val changedIndex = token.length / 2
        val original = token[changedIndex]
        val tampered = token.substring(0, changedIndex) + (if (original == 'A') 'B' else 'A') +
            token.substring(changedIndex + 1)

        assertThatThrownBy { tokenService.parseRefreshToken("raw-secret-value") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("刷新令牌格式无效")
            .hasMessageNotContaining("raw-secret-value")
        assertThatThrownBy { tokenService.parseRefreshToken(tampered) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("刷新令牌格式无效")
            .hasMessageNotContaining(tampered)
    }
}
