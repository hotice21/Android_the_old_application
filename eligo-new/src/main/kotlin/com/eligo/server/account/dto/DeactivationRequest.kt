package com.eligo.server.account.dto

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class DeactivationRequest(
    @field:NotBlank(message = "微信登录凭证不能为空")
    @field:Size(max = 128, message = "微信登录凭证长度不能超过 128 个字符")
    val wechatCode: String,
    @field:AssertTrue(message = "必须明确确认注销账号")
    val confirmed: Boolean
)
