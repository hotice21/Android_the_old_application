package com.eligo.server.participation

import java.util.function.Function

import com.eligo.server.account.service.AccountStateLockService
import com.eligo.server.activity.service.ActivityParticipationAccessService
import com.eligo.server.activity.service.ActivityParticipationSnapshot
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.participation.entity.ActivityParticipationEntity
import com.eligo.server.participation.error.ParticipationErrorCode
import com.eligo.server.participation.mapper.ActivityParticipationMapper
import com.eligo.server.participation.service.DefaultParticipationCommandService
import com.eligo.server.participation.vo.ActivityParticipationView
import com.eligo.server.profile.service.ProfileCompletionReader
import com.eligo.server.security.UserPrincipal
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.InOrder
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ParticipationCommandServiceTests {

    private val activities = mock<ActivityParticipationAccessService>()
    private val participations = mock<ActivityParticipationMapper>()
    private val completion = mock<ProfileCompletionReader>()
    private val accountStates = mock<AccountStateLockService>()
    private lateinit var service: DefaultParticipationCommandService

    @BeforeEach
    fun setUp() {
        whenever(completion.isCompleted(USER_ID)).thenReturn(true)
        service = DefaultParticipationCommandService(
            activities,
            participations,
            completion,
            accountStates = accountStates,
            clock = Clock.fixed(NOW, ZoneOffset.UTC))
    }

    @Test
    fun joinsPublishedActivityAndIncrementsCountOnce() {
        val activity = publishedActivity()
        whenever(activities.lockById(ACTIVITY_ID)).thenReturn(Optional.of(activity))
        whenever(participations.lockByActivityAndUser(ACTIVITY_ID, USER_ID))
            .thenReturn(Optional.empty())
        whenever(participations.insert(any<ActivityParticipationEntity>()))
            .thenAnswer { invocation ->
                val entity = invocation.getArgument<ActivityParticipationEntity>(0)
                entity.id = 8001L
                1
            }
        whenever(activities.incrementParticipantCount(ACTIVITY_ID)).thenReturn(1)

        val result = service.join(PRINCIPAL, ACTIVITY_ID)

        assertThat(result.participationId).isEqualTo("8001")
        assertThat(result.userId).isEqualTo(USER_ID.toString())
        assertThat(result.status).isEqualTo("ACTIVE")
        assertThat(result.joinedAt).isEqualTo(NOW)
        assertThat(result.participantCount).isEqualTo(1)
        verify(participations).insert(any<ActivityParticipationEntity>())
        verify(activities).incrementParticipantCount(ACTIVITY_ID)
        val locks: InOrder = inOrder(activities, accountStates, participations)
        locks.verify(activities).lockById(ACTIVITY_ID)
        locks.verify(accountStates).lockActive(USER_ID)
        locks.verify(participations).lockByActivityAndUser(ACTIVITY_ID, USER_ID)
    }

    @Test
    fun activeJoinIsIdempotentBeforeProfileAndActivityChecks() {
        val activity = withStatus(publishedActivity(), 3)
        val active = participation(1, null)
        whenever(activities.lockById(ACTIVITY_ID)).thenReturn(Optional.of(activity))
        whenever(participations.lockByActivityAndUser(ACTIVITY_ID, USER_ID))
            .thenReturn(Optional.of(active))
        whenever(completion.isCompleted(USER_ID)).thenReturn(false)

        val result = service.join(PRINCIPAL, ACTIVITY_ID)

        assertThat(result.status).isEqualTo("ACTIVE")
        verify(activities, never()).incrementParticipantCount(ACTIVITY_ID)
        verify(participations, never()).insert(any<ActivityParticipationEntity>())
    }

    @Test
    fun rejoinRefreshesJoinedAtAndClearsCancellationFields() {
        val activity = withParticipantCount(publishedActivity(), 3)
        val cancelled = participation(2, null)
        cancelled.cancelledAt = LOCAL_NOW.minusDays(1)
        whenever(activities.lockById(ACTIVITY_ID)).thenReturn(Optional.of(activity))
        whenever(participations.lockByActivityAndUser(ACTIVITY_ID, USER_ID))
            .thenReturn(Optional.of(cancelled))
        whenever(participations.reactivate(eq(8001L), eq(LOCAL_NOW))).thenReturn(1)
        whenever(activities.incrementParticipantCount(ACTIVITY_ID)).thenReturn(1)

        val result = service.join(PRINCIPAL, ACTIVITY_ID)

        assertThat(result.status).isEqualTo("ACTIVE")
        assertThat(result.joinedAt).isEqualTo(NOW)
        assertThat(result.cancelledAt).isNull()
        assertThat(result.terminatedAt).isNull()
        assertThat(result.terminationReason).isNull()
        assertThat(result.participantCount).isEqualTo(4)
    }

    @Test
    fun removedParticipantCannotJoinAgainBeforeProfileAndActivityChecks() {
        val activity = publishedActivity()
        val removed = participation(3, 2)
        whenever(activities.lockById(ACTIVITY_ID)).thenReturn(Optional.of(activity))
        whenever(participations.lockByActivityAndUser(ACTIVITY_ID, USER_ID))
            .thenReturn(Optional.of(removed))
        whenever(completion.isCompleted(USER_ID)).thenReturn(false)

        assertError({ service.join(PRINCIPAL, ACTIVITY_ID) },
            ParticipationErrorCode.REMOVED_PARTICIPANT_CANNOT_REJOIN)
        verify(participations, never()).reactivate(
            eq(8001L), any<LocalDateTime>())
        verify(activities, never()).incrementParticipantCount(ACTIVITY_ID)
    }

    @Test
    fun joinUsesSpecifiedBusinessErrorPrecedence() {
        var activity = publishedActivity()
        whenever(activities.lockById(ACTIVITY_ID))
            .thenAnswer { Optional.of(activity) }
        whenever(participations.lockByActivityAndUser(ACTIVITY_ID, USER_ID))
            .thenReturn(Optional.empty())

        whenever(completion.isCompleted(USER_ID)).thenReturn(false)
        assertError({ service.join(PRINCIPAL, ACTIVITY_ID) },
            AccountUserFileErrorCode.PROFILE_INCOMPLETE)

        whenever(completion.isCompleted(USER_ID)).thenReturn(true)
        activity = withStatus(activity, 3)
        assertError({ service.join(PRINCIPAL, ACTIVITY_ID) },
            ParticipationErrorCode.ACTIVITY_CANCELLED)

        activity = withStatus(activity, 2)
        activity = withSchedule(
            activity, activity.registrationStartsAt,
            activity.registrationEndsAt, activity.startsAt, LOCAL_NOW)
        assertError({ service.join(PRINCIPAL, ACTIVITY_ID) },
            ParticipationErrorCode.ACTIVITY_ENDED)

        activity = withSchedule(
            activity, activity.registrationStartsAt,
            activity.registrationEndsAt, LOCAL_NOW, LOCAL_NOW.plusHours(5))
        assertError({ service.join(PRINCIPAL, ACTIVITY_ID) },
            ParticipationErrorCode.ACTIVITY_STARTED)

        activity = withSchedule(
            activity, LOCAL_NOW.plusSeconds(1),
            activity.registrationEndsAt, LOCAL_NOW.plusHours(3), activity.endsAt)
        assertError({ service.join(PRINCIPAL, ACTIVITY_ID) },
            ParticipationErrorCode.REGISTRATION_NOT_STARTED)

        activity = withSchedule(
            activity, LOCAL_NOW.minusHours(1), LOCAL_NOW,
            activity.startsAt, activity.endsAt)
        assertError({ service.join(PRINCIPAL, ACTIVITY_ID) },
            ParticipationErrorCode.REGISTRATION_ENDED)

        activity = withSchedule(
            activity, activity.registrationStartsAt, LOCAL_NOW.plusHours(1),
            activity.startsAt, activity.endsAt)
        activity = withOwner(activity, USER_ID, null)
        whenever(activities.isOwner(USER_ID, activity)).thenReturn(true)
        assertError({ service.join(PRINCIPAL, ACTIVITY_ID) },
            ParticipationErrorCode.OWNER_CANNOT_JOIN)

        activity = withOwner(activity, USER_ID + 1, null)
        activity = withParticipantCount(activity, activity.capacity!!)
        assertError({ service.join(PRINCIPAL, ACTIVITY_ID) },
            ParticipationErrorCode.CAPACITY_FULL)
    }

    @Test
    fun cancelsActiveParticipationAndInactiveCancellationIsIdempotent() {
        var activity = withParticipantCount(publishedActivity(), 2)
        val active = participation(1, null)
        whenever(activities.lockById(ACTIVITY_ID))
            .thenAnswer { Optional.of(activity) }
        whenever(participations.lockByActivityAndUser(ACTIVITY_ID, USER_ID))
            .thenReturn(Optional.of(active))
        whenever(participations.cancelActive(8001L, LOCAL_NOW)).thenReturn(1)
        whenever(activities.decrementParticipantCount(ACTIVITY_ID)).thenReturn(1)

        val result = service.cancel(PRINCIPAL, ACTIVITY_ID)

        assertThat(result.status).isEqualTo("CANCELLED")
        assertThat(result.cancelledAt).isEqualTo(NOW)
        assertThat(result.participantCount).isEqualTo(1)

        activity = withStatus(activity, 3)
        val repeated = service.cancel(PRINCIPAL, ACTIVITY_ID)
        assertThat(repeated.status).isEqualTo("CANCELLED")
        verify(activities).decrementParticipantCount(ACTIVITY_ID)
    }

    @Test
    fun ownerRemovesParticipantAndRepeatedRemovalIsIdempotent() {
        val activity = withParticipantCount(
            withOwner(publishedActivity(), USER_ID, null), 2)
        val targetUserId = 303L
        val active = participation(1, null)
        active.userId = targetUserId
        whenever(activities.lockById(ACTIVITY_ID)).thenReturn(Optional.of(activity))
        whenever(activities.isOwner(USER_ID, activity)).thenReturn(true)
        whenever(participations.lockByActivityAndUser(ACTIVITY_ID, targetUserId))
            .thenReturn(Optional.of(active))
        whenever(participations.terminateByOwner(8001L, LOCAL_NOW)).thenReturn(1)
        whenever(activities.decrementParticipantCount(ACTIVITY_ID)).thenReturn(1)

        val result = service.remove(PRINCIPAL, ACTIVITY_ID, targetUserId)

        assertThat(result.status).isEqualTo("TERMINATED")
        assertThat(result.terminationReason).isEqualTo("REMOVED_BY_OWNER")
        assertThat(result.participantCount).isEqualTo(1)

        val repeated = service.remove(PRINCIPAL, ACTIVITY_ID, targetUserId)
        assertThat(repeated.status).isEqualTo("TERMINATED")
        verify(activities).decrementParticipantCount(ACTIVITY_ID)
    }

    @Test
    fun removeRejectsCancelledOrActivityCancelledParticipation() {
        val activity = withOwner(publishedActivity(), USER_ID, null)
        val cancelled = participation(2, null)
        whenever(activities.lockById(ACTIVITY_ID)).thenReturn(Optional.of(activity))
        whenever(activities.isOwner(USER_ID, activity)).thenReturn(true)
        whenever(participations.lockByActivityAndUser(ACTIVITY_ID, 303L))
            .thenReturn(Optional.of(cancelled))

        assertError({ service.remove(PRINCIPAL, ACTIVITY_ID, 303L) },
            ParticipationErrorCode.PARTICIPATION_NOT_REMOVABLE)

        val terminated = participation(3, 1)
        whenever(participations.lockByActivityAndUser(ACTIVITY_ID, 303L))
            .thenReturn(Optional.of(terminated))
        assertError({ service.remove(PRINCIPAL, ACTIVITY_ID, 303L) },
            ParticipationErrorCode.PARTICIPATION_NOT_REMOVABLE)
    }

    @Test
    fun currentOrganizationOwnerCanManageEnterpriseActivity() {
        val activity = withParticipantCount(
            withOwner(publishedActivity(), null, 401L), 1)
        val active = participation(1, null)
        active.userId = 303L
        whenever(activities.lockById(ACTIVITY_ID)).thenReturn(Optional.of(activity))
        whenever(activities.isOwner(USER_ID, activity)).thenReturn(true)
        whenever(participations.lockByActivityAndUser(ACTIVITY_ID, 303L))
            .thenReturn(Optional.of(active))
        whenever(participations.terminateByOwner(8001L, LOCAL_NOW)).thenReturn(1)
        whenever(activities.decrementParticipantCount(ACTIVITY_ID)).thenReturn(1)

        assertThat(service.remove(PRINCIPAL, ACTIVITY_ID, 303L).status)
            .isEqualTo("TERMINATED")
    }

    private fun publishedActivity(): ActivityParticipationSnapshot {
        return ActivityParticipationSnapshot(
            ACTIVITY_ID, 2, USER_ID + 1, null,
            LOCAL_NOW.minusHours(1), LOCAL_NOW.plusHours(1),
            LOCAL_NOW.plusHours(2), LOCAL_NOW.plusHours(3), 20, 0)
    }

    private fun withStatus(
        activity: ActivityParticipationSnapshot, status: Int): ActivityParticipationSnapshot {
        return ActivityParticipationSnapshot(
            activity.activityId, status, activity.ownerUserId,
            activity.ownerOrganizationId, activity.registrationStartsAt,
            activity.registrationEndsAt, activity.startsAt, activity.endsAt,
            activity.capacity, activity.participantCount)
    }

    private fun withParticipantCount(
        activity: ActivityParticipationSnapshot, participantCount: Int): ActivityParticipationSnapshot {
        return ActivityParticipationSnapshot(
            activity.activityId, activity.status, activity.ownerUserId,
            activity.ownerOrganizationId, activity.registrationStartsAt,
            activity.registrationEndsAt, activity.startsAt, activity.endsAt,
            activity.capacity, participantCount)
    }

    private fun withOwner(
        activity: ActivityParticipationSnapshot,
        ownerUserId: Long?,
        ownerOrganizationId: Long?): ActivityParticipationSnapshot {
        return ActivityParticipationSnapshot(
            activity.activityId, activity.status, ownerUserId,
            ownerOrganizationId, activity.registrationStartsAt,
            activity.registrationEndsAt, activity.startsAt, activity.endsAt,
            activity.capacity, activity.participantCount)
    }

    private fun withSchedule(
        activity: ActivityParticipationSnapshot,
        registrationStartsAt: LocalDateTime?,
        registrationEndsAt: LocalDateTime?,
        startsAt: LocalDateTime?,
        endsAt: LocalDateTime?): ActivityParticipationSnapshot {
        return ActivityParticipationSnapshot(
            activity.activityId, activity.status, activity.ownerUserId,
            activity.ownerOrganizationId, registrationStartsAt,
            registrationEndsAt, startsAt, endsAt,
            activity.capacity, activity.participantCount)
    }

    private fun participation(status: Int, reason: Int?): ActivityParticipationEntity {
        val participation = ActivityParticipationEntity()
        participation.id = 8001L
        participation.activityId = ACTIVITY_ID
        participation.userId = USER_ID
        participation.status = status
        participation.joinedAt = LOCAL_NOW.minusDays(2)
        participation.terminationReason = reason
        if (status == 3) {
            participation.terminatedAt = LOCAL_NOW.minusDays(1)
        }
        participation.version = 0
        return participation
    }

    private fun assertError(operation: () -> Unit, expectedError: Any) {
        assertThatThrownBy(operation)
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  exception -> (exception as BusinessException).errorCode  })
            .isEqualTo(expectedError)
    }

    companion object {
        private const val USER_ID = 202L
        private const val ACTIVITY_ID = 1001L
        private val NOW = Instant.parse("2026-08-09T00:00:00Z")
        private val LOCAL_NOW = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
        private val PRINCIPAL = UserPrincipal(USER_ID, "session-m3")
    }
}
