package com.eligo.server.activity

import com.eligo.server.activity.mapper.ActivityCreateIdempotencyTombstoneMapper
import com.eligo.server.activity.mapper.ActivityMapper
import com.eligo.server.activity.service.ActivityLifecycleProcessor
import com.eligo.server.activity.service.ActivityMaintenanceJob
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class ActivityMaintenanceJobTests {

    private val activities = mock(ActivityMapper::class.java)
    private val processor = mock(ActivityLifecycleProcessor::class.java)
    private val idempotencyTombstones = mock(ActivityCreateIdempotencyTombstoneMapper::class.java)
    private val job = ActivityMaintenanceJob(
        activities,
        processor,
        idempotencyTombstones,
        Clock.fixed(Instant.parse("2026-08-09T05:00:00Z"), ZoneOffset.UTC)
    )

    @Test
    fun processesDueActivitiesAndCleansExpiredIdempotencyTombstones() {
        `when`(activities.findPublishedIdsDueToEnd(NOW, 100))
            .thenReturn(listOf(101L, 102L))

        job.maintainActivities()

        verify(processor).endIfDue(101L, NOW)
        verify(processor).endIfDue(102L, NOW)
        verify(idempotencyTombstones).deleteExpired(NOW, 100)
    }

    @Test
    fun oneActivityFailureDoesNotStopTheRemainingBatchOrCleanup() {
        `when`(activities.findPublishedIdsDueToEnd(NOW, 100))
            .thenReturn(listOf(101L, 102L, 103L))
        doThrow(IllegalStateException("测试异常"))
            .`when`(processor).endIfDue(102L, NOW)

        assertThatCode { job.maintainActivities() }.doesNotThrowAnyException()

        verify(processor).endIfDue(101L, NOW)
        verify(processor).endIfDue(103L, NOW)
        verify(idempotencyTombstones).deleteExpired(NOW, 100)
    }

    companion object {
        private val NOW = LocalDateTime.parse("2026-08-09T05:00:00")
    }
}
