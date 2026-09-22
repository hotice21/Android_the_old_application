package com.eligo.server.account.service

import com.eligo.server.account.entity.UserDataRequestEntity
import com.eligo.server.account.entity.UserDataRequestEventEntity
import com.eligo.server.account.entity.UserEntity
import com.eligo.server.account.mapper.UserDataRequestEventMapper
import com.eligo.server.account.mapper.UserDataRequestMapper
import com.eligo.server.account.mapper.UserLoginSessionMapper
import com.eligo.server.account.mapper.UserMapper
import com.eligo.server.account.mapper.UserPhoneBindingMapper
import com.eligo.server.account.mapper.UserWechatAccountMapper
import com.eligo.server.profile.mapper.UserInterestTagMapper
import com.eligo.server.profile.mapper.UserProfileMapper
import com.eligo.server.favorite.service.ActivityEngagementAccountLifecycleService
import com.eligo.server.post.service.PostAccountLifecycleService
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.mockito.kotlin.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class AccountDataJobTests {
    companion object {
        private val NOW: LocalDateTime = LocalDateTime.parse("2026-07-30T08:00:00")
    }

    private val requests = mock(UserDataRequestMapper::class.java)
    private val events = mock(UserDataRequestEventMapper::class.java)
    private val users = mock(UserMapper::class.java)
    private val sessions = mock(UserLoginSessionMapper::class.java)
    private val wechat = mock(UserWechatAccountMapper::class.java)
    private val phones = mock(UserPhoneBindingMapper::class.java)
    private val profiles = mock(UserProfileMapper::class.java)
    private val interests = mock(UserInterestTagMapper::class.java)
    private val postLifecycle = mock(PostAccountLifecycleService::class.java)
    private val engagementLifecycle = mock(ActivityEngagementAccountLifecycleService::class.java)
    private val exports = mock(PersonalDataExportService::class.java)
    private lateinit var job: AccountDataJob

    @BeforeEach
    fun setUp() {
        job = AccountDataJob(requests, events, users, sessions, wechat, phones,
                profiles, interests, postLifecycle, engagementLifecycle, exports,
                Clock.fixed(Instant.parse("2026-07-30T08:00:00Z"), ZoneOffset.UTC))
    }

    @Test
    fun dueRequestRevokesSessionsUnlinksIdentitiesAndAnonymizesProfile() {
        `when`(requests.findDueDeactivationIds(NOW, 20)).thenReturn(listOf(501L))
        val request = request(UserDataRequestEntity.STATUS_WAITING_EXECUTION, 0)
        `when`(requests.lockById(501L)).thenReturn(Optional.of(request))
        `when`(requests.markProcessing(501L, UserDataRequestEntity.STATUS_WAITING_EXECUTION, 0, NOW))
            .thenReturn(1)
        `when`(users.lockById(202L)).thenReturn(Optional.of(user(2, 0)))
        `when`(users.markDeactivated(202L, 0, NOW)).thenReturn(1)
        `when`(requests.markCompleted(501L, 1, NOW)).thenReturn(1)

        job.executePendingDeactivations()

        verify(sessions).revokeAllActiveByUserId(202L, "ACCOUNT_DEACTIVATED", NOW)
        verify(postLifecycle).applyDeactivation(202L, NOW)
        verify(engagementLifecycle).applyDeactivation(202L, NOW)
        verify(wechat).unbindAllActiveByUserId(202L, NOW)
        verify(phones).anonymizeAndUnbindAllActiveByUserId(202L, NOW, "ACCOUNT_DEACTIVATED")
        verify(interests).deleteByUserId(202L)
        verify(profiles).anonymizeByUserId(202L, NOW)
        verify(users).markDeactivated(202L, 0, NOW)
        verify(requests).markCompleted(501L, 1, NOW)
        verify(events, org.mockito.Mockito.times(2)).insert(any<UserDataRequestEventEntity>())
    }

    @Test
    fun batchContinuesAfterSecondRequestFails() {
        `when`(requests.findDueDeactivationIds(NOW, 20)).thenReturn(listOf(501L, 502L, 503L))
        val first = request(UserDataRequestEntity.STATUS_WAITING_EXECUTION, 0)
        first.id = 501L
        val second = request(UserDataRequestEntity.STATUS_WAITING_EXECUTION, 0)
        second.id = 502L
        val third = request(UserDataRequestEntity.STATUS_WAITING_EXECUTION, 0)
        third.id = 503L
        `when`(requests.lockById(501L)).thenReturn(Optional.of(first))
        `when`(requests.lockById(502L)).thenReturn(Optional.of(second))
        `when`(requests.lockById(503L)).thenReturn(Optional.of(third))
        `when`(requests.markProcessing(any<Long>(), any<Int>(), any<Int>(), any())).thenReturn(1)
        `when`(users.lockById(202L)).thenReturn(
                Optional.of(user(2, 0)),
                Optional.of(user(1, 0)),
                Optional.of(user(2, 0)))
        `when`(users.markDeactivated(202L, 0, NOW)).thenReturn(1)
        `when`(requests.markCompleted(any<Long>(), any<Int>(), any())).thenReturn(1)

        assertThatCode { job.executePendingDeactivations() }
            .doesNotThrowAnyException()

        verify(requests).markCompleted(501L, 1, NOW)
        verify(requests).markCompleted(503L, 1, NOW)
    }

    @Test
    fun completedOrChangedRequestIsNotProcessedAgain() {
        `when`(requests.findDueDeactivationIds(NOW, 20)).thenReturn(listOf(501L))
        `when`(requests.lockById(501L))
            .thenReturn(Optional.of(request(UserDataRequestEntity.STATUS_COMPLETED, 2)))

        job.executePendingDeactivations()

        verify(sessions, never()).revokeAllActiveByUserId(any<Long>(), any(), any())
        verify(users, never()).markDeactivated(any<Long>(), any<Int>(), any())
        verify(requests, never()).markCompleted(any<Long>(), any<Int>(), any())
    }

    @Test
    fun optimisticClaimFailureLeavesPersonalDataUntouched() {
        `when`(requests.findDueDeactivationIds(NOW, 20)).thenReturn(listOf(501L))
        `when`(requests.lockById(501L)).thenReturn(Optional.of(
                request(UserDataRequestEntity.STATUS_WAITING_EXECUTION, 0)))
        `when`(requests.markProcessing(501L, UserDataRequestEntity.STATUS_WAITING_EXECUTION, 0, NOW))
            .thenReturn(0)

        job.executePendingDeactivations()

        verify(sessions, never()).revokeAllActiveByUserId(any<Long>(), any(), any())
        verify(profiles, never()).anonymizeByUserId(any<Long>(), any())
    }

    @Test
    fun dueExportCreatesPrivateResultForTwentyFourHours() {
        `when`(requests.findDueExportIds(NOW, 20)).thenReturn(listOf(601L))
        val request = request(UserDataRequestEntity.STATUS_REQUESTED, 0)
        request.id = 601L
        request.requestType = UserDataRequestEntity.TYPE_EXPORT
        `when`(requests.lockById(601L)).thenReturn(Optional.of(request))
        `when`(requests.markProcessing(601L, UserDataRequestEntity.STATUS_REQUESTED, 0, NOW)).thenReturn(1)
        `when`(exports.create(202L, NOW.plusDays(1)))
            .thenReturn(PersonalDataExportService.ExportResult(801L))
        `when`(requests.markExportCompleted(601L, 1, 801L, NOW.plusDays(1), NOW)).thenReturn(1)

        job.executePendingExports()

        verify(exports).create(202L, NOW.plusDays(1))
        verify(requests).markExportCompleted(601L, 1, 801L, NOW.plusDays(1), NOW)
        verify(events, org.mockito.Mockito.times(2)).insert(any<UserDataRequestEventEntity>())
    }

    @Test
    fun failedRequestRecordsStableFailureAndCanBeRetried() {
        val failed = request(UserDataRequestEntity.STATUS_FAILED, 1)
        `when`(requests.findDueDeactivationIds(NOW, 20)).thenReturn(listOf(501L))
        `when`(requests.lockById(501L)).thenReturn(Optional.of(failed))
        `when`(requests.markProcessing(501L, UserDataRequestEntity.STATUS_FAILED, 1, NOW))
            .thenReturn(1)
        `when`(users.lockById(202L)).thenReturn(Optional.of(user(2, 0)))
        `when`(users.markDeactivated(202L, 0, NOW)).thenReturn(1)
        `when`(requests.markCompleted(501L, 2, NOW)).thenReturn(1)

        job.executePendingDeactivations()

        verify(requests).markProcessing(501L, UserDataRequestEntity.STATUS_FAILED, 1, NOW)
        verify(requests).markCompleted(501L, 2, NOW)
        verify(events, org.mockito.Mockito.times(2)).insert(any<UserDataRequestEventEntity>())
    }

    @Test
    fun failureRecorderIncrementsRetryAndAppendsSanitizedEvent() {
        val request = request(UserDataRequestEntity.STATUS_WAITING_EXECUTION, 3)
        `when`(requests.lockById(501L)).thenReturn(Optional.of(request))
        `when`(requests.markFailed(501L, UserDataRequestEntity.STATUS_WAITING_EXECUTION, 3,
                AccountDataFailureRecorder.DEACTIVATION_FAILURE,
                "账号注销后台任务执行失败", NOW)).thenReturn(1)
        val recorder = AccountDataFailureRecorder(requests, events)

        recorder.record(501L, UserDataRequestEntity.TYPE_DEACTIVATION,
                IllegalStateException("包含敏感路径 /secret/token"), NOW)

        verify(requests).markFailed(501L, UserDataRequestEntity.STATUS_WAITING_EXECUTION, 3,
                AccountDataFailureRecorder.DEACTIVATION_FAILURE,
                "账号注销后台任务执行失败", NOW)
        val event = org.mockito.ArgumentCaptor.forClass(UserDataRequestEventEntity::class.java)
        verify(events).insert(event.capture())
        assertThat(event.value.eventType)
            .isEqualTo("DEACTIVATION_FAILED")
        assertThat(event.value.detailJson)
            .doesNotContain("secret", "token")
    }

    @Test
    fun batchCoordinatorHasNoTransactionAndProcessorsRequireNew() {
        assertThat(AccountDataJob::class.java
                .getMethod("executePendingDeactivations")
                .getAnnotation(org.springframework.transaction.annotation.Transactional::class.java)).isNull()
        val deactivation = DeactivationDataRequestProcessor::class.java
                .getMethod("process", Long::class.java, LocalDateTime::class.java)
                .getAnnotation(org.springframework.transaction.annotation.Transactional::class.java)
        val export = ExportDataRequestProcessor::class.java
                .getMethod("process", Long::class.java, LocalDateTime::class.java)
                .getAnnotation(org.springframework.transaction.annotation.Transactional::class.java)
        assertThat(deactivation.propagation)
            .isEqualTo(org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
        assertThat(export.propagation)
            .isEqualTo(org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
        val failure = AccountDataFailureRecorder::class.java
                .getMethod("record", Long::class.java, Int::class.java,
                        RuntimeException::class.java, LocalDateTime::class.java)
                .getAnnotation(org.springframework.transaction.annotation.Transactional::class.java)
        assertThat(failure.propagation)
            .isEqualTo(org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    }

    @Test
    fun completedExportIsNotCreatedAgain() {
        `when`(requests.findDueExportIds(NOW, 20)).thenReturn(listOf(601L))
        val request = request(UserDataRequestEntity.STATUS_COMPLETED, 2)
        request.id = 601L
        request.requestType = UserDataRequestEntity.TYPE_EXPORT
        `when`(requests.lockById(601L)).thenReturn(Optional.of(request))

        job.executePendingExports()

        verify(exports, never()).create(any<Long>(), any())
    }

    @Test
    fun failureRecordLogContainsRequestIdentityAndThrowable() {
        val exportProcessor = mock(ExportDataRequestProcessor::class.java)
        val recorder = mock(AccountDataFailureRecorder::class.java)
        val isolatedJob = AccountDataJob(requests,
                mock(DeactivationDataRequestProcessor::class.java), exportProcessor, recorder,
                Clock.fixed(Instant.parse("2026-07-30T08:00:00Z"), ZoneOffset.UTC))
        `when`(requests.findDueExportIds(NOW, 20)).thenReturn(listOf(601L))
        org.mockito.Mockito.doThrow(IllegalStateException("process failure"))
                .`when`(exportProcessor).process(601L, NOW)
        org.mockito.Mockito.doThrow(IllegalArgumentException("record failure"))
                .`when`(recorder).record(any<Long>(), any<Int>(), any<RuntimeException>(), any())
        val logger = org.slf4j.LoggerFactory.getLogger(AccountDataJob::class.java) as ch.qos.logback.classic.Logger
        val appender = ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        try {
            isolatedJob.executePendingExports()
        } finally {
            logger.detachAppender(appender)
        }

        assertThat(appender.list).hasSize(1)
        val event = appender.list[0]
        assertThat(event.formattedMessage)
            .contains("requestId=601", "requestType=2")
        assertThat(event.throwableProxy).isNotNull()
    }

    private fun request(status: Int, version: Int): UserDataRequestEntity {
        val request = UserDataRequestEntity()
        request.id = 501L
        request.userId = 202L
        request.requestType = UserDataRequestEntity.TYPE_DEACTIVATION
        request.status = status
        request.requestedAt = LocalDateTime.parse("2026-07-23T08:00:00")
        request.executeAfter = NOW
        request.version = version
        return request
    }

    private fun user(status: Int, version: Int): UserEntity {
        val user = UserEntity()
        user.id = 202L
        user.status = status
        user.version = version
        return user
    }
}
