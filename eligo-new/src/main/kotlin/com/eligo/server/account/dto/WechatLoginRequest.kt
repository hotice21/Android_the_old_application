package com.eligo.server.account.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class WechatLoginRequest(
    @field:NotBlank(message = "微信登录凭证不能为空")
    @field:Size(max = 128, message = "微信登录凭证长度不能超过 128 个字符")
    val wechatCode: String?,
    @field:NotBlank(message = "安装实例标识不能为空")
    @field:Size(min = 16, max = 128, message = "安装实例标识长度必须为 16 到 128 个字符")
    val installationId: String?,
    @field:NotBlank(message = "设备名称不能为空")
    @field:Size(max = 64, message = "设备名称长度不能超过 64 个字符")
    val deviceName: String?,
    @field:NotBlank(message = "平台不能为空")
    @field:Pattern(regexp = "WECHAT_MINIPROGRAM", message = "平台必须为微信小程序")
    val platform: String?,
    @field:Size(max = 32, message = "系统版本长度不能超过 32 个字符")
    val osVersion: String?,
    @field:NotBlank(message = "应用版本不能为空")
    @field:Size(max = 32, message = "应用版本长度不能超过 32 个字符")
    val appVersion: String?,
    @field:Size(max = 32, message = "地区编码长度不能超过 32 个字符")
    val regionCode: String?
)
