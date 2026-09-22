package com.eligo.server.activity.service

import com.eligo.server.activity.mapper.ActivityCreateIdempotencyTombstoneMapper
import com.eligo.server.activity.mapper.ActivityMapper
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("!test")
class ActivityMaintenanceJob(
    private val activities: ActivityMapper,
    private val lifecycleProcessor: ActivityLifecycleProcessor,
    private val idempotencyTombstones: ActivityCreateIdempotencyTombstoneMapper,
    private val clock: Clock = Clock.systemUTC()
) {

    @Scheduled(
        initialDelayString = "\${eligo.activity.maintenance-initial-delay-ms:60000}",
        fixedDelayString = "\${eligo.activity.maintenance-interval-ms:60000}"
    )
    fun maintainActivities() {
        val now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
        endDueActivities(now)
        cleanupExpiredIdempotencyTombstones(now)
    }

    private fun endDueActivities(now: LocalDateTime) {
        var failed = 0
        for (activityId in activities.findPublishedIdsDueToEnd(now, BATCH_SIZE)) {
            try {
                lifecycleProcessor.endIfDue(activityId, now)
            } catch (exception: RuntimeException) {
                failed++
            }
        }
        if (failed > 0) {
            log.error("到期活动状态迁移失败 count={}", failed)
        }
    }

    private fun cleanupExpiredIdempotencyTombstones(now: LocalDateTime) {
        try {
            idempotencyTombstones.deleteExpired(now, BATCH_SIZE)
        } catch (exception: RuntimeException) {
            log.error("活动创建幂等删除凭证清理失败")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ActivityMaintenanceJob::class.java)
        private const val BATCH_SIZE = 100
    }
}
