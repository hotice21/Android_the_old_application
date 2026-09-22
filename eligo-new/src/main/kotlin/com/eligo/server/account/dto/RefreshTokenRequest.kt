package com.eligo.server.account.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RefreshTokenRequest(
    @field:NotBlank(message = "刷新令牌不能为空")
    @field:Size(max = 2048, message = "刷新令牌长度不能超过 2048 个字符")
    val refreshToken: String,
    @field:NotBlank(message = "安装实例标识不能为空")
    @field:Size(min = 16, max = 128, message = "安装实例标识长度必须为 16 到 128 个字符")
    val installationId: String
)
