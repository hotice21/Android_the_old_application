package com.eligo.server.integration.wechat

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.web.client.RestClient

class WechatRestClientFactoryTests {

    @Test
    fun clonesManagedBuilderAndAppliesBaseUrlWithoutReplacingRequestFactory() {
        val managedBuilder = mock(RestClient.Builder::class.java)
        val configuredBuilder = mock(RestClient.Builder::class.java)
        val restClient = mock(RestClient::class.java)
        `when`(managedBuilder.clone()).thenReturn(configuredBuilder)
        `when`(configuredBuilder.baseUrl("https://api.weixin.qq.com"))
            .thenReturn(configuredBuilder)
        `when`(configuredBuilder.build()).thenReturn(restClient)

        val result =
            WechatRestClientFactory(managedBuilder)
                .build("https://api.weixin.qq.com")

        assertThat(result).isSameAs(restClient)
        verify(configuredBuilder).baseUrl("https://api.weixin.qq.com")
        // 绝不能覆盖请求工厂，否则 MockRestServiceServer 会被旁路、测试会请求真实网络
        verify(configuredBuilder, never()).requestFactory(any<ClientHttpRequestFactory>())
    }
}
