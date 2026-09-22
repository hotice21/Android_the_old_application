package com.eligo.server.integration.wechat

data class WechatSession(
    val openid: String,
    val unionid: String?,
    val sessionKey: String
)
