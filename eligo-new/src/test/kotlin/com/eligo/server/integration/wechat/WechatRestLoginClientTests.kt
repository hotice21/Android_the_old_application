package com.eligo.server.integration.wechat

import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.http.HttpMethod.GET
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

@ExtendWith(OutputCaptureExtension::class)
class WechatRestLoginClientTests {

    @Test
    fun exchangesCodeWithOfficialParametersWithoutRealNetwork(output: CapturedOutput) {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = WechatRestLoginClient(
            WechatProperties("wx-app", "wx-secret", "https://api.weixin.qq.com"), builder
        )
        server.expect(
                once(),
                requestTo("https://api.weixin.qq.com/sns/jscode2session?appid=wx-app&secret=wx-secret&js_code=temporary-code&grant_type=authorization_code")
            )
            .andExpect(method(GET))
            .andExpect(queryParam("appid", "wx-app"))
            .andExpect(queryParam("secret", "wx-secret"))
            .andExpect(queryParam("js_code", "temporary-code"))
            .andExpect(queryParam("grant_type", "authorization_code"))
            .andRespond(
                withSuccess(
                    """{"openid":"openid-a","session_key":"session-a","unionid":"unionid-a"}""",
                    MediaType.TEXT_PLAIN
                )
            )

        val session = client.exchangeCode("temporary-code")

        assertThat(session.openid).isEqualTo("openid-a")
        assertThat(session.unionid).isEqualTo("unionid-a")
        assertThat(output).doesNotContain("event=wechat_login_exchange_failed")
        server.verify()
    }

    @Test
    fun wechatErrorLogsOnlySafeErrorCode(output: CapturedOutput) {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = WechatRestLoginClient(
            WechatProperties("wx-app", "wx-secret", "https://api.weixin.qq.com"), builder
        )
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("/sns/jscode2session")))
            .andRespond(
                withSuccess(
                    """{"errcode":40029,"errmsg":"invalid-code-message temporary-code wx-secret"}""",
                    MediaType.APPLICATION_JSON
                )
            )

        assertCredentialError { client.exchangeCode("temporary-code") }

        assertThat(output)
            .contains("event=wechat_login_exchange_failed")
            .contains("failureType=WECHAT_ERROR")
            .contains("wechatErrorCode=40029")
            .doesNotContain("temporary-code")
            .doesNotContain("wx-secret")
            .doesNotContain("invalid-code-message")
    }

    @Test
    fun invalidResponseLogsOnlyFixedReason(output: CapturedOutput) {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = WechatRestLoginClient(
            WechatProperties("wx-app", "wx-secret", "https://api.weixin.qq.com"), builder
        )
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("/sns/jscode2session")))
            .andRespond(withSuccess("""{"openid":"openid-sensitive"}""", MediaType.APPLICATION_JSON))

        assertCredentialError { client.exchangeCode("temporary-code") }

        assertThat(output)
            .contains("event=wechat_login_exchange_failed")
            .contains("failureType=INVALID_RESPONSE")
            .doesNotContain("openid-sensitive")
            .doesNotContain("temporary-code")
            .doesNotContain("wx-secret")
    }

    @Test
    fun invalidJsonLogsOnlyFixedReason(output: CapturedOutput) {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = WechatRestLoginClient(
            WechatProperties("wx-app", "wx-secret", "https://api.weixin.qq.com"), builder
        )
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("/sns/jscode2session")))
            .andRespond(withSuccess("invalid-response-sensitive", MediaType.TEXT_PLAIN))

        assertCredentialError { client.exchangeCode("temporary-code") }

        assertThat(output)
            .contains("event=wechat_login_exchange_failed")
            .contains("failureType=INVALID_RESPONSE")
            .doesNotContain("invalid-response-sensitive")
            .doesNotContain("temporary-code")
            .doesNotContain("wx-secret")
    }

    @Test
    fun clientExceptionLogsOnlyExceptionType(output: CapturedOutput) {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = WechatRestLoginClient(
            WechatProperties("wx-app", "wx-secret", "https://api.weixin.qq.com"), builder
        )
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("/sns/jscode2session")))
            .andRespond(withServerError())

        assertCredentialError { client.exchangeCode("temporary-code") }

        assertThat(output)
            .contains("event=wechat_login_exchange_failed")
            .contains("failureType=CLIENT_EXCEPTION")
            .contains("exceptionType=")
            .doesNotContain("temporary-code")
            .doesNotContain("wx-secret")
            .doesNotContain("api.weixin.qq.com")
    }

    @Test
    fun missingConfigurationUsesStableCredentialError() {
        assertCredentialError {
            WechatRestLoginClient(
                WechatProperties("", "", "https://api.weixin.qq.com"), RestClient.builder()
            ).exchangeCode("temporary-code")
        }
    }

    private fun assertCredentialError(operation: Runnable) {
        assertThatThrownBy { operation.run() }
            .isInstanceOfSatisfying(BusinessException::class.java) { exception ->
                assertThat(exception.errorCode).isEqualTo(AccountUserFileErrorCode.WECHAT_CREDENTIAL_INVALID)
            }
    }
}
