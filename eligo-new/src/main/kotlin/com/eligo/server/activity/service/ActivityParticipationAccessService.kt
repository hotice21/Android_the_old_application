package com.eligo.server.activity.service

import com.eligo.server.activity.entity.ActivityEntity
import com.eligo.server.activity.mapper.ActivityMapper
import com.eligo.server.organization.mapper.OrganizationMapper
import java.util.Optional
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test")
class ActivityParticipationAccessService(
    private val activities: ActivityMapper,
    private val organizations: OrganizationMapper
) {

    @Transactional(propagation = Propagation.MANDATORY)
    fun lockById(activityId: Long): Optional<ActivityParticipationSnapshot> =
        activities.lockById(activityId).map { snapshot(it) }

    @Transactional(readOnly = true)
    fun findById(activityId: Long): Optional<ActivityParticipationSnapshot> =
        Optional.ofNullable(activities.selectById(activityId)).map { snapshot(it) }

    @Transactional(propagation = Propagation.MANDATORY)
    fun isOwner(userId: Long, activity: ActivityParticipationSnapshot): Boolean {
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

    @Transactional(propagation = Propagation.MANDATORY)
    fun incrementParticipantCount(activityId: Long): Int =
        activities.incrementParticipantCount(activityId)

    @Transactional(propagation = Propagation.MANDATORY)
    fun decrementParticipantCount(activityId: Long): Int =
        activities.decrementParticipantCount(activityId)

    private fun snapshot(activity: ActivityEntity): ActivityParticipationSnapshot =
        ActivityParticipationSnapshot(
            activity.id!!,
            activity.status,
            activity.ownerUserId,
            activity.ownerOrganizationId,
            activity.registrationStartsAt,
            activity.registrationEndsAt,
            activity.startsAt,
            activity.endsAt,
            activity.capacity,
            activity.participantCount,
            activity.registrationGender ?: 1
        )
}
