package com.eligo.server.participation

import java.util.function.Function

import com.eligo.server.account.service.AccountStateLockService
import com.eligo.server.activity.service.ActivityParticipationAccessService
import com.eligo.server.activity.service.ActivityParticipationSnapshot
import com.eligo.server.common.error.BusinessException
import com.eligo.server.participation.entity.ActivityParticipationEntity
import com.eligo.server.participation.error.ParticipationErrorCode
import com.eligo.server.participation.mapper.ActivityParticipationMapper
import com.eligo.server.participation.service.DefaultParticipationCommandService
import com.eligo.server.profile.service.ProfileCompletionReader
import com.eligo.server.profile.service.ProfileGenderReader
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
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ParticipationGenderRestrictionTests {

    private val activities = mock<ActivityParticipationAccessService>()
    private val participations = mock<ActivityParticipationMapper>()
    private val completion = mock<ProfileCompletionReader>()
    private val genders = mock<ProfileGenderReader>()
    private val accountStates = mock<AccountStateLockService>()
    private lateinit var service: DefaultParticipationCommandService

    @BeforeEach
    fun setUp() {
        whenever(completion.isCompleted(USER_ID)).thenReturn(true)
        service = DefaultParticipationCommandService(
            activities,
            participations,
            completion,
            genders,
            accountStates,
            Clock.fixed(NOW, ZoneOffset.UTC))
    }

    @Test
    fun matchingGenderCanJoinRestrictedActivity() {
        whenever(genders.genderCode(USER_ID)).thenReturn(2)
        whenever(activities.lockById(ACTIVITY_ID)).thenReturn(Optional.of(activity(3)))
        whenever(participations.lockByActivityAndUser(ACTIVITY_ID, USER_ID))
            .thenReturn(Optional.empty())
        whenever(participations.insert(any<ActivityParticipationEntity>()))
            .thenAnswer { invocation ->
                invocation.getArgument<ActivityParticipationEntity>(0).id = 8001L
                1
            }
        whenever(activities.incrementParticipantCount(ACTIVITY_ID)).thenReturn(1)

        assertThat(service.join(PRINCIPAL, ACTIVITY_ID).status).isEqualTo("ACTIVE")
        verify(genders).genderCode(USER_ID)
    }

    @Test
    fun mismatchingGenderIsRejectedWithDistinctParticipationError() {
        whenever(genders.genderCode(USER_ID)).thenReturn(1)
        whenever(activities.lockById(ACTIVITY_ID)).thenReturn(Optional.of(activity(3)))
        whenever(participations.lockByActivityAndUser(ACTIVITY_ID, USER_ID))
            .thenReturn(Optional.empty())

        assertThatThrownBy { service.join(PRINCIPAL, ACTIVITY_ID) }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  exception -> (exception as BusinessException).errorCode  })
            .isEqualTo(ParticipationErrorCode.REGISTRATION_GENDER_MISMATCH)
        verify(participations, never()).insert(any<ActivityParticipationEntity>())
        verify(activities, never()).incrementParticipantCount(ACTIVITY_ID)
    }

    @Test
    fun unlimitedGenderDoesNotReadProfileGender() {
        whenever(activities.lockById(ACTIVITY_ID)).thenReturn(Optional.of(activity(1)))
        whenever(participations.lockByActivityAndUser(ACTIVITY_ID, USER_ID))
            .thenReturn(Optional.empty())
        whenever(participations.insert(any<ActivityParticipationEntity>()))
            .thenAnswer { invocation ->
                invocation.getArgument<ActivityParticipationEntity>(0).id = 8001L
                1
            }
        whenever(activities.incrementParticipantCount(ACTIVITY_ID)).thenReturn(1)

        assertThat(service.join(PRINCIPAL, ACTIVITY_ID).status).isEqualTo("ACTIVE")
        verify(genders, never()).genderCode(USER_ID)
    }

    private fun activity(registrationGenderCode: Int): ActivityParticipationSnapshot {
        return ActivityParticipationSnapshot(
            ACTIVITY_ID,
            2,
            USER_ID + 1,
            null,
            LOCAL_NOW.minusHours(1),
            LOCAL_NOW.plusHours(1),
            LOCAL_NOW.plusHours(2),
            LOCAL_NOW.plusHours(3),
            20,
            0,
            registrationGenderCode)
    }

    companion object {
        private const val USER_ID = 202L
        private const val ACTIVITY_ID = 1001L
        private val NOW = Instant.parse("2026-08-18T00:00:00Z")
        private val LOCAL_NOW = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
        private val PRINCIPAL = UserPrincipal(USER_ID, "session-m3")
    }
}
