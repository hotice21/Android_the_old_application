package com.eligo.server.integration.wechat

import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Component
@Profile("!test")
class WechatRestPhoneClient @Autowired constructor(
    private val properties: WechatProperties,
    private val accessTokenProvider: WechatAccessTokenProvider,
    private val restClients: WechatRestClientFactory
) : WechatPhoneClient {

    private val restClient: RestClient = restClients.build(properties.baseUri)

    constructor(
        properties: WechatProperties,
        accessTokenProvider: WechatAccessTokenProvider,
        builder: RestClient.Builder
    ) : this(properties, accessTokenProvider, WechatRestClientFactory(builder))

    override fun exchangePhoneCode(code: String): AuthorizedPhone {
        if (isBlank(code) || isBlank(properties.baseUri)) {
            throw invalidCredential()
        }
        val accessToken = accessTokenProvider.currentAccessToken()
        if (isBlank(accessToken)) {
            throw invalidCredential()
        }
        return try {
            val response = restClient.post()
                .uri { uriBuilder -> uriBuilder
                    .path("/wxa/business/getuserphonenumber")
                    .queryParam("access_token", accessToken)
                    .build() }
                .body(PhoneCodeRequest(code))
                .retrieve()
                .body(WechatPhoneResponse::class.java)
            if (!isValid(response)) {
                throw invalidCredential()
            }
            AuthorizedPhone(
                response!!.phone_info!!.countryCode!!,
                response.phone_info!!.purePhoneNumber!!)
        } catch (exception: BusinessException) {
            throw exception
        } catch (exception: RestClientException) {
            throw invalidCredential()
        }
    }

    private fun isValid(response: WechatPhoneResponse?): Boolean {
        if (response == null
            || response.errcode == null
            || response.errcode != 0
            || response.phone_info == null) {
            return false
        }
        val countryCode = response.phone_info!!.countryCode
        val phone = response.phone_info!!.purePhoneNumber
        return countryCode != null
            && countryCode.matches("[0-9]{1,8}".toRegex())
            && phone != null
            && phone.matches("[0-9]{7,20}".toRegex())
    }

    private fun isBlank(value: String?): Boolean {
        return value == null || value.isBlank()
    }

    private fun invalidCredential(): BusinessException {
        return BusinessException(AccountUserFileErrorCode.WECHAT_CREDENTIAL_INVALID)
    }

    private data class PhoneCodeRequest(val code: String)

    private data class WechatPhoneResponse(
        val errcode: Int?,
        val errmsg: String?,
        val phone_info: PhoneInfo?
    )

    private data class PhoneInfo(
        val phoneNumber: String?,
        val purePhoneNumber: String?,
        val countryCode: String?
    )
}
