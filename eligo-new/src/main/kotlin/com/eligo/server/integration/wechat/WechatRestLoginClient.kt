package com.eligo.server.integration.wechat

import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper

@Component
class WechatRestLoginClient @Autowired constructor(
    private val properties: WechatProperties,
    private val objectMapper: ObjectMapper,
    builder: RestClient.Builder = RestClient.builder()
) : WechatLoginClient {

    constructor(properties: WechatProperties, builder: RestClient.Builder) : this(
        properties, ObjectMapper(), builder
    )

    private val restClient: RestClient = builder.baseUrl(properties.baseUri).build()

    override fun exchangeCode(code: String): WechatSession {
        if (isBlank(properties.appId) || isBlank(properties.appSecret) || isBlank(code)) {
            throw invalidCredential()
        }
        try {
            val responseBody = restClient.get()
                .uri { uriBuilder -> uriBuilder
                    .path("/sns/jscode2session")
                    .queryParam("appid", properties.appId)
                    .queryParam("secret", properties.appSecret)
                    .queryParam("js_code", code)
                    .queryParam("grant_type", "authorization_code")
                    .build() }
                .retrieve()
                .body(String::class.java)
            if (isBlank(responseBody)) {
                log.warn("event=wechat_login_exchange_failed failureType=INVALID_RESPONSE")
                throw invalidCredential()
            }
            val response = objectMapper.readValue(responseBody, WechatCodeResponse::class.java)
            if (response.errcode != null) {
                log.warn(
                    "event=wechat_login_exchange_failed failureType=WECHAT_ERROR wechatErrorCode={}",
                    response.errcode)
                throw invalidCredential()
            }
            if (isBlank(response.openid) || isBlank(response.session_key)) {
                log.warn("event=wechat_login_exchange_failed failureType=INVALID_RESPONSE")
                throw invalidCredential()
            }
            return WechatSession(response.openid!!, response.unionid, response.session_key!!)
        } catch (exception: BusinessException) {
            throw exception
        } catch (exception: JacksonException) {
            log.warn("event=wechat_login_exchange_failed failureType=INVALID_RESPONSE")
            throw invalidCredential()
        } catch (exception: RestClientException) {
            log.warn(
                "event=wechat_login_exchange_failed failureType=CLIENT_EXCEPTION exceptionType={}",
                exception.javaClass.simpleName)
            throw invalidCredential()
        }
    }

    private fun isBlank(value: String?): Boolean {
        return value == null || value.isBlank()
    }

    private fun invalidCredential(): BusinessException {
        return BusinessException(AccountUserFileErrorCode.WECHAT_CREDENTIAL_INVALID)
    }

    private data class WechatCodeResponse(
        val openid: String?,
        val session_key: String?,
        val unionid: String?,
        val errcode: Int?,
        val errmsg: String?
    )

    companion object {
        private val log = LoggerFactory.getLogger(WechatRestLoginClient::class.java)
    }
}
