package com.eligo.server.integration.wechat

import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.ThrowableAssert
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.http.HttpMethod.POST

class WechatRestPhoneClientTests {

    @Test
    fun exchangesPhoneCodeUsingOfficialPostBodyWithoutRealNetwork() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = client(builder, "access-token")
        server.expect(
                once(),
                requestTo(
                    "https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=access-token"
                )
            )
            .andExpect(method(POST))
            .andExpect(queryParam("access_token", "access-token"))
            .andExpect(content().json("""{"code":"phone-code"}"""))
            .andRespond(
                withSuccess(
                    """
                    {"errcode":0,"errmsg":"ok","phone_info":{
                      "phoneNumber":"+86 13800121234",
                      "purePhoneNumber":"13800121234",
                      "countryCode":"86",
                      "watermark":{"timestamp":1,"appid":"wx-app"}}}
                    """,
                    MediaType.APPLICATION_JSON
                )
            )

        val phone = client.exchangePhoneCode("phone-code")

        assertThat(phone.countryCode).isEqualTo("86")
        assertThat(phone.purePhoneNumber).isEqualTo("13800121234")
        server.verify()
    }

    @Test
    fun wechatBusinessErrorDoesNotLeakTokenCodeOrUpstreamMessage() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("getuserphonenumber")))
            .andRespond(
                withSuccess(
                    """{"errcode":40029,"errmsg":"invalid phone-code access-token"}""",
                    MediaType.APPLICATION_JSON
                )
            )

        assertCredentialError { client(builder, "access-token").exchangePhoneCode("phone-code") }
    }

    @Test
    fun missingOrInvalidPhoneFieldsUseStableCredentialError() {
        val missingBuilder = RestClient.builder()
        val missingServer = MockRestServiceServer.bindTo(missingBuilder).build()
        missingServer.expect(once(), requestTo(org.hamcrest.Matchers.containsString("getuserphonenumber")))
            .andRespond(withSuccess("""{"errcode":0,"phone_info":{}}""", MediaType.APPLICATION_JSON))
        assertCredentialError { client(missingBuilder, "access-token").exchangePhoneCode("phone-code") }

        val invalidBuilder = RestClient.builder()
        val invalidServer = MockRestServiceServer.bindTo(invalidBuilder).build()
        invalidServer.expect(once(), requestTo(org.hamcrest.Matchers.containsString("getuserphonenumber")))
            .andRespond(
                withSuccess(
                    """{"errcode":0,"phone_info":{"purePhoneNumber":"138-0012-1234","countryCode":"CN"}}""",
                    MediaType.APPLICATION_JSON
                )
            )
        assertCredentialError { client(invalidBuilder, "access-token").exchangePhoneCode("phone-code") }
    }

    @Test
    fun blankInputTokenAndHttpErrorsUseStableCredentialError() {
        assertCredentialError { client(RestClient.builder(), "access-token").exchangePhoneCode(" ") }
        assertCredentialError { client(RestClient.builder(), "").exchangePhoneCode("phone-code") }

        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("getuserphonenumber")))
            .andRespond(withServerError())
        assertCredentialError { client(builder, "access-token").exchangePhoneCode("phone-code") }
    }

    private fun client(builder: RestClient.Builder, token: String): WechatRestPhoneClient {
        val provider = WechatAccessTokenProvider { token }
        return WechatRestPhoneClient(
            WechatProperties("wx-app", "wx-secret", "https://api.weixin.qq.com"),
            provider,
            builder
        )
    }

    private fun assertCredentialError(operation: ThrowableAssert.ThrowingCallable) {
        assertThatThrownBy(operation)
            .isInstanceOfSatisfying(
                BusinessException::class.java
            ) { exception ->
                assertThat(exception.errorCode)
                    .isEqualTo(AccountUserFileErrorCode.WECHAT_CREDENTIAL_INVALID)
                assertThat(exception.message).isEqualTo("微信凭证无效")
                assertThat(exception.message).doesNotContain("access-token")
                assertThat(exception.message).doesNotContain("phone-code")
                assertThat(exception.message).doesNotContain("invalid")
            }
    }
}
