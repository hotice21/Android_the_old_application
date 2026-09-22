package com.eligo.server.security

import com.eligo.server.config.security.JwtProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.Base64

class VersionedRefreshTokenTests {
    private val KEY = Base64.getEncoder().encodeToString(
        byteArrayOf(
            32, 31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 21, 20, 19, 18, 17,
            16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1
        )
    )
    private val codec = AesGcmSensitiveDataCodec(SensitiveDataProperties(KEY, KEY))
    private val service = JwtTokenService(
        JwtProperties("eligo", Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofSeconds(30), KEY),
        codec
    )

    @Test
    fun versionedTokenAuthenticatesSessionKeyAndVersionWithoutExposingThem() {
        val pair = service.issueTokenPair(UserPrincipal(42L, "session-key-123"), 7)

        val claims = service.parseRefreshToken(pair.refreshToken)

        assertThat(claims).isEqualTo(JwtTokenService.RefreshTokenClaims("session-key-123", 7))
        assertThat(pair.refreshToken).doesNotContain("session-key-123")
    }

    @Test
    fun malformedAndTamperedTokensUseNonLeakingStableError() {
        val valid = service.issueTokenPair(UserPrincipal(42L, "session-key-123"), 1).refreshToken
        val changedIndex = valid.length / 2
        val original = valid[changedIndex]
        val tampered = valid.substring(0, changedIndex) + (if (original == 'A') 'B' else 'A') +
            valid.substring(changedIndex + 1)

        assertRejected("malformed-secret-value")
        assertRejected(tampered)
    }

    private fun assertRejected(token: String) {
        assertThatThrownBy { service.parseRefreshToken(token) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("刷新令牌格式无效")
            .hasMessageNotContaining(token)
    }
}
