package com.eligo.server.participation

import java.util.function.Function
import java.util.function.Consumer

import com.eligo.server.account.dto.DeactivationRequest
import com.eligo.server.account.entity.UserDataRequestEntity
import com.eligo.server.account.service.AccountDataService
import com.eligo.server.account.service.DeactivationDataRequestProcessor
import com.eligo.server.activity.service.ActivityCommandService
import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.database.Stage2TestDatabaseCleaner
import com.eligo.server.integration.wechat.WechatRestClientFactory
import com.eligo.server.integration.wechat.WechatLoginClient
import com.eligo.server.integration.wechat.WechatSession
import com.eligo.server.participation.error.ParticipationErrorCode
import com.eligo.server.participation.mapper.ActivityParticipationMapper
import com.eligo.server.participation.service.ParticipationAccountDeactivationBlocker
import com.eligo.server.participation.service.ParticipationCommandService
import com.eligo.server.participation.service.ParticipationReadService
import com.eligo.server.participation.vo.ActivityParticipantSummaryView
import com.eligo.server.participation.vo.ActivityParticipationView
import com.eligo.server.participation.vo.MyParticipationView
import com.eligo.server.security.UserPrincipal
import com.eligo.server.security.SensitiveDataCodec
import java.math.BigDecimal
import java.sql.Connection
import java.sql.PreparedStatement
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.util.Arrays
import java.util.Optional
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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

@SpringBootTest(properties = [
    "eligo.security.jwt.signing-key-base64=IB8eHRwbGhkYFxYVFBMSERAPDg0MCwoJCAcGBQQDAgE=",
    "eligo.security.sensitive-data.encryption-key-base64=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
    "eligo.security.sensitive-data.lookup-key-base64=Hx4dHBsaGRgXFhUUExIREA8ODQwLCgkIBwYFBAMCAQA=",
    "eligo.integration.wechat.app-id=m3-concurrency-app"
])
@ActiveProfiles("it")
@EnabledIfSystemProperty(named = "eligo.database.tests", matches = "true")
class ParticipationDatabaseIntegrationTests {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var dataSource: DataSource

    @Autowired
    lateinit var commandService: ParticipationCommandService

    @Autowired
    lateinit var readService: ParticipationReadService

    @Autowired
    lateinit var activityCommandService: ActivityCommandService

    @Autowired
    lateinit var accountDataService: AccountDataService

    @Autowired
    lateinit var deactivationProcessor: DeactivationDataRequestProcessor

    @Autowired
    lateinit var deactivationBlocker: ParticipationAccountDeactivationBlocker

    @Autowired
    lateinit var participations: ActivityParticipationMapper

    @MockitoBean
    lateinit var redis: StringRedisTemplate

    @MockitoBean
    lateinit var wechatRestClientFactory: WechatRestClientFactory

    @MockitoBean
    lateinit var wechatLoginClient: WechatLoginClient

    @MockitoBean
    lateinit var sensitiveDataCodec: SensitiveDataCodec

    @BeforeEach
    fun prepareDatabase() {
        Stage2TestDatabaseCleaner.cleanAllStage2Tables(jdbcTemplate)
        insertUser(OWNER_ID, "发起者")
        insertUser(FIRST_USER_ID, "参与者甲")
        insertUser(SECOND_USER_ID, "参与者乙")
        insertFile()
        insertPublishedActivity(20)
        whenever(wechatLoginClient.exchangeCode("m3-deactivation-code"))
            .thenReturn(WechatSession("m3-openid", null, "session-key"))
        whenever(sensitiveDataCodec.lookupHash("wechat-openid:m3-openid"))
            .thenReturn(openidHash())
    }

