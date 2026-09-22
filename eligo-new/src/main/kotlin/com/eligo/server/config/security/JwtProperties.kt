package com.eligo.server.config.security

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@ConfigurationProperties(prefix = "eligo.security.jwt")
data class JwtProperties(
    @field:NotBlank(message = "JWT 签发方不能为空") val issuer: String,
    @field:NotNull(message = "JWT 访问令牌有效期不能为空") val accessTokenTtl: Duration,
    @field:NotNull(message = "JWT 刷新令牌有效期不能为空") val refreshTokenTtl: Duration,
    @field:NotNull(message = "JWT 时钟偏差不能为空") val clockSkew: Duration,
    @field:NotBlank(message = "JWT 签名密钥不能为空") val signingKeyBase64: String
) {
    init {
        if (accessTokenTtl.isZero || accessTokenTtl.isNegative) {
            throw IllegalArgumentException("JWT 访问令牌有效期必须大于零")
        }
        if (refreshTokenTtl.isZero || refreshTokenTtl.isNegative) {
            throw IllegalArgumentException("JWT 刷新令牌有效期必须大于零")
        }
        if (clockSkew.isNegative) {
            throw IllegalArgumentException("JWT 时钟偏差不能小于零")
        }
    }
}
