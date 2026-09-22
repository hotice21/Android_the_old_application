package com.eligo.server.agreement

import java.util.function.Function

import com.eligo.server.agreement.service.AgreementService
import com.eligo.server.agreement.vo.AgreementConsentView
import com.eligo.server.database.Stage2TestDatabaseCleaner
import com.eligo.server.integration.wechat.WechatRestClientFactory
import com.eligo.server.security.UserPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(
    properties = [
        "eligo.security.jwt.signing-key-base64=IB8eHRwbGhkYFxYVFBMSERAPDg0MCwoJCAcGBQQDAgE=",
        "eligo.security.sensitive-data.encryption-key-base64=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "eligo.security.sensitive-data.lookup-key-base64=Hx4dHBsaGRgXFhUUExIREA8ODQwLCgkIBwYFBAMCAQA="
    ]
)
@ActiveProfiles("it")
@EnabledIfSystemProperty(named = "eligo.agreement.concurrency.tests", matches = "true")
class AgreementConsentConcurrencyMySqlIntegrationTests {
    private val userId = 920001L
    private val agreementId = 920002L

    @Autowired
    private lateinit var agreementService: AgreementService

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @MockitoBean
    private lateinit var wechatRestClientFactory: WechatRestClientFactory

    @MockitoBean
    private lateinit var redisTemplate: StringRedisTemplate

    @BeforeEach
    fun prepareEffectiveAgreement() {
        Stage2TestDatabaseCleaner.cleanStage2Database(jdbcTemplate) {
            jdbcTemplate.update("DELETE FROM agreement_consents")
            jdbcTemplate.update("DELETE FROM agreements")
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId)
        }
        jdbcTemplate.update(
            """
            INSERT INTO users (id, status, version, created_at, updated_at)
            VALUES (?, 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """.trimIndent(), userId
        )
        jdbcTemplate.update(
            """
            INSERT INTO agreements (
                id, agreement_type, version_code, title, content, content_hash,
                status, requires_reconsent, effective_at, version, created_at, updated_at
            ) VALUES (?, 1, 'concurrency-v1', '并发协议', '协议正文',
                UNHEX(SHA2('concurrency-v1', 256)), 3, 1,
                UTC_TIMESTAMP(3), 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """.trimIndent(), agreementId
        )
    }

    @AfterEach
    fun cleanDatabase() {
        Stage2TestDatabaseCleaner.cleanAllStage2Tables(jdbcTemplate)
    }

    @Test
    fun concurrentConsentReturnsOneCreatedResponseAndOneOriginalFactResponse() {
        val principal = UserPrincipal(userId, "session-concurrency")

        val attempts = runConcurrently(
            { agreementService.consent(principal, agreementId) },
            { agreementService.consent(principal, agreementId) }
        )

        assertThat(attempts).allSatisfy { attempt -> assertThat(attempt.error).isNull() }
        assertThat(attempts).extracting(Function {  attempt -> attempt.response!!.created  })
            .containsExactlyInAnyOrder(true, false)
        assertThat(attempts).extracting(Function {  attempt -> attempt.response!!.agreementId  })
            .containsOnly(agreementId.toString())
        assertThat(attempts).extracting(Function {  attempt -> attempt.response!!.agreedAt  })
            .containsOnly(attempts[0].response!!.agreedAt)
        assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM agreement_consents
                WHERE user_id = ? AND agreement_id = ?
                """.trimIndent(), Int::class.java, userId, agreementId
            )
        ).isEqualTo(1)
    }

    private fun runConcurrently(
        firstOperation: Callable<AgreementConsentView>,
        secondOperation: Callable<AgreementConsentView>
    ): List<Attempt> {
        val executor: ExecutorService = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        try {
            val first = executor.submit(wrap(firstOperation, ready, start))
            val second = executor.submit(wrap(secondOperation, ready, start))
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()
            start.countDown()
            return listOf(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))
        } finally {
            executor.shutdownNow()
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
        }
    }

    private fun wrap(
        operation: Callable<AgreementConsentView>,
        ready: CountDownLatch,
        start: CountDownLatch
    ): Callable<Attempt> {
        return Callable {
            ready.countDown()
            start.await(5, TimeUnit.SECONDS)
            try {
                Attempt(operation.call(), null)
            } catch (throwable: Throwable) {
                Attempt(null, throwable)
            }
        }
    }

    private data class Attempt(val response: AgreementConsentView?, val error: Throwable?)
}
