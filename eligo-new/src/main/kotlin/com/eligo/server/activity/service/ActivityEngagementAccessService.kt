package com.eligo.server.activity.service

import com.eligo.server.activity.entity.ActivityEntity
import com.eligo.server.activity.mapper.ActivityMapper
import com.eligo.server.activity.vo.PublicActivitySummaryView
import com.eligo.server.organization.mapper.OrganizationMapper
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test")
class ActivityEngagementAccessService(
    private val activities: ActivityMapper,
    private val organizations: OrganizationMapper,
    private val reads: ActivityReadService,
    private val clock: Clock = Clock.systemUTC()
) {

    @Transactional(readOnly = true)
    fun findById(activityId: Long): Optional<ActivityEngagementSnapshot> {
        if (activityId <= 0) {
            return Optional.empty()
        }
        val activity = activities.selectById(activityId) ?: return Optional.empty()
        val effectiveStatus = if (activity.status == PUBLISHED
            && activity.endsAt != null
            && !now().isBefore(activity.endsAt)
        ) ENDED else activity.status
        return Optional.of(
            ActivityEngagementSnapshot(
                activity.id!!,
                effectiveStatus!!,
                activity.endsAt,
                activity.ownerUserId,
                activity.ownerOrganizationId
            )
        )
    }

    @Transactional(readOnly = true)
    fun findPublicById(activityId: Long): Optional<ActivityEngagementSnapshot> =
        findById(activityId).filter { activity -> publicStatus(activity.status) }

    @Transactional(readOnly = true)
    fun isOwner(userId: Long, activity: ActivityEngagementSnapshot): Boolean {
        if (activity.ownerUserId == userId) {
            return true
        }
        if (activity.ownerOrganizationId == null) {
            return false
        }
        return organizations.findActiveOwnedByUserId(userId).any { item ->
            item.id == activity.ownerOrganizationId
        }
    }

    @Transactional(readOnly = true)
    fun findPublicSummaries(
        activityIds: List<Long>
    ): Map<Long, PublicActivitySummaryView> =
        reads.findPublicSummaries(activityIds.distinct())

    private fun publicStatus(status: Int?): Boolean =
        status != null
            && (status == PUBLISHED || status == CANCELLED || status == ENDED)

    private fun now(): LocalDateTime =
        LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)

    companion object {
        private const val PUBLISHED = 2
        private const val CANCELLED = 3
        private const val ENDED = 4
    }
}
