package com.eligo.server.participation

import com.eligo.server.participation.mapper.ActivityParticipationMapper
import com.eligo.server.participation.service.ParticipationAccountDeactivationBlocker
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ParticipationAccountDeactivationBlockerTests {

    private val participations = mock<ActivityParticipationMapper>()
    private lateinit var blocker: ParticipationAccountDeactivationBlocker

    @BeforeEach
    fun setUp() {
        blocker = ParticipationAccountDeactivationBlocker(participations)
    }

    @Test
    fun activeParticipationInOngoingActivityBlocksAccountDeactivation() {
        whenever(participations.existsActiveInOngoingActivityByUserId(USER_ID, NOW)).thenReturn(true)

        assertThat(blocker.blockingReason(USER_ID, NOW)).contains("存在有效活动报名")
    }

    @Test
    fun noActiveParticipationInOngoingActivityDoesNotBlockAccountDeactivation() {
        whenever(participations.existsActiveInOngoingActivityByUserId(USER_ID, NOW)).thenReturn(false)

        assertThat(blocker.blockingReason(USER_ID, NOW)).isEmpty()
    }

    companion object {
        private const val USER_ID = 202L
        private val NOW = LocalDateTime.parse("2026-08-16T08:00:00")
    }
}
