package com.eligo.server.account.service

import com.eligo.server.account.dto.DeactivationRequest
import com.eligo.server.account.dto.RefreshTokenRequest
import com.eligo.server.account.dto.WechatLoginRequest
import com.eligo.server.account.vo.LoginResponse
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.database.Stage2TestDatabaseCleaner
import com.eligo.server.integration.wechat.WechatLoginClient
import com.eligo.server.integration.wechat.WechatRestClientFactory
import com.eligo.server.integration.wechat.WechatSession
import com.eligo.server.security.UserPrincipal
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.apache.ibatis.executor.Executor
import org.apache.ibatis.mapping.MappedStatement
import org.apache.ibatis.plugin.Interceptor
import org.apache.ibatis.plugin.Intercepts
import org.apache.ibatis.plugin.Invocation
import org.apache.ibatis.plugin.Signature
import org.apache.ibatis.session.ResultHandler
import org.apache.ibatis.session.RowBounds
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.any
import org.mockito.Mockito.`when`

@SpringBootTest(properties = [
        "eligo.security.jwt.signing-key-base64=IB8eHRwbGhkYFxYVFBMSERAPDg0MCwoJCAcGBQQDAgE=",
        "eligo.security.sensitive-data.encryption-key-base64=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "eligo.security.sensitive-data.lookup-key-base64=Hx4dHBsaGRgXFhUUExIREA8ODQwLCgkIBwYFBAMCAQA="
])
@ActiveProfiles("it")
@EnabledIfSystemProperty(named = "eligo.auth.concurrency.tests", matches = "true")
@Import(AuthConcurrencyMySqlIntegrationTests.LockProbeConfiguration::class)
class AuthConcurrencyMySqlIntegrationTests {

    @Autowired
    private lateinit var authService: AuthService

    @Autowired
    private lateinit var accountDataService: AccountDataService

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @MockitoBean
    private lateinit var wechatLoginClient: WechatLoginClient

    @MockitoBean
    private lateinit var wechatRestClientFactory: WechatRestClientFactory

    @MockitoBean
    private lateinit var redisTemplate: StringRedisTemplate

    @Autowired
    private lateinit var lockOrderProbe: AuthLockOrderProbe

    @BeforeEach
    fun cleanDatabase() {
        Stage2TestDatabaseCleaner.cleanAllStage2Tables(jdbcTemplate)
        `when`(wechatLoginClient.exchangeCode(any<String>()))
            .thenReturn(WechatSession("concurrent-openid", "concurrent-unionid", "临时会话值"))
    }

