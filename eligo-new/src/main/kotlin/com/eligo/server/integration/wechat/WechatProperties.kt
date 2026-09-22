package com.eligo.server.integration.wechat

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "eligo.integration.wechat")
class WechatProperties(
    var appId: String = "",
    var appSecret: String = "",
    var baseUri: String = "https://api.weixin.qq.com"
)
