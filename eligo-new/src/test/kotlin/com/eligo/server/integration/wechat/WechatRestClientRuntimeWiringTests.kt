package com.eligo.server.integration.wechat

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Import
import org.springframework.web.client.RestClient

class WechatRestClientRuntimeWiringTests {

    private val contextRunner =
        ApplicationContextRunner()
            .withPropertyValues(
                "spring.profiles.active=startup-test",
                "spring.autoconfigure.exclude=" +
                    "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration," +
                    "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration," +
                    "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration"
            )
            .withUserConfiguration(TestApplication::class.java)

    @Test
    fun runtimeAutoConfigurationProvidesRestClientBuilderForWechatFactory() {
        contextRunner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).hasSingleBean(RestClient.Builder::class.java)
            assertThat(context).hasSingleBean(WechatRestClientFactory::class.java)
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(WechatRestClientFactory::class)
    class TestApplication
}
