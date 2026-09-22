package com.eligo.server.security

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "eligo.security.sensitive-data")
data class SensitiveDataProperties(
    @field:NotBlank(message = "敏感数据加密密钥不能为空") val encryptionKeyBase64: String,
    @field:NotBlank(message = "敏感数据查找密钥不能为空") val lookupKeyBase64: String
)
