package com.eligo.server.recommendation

import com.eligo.server.config.security.JwtProperties
import java.util.Base64
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RecommendationProperties::class)
class RecommendationConfiguration {

    @Bean
    fun recommendationCursorCodec(jwtProperties: JwtProperties): RecommendationCursorCodec {
        try {
            return RecommendationCursorCodec(
                Base64.getDecoder().decode(jwtProperties.signingKeyBase64))
        } catch (exception: IllegalArgumentException) {
            throw IllegalStateException("JWT 签名密钥无法用于推荐游标签名", exception)
        }
    }
}
