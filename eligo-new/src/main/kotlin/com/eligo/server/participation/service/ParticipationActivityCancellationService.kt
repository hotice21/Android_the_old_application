package com.eligo.server.participation.service

import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.participation.mapper.ActivityParticipationMapper
import java.time.LocalDateTime
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test")
class ParticipationActivityCancellationService(
    private val participations: ActivityParticipationMapper
) {

    @Transactional(propagation = Propagation.MANDATORY)
    fun terminateActiveParticipations(
        activityId: Long,
        expectedCount: Int,
        now: LocalDateTime
    ) {
        val terminated = participations.terminateActiveForActivityCancellation(activityId, now)
        if (terminated != expectedCount) {
            throw BusinessException(CommonErrorCode.CONFLICT)
        }
    }
}
