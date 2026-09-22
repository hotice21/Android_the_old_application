package com.eligo.server.integration.wechat

import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.ThrowableAssert
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
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
import java.time.Duration

class RedisWechatAccessTokenProviderTests {

    @Suppress("unchecked_cast")
    @Test
    fun redisHitReturnsCachedTokenWithoutCallingWechat() {
        val redis = mock(StringRedisTemplate::class.java)
        val values = mock(ValueOperations::class.java) as ValueOperations<String, String>
        `when`(redis.opsForValue()).thenReturn(values)
        `when`(values.get("eligo:wechat:access-token:wx-app")).thenReturn("cached-token")
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val provider = provider(redis, builder)

        assertThat(provider.currentAccessToken()).isEqualTo("cached-token")

        verify(values, never()).set(
            any<String>(),
            any<String>(),
            any<Duration>()
        )
        server.verify()
    }

    @Suppress("unchecked_cast")
    @Test
    fun cacheMissGetsOfficialTokenAndUsesFiveMinuteSafetyMargin() {
        val redis = mock(StringRedisTemplate::class.java)
        val values = mock(ValueOperations::class.java) as ValueOperations<String, String>
        `when`(redis.opsForValue()).thenReturn(values)
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(
                once(),
                requestTo(
                    "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=wx-app&secret=wx-secret"
                )
            )
            .andExpect(method(GET))
            .andExpect(queryParam("grant_type", "client_credential"))
            .andExpect(queryParam("appid", "wx-app"))
            .andExpect(queryParam("secret", "wx-secret"))
            .andRespond(
                withSuccess(
                    """{"access_token":"fresh-token","expires_in":7200}""",
                    MediaType.APPLICATION_JSON
                )
            )

        assertThat(provider(redis, builder).currentAccessToken()).isEqualTo("fresh-token")

        verify(values)
            .set(
                "eligo:wechat:access-token:wx-app",
                "fresh-token",
                Duration.ofSeconds(6900)
            )
        server.verify()
    }

    @Suppress("unchecked_cast")
    @Test
    fun tokenErrorsUseStableBoundaryWithoutLeakingCredentialsOrUpstreamContent() {
        val redis = mock(StringRedisTemplate::class.java)
        val values = mock(ValueOperations::class.java) as ValueOperations<String, String>
        `when`(redis.opsForValue()).thenReturn(values)
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("/cgi-bin/token")))
            .andRespond(
                withSuccess(
                    """{"errcode":40013,"errmsg":"invalid appid wx-secret"}""",
                    MediaType.APPLICATION_JSON
                )
            )

        assertCredentialError { provider(redis, builder).currentAccessToken() }
        verify(values, never()).set(
            any<String>(),
            any<String>(),
            any<Duration>()
        )

        assertCredentialError {
            RedisWechatAccessTokenProvider(
                WechatProperties(
                    "", "wx-secret", "https://api.weixin.qq.com"
                ),
                redis,
                RestClient.builder()
            ).currentAccessToken()
        }
    }

    @Suppress("unchecked_cast")
    @Test
    fun emptyFieldsAndHttpErrorsUseStableBoundary() {
        val redis = mock(StringRedisTemplate::class.java)
        val values = mock(ValueOperations::class.java) as ValueOperations<String, String>
        `when`(redis.opsForValue()).thenReturn(values)

        val emptyBuilder = RestClient.builder()
        val emptyServer = MockRestServiceServer.bindTo(emptyBuilder).build()
        emptyServer.expect(once(), requestTo(org.hamcrest.Matchers.containsString("/cgi-bin/token")))
            .andRespond(withSuccess("""{"access_token":"","expires_in":7200}""", MediaType.APPLICATION_JSON))
        assertCredentialError { provider(redis, emptyBuilder).currentAccessToken() }

        val failingBuilder = RestClient.builder()
        val failingServer = MockRestServiceServer.bindTo(failingBuilder).build()
        failingServer.expect(once(), requestTo(org.hamcrest.Matchers.containsString("/cgi-bin/token")))
            .andRespond(withServerError())
        assertCredentialError { provider(redis, failingBuilder).currentAccessToken() }
    }

    private fun provider(
        redis: StringRedisTemplate, builder: RestClient.Builder
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
                assertThat(exception.message).doesNotContain("wx-secret")
                assertThat(exception.message).doesNotContain("cached-token")
                assertThat(exception.message).doesNotContain("fresh-token")
                assertThat(exception.message).doesNotContain("invalid appid")
                assertThat(exception.cause).isNull()
            }
    }
}
