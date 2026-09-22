package com.eligo.server.integration.wechat

interface WechatPhoneClient {

    fun exchangePhoneCode(code: String): AuthorizedPhone
}
