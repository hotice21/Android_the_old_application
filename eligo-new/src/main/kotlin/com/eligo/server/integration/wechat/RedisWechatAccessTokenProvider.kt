package com.eligo.server.integration.wechat

import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import java.time.Duration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Component
@Profile("!test")
class RedisWechatAccessTokenProvider @Autowired constructor(
    private val properties: WechatProperties,
    private val redis: StringRedisTemplate,
    private val restClients: WechatRestClientFactory
) : WechatAccessTokenProvider {

    private val restClient: RestClient = restClients.build(properties.baseUri)

    constructor(
        properties: WechatProperties,
        redis: StringRedisTemplate,
        builder: RestClient.Builder
    ) : this(properties, redis, WechatRestClientFactory(builder))

    override fun currentAccessToken(): String {
        if (isBlank(properties.appId)
            || isBlank(properties.appSecret)
            || isBlank(properties.baseUri)) {
            throw invalidCredential()
        }
        val key = CACHE_KEY_PREFIX + properties.appId
        return try {
            val cached = redis.opsForValue().get(key)
            if (!isBlank(cached)) {
                return cached!!
            }

            val response = restClient.get()
                .uri { uriBuilder -> uriBuilder
                    .path("/cgi-bin/token")
                    .queryParam("grant_type", "client_credential")
                    .queryParam("appid", properties.appId)
                    .queryParam("secret", properties.appSecret)
                    .build() }
                .retrieve()
                .body(WechatTokenResponse::class.java)
            if (response == null
                || response.errcode != null
                || isBlank(response.access_token)
                || response.expires_in == null
                || response.expires_in!! <= TOKEN_SAFETY_MARGIN_SECONDS) {
                throw invalidCredential()
            }
            val ttl = Duration.ofSeconds(
                (response.expires_in!! - TOKEN_SAFETY_MARGIN_SECONDS).toLong())
            redis.opsForValue().set(key, response.access_token!!, ttl)
            response.access_token!!
        } catch (exception: BusinessException) {
            throw exception
        } catch (exception: DataAccessException) {
            throw invalidCredential()
        } catch (exception: RestClientException) {
            throw invalidCredential()
        }
    }

    private fun isBlank(value: String?): Boolean {
        return value == null || value.isBlank()
    }

    private fun invalidCredential(): BusinessException {
        return BusinessException(AccountUserFileErrorCode.WECHAT_CREDENTIAL_INVALID)
    }

    private data class WechatTokenResponse(
        val access_token: String?,
        val expires_in: Int?,
        val errcode: Int?,
        val errmsg: String?
    )

    companion object {
        private const val TOKEN_SAFETY_MARGIN_SECONDS = 300
        private const val CACHE_KEY_PREFIX = "eligo:wechat:access-token:"
    }
}
