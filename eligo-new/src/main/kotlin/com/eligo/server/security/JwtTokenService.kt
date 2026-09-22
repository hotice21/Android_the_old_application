package com.eligo.server.security

import com.eligo.server.config.security.JwtProperties
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.Date

@Service
class JwtTokenService(
    private val jwtProperties: JwtProperties,
    private val sensitiveDataCodec: SensitiveDataCodec
) {
    private val signer: MACSigner

    init {
        signer = try {
            MACSigner(decodeSigningKey(jwtProperties.signingKeyBase64))
        } catch (exception: Exception) {
            throw IllegalArgumentException("JWT 签名密钥无效", exception)
        }
    }

    private val secureRandom = SecureRandom()

    fun issueTokenPair(principal: UserPrincipal): TokenPair = issueTokenPair(principal, 0)

    fun issueTokenPair(principal: UserPrincipal, refreshTokenVersion: Int): TokenPair {
        if (refreshTokenVersion < 0) {
            throw IllegalArgumentException("刷新令牌版本不能为负数")
        }
        val issuedAt = Instant.now()
        val accessTokenExpiresAt = issuedAt.plus(jwtProperties.accessTokenTtl)
        val refreshTokenExpiresAt = issuedAt.plus(jwtProperties.refreshTokenTtl)
        return TokenPair(
            createAccessToken(principal, issuedAt, accessTokenExpiresAt),
            accessTokenExpiresAt,
            generateRefreshToken(principal.sessionKey, refreshTokenVersion),
            refreshTokenExpiresAt
        )
    }

    fun refreshTokenHash(refreshToken: String): ByteArray =
        sensitiveDataCodec.lookupHash(REFRESH_TOKEN_HASH_PREFIX + refreshToken)

    fun parseRefreshToken(refreshToken: String): RefreshTokenClaims {
        return try {
            val plaintext = sensitiveDataCodec.decrypt(refreshToken)
            val segments = plaintext.split(":")
            if (segments.size != 4 || segments[0] != "rt1") {
                throw invalidRefreshToken()
            }
            val sessionKey = String(Base64.getUrlDecoder().decode(segments[1]), StandardCharsets.UTF_8)
            val version = segments[2].toInt()
            val secret = Base64.getUrlDecoder().decode(segments[3])
            if (sessionKey.isBlank() || sessionKey.length > 128 || version < 0
                || secret.size != REFRESH_TOKEN_LENGTH_BYTES
            ) {
                throw invalidRefreshToken()
            }
            RefreshTokenClaims(sessionKey, version)
        } catch (exception: RuntimeException) {
            throw invalidRefreshToken()
        }
    }

    private fun createAccessToken(principal: UserPrincipal, issuedAt: Instant, expiresAt: Instant): String {
        return try {
            val claims = JWTClaimsSet.Builder()
                .issuer(jwtProperties.issuer)
                .subject(principal.userId.toString())
                .claim("sid", principal.sessionKey)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .build()
            val token = SignedJWT(JWSHeader(JWSAlgorithm.HS256), claims)
            token.sign(signer)
            token.serialize()
        } catch (exception: Exception) {
            throw IllegalStateException("JWT 签发失败", exception)
        }
    }

    private fun generateRefreshToken(sessionKey: String, version: Int): String {
        val bytes = ByteArray(REFRESH_TOKEN_LENGTH_BYTES)
        secureRandom.nextBytes(bytes)
        val encodedSessionKey = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(sessionKey.toByteArray(StandardCharsets.UTF_8))
        val encodedSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return sensitiveDataCodec.encrypt("rt1:" + encodedSessionKey + ":" + version + ":" + encodedSecret)
    }

    private fun invalidRefreshToken(): IllegalArgumentException = IllegalArgumentException("刷新令牌格式无效")

    private fun decodeSigningKey(encodedKey: String): ByteArray {
        return try {
            val key = Base64.getDecoder().decode(encodedKey)
            if (key.size != KEY_LENGTH_BYTES) {
                throw IllegalArgumentException("JWT 签名密钥必须为 Base64 编码的 32 字节值")
            }
            key
        } catch (exception: IllegalArgumentException) {
            throw IllegalArgumentException("JWT 签名密钥必须为 Base64 编码的 32 字节值", exception)
        }
    }

    data class RefreshTokenClaims(val sessionKey: String, val version: Int)

    companion object {
        private const val KEY_LENGTH_BYTES = 32
        private const val REFRESH_TOKEN_LENGTH_BYTES = 32
        private const val REFRESH_TOKEN_HASH_PREFIX = "refresh-token:"
    }
}
