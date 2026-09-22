package com.eligo.server.integration.wechat

import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.ThrowableAssert
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.time.Duration

class RedisWechatAccessTokenFailureTests {

    @Test
    fun redisReadFailureUsesStableBoundaryWithoutLeakingDriverDetails() {
        val redis = mock(StringRedisTemplate::class.java)
        `when`(redis.opsForValue())
            .thenThrow(
                DataAccessResourceFailureException(
                    "读取失败，参数包含 wx-secret 和 cached-token"
                )
            )
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()

        assertCredentialError { provider(redis, builder).currentAccessToken() }

        server.verify()
    }

    @Suppress("unchecked_cast")
    @Test
    fun redisWriteFailureUsesStableBoundaryWithoutLeakingFreshToken() {
        val redis = mock(StringRedisTemplate::class.java)
        val values = mock(ValueOperations::class.java) as ValueOperations<String, String>
        `when`(redis.opsForValue()).thenReturn(values)
        doThrow(
            DataAccessResourceFailureException(
                "写入失败，参数包含 wx-secret 和 fresh-token"
            )
        ).`when`(values)
            .set(
                "eligo:wechat:access-token:wx-app",
                "fresh-token",
                Duration.ofSeconds(6900)
            )
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("/cgi-bin/token")))
            .andRespond(
                withSuccess(
                    """{"access_token":"fresh-token","expires_in":7200}""",
                    MediaType.APPLICATION_JSON
                )
            )

        assertCredentialError { provider(redis, builder).currentAccessToken() }

        server.verify()
    }

    private fun provider(
        redis: StringRedisTemplate,
        builder: RestClient.Builder
    ): RedisWechatAccessTokenProvider {
        return RedisWechatAccessTokenProvider(
            WechatProperties("wx-app", "wx-secret", "https://api.weixin.qq.com"),
            redis,
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
                assertThat(exception.message)
                    .doesNotContain("wx-secret", "cached-token", "fresh-token")
                assertThat(exception.cause).isNull()
            }
    }
}
