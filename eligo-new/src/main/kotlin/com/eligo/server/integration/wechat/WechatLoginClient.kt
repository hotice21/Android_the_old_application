package com.eligo.server.integration.wechat

interface WechatLoginClient {

    fun exchangeCode(code: String): WechatSession
}
