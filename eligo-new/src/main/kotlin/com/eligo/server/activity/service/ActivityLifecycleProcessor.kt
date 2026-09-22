package com.eligo.server.activity.service

import com.eligo.server.activity.entity.ActivityLifecycleEventEntity
import com.eligo.server.activity.mapper.ActivityLifecycleEventMapper
import com.eligo.server.activity.mapper.ActivityMapper
import java.time.LocalDateTime
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test")
class ActivityLifecycleProcessor(
    private val activities: ActivityMapper,
    private val events: ActivityLifecycleEventMapper
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun endIfDue(activityId: Long, now: LocalDateTime): Boolean {
        if (activities.endPublishedIfDue(activityId, now) != 1) {
            return false
        }
        val event = ActivityLifecycleEventEntity().apply {
            this.activityId = activityId
            fromStatus = PUBLISHED
            toStatus = ENDED
            operatorUserId = null
            createdAt = now
        }
        events.insert(event)
        return true
    }

    companion object {
        private const val PUBLISHED = 2
        private const val ENDED = 4
    }
}
