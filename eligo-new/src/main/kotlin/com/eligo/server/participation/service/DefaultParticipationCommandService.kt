package com.eligo.server.participation.service

import com.eligo.server.account.service.AccountStateLockService
import com.eligo.server.activity.service.ActivityParticipationAccessService
import com.eligo.server.activity.service.ActivityParticipationSnapshot
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.participation.entity.ActivityParticipationEntity
import com.eligo.server.participation.error.ParticipationErrorCode
import com.eligo.server.participation.mapper.ActivityParticipationMapper
import com.eligo.server.participation.vo.ActivityParticipationView
import com.eligo.server.profile.service.ProfileCompletionReader
import com.eligo.server.profile.service.ProfileGenderReader
import com.eligo.server.security.UserPrincipal
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test")
class DefaultParticipationCommandService(
    private val activities: ActivityParticipationAccessService,
    private val participations: ActivityParticipationMapper,
    private val completion: ProfileCompletionReader,
    private val genders: ProfileGenderReader = ProfileGenderReader { null },
    private val accountStates: AccountStateLockService,
    private val clock: Clock = Clock.systemUTC()
) : ParticipationCommandService {

    override
    @Transactional
    fun join(principal: UserPrincipal?, activityId: Long): ActivityParticipationView {
        requirePrincipalAndId(principal, activityId)
        val activity = activities.lockById(activityId).orElseThrow(::notFound)
        if (activity.status == null
            || activity.status == 1
            || activity.status == HIDDEN
        ) {
            throw notFound()
        }

        accountStates.lockActive(principal!!.userId)
        var current = participations
            .lockByActivityAndUser(activityId, principal.userId)
            .orElse(null)
        if (current != null && current.status == ACTIVE) {
            return view(current, activity.participantCount)
        }
        if (current != null
            && current.status == TERMINATED
            && current.terminationReason == REMOVED_BY_OWNER_REASON
        ) {
            throw BusinessException(
                ParticipationErrorCode.REMOVED_PARTICIPANT_CANNOT_REJOIN
            )
        }
        if (!completion.isCompleted(principal.userId)) {
            throw BusinessException(AccountUserFileErrorCode.PROFILE_INCOMPLETE)
        }

        val now = now()
        rejectJoinState(activity, current, principal.userId, now)
        if (current == null) {
            current = ActivityParticipationEntity()
            current.activityId = activityId
            current.userId = principal.userId
            current.status = ACTIVE
            current.joinedAt = now
            current.version = 0
            current.createdAt = now
            current.updatedAt = now
            if (participations.insert(current) != 1) {
                throw conflict()
            }
        } else {
            if (participations.reactivate(current.id!!, now) != 1) {
                throw conflict()
            }
            current.status = ACTIVE
            current.joinedAt = now
            current.cancelledAt = null
            current.terminatedAt = null
            current.terminationReason = null
            current.version = (current.version ?: 0) + 1
            current.updatedAt = now
        }
        if (activities.incrementParticipantCount(activityId) != 1) {
            throw BusinessException(ParticipationErrorCode.CAPACITY_FULL)
        }
        return view(current, (activity.participantCount ?: 0) + 1)
    }

    override
    @Transactional
    fun cancel(principal: UserPrincipal?, activityId: Long): ActivityParticipationView {
        requirePrincipalAndId(principal, activityId)
        val activity = activities.lockById(activityId).orElseThrow(::notFound)
        val participation = participations
            .lockByActivityAndUser(activityId, principal!!.userId)
            .orElseThrow(::notFound)
        if (participation.status != ACTIVE) {
            return view(participation, activity.participantCount)
        }

        val now = now()
        if (activity.status == CANCELLED_ACTIVITY) {
            throw BusinessException(ParticipationErrorCode.ACTIVITY_CANCELLED)
        }
        if (activity.status == ENDED_ACTIVITY || reached(activity.endsAt, now)) {
            throw BusinessException(ParticipationErrorCode.ACTIVITY_ENDED)
        }
        if (reached(activity.startsAt, now)) {
            throw BusinessException(ParticipationErrorCode.ACTIVITY_STARTED)
        }
        if (participations.cancelActive(participation.id!!, now) != 1
            || activities.decrementParticipantCount(activityId) != 1
        ) {
            throw conflict()
        }
        participation.status = CANCELLED_PARTICIPATION
        participation.cancelledAt = now
        participation.terminatedAt = null
        participation.terminationReason = null
        participation.version = (participation.version ?: 0) + 1
        participation.updatedAt = now
        return view(participation, (activity.participantCount ?: 0) - 1)
    }

    override
    @Transactional
    fun remove(
        principal: UserPrincipal?,
        activityId: Long,
        userId: Long
    ): ActivityParticipationView {
        requirePrincipalAndId(principal, activityId)
        if (userId <= 0) {
            throw validation()
        }
        val activity = activities.lockById(activityId).orElseThrow(::notFound)
        if (activity.status == null
            || activity.status == 1
            || !isOwner(principal!!.userId, activity)
        ) {
            throw notFound()
        }
        val participation = participations
            .lockByActivityAndUser(activityId, userId)
            .orElseThrow(::notFound)
        if (participation.status == TERMINATED
            && participation.terminationReason == REMOVED_BY_OWNER_REASON
        ) {
            return view(participation, activity.participantCount)
        }
        if (participation.status == CANCELLED_PARTICIPATION
            || (participation.status == TERMINATED
                && participation.terminationReason == ACTIVITY_CANCELLED_REASON)
        ) {
            throw BusinessException(ParticipationErrorCode.PARTICIPATION_NOT_REMOVABLE)
        }
        if (activity.status == CANCELLED_ACTIVITY) {
            throw BusinessException(ParticipationErrorCode.ACTIVITY_CANCELLED)
        }
        val now = now()
        if (activity.status == ENDED_ACTIVITY || reached(activity.endsAt, now)) {
            throw BusinessException(ParticipationErrorCode.ACTIVITY_ENDED)
        }
        if (participation.status != ACTIVE) {
            throw BusinessException(ParticipationErrorCode.PARTICIPATION_NOT_REMOVABLE)
        }
        if (participations.terminateByOwner(participation.id!!, now) != 1
            || activities.decrementParticipantCount(activityId) != 1
        ) {
            throw conflict()
        }
        participation.status = TERMINATED
        participation.cancelledAt = null
        participation.terminatedAt = now
        participation.terminationReason = REMOVED_BY_OWNER_REASON
        participation.version = (participation.version ?: 0) + 1
        participation.updatedAt = now
        return view(participation, (activity.participantCount ?: 0) - 1)
    }

    private fun rejectJoinState(
        activity: ActivityParticipationSnapshot,
        participation: ActivityParticipationEntity?,
        userId: Long,
        now: LocalDateTime
    ) {
        if (activity.status == CANCELLED_ACTIVITY
            || (participation != null
                && participation.status == TERMINATED
                && participation.terminationReason == ACTIVITY_CANCELLED_REASON)
        ) {
            throw BusinessException(ParticipationErrorCode.ACTIVITY_CANCELLED)
        }
        if (activity.status == ENDED_ACTIVITY || reached(activity.endsAt, now)) {
            throw BusinessException(ParticipationErrorCode.ACTIVITY_ENDED)
        }
        if (reached(activity.startsAt, now)) {
            throw BusinessException(ParticipationErrorCode.ACTIVITY_STARTED)
        }
        if (activity.registrationStartsAt != null
            && now.isBefore(activity.registrationStartsAt)
        ) {
            throw BusinessException(ParticipationErrorCode.REGISTRATION_NOT_STARTED)
        }
        if (reached(activity.registrationEndsAt, now)) {
            throw BusinessException(ParticipationErrorCode.REGISTRATION_ENDED)
        }
        if (isOwner(userId, activity)) {
            throw BusinessException(ParticipationErrorCode.OWNER_CANNOT_JOIN)
        }
        if (activity.capacity == null
            || activity.participantCount == null
            || activity.participantCount >= activity.capacity
        ) {
            throw BusinessException(ParticipationErrorCode.CAPACITY_FULL)
        }
        if (activity.status != PUBLISHED) {
            throw notFound()
        }
        if (!matchesRegistrationGender(activity.registrationGender, userId)) {
            throw BusinessException(ParticipationErrorCode.REGISTRATION_GENDER_MISMATCH)
        }
    }

    private fun matchesRegistrationGender(restriction: Int?, userId: Long): Boolean {
        if (restriction == null || restriction == 1) {
            return true
        }
        val profileGender = genders.genderCode(userId)
        return (restriction == 2 && profileGender == 1)
            || (restriction == 3 && profileGender == 2)
    }

    private fun isOwner(userId: Long, activity: ActivityParticipationSnapshot): Boolean =
        activities.isOwner(userId, activity)

    private fun view(
        participation: ActivityParticipationEntity,
        participantCount: Int?
    ): ActivityParticipationView =
        ActivityParticipationView(
            participation.id.toString(),
            participation.activityId.toString(),
            participation.userId.toString(),
            statusName(participation.status),
            instant(participation.joinedAt),
            instant(participation.cancelledAt),
            instant(participation.terminatedAt),
            reasonName(participation.terminationReason),
            participantCount
        )

    private fun statusName(status: Int?): String = when (status) {
        ACTIVE -> "ACTIVE"
        CANCELLED_PARTICIPATION -> "CANCELLED"
        TERMINATED -> "TERMINATED"
        else -> throw conflict()
    }

    private fun reasonName(reason: Int?): String? {
        if (reason == null) {
            return null
        }
        return when (reason) {
            ACTIVITY_CANCELLED_REASON -> "ACTIVITY_CANCELLED"
            REMOVED_BY_OWNER_REASON -> "REMOVED_BY_OWNER"
            else -> throw conflict()
        }
    }

    private fun requirePrincipalAndId(principal: UserPrincipal?, activityId: Long) {
        if (principal == null) {
            throw BusinessException(CommonErrorCode.AUTHENTICATION_REQUIRED)
        }
        if (activityId <= 0) {
            throw validation()
        }
    }

    private fun reached(boundary: LocalDateTime?, now: LocalDateTime): Boolean =
        boundary != null && !now.isBefore(boundary)

    private fun now(): LocalDateTime =
        LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)

    private fun instant(value: LocalDateTime?): Instant? =
        value?.toInstant(ZoneOffset.UTC)

    private fun validation(): BusinessException =
        BusinessException(CommonErrorCode.VALIDATION_FAILED)

    private fun notFound(): BusinessException =
        BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)

    private fun conflict(): BusinessException =
        BusinessException(CommonErrorCode.CONFLICT)

    companion object {
        private const val PUBLISHED = 2
        private const val CANCELLED_ACTIVITY = 3
        private const val ENDED_ACTIVITY = 4
        private const val HIDDEN = 5
        private const val ACTIVE = 1
        private const val CANCELLED_PARTICIPATION = 2
        private const val TERMINATED = 3
        private const val ACTIVITY_CANCELLED_REASON = 1
        private const val REMOVED_BY_OWNER_REASON = 2
    }
}
