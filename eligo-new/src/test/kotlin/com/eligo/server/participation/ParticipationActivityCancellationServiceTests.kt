package com.eligo.server.participation

import java.util.function.Function

import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.participation.mapper.ActivityParticipationMapper
import com.eligo.server.participation.service.ParticipationActivityCancellationService
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ParticipationActivityCancellationServiceTests {

    private val participations = mock<ActivityParticipationMapper>()
    private val service = ParticipationActivityCancellationService(participations)

    @Test
    fun terminatesExpectedActiveParticipations() {
        whenever(participations.terminateActiveForActivityCancellation(ACTIVITY_ID, NOW))
            .thenReturn(2)

        service.terminateActiveParticipations(ACTIVITY_ID, 2, NOW)

        verify(participations)
            .terminateActiveForActivityCancellation(ACTIVITY_ID, NOW)
    }

    @Test
    fun rejectsUnexpectedTerminationCount() {
        whenever(participations.terminateActiveForActivityCancellation(ACTIVITY_ID, NOW))
            .thenReturn(1)

        assertThatThrownBy {
            service.terminateActiveParticipations(ACTIVITY_ID, 2, NOW)
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting(Function {  exception -> (exception as BusinessException).errorCode  })
            .isEqualTo(CommonErrorCode.CONFLICT)
    }

    companion object {
        private const val ACTIVITY_ID = 301L
        private val NOW = LocalDateTime.parse("2026-08-16T01:00:00")
    }
}
