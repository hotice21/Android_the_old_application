package com.eligo.server.integration.wechat

fun interface WechatAccessTokenProvider {

    fun currentAccessToken(): String
}