    @Test
    fun joinCancelRejoinRemovalBlockAndActivityCancelStayAtomic() {
        val participant = principal(FIRST_USER_ID)
        val owner = principal(OWNER_ID)

        val joined = commandService.join(participant, ACTIVITY_ID)
        assertThat(joined.status).isEqualTo("ACTIVE")
        assertThat(deactivationBlockingReason(FIRST_USER_ID))
            .contains("存在有效活动报名")
        assertInvariant(1)

        val participants = readService.listParticipants(participant, ACTIVITY_ID, null, 20)
        assertThat(participants.items).singleElement().satisfies(Consumer {  item ->
            assertThat(item.userId).isEqualTo(FIRST_USER_ID.toString())
            assertThat(item.nickname).isEqualTo("参与者甲")
         })

        val cancelled = commandService.cancel(participant, ACTIVITY_ID)
        assertThat(cancelled.status).isEqualTo("CANCELLED")
        assertThat(deactivationBlockingReason(FIRST_USER_ID)).isEmpty()
        assertInvariant(0)

        jdbcTemplate.update(
            """
            UPDATE activity_participations
            SET joined_at=DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 DAY)
            WHERE id=?
            """, joined.participationId!!.toLong())
        val previousJoinedAt = jdbcTemplate.queryForObject(
            "SELECT joined_at FROM activity_participations WHERE id=?",
            java.sql.Timestamp::class.java,
            joined.participationId!!.toLong())!!.toInstant()

        val rejoined = commandService.join(participant, ACTIVITY_ID)
        assertThat(rejoined.participationId).isEqualTo(joined.participationId)
        assertThat(rejoined.joinedAt).isAfter(previousJoinedAt)
        assertThat(rejoined.cancelledAt).isNull()
        assertThat(rejoined.terminatedAt).isNull()
        assertThat(rejoined.terminationReason).isNull()
        assertThat(deactivationBlockingReason(FIRST_USER_ID))
            .contains("存在有效活动报名")
        assertInvariant(1)

        val removed = commandService.remove(owner, ACTIVITY_ID, FIRST_USER_ID)
        assertThat(removed.status).isEqualTo("TERMINATED")
        assertThat(removed.terminationReason).isEqualTo("REMOVED_BY_OWNER")
        assertThat(deactivationBlockingReason(FIRST_USER_ID)).isEmpty()
        assertInvariant(0)

        assertThatThrownBy { commandService.join(participant, ACTIVITY_ID) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  exception -> (exception as BusinessException).errorCode  })
            .isEqualTo(ParticipationErrorCode.REMOVED_PARTICIPANT_CANNOT_REJOIN)
        assertInvariant(0)

        val secondParticipant = principal(SECOND_USER_ID)
        commandService.join(secondParticipant, ACTIVITY_ID)
        assertInvariant(1)
        activityCommandService.cancel(owner, ACTIVITY_ID)

        val removedState = jdbcTemplate.queryForMap(
            """
            SELECT a.status AS activity_status,
                   a.participant_count,
                   ap.status AS participation_status,
                   ap.termination_reason
            FROM activities a
            JOIN activity_participations ap ON ap.activity_id=a.id
            WHERE a.id=? AND ap.user_id=?
            """, ACTIVITY_ID, FIRST_USER_ID)
        assertThat(removedState)
            .containsEntry("activity_status", 3)
            .containsEntry("participant_count", 0)
            .containsEntry("participation_status", 3)
            .containsEntry("termination_reason", 2)
        val cancelledActivityState = jdbcTemplate.queryForMap(
            """
            SELECT ap.status AS participation_status,
                   ap.termination_reason
            FROM activity_participations ap
            WHERE ap.activity_id=? AND ap.user_id=?
            """, ACTIVITY_ID, SECOND_USER_ID)
        assertThat(cancelledActivityState)
            .containsEntry("participation_status", 3)
            .containsEntry("termination_reason", 1)
        assertInvariant(0)