    @Test
    fun concurrentFirstLoginCreatesOnlyOneUserForOneWechatIdentity() {
        val attempts = runConcurrently(
                Callable { authService.login(loginRequest("installation-concurrent-login")) },
                Callable { authService.login(loginRequest("installation-concurrent-login")) })

        assertThat(attempts).allMatch { attempt -> attempt.error == null }
        assertThat(attempts.stream().map { attempt -> attempt.response!!.userId }.distinct().count())
            .isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Int::class.java)).isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_wechat_accounts WHERE status = 1", Int::class.java)).isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_login_sessions WHERE status = 1 AND expires_at > UTC_TIMESTAMP(3)",
                Int::class.java)).isEqualTo(1)
    }

    @Test
    fun firstLoginIdentityRaceAndDeactivationUseOneLockOrderWithoutDeadlock() {
        val winnerReadyToCommit = CountDownLatch(1)
        val releaseWinner = CountDownLatch(1)
        val reverseIdentityLocked = CountDownLatch(1)
        val releaseReversePath = CountDownLatch(1)
        val ordinaryRetryRead = CountDownLatch(1)
        val releaseOrdinaryPath = CountDownLatch(1)
        val deactivationUserLocked = CountDownLatch(1)
        lockOrderProbe.configure(
                winnerReadyToCommit,
                releaseWinner,
                reverseIdentityLocked,
                releaseReversePath,
                ordinaryRetryRead,
                releaseOrdinaryPath,
                deactivationUserLocked)

        val executor = Executors.newFixedThreadPool(3)
        try {
            val winner = executor.submit(Callable { namedLoginAttempt(
                    "首次登录胜者", "installation-winner") })
            if (!winnerReadyToCommit.await(10, TimeUnit.SECONDS)) {
                val premature = winner.get(5, TimeUnit.SECONDS)
                if (premature.error != null) {
                    throw AssertionError("首次登录胜者在提交闩锁前失败", premature.error)
                }
                throw AssertionError("登录会话 Mapper 监测点未触发")
            }

            val competitor = executor.submit(Callable { namedLoginAttempt(
                    "首次登录竞争者", "installation-competitor") })
            awaitDatabaseLockWait(
                    "user_wechat_accounts", "uk_wechat_active_identity")
            releaseWinner.countDown()
            val winnerResult = winner.get(20, TimeUnit.SECONDS)
            assertThat(winnerResult.error).isNull()

            val usedReversePath = awaitEither(
                    reverseIdentityLocked, ordinaryRetryRead, 10, TimeUnit.SECONDS)
            assertThat(usedReversePath || ordinaryRetryRead.count == 0L).isTrue()

            val deactivation = executor.submit(Callable {
                Thread.currentThread().name = "注销申请"
                try {
                    accountDataService.requestDeactivation(
                            UserPrincipal(
                                    winnerResult.response!!.userId!!.toLong(),
                                    "注销并发会话"),
                            DeactivationRequest("deactivation-code", true))
                    null
                } catch (throwable: Throwable) {
                    throwable
                }
            })
            assertThat(deactivationUserLocked.await(10, TimeUnit.SECONDS)).isTrue()

            if (usedReversePath) {
                awaitDatabaseLockWait("user_wechat_accounts", null)
                releaseReversePath.countDown()
            } else {
                assertThat(deactivation.get(20, TimeUnit.SECONDS)).isNull()
                releaseOrdinaryPath.countDown()
            }

            val competitorResult = competitor.get(20, TimeUnit.SECONDS)
            val deactivationFailure = deactivation.get(20, TimeUnit.SECONDS)
            assertThat(competitorResult.error).isNull()
            assertThat(deactivationFailure).isNull()
        } finally {
            releaseWinner.countDown()
            releaseReversePath.countDown()
            releaseOrdinaryPath.countDown()
            executor.shutdownNow()
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
        }

        assertThat(lockOrderProbe.reverseIdentityLockCount()).isZero()
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users", Int::class.java)).isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_wechat_accounts WHERE status=1",
                Int::class.java)).isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM users LIMIT 1", Int::class.java)).isEqualTo(2)
    }

    @Test
    fun concurrentSameRefreshTokenSucceedsAtMostOnceAndReplayRevokesSession() {
        val login = authService.login(loginRequest("installation-concurrent-refresh"))
        val request = RefreshTokenRequest(
                login.refreshToken!!, "installation-concurrent-refresh")

        val attempts = runConcurrently(
                Callable { authService.refresh(request) },
                Callable { authService.refresh(request) })

        assertThat(attempts.stream().filter { attempt -> attempt.response != null }.count()).isEqualTo(1)
        assertThat(attempts.stream().filter { attempt -> attempt.error is BusinessException }.count())
            .isEqualTo(1)
        val rejected = attempts.stream()
                .map(Attempt::error).filter { it is BusinessException }.findFirst().orElseThrow() as BusinessException
        assertThat(rejected.errorCode).isEqualTo(AccountUserFileErrorCode.REFRESH_TOKEN_INVALID)
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM user_login_sessions WHERE id = ?", Int::class.java,
                login.sessionId!!.toLong())).isEqualTo(2)
        assertThat(jdbcTemplate.queryForObject(
                "SELECT revoke_reason FROM user_login_sessions WHERE id = ?", String::class.java,
                login.sessionId!!.toLong())).isEqualTo("REFRESH_TOKEN_REPLAY")
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_security_events WHERE event_type = 'REFRESH_TOKEN_REPLAY'",
                Int::class.java)).isEqualTo(1)
    }

    private fun runConcurrently(
            firstOperation: Callable<LoginResponse>,
            secondOperation: Callable<LoginResponse>
    ): List<Attempt> {
        val executor = Executors.newFixedThreadPool(2)
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

    private fun namedLoginAttempt(threadName: String, installationId: String): Attempt {
        Thread.currentThread().name = threadName
        return try {
            Attempt(authService.login(loginRequest(installationId)), null)
        } catch (throwable: Throwable) {
            Attempt(null, throwable)
        }
    }

    private fun awaitEither(
            first: CountDownLatch,
            second: CountDownLatch,
            timeout: Long,
            unit: TimeUnit
    ): Boolean {
        val deadline = System.nanoTime() + unit.toNanos(timeout)
        while (System.nanoTime() < deadline) {
            if (first.count == 0L) {
                return true
            }
            if (second.count == 0L) {
                return false
            }
            Thread.sleep(10)
        }
        throw AssertionError("等待登录竞争路径超时")
    }

    private fun awaitDatabaseLockWait(tableName: String, indexName: String?) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            val waiting = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM performance_schema.data_lock_waits waits
                    JOIN performance_schema.data_locks requested
                      ON requested.engine=waits.engine
                     AND requested.engine_lock_id=waits.requesting_engine_lock_id
                    WHERE requested.object_schema=DATABASE()
                      AND requested.object_name=?
                      AND (? IS NULL OR requested.index_name=?)
                      AND requested.lock_type='RECORD'
                      AND requested.lock_status='WAITING'
                    """, Int::class.java, tableName, indexName, indexName)
            if (waiting != null && waiting >= 1) {
                return
            }
            Thread.sleep(20)
        }
        throw AssertionError(
                "等待目标数据库锁超时，表=$tableName，索引=$indexName")
    }

    private fun wrap(
            operation: Callable<LoginResponse>,
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

    private fun loginRequest(installationId: String): WechatLoginRequest {
        return WechatLoginRequest(
                "temporary-wechat-code", installationId, "临时并发设备",
                "WECHAT_MINIPROGRAM", "18.0", "2.3.0", "CN-44")
    }

    private data class Attempt(val response: LoginResponse?, val error: Throwable?)

    @TestConfiguration(proxyBeanMethods = false)
    class LockProbeConfiguration {

        @Bean
        fun authLockOrderProbe(): AuthLockOrderProbe {
            return AuthLockOrderProbe()
        }

        @Bean
        fun authLockOrderInterceptor(probe: AuthLockOrderProbe): Interceptor {
            return AuthLockOrderInterceptor(probe)
        }
    }

    class AuthLockOrderProbe {
        companion object {
            private const val LOGIN_SESSION_INSERT =
                    "com.eligo.server.account.mapper.UserLoginSessionMapper.insert"
            private const val FIND_IDENTITY =
                    "com.eligo.server.account.mapper.UserWechatAccountMapper" +
                            ".findActiveByAppIdAndOpenidHash"
            private const val LOCK_IDENTITY =
                    "com.eligo.server.account.mapper.UserWechatAccountMapper" +
                            ".lockActiveByAppIdAndOpenidHash"
            private const val LOCK_USER =
                    "com.eligo.server.account.mapper.UserMapper.lockById"
        }

        @Volatile private var scenario: Scenario? = null
        private val reverseIdentityLockCount = AtomicInteger()

        fun configure(
                winnerReadyToCommit: CountDownLatch,
                releaseWinner: CountDownLatch,
                reverseIdentityLocked: CountDownLatch,
                releaseReversePath: CountDownLatch,
                ordinaryRetryRead: CountDownLatch,
                releaseOrdinaryPath: CountDownLatch,
                deactivationUserLocked: CountDownLatch) {
            reverseIdentityLockCount.set(0)
            scenario = Scenario(
                    winnerReadyToCommit,
                    releaseWinner,
                    reverseIdentityLocked,
                    releaseReversePath,
                    ordinaryRetryRead,
                    releaseOrdinaryPath,
                    deactivationUserLocked)
        }

        @Throws(InterruptedException::class)
        fun after(statementId: String, result: Any?) {
            val current = scenario ?: return
            val threadName = Thread.currentThread().name
            if ("首次登录胜者" == threadName && LOGIN_SESSION_INSERT == statementId) {
                current.winnerReadyToCommit.countDown()
                requireReleased(current.releaseWinner, "等待释放首次登录胜者超时")
                return
            }
            if ("首次登录竞争者" == threadName && FIND_IDENTITY == statementId
                    && result is List<*> && result.isNotEmpty()) {
                current.ordinaryRetryRead.countDown()
                requireReleased(current.releaseOrdinaryPath, "等待释放普通重试路径超时")
                return
            }
            if ("首次登录竞争者" == threadName && LOCK_IDENTITY == statementId) {
                reverseIdentityLockCount.incrementAndGet()
                current.reverseIdentityLocked.countDown()
                requireReleased(current.releaseReversePath, "等待释放反向身份锁路径超时")
                return
            }
            if ("注销申请" == threadName && LOCK_USER == statementId) {
                current.deactivationUserLocked.countDown()
            }
        }

        fun reverseIdentityLockCount(): Int {
            return reverseIdentityLockCount.get()
        }

        @Throws(InterruptedException::class)
        private fun requireReleased(latch: CountDownLatch, message: String) {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw AssertionError(message)
            }
        }

        private data class Scenario(
                val winnerReadyToCommit: CountDownLatch,
                val releaseWinner: CountDownLatch,
                val reverseIdentityLocked: CountDownLatch,
                val releaseReversePath: CountDownLatch,
                val ordinaryRetryRead: CountDownLatch,
                val releaseOrdinaryPath: CountDownLatch,
                val deactivationUserLocked: CountDownLatch
        )
    }

    @Intercepts(value = [
        Signature(
                type = Executor::class,
                method = "update",
                args = [MappedStatement::class, Any::class]),
        Signature(
                type = Executor::class,
                method = "query",
                args = [
                    MappedStatement::class,
                    Any::class,
                    RowBounds::class,
                    ResultHandler::class
                ])
    ])
    class AuthLockOrderInterceptor(private val probe: AuthLockOrderProbe) : Interceptor {

        @Throws(Throwable::class)
        override fun intercept(invocation: Invocation): Any {
            val result = invocation.proceed()
            val statement = invocation.args[0] as MappedStatement
            probe.after(statement.id, result)
            return result
        }
    }
}
