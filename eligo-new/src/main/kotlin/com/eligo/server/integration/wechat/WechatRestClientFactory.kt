package com.eligo.server.integration.wechat

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * 构建微信接口使用的 RestClient。
 *
 * 注意：此处只设置 baseUrl，绝不替换请求工厂。
 * 否则会覆盖测试中通过 MockRestServiceServer 绑定的 MockClientHttpRequestFactory，
 * 使离线测试请求真实网络。连接/读取超时通过 Spring Boot 的
 * `spring.http.client.connect-timeout / read-timeout` 全局配置。
 */
@Component
@Profile("!test")
class WechatRestClientFactory(
    private val builder: RestClient.Builder
) {

    fun build(baseUri: String): RestClient {
        return builder.clone()
            .baseUrl(baseUri)
            .build()
    }
}