        val mine = readService.listMyParticipations(
            secondParticipant, null, 20, "TERMINATED", "测试")
        assertThat(mine.items).singleElement().satisfies(Consumer {  item ->
            assertThat(item.userId).isEqualTo(SECOND_USER_ID.toString())
            assertThat(item.terminationReason).isEqualTo("ACTIVITY_CANCELLED")
            assertThat(item.activity!!.status).isEqualTo("CANCELLED")
            assertThat(item.activity!!.latitude)
                .isEqualByComparingTo(BigDecimal("22.5430960"))
            assertThat(item.activity!!.longitude)
                .isEqualByComparingTo(BigDecimal("114.0578650"))
         })
    }

    @Test
    fun normallyEndedActivityKeepsParticipationHistoryWithoutBlockingDeactivation() {
        commandService.join(principal(FIRST_USER_ID), ACTIVITY_ID)

        jdbcTemplate.update(
            "UPDATE activities SET status=4 WHERE id=?",
            ACTIVITY_ID)

        assertThat(jdbcTemplate.queryForObject(
            """
            SELECT status
            FROM activity_participations
            WHERE activity_id=? AND user_id=?
            """, Int::class.java, ACTIVITY_ID, FIRST_USER_ID)).isEqualTo(1)
        assertThat(deactivationBlockingReason(FIRST_USER_ID)).isEmpty()
    }

    @Test
    fun terminatedParticipationCannotBeReactivatedByMapper() {
        val activityCancelledId = 9983001L
        val removedByOwnerId = 9983002L
        insertTerminatedParticipation(activityCancelledId, FIRST_USER_ID, 1)
        insertTerminatedParticipation(removedByOwnerId, SECOND_USER_ID, 2)
        val now = LocalDateTime.now(Clock.systemUTC())

        assertThat(participations.reactivate(activityCancelledId, now)).isZero()
        assertThat(participations.reactivate(removedByOwnerId, now)).isZero()
        assertThat(jdbcTemplate.queryForList(
            """
            SELECT status
            FROM activity_participations
            WHERE id IN (?, ?)
            ORDER BY id
            """, Int::class.java, activityCancelledId, removedByOwnerId))
            .containsExactly(3, 3)
    }

    @Test
    fun effectivelyEndedPublishedActivityDoesNotBlockBeforeLifecycleJob() {
        commandService.join(principal(FIRST_USER_ID), ACTIVITY_ID)

        jdbcTemplate.update(
            """
            UPDATE activities
            SET registration_starts_at=DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 4 HOUR),
                registration_ends_at=DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 3 HOUR),
                starts_at=DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 2 HOUR),
                ends_at=UTC_TIMESTAMP(3)
            WHERE id=?
            """, ACTIVITY_ID)

        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM activities WHERE id=?",
            Int::class.java,
            ACTIVITY_ID)).isEqualTo(2)
        assertThat(deactivationBlockingReason(FIRST_USER_ID)).isEmpty()
    }

    @Test
    fun realDeactivationAggregateSerializesAndRejectsConcurrentJoin() {
        assertThat(deactivationBlockingReason(FIRST_USER_ID)).isEmpty()
        insertWechatIdentity(FIRST_USER_ID)
        val executor = Executors.newFixedThreadPool(2)
        dataSource.getConnection().use { identityBlocker ->
            identityBlocker.autoCommit = false
            lockWechatIdentity(identityBlocker, FIRST_USER_ID)
            var released = false
            try {
                val deactivation = executor.submit(Callable {
                    try {
                        accountDataService.requestDeactivationOutcome(
                            principal(FIRST_USER_ID),
                            DeactivationRequest("m3-deactivation-code", true))
                        0
                    } catch (exception: BusinessException) {
                        exception.errorCode.code
                    }
                })
                awaitDatabaseLockWait(
                    "user_wechat_accounts",
                    "uk_wechat_active_user_app",
                    "$FIRST_USER_ID,%")

                val join = executor.submit(Callable {
                    try {
                        commandService.join(principal(FIRST_USER_ID), ACTIVITY_ID)
                        0
                    } catch (exception: BusinessException) {
                        exception.errorCode.code
                    }
                })
                awaitDatabaseLockWait("users", "PRIMARY", FIRST_USER_ID.toString())
                identityBlocker.commit()
                released = true

                assertThat(deactivation.get(20, TimeUnit.SECONDS)).isZero()
                assertThat(join.get(20, TimeUnit.SECONDS)).isEqualTo(11501)
            } finally {
                if (!released) {
                    identityBlocker.rollback()
                }
            }
        }
        executor.shutdownNow()
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()

        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM users WHERE id=?",
            Int::class.java,
            FIRST_USER_ID)).isEqualTo(2)
        assertThat(jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM user_data_requests
            WHERE user_id=? AND request_type=? AND status=?
            """, Int::class.java, FIRST_USER_ID,
            UserDataRequestEntity.TYPE_DEACTIVATION,
            UserDataRequestEntity.STATUS_WAITING_EXECUTION)).isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM activity_participations
            WHERE activity_id=? AND user_id=?
            """, Int::class.java, ACTIVITY_ID, FIRST_USER_ID)).isZero()
        assertInvariant(0)
    }

    @Test
    fun realDeactivationAggregateSerializesAndRejectsConcurrentPublication() {
        jdbcTemplate.update(
            """
            UPDATE activities
            SET status=1, published_at=NULL, version=version+1,
                updated_at=UTC_TIMESTAMP(3)
            WHERE id=?
            """, ACTIVITY_ID)
        insertWechatIdentity(OWNER_ID)
        val executor = Executors.newFixedThreadPool(2)
        dataSource.getConnection().use { identityBlocker ->
            identityBlocker.autoCommit = false
            lockWechatIdentity(identityBlocker, OWNER_ID)
            var released = false
            try {
                val deactivation = executor.submit(Callable {
                    try {
                        accountDataService.requestDeactivationOutcome(
                            principal(OWNER_ID),
                            DeactivationRequest("m3-deactivation-code", true))
                        0
                    } catch (exception: BusinessException) {
                        exception.errorCode.code
                    }
                })
                awaitDatabaseLockWait(
                    "user_wechat_accounts",
                    "uk_wechat_active_user_app",
                    "$OWNER_ID,%")

                val publication = executor.submit(Callable {
                    try {
                        activityCommandService.publish(principal(OWNER_ID), ACTIVITY_ID)
                        0
                    } catch (exception: BusinessException) {
                        exception.errorCode.code
                    }
                })
                awaitDatabaseLockWait("users", "PRIMARY", OWNER_ID.toString())
                identityBlocker.commit()
                released = true

                assertThat(deactivation.get(20, TimeUnit.SECONDS)).isZero()
                assertThat(publication.get(20, TimeUnit.SECONDS)).isEqualTo(11501)
            } finally {
                if (!released) {
                    identityBlocker.rollback()
                }
            }
        }
        executor.shutdownNow()
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()

        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM users WHERE id=?",
            Int::class.java,
            OWNER_ID)).isEqualTo(2)
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM activities WHERE id=?",
            Int::class.java,
            ACTIVITY_ID)).isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM activity_lifecycle_events
            WHERE activity_id=? AND to_status=2
            """, Int::class.java, ACTIVITY_ID)).isZero()
    }

    @Test
    fun repeatedRequestAndDueProcessorUseOneLockOrderWithoutDeadlock() {
        insertWechatIdentity(FIRST_USER_ID)
        assertThat(accountDataService.requestDeactivationOutcome(
            principal(FIRST_USER_ID),
            DeactivationRequest("m3-deactivation-code", true)).created).isTrue()
        val requestId = jdbcTemplate.queryForObject(
            """
            SELECT id FROM user_data_requests
            WHERE user_id=? AND request_type=?
            """, Long::class.java, FIRST_USER_ID,
            UserDataRequestEntity.TYPE_DEACTIVATION)!!
        jdbcTemplate.update(
            """
            UPDATE user_data_requests
            SET requested_at=DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 8 DAY),
                execute_after=DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 DAY),
                updated_at=UTC_TIMESTAMP(3)
            WHERE id=?
            """, requestId)
        val processingTime = jdbcTemplate.queryForObject(
            "SELECT UTC_TIMESTAMP(3)", LocalDateTime::class.java)!!

        val executor = Executors.newFixedThreadPool(2)
        dataSource.getConnection().use { identityBlocker ->
            identityBlocker.autoCommit = false
            lockWechatIdentity(identityBlocker, FIRST_USER_ID)
            var released = false
            try {
                val repeatedRequest = executor.submit(Callable {
                    try {
                        if (accountDataService.requestDeactivationOutcome(
                                principal(FIRST_USER_ID),
                                DeactivationRequest("m3-deactivation-code", true)).created) -1 else 0
                    } catch (exception: RuntimeException) {
                        1
                    }
                })
                awaitDatabaseLockWait(
                    "user_wechat_accounts",
                    "uk_wechat_active_user_app",
                    "$FIRST_USER_ID,%")

                val processor = executor.submit(Callable {
                    try {
                        deactivationProcessor.process(requestId, processingTime)
                        0
                    } catch (exception: RuntimeException) {
                        1
                    }
                })
                awaitDatabaseLockWait("users", "PRIMARY", FIRST_USER_ID.toString())
                identityBlocker.commit()
                released = true

                assertThat(repeatedRequest.get(20, TimeUnit.SECONDS)).isZero()
                assertThat(processor.get(20, TimeUnit.SECONDS)).isZero()
            } finally {
                if (!released) {
                    identityBlocker.rollback()
                }
            }
        }
        executor.shutdownNow()
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()

        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM users WHERE id=?",
            Int::class.java,
            FIRST_USER_ID)).isEqualTo(3)
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM user_data_requests WHERE id=?",
            Int::class.java,
            requestId)).isEqualTo(UserDataRequestEntity.STATUS_COMPLETED)
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM user_wechat_accounts WHERE user_id=?",
            Int::class.java,
            FIRST_USER_ID)).isEqualTo(2)
        assertThat(jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM user_data_requests
            WHERE user_id=? AND request_type=?
            """, Int::class.java, FIRST_USER_ID,
            UserDataRequestEntity.TYPE_DEACTIVATION)).isEqualTo(1)
    }

    @Test
    fun cancellationCountConflictRollsBackM2AndM3ChangesTogether() {
        commandService.join(principal(FIRST_USER_ID), ACTIVITY_ID)
        jdbcTemplate.update(
            """
            UPDATE activities
            SET participant_count=2, version=version+1,
                updated_at=UTC_TIMESTAMP(3)
            WHERE id=?
            """, ACTIVITY_ID)

        assertThatThrownBy {
            activityCommandService.cancel(principal(OWNER_ID), ACTIVITY_ID)
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  exception ->
                (exception as BusinessException).errorCode.code
             })
            .isEqualTo(CommonErrorCode.CONFLICT.code)

        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM activities WHERE id=?",
            Int::class.java,
            ACTIVITY_ID)).isEqualTo(2)
        assertThat(jdbcTemplate.queryForObject(
            "SELECT participant_count FROM activities WHERE id=?",
            Int::class.java,
            ACTIVITY_ID)).isEqualTo(2)
        assertThat(jdbcTemplate.queryForObject(
            """
            SELECT status FROM activity_participations
            WHERE activity_id=? AND user_id=?
            """, Int::class.java, ACTIVITY_ID, FIRST_USER_ID)).isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM activity_lifecycle_events
            WHERE activity_id=? AND to_status=3
            """, Int::class.java, ACTIVITY_ID)).isZero()
    }

    @Test
    fun concurrentLastSlotAllowsExactlyOneParticipant() {
        jdbcTemplate.update(
            "UPDATE activities SET capacity=1 WHERE id=?", ACTIVITY_ID)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit(joinTask(FIRST_USER_ID, ready, start))
            val second = executor.submit(joinTask(SECOND_USER_ID, ready, start))
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()
            start.countDown()

            assertThat(listOf(
                first.get(20, TimeUnit.SECONDS),
                second.get(20, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(0, 11703)
        } finally {
            executor.shutdownNow()
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
        }
        assertInvariant(1)
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM activity_participations WHERE activity_id=?",
            Int::class.java,
            ACTIVITY_ID)).isEqualTo(1)
    }

    @Test
    fun concurrentJoinBySameUserCreatesOneParticipationAndCountsOnce() {
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit(joinTask(FIRST_USER_ID, ready, start))
            val second = executor.submit(joinTask(FIRST_USER_ID, ready, start))
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()
            start.countDown()

            assertThat(listOf(
                first.get(20, TimeUnit.SECONDS),
                second.get(20, TimeUnit.SECONDS)))
                .containsExactly(0, 0)
        } finally {
            executor.shutdownNow()
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
        }
        assertInvariant(1)
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM activity_participations WHERE activity_id=?",
            Int::class.java,
            ACTIVITY_ID)).isEqualTo(1)
    }

    @Test
    fun concurrentJoinAndActivityCancellationLeaveNoActiveParticipation() {
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val join = executor.submit(joinTask(FIRST_USER_ID, ready, start))
            val cancellation = executor.submit(Callable {
                ready.countDown()
                start.await(5, TimeUnit.SECONDS)
                try {
                    activityCommandService.cancel(principal(OWNER_ID), ACTIVITY_ID)
                    0
                } catch (exception: BusinessException) {
                    exception.errorCode.code
                }
            })
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()
            start.countDown()

            assertThat(cancellation.get(20, TimeUnit.SECONDS)).isZero()
            assertThat(join.get(20, TimeUnit.SECONDS)).isIn(0, 11706)
        } finally {
            executor.shutdownNow()
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
        }

        assertThat(jdbcTemplate.queryForMap(
            "SELECT status, participant_count FROM activities WHERE id=?",
            ACTIVITY_ID))
            .containsEntry("status", 3)
            .containsEntry("participant_count", 0)
        assertInvariant(0)

        val participationCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM activity_participations WHERE activity_id=?",
            Int::class.java,
            ACTIVITY_ID)
        assertThat(participationCount).isIn(0, 1)
        if (participationCount == 1) {
            assertThat(jdbcTemplate.queryForMap(
                """
                SELECT status, termination_reason
                FROM activity_participations
                WHERE activity_id=? AND user_id=?
                """, ACTIVITY_ID, FIRST_USER_ID))
                .containsEntry("status", 3)
                .containsEntry("termination_reason", 1)
        }
    }

    @Test
    fun failedParticipantCountUpdateRollsBackParticipationTransition() {
        val participant = principal(FIRST_USER_ID)
        commandService.join(participant, ACTIVITY_ID)
        jdbcTemplate.update(
            "UPDATE activities SET participant_count=0 WHERE id=?",
            ACTIVITY_ID)

        assertThatThrownBy { commandService.cancel(participant, ACTIVITY_ID) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  exception -> (exception as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.CONFLICT)

        assertThat(jdbcTemplate.queryForMap(
            """
            SELECT status, cancelled_at
            FROM activity_participations
            WHERE activity_id=? AND user_id=?
            """, ACTIVITY_ID, FIRST_USER_ID))
            .containsEntry("status", 1)
            .containsEntry("cancelled_at", null)

        jdbcTemplate.update(
            "UPDATE activities SET participant_count=1 WHERE id=?",
            ACTIVITY_ID)
        assertInvariant(1)
    }

    @Test
    fun concurrentSelfCancellationAndActivityCancellationStayAtomic() {
        commandService.join(principal(FIRST_USER_ID), ACTIVITY_ID)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val selfCancellation = executor.submit(Callable {
                ready.countDown()
                start.await(5, TimeUnit.SECONDS)
                try {
                    commandService.cancel(principal(FIRST_USER_ID), ACTIVITY_ID)
                    0
                } catch (exception: BusinessException) {
                    exception.errorCode.code
                }
            })
            val activityCancellation = executor.submit(
                activityCancellationTask(ready, start))
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()
            start.countDown()

            assertThat(selfCancellation.get(20, TimeUnit.SECONDS)).isZero()
            assertThat(activityCancellation.get(20, TimeUnit.SECONDS)).isZero()
        } finally {
            executor.shutdownNow()
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
        }

        assertInvariant(0)
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM activities WHERE id=?",
            Int::class.java,
            ACTIVITY_ID)).isEqualTo(3)
        assertThat(jdbcTemplate.queryForObject(
            """
            SELECT status FROM activity_participations
            WHERE activity_id=? AND user_id=?
            """, Int::class.java, ACTIVITY_ID, FIRST_USER_ID)).isIn(2, 3)
    }

    @Test
    fun concurrentOwnerRemovalAndActivityCancellationStayAtomic() {
        commandService.join(principal(FIRST_USER_ID), ACTIVITY_ID)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val removal = executor.submit(Callable {
                ready.countDown()
                start.await(5, TimeUnit.SECONDS)
                try {
                    commandService.remove(principal(OWNER_ID), ACTIVITY_ID, FIRST_USER_ID)
                    0
                } catch (exception: BusinessException) {
                    exception.errorCode.code
                }
            })
            val activityCancellation = executor.submit(
                activityCancellationTask(ready, start))
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()
            start.countDown()

            assertThat(removal.get(20, TimeUnit.SECONDS)).isIn(0, 11708)
            assertThat(activityCancellation.get(20, TimeUnit.SECONDS)).isZero()
        } finally {
            executor.shutdownNow()
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
        }

        assertInvariant(0)
        assertThat(jdbcTemplate.queryForMap(
            """
            SELECT status, termination_reason
            FROM activity_participations
            WHERE activity_id=? AND user_id=?
            """, ACTIVITY_ID, FIRST_USER_ID))
            .containsEntry("status", 3)
            .satisfies(Consumer {  state -> assertThat(state["termination_reason"]).isIn(1, 2)  })
    }

    private fun joinTask(
        userId: Long,
        ready: CountDownLatch,
        start: CountDownLatch
    ): Callable<Int> = Callable {
        ready.countDown()
        start.await(5, TimeUnit.SECONDS)
        try {
            commandService.join(principal(userId), ACTIVITY_ID)
            0
        } catch (exception: BusinessException) {
            exception.errorCode.code
        }
    }

    private fun awaitDatabaseLockWait(
        tableName: String,
        indexName: String,
        lockDataPattern: String
    ) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            val waiting = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM performance_schema.data_lock_waits waits
                JOIN performance_schema.data_locks requested
                  ON requested.engine=waits.engine
                 AND requested.engine_lock_id=waits.requesting_engine_lock_id
                WHERE requested.object_schema=DATABASE()
                  AND requested.object_name=?
                  AND requested.index_name=?
                  AND requested.lock_type='RECORD'
                  AND requested.lock_status='WAITING'
                  AND requested.lock_data LIKE ?
                """, Int::class.java, tableName, indexName, lockDataPattern)
            if (waiting != null && waiting >= 1) {
                return
            }
            Thread.sleep(20)
        }
        throw AssertionError(
            "等待目标数据库行锁超时，表=$tableName"
                + "，索引=$indexName"
                + "，锁数据=$lockDataPattern")
    }

    private fun activityCancellationTask(
        ready: CountDownLatch,
        start: CountDownLatch
    ): Callable<Int> = Callable {
        ready.countDown()
        start.await(5, TimeUnit.SECONDS)
        try {
            activityCommandService.cancel(principal(OWNER_ID), ACTIVITY_ID)
            0
        } catch (exception: BusinessException) {
            exception.errorCode.code
        }
    }

    private fun assertInvariant(expectedActive: Int) {
        val count = jdbcTemplate.queryForObject(
            "SELECT participant_count FROM activities WHERE id=?",
            Int::class.java,
            ACTIVITY_ID)
        val active = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM activity_participations
            WHERE activity_id=? AND status=1
            """, Int::class.java, ACTIVITY_ID)
        assertThat(count).isEqualTo(expectedActive).isEqualTo(active)
    }

    private fun deactivationBlockingReason(userId: Long): Optional<String> {
        val now = jdbcTemplate.queryForObject(
            "SELECT UTC_TIMESTAMP(3)", LocalDateTime::class.java)!!
        return deactivationBlocker.blockingReason(userId, now)
    }

    private fun insertUser(userId: Long, nickname: String) {
        jdbcTemplate.update(
            """
            INSERT INTO users (id, status, version, created_at, updated_at)
            VALUES (?, 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, userId)
        jdbcTemplate.update(
            """
            INSERT INTO user_profiles (
                user_id, nickname, province_code, province_name,
                city_code, city_name, district_code, district_name,
                completed_at, version, created_at, updated_at
            ) VALUES (?, ?, '44', '广东省', '4403', '深圳市',
                '440305', '南山区', UTC_TIMESTAMP(3), 0,
                UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, userId, nickname)
    }

    private fun insertFile() {
        jdbcTemplate.update(
            """
            INSERT INTO file_objects (
                id, uploader_type, uploader_id, purpose, storage_provider,
                bucket_name, object_key, original_filename, content_type,
                file_extension, size_bytes, sha256, access_level, scan_status,
                lifecycle_status, version, created_at, updated_at
            ) VALUES (?, 1, ?, 'ACTIVITY', 'local', 'eligo', ?,
                'cover.jpg', 'image/jpeg', 'jpg', 1,
                UNHEX(SHA2('m3-cover', 256)), 1, 2, 2, 0,
                UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, COVER_FILE_ID, OWNER_ID, "activity/$COVER_FILE_ID")
    }

    private fun insertWechatIdentity(userId: Long) {
        jdbcTemplate.update(
            """
            INSERT INTO user_wechat_accounts (
                id, user_id, app_id, openid_ciphertext,
                openid_lookup_hash, status, bound_at,
                created_at, updated_at
            ) VALUES (?, ?, 'm3-concurrency-app', X'01', ?, 1,
                UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, 9984001L, userId, openidHash())
    }

    private fun lockWechatIdentity(connection: Connection, userId: Long) {
        connection.prepareStatement(
            """
            SELECT id FROM user_wechat_accounts
            WHERE user_id=? AND status=1 FOR UPDATE
            """).use { statement ->
            statement.setLong(1, userId)
            statement.executeQuery().use { result ->
                assertThat(result.next()).isTrue()
            }
        }
    }

    private fun openidHash(): ByteArray {
        val hash = ByteArray(32)
        Arrays.fill(hash, 7.toByte())
        return hash
    }

    private fun insertPublishedActivity(capacity: Int) {
        jdbcTemplate.update(
            """
            INSERT INTO activities (
                id, owner_user_id, operator_user_id, status, title,
                category_code, description, cover_file_id,
                registration_starts_at, registration_ends_at,
                starts_at, ends_at, region_code, address_detail,
                latitude, longitude,
                capacity, participant_count, published_at, version,
                created_at, updated_at
            ) VALUES (?, ?, ?, 2, '测试报名活动', 'HIKING', '活动介绍', ?,
                DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 HOUR),
                DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 1 HOUR),
                DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 2 HOUR),
                DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 3 HOUR),
                '440305', '深圳市南山区测试地址',
                22.5430960, 114.0578650, ?, 0,
                UTC_TIMESTAMP(3), 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """, ACTIVITY_ID, OWNER_ID, OWNER_ID, COVER_FILE_ID, capacity)
    }

    private fun insertTerminatedParticipation(id: Long, userId: Long, reason: Int) {
        jdbcTemplate.update(
            """
            INSERT INTO activity_participations (
                id, activity_id, user_id, status, joined_at,
                terminated_at, termination_reason, version,
                created_at, updated_at
            ) VALUES (?, ?, ?, 3,
                DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 2 HOUR),
                DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 HOUR), ?, 0,
                DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 2 HOUR),
                DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 HOUR))
            """, id, ACTIVITY_ID, userId, reason)
    }

    private fun principal(userId: Long): UserPrincipal {
        return UserPrincipal(userId, "session-$userId")
    }

    companion object {
        private const val OWNER_ID = 9980001L
        private const val FIRST_USER_ID = 9980002L
        private const val SECOND_USER_ID = 9980003L
        private const val ACTIVITY_ID = 9981001L
        private const val COVER_FILE_ID = 9982001L
    }
}
