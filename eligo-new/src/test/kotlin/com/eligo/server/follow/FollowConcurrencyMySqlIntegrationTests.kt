package com.eligo.server.follow

import java.util.function.Function

import com.eligo.server.database.Stage2TestDatabaseCleaner
import com.eligo.server.follow.service.FollowCommandService
import com.eligo.server.follow.vo.FollowStateView
import com.eligo.server.integration.wechat.WechatRestClientFactory
import com.eligo.server.profile.service.ProfileService
import com.eligo.server.security.UserPrincipal
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest(
    properties = [
        "eligo.security.jwt.signing-key-base64=IB8eHRwbGhkYFxYVFBMSERAPDg0MCwoJCAcGBQQDAgE=",
        "eligo.security.sensitive-data.encryption-key-base64=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "eligo.security.sensitive-data.lookup-key-base64=Hx4dHBsaGRgXFhUUExIREA8ODQwLCgkIBwYFBAMCAQA="
    ]
)
@ActiveProfiles("it")
@EnabledIfSystemProperty(named = "eligo.database.tests", matches = "true")
class FollowConcurrencyMySqlIntegrationTests {

    private val followerId = 41_001L
    private val targetUserId = 41_002L
    private val organizationId = 41_003L
    private val principal = UserPrincipal(followerId, "follow-concurrency")

    @Autowired
    lateinit var follows: FollowCommandService

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @MockitoBean
    lateinit var completion: ProfileService

    @MockitoBean
    lateinit var redis: StringRedisTemplate

    @MockitoBean
    lateinit var wechatRestClientFactory: WechatRestClientFactory

    @BeforeEach
    fun prepareTargets() {
        Stage2TestDatabaseCleaner.cleanAllStage2Tables(jdbcTemplate)
        insertUser(followerId, "关注者")
        insertUser(targetUserId, "目标用户")
        jdbcTemplate.update(
            """
            INSERT INTO organizations (
                id, name, province_code, province_name, city_code, city_name,
                district_code, district_name, address_detail,
                contact_phone_ciphertext, contact_phone_lookup_hash,
                contact_phone_last_four, status, version, created_at, updated_at
            ) VALUES (?, '并发测试企业', '44', '广东省', '4403', '深圳市',
                '440305', '南山区', '测试地址', X'01',
                UNHEX(SHA2('follow-concurrency-org', 256)), '0001', 1, 0,
                UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, organizationId
        )
    }

    @AfterEach
    fun cleanDatabase() {
        Stage2TestDatabaseCleaner.cleanAllStage2Tables(jdbcTemplate)
    }

    @Test
    fun concurrentUserFollowReturnsSameWinningRelationship() {
        val barrier = CompletionBarrier()
        whenever(completion.isCompleted(followerId)).thenAnswer { barrier.await() }

        val attempts = runConcurrently(
            { follows.followUser(principal, targetUserId) },
            { follows.followUser(principal, targetUserId) }
        )

        assertSuccessfulSameWinner(attempts)
        assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM user_follows
                WHERE follower_user_id=? AND followed_user_id=?
                """, Long::class.java, followerId, targetUserId
            )
        ).isEqualTo(1L)
    }

    @Test
    fun concurrentOrganizationFollowReturnsSameWinningRelationship() {
        val barrier = CompletionBarrier()
        whenever(completion.isCompleted(followerId)).thenAnswer { barrier.await() }

        val attempts = runConcurrently(
            { follows.followOrganization(principal, organizationId) },
            { follows.followOrganization(principal, organizationId) }
        )

        assertSuccessfulSameWinner(attempts)
        assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM organization_follows
                WHERE follower_user_id=? AND organization_id=?
                """, Long::class.java, followerId, organizationId
            )
        ).isEqualTo(1L)
    }

    private fun insertUser(userId: Long, nickname: String) {
        jdbcTemplate.update(
            """
            INSERT INTO users (id, status, version, created_at, updated_at)
            VALUES (?, 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, userId
        )
        jdbcTemplate.update(
            """
            INSERT INTO user_profiles (
                user_id, nickname, completed_at, version, created_at, updated_at
            ) VALUES (?, ?, UTC_TIMESTAMP(3), 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, userId, nickname
        )
    }

    private fun runConcurrently(
        firstOperation: Callable<FollowStateView>,
        secondOperation: Callable<FollowStateView>
    ): List<Attempt> {
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        try {
            val first = executor.submit(wrap(firstOperation, ready, start))
            val second = executor.submit(wrap(secondOperation, ready, start))
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()
            start.countDown()
            return listOf(
                first.get(20, TimeUnit.SECONDS),
                second.get(20, TimeUnit.SECONDS)
            )
        } finally {
            executor.shutdownNow()
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
        }
    }

    private fun wrap(
        operation: Callable<FollowStateView>,
        ready: CountDownLatch,
        start: CountDownLatch
    ): Callable<Attempt> = Callable {
        ready.countDown()
        start.await(5, TimeUnit.SECONDS)
        try {
            Attempt(operation.call(), null)
        } catch (throwable: Throwable) {
            Attempt(null, throwable)
        }
    }

    private fun assertSuccessfulSameWinner(attempts: List<Attempt>) {
        assertThat(attempts)
            .allSatisfy { attempt ->
                assertThat(attempt.error).isNull()
                assertThat(attempt.response!!.following).isTrue()
            }
        assertThat(attempts).extracting(Function {  it.response!!.followedAt  })
            .containsOnly(attempts[0].response!!.followedAt)
    }

    private data class Attempt(val response: FollowStateView?, val error: Throwable?)

    private class CompletionBarrier {
        private val reached = CountDownLatch(2)
        private val release = CountDownLatch(1)

        fun await(): Boolean {
            reached.countDown()
            if (reached.await(5, TimeUnit.SECONDS)) {
                release.countDown()
            }
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw IllegalStateException("并发关注未同时到达资料完成度检查")
            }
            return true
        }
    }
}
