package com.eligo.server.account.service

import com.eligo.server.account.vo.PhoneBindingView
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.database.Stage2TestDatabaseCleaner
import com.eligo.server.integration.wechat.AuthorizedPhone
import com.eligo.server.integration.wechat.WechatPhoneClient
import com.eligo.server.integration.wechat.WechatRestClientFactory
import com.eligo.server.security.SensitiveDataCodec
import com.eligo.server.security.UserPrincipal
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.sql.PreparedStatement
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.any
import org.mockito.Mockito.`when`

@SpringBootTest(properties = [
    "eligo.security.jwt.signing-key-base64=IB8eHRwbGhkYFxYVFBMSERAPDg0MCwoJCAcGBQQDAgE=",
    "eligo.security.sensitive-data.encryption-key-base64=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
    "eligo.security.sensitive-data.lookup-key-base64=Hx4dHBsaGRgXFhUUExIREA8ODQwLCgkIBwYFBAMCAQA="
])
@ActiveProfiles("it")
@EnabledIfSystemProperty(named = "eligo.phone-binding.concurrency.tests", matches = "true")
class PhoneBindingConcurrencyMySqlIntegrationTests {

    companion object {
        private const val FIRST_USER_ID = 940001L
        private const val SECOND_USER_ID = 940002L
        private const val OLD_PHONE = "13800000001"
        private const val TARGET_PHONE = "13900000002"
    }

    @Autowired private lateinit var phoneBindingService: PhoneBindingService
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var dataSource: DataSource
    @Autowired private lateinit var codec: SensitiveDataCodec
    @Autowired private lateinit var lookupKey: PhoneLookupKey
    @MockitoBean private lateinit var wechatPhoneClient: WechatPhoneClient
    @MockitoBean private lateinit var wechatRestClientFactory: WechatRestClientFactory
    @MockitoBean private lateinit var redis: StringRedisTemplate

    private val authorizedPhones = ConcurrentHashMap<String, AuthorizedPhone>()

    @BeforeEach
    fun prepareUsers() {
        Stage2TestDatabaseCleaner.cleanStage2Database(jdbcTemplate) {
            jdbcTemplate.update(
                    "DELETE FROM account_security_events WHERE user_id IN (?, ?)",
                    FIRST_USER_ID,
                    SECOND_USER_ID)
            jdbcTemplate.update(
                    "DELETE FROM user_phone_bindings WHERE user_id IN (?, ?)",
                    FIRST_USER_ID,
                    SECOND_USER_ID)
            jdbcTemplate.update(
                    "DELETE FROM user_profiles WHERE user_id IN (?, ?)",
                    FIRST_USER_ID,
                    SECOND_USER_ID)
            jdbcTemplate.update(
                    "DELETE FROM users WHERE id IN (?, ?)", FIRST_USER_ID, SECOND_USER_ID)
        }
        insertUser(FIRST_USER_ID)
        insertUser(SECOND_USER_ID)
        insertProfile(FIRST_USER_ID)
        insertProfile(SECOND_USER_ID)
        authorizedPhones.clear()
        `when`(wechatPhoneClient.exchangePhoneCode(any<String>()))
            .thenAnswer { invocation -> authorizedPhones[invocation.getArgument(0)] }
    }

    @Test
    fun concurrentDifferentPhonesForSameUserAreSerializedByUserLock() {
        authorizedPhones["first"] = AuthorizedPhone("86", OLD_PHONE)
        authorizedPhones["second"] = AuthorizedPhone("86", TARGET_PHONE)
        val principal = UserPrincipal(FIRST_USER_ID, "session-a")

        val attempts = runConcurrently(
                { phoneBindingService.bindOrReplace(principal, "first") },
                { phoneBindingService.bindOrReplace(principal, "second") })

        assertThat(attempts).allSatisfy { attempt -> assertThat(attempt.error).isNull() }
        assertThat(countBindings(FIRST_USER_ID, 1)).isEqualTo(1)
        assertThat(countAllBindings(FIRST_USER_ID)).isEqualTo(2)
        assertThat(countEvents(FIRST_USER_ID)).isEqualTo(2)
    }

    @Test
    fun concurrentSamePhoneForDifferentUsersReturnsOneStableOwnershipConflict() {
        authorizedPhones["shared"] = AuthorizedPhone("86", TARGET_PHONE)

        val attempts = runConcurrently(
                {
                    phoneBindingService.bindOrReplace(
                            UserPrincipal(FIRST_USER_ID, "session-a"),
                            "shared")
                },
                {
                    phoneBindingService.bindOrReplace(
                            UserPrincipal(SECOND_USER_ID, "session-b"),
                            "shared")
                })

        assertThat(attempts.stream().filter { attempt -> attempt.error == null }.count())
            .isEqualTo(1)
        assertThat(attempts.stream().filter { attempt -> attempt.error != null }.count())
            .isEqualTo(1)
        val rejected = attempts.stream()
                .map(Attempt::error)
                .filter { error -> error != null }
                .findFirst()
                .orElseThrow()
        assertThat(rejected)
            .isInstanceOfSatisfying(BusinessException::class.java) { exception ->
                assertThat(exception.errorCode)
                    .isEqualTo(AccountUserFileErrorCode.PHONE_ALREADY_BOUND)
            }
        assertThat(countBindings(FIRST_USER_ID, 1) + countBindings(SECOND_USER_ID, 1))
            .isEqualTo(1)
    }

    @Test
    fun replacementUniqueRaceRollsBackOldBindingAndSecurityEvent() {
        authorizedPhones["old"] = AuthorizedPhone("86", OLD_PHONE)
        authorizedPhones["target"] = AuthorizedPhone("86", TARGET_PHONE)
        val principal = UserPrincipal(FIRST_USER_ID, "session-a")
        phoneBindingService.bindOrReplace(principal, "old")
        Stage2TestDatabaseCleaner.cleanStage2Database(
                jdbcTemplate) {
            jdbcTemplate.update(
                    "DELETE FROM account_security_events WHERE user_id = ?", FIRST_USER_ID)
        }

        dataSource.connection.use { competing ->
            competing.autoCommit = false
            insertPendingBinding(competing, SECOND_USER_ID, TARGET_PHONE)

            val executor = Executors.newSingleThreadExecutor()
            try {
                val replacement = executor.submit(
                        Callable {
                            attempt(Callable {
                                phoneBindingService.bindOrReplace(
                                        principal,
                                        "target")
                            })
                        })
                awaitLockWait()
                competing.commit()
                competing.close()

                val result = replacement.get(20, TimeUnit.SECONDS)
                assertThat(result.error)
                    .isInstanceOfSatisfying(BusinessException::class.java) { exception ->
                        assertThat(exception.errorCode)
                            .isEqualTo(
                                    AccountUserFileErrorCode.PHONE_ALREADY_BOUND)
                    }
            } finally {
                executor.shutdownNow()
                assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
            }
        }

        assertThat(countBindings(FIRST_USER_ID, 1)).isEqualTo(1)
        assertThat(countAllBindings(FIRST_USER_ID)).isEqualTo(1)
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT phone_last_four FROM user_phone_bindings WHERE user_id = ? AND status = 1",
                                String::class.java,
                                FIRST_USER_ID))
                .isEqualTo("0001")
        assertThat(countEvents(FIRST_USER_ID)).isZero()
    }

    private fun insertPendingBinding(connection: Connection, userId: Long, phone: String) {
        connection.prepareStatement("""
                        INSERT INTO user_phone_bindings (
                            id, user_id, country_code, phone_ciphertext, phone_lookup_hash,
                            phone_last_four, status, bound_at, created_at, updated_at
                        ) VALUES (?, ?, '86', ?, ?, ?, 1,
                            UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                        """).use { statement ->
            statement.setLong(1, 940010L)
            statement.setLong(2, userId)
            statement.setBytes(
                    3,
                    codec.encrypt(phone).toByteArray(StandardCharsets.UTF_8))
            statement.setBytes(4, codec.lookupHash(lookupKey.canonical("86", phone)))
            statement.setString(5, phone.substring(phone.length - 4))
            statement.executeUpdate()
        }
    }

    private fun awaitLockWait() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            val waiting = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM performance_schema.data_lock_waits",
                    Int::class.java)
            if (waiting != null && waiting > 0) {
                return
            }
            Thread.sleep(20)
        }
        throw AssertionError("等待手机号唯一索引锁竞争超时")
    }

    private fun runConcurrently(
            first: Callable<PhoneBindingView>,
            second: Callable<PhoneBindingView>
    ): List<Attempt> {
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        try {
            val firstFuture = executor.submit(wrap(first, ready, start))
            val secondFuture = executor.submit(wrap(second, ready, start))
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()
            start.countDown()
            return listOf(
                    firstFuture.get(20, TimeUnit.SECONDS),
                    secondFuture.get(20, TimeUnit.SECONDS))
        } finally {
            executor.shutdownNow()
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
        }
    }

    private fun wrap(
            operation: Callable<PhoneBindingView>,
            ready: CountDownLatch,
            start: CountDownLatch
    ): Callable<Attempt> {
        return Callable {
            ready.countDown()
            start.await(5, TimeUnit.SECONDS)
            attempt(operation)
        }
    }

    private fun attempt(operation: Callable<PhoneBindingView>): Attempt {
        return try {
            Attempt(operation.call(), null)
        } catch (throwable: Throwable) {
            Attempt(null, throwable)
        }
    }

    private fun insertUser(userId: Long) {
        jdbcTemplate.update("""
                INSERT INTO users (id, status, version, created_at, updated_at)
                VALUES (?, 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, userId)
    }

    private fun insertProfile(userId: Long) {
        jdbcTemplate.update("""
                INSERT INTO user_profiles (user_id, version, created_at, updated_at)
                VALUES (?, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                """, userId)
    }

    private fun countBindings(userId: Long, status: Int): Int {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_phone_bindings WHERE user_id = ? AND status = ?",
                Int::class.java,
                userId,
                status) ?: 0
    }

    private fun countAllBindings(userId: Long): Int {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_phone_bindings WHERE user_id = ?",
                Int::class.java,
                userId) ?: 0
    }

    private fun countEvents(userId: Long): Int {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_security_events WHERE user_id = ? AND event_type LIKE 'PHONE_%'",
                Int::class.java,
                userId) ?: 0
    }

    private data class Attempt(val response: PhoneBindingView?, val error: Throwable?)
}
