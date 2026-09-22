package com.eligo.server.participation.service

import com.eligo.server.account.service.AccountDeactivationBlocker
import com.eligo.server.participation.mapper.ActivityParticipationMapper
import java.time.LocalDateTime
import java.util.Optional
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test")
class ParticipationAccountDeactivationBlocker(
    private val participations: ActivityParticipationMapper
) : AccountDeactivationBlocker {

    override
    @Transactional(readOnly = true)
    fun blockingReason(userId: Long, now: LocalDateTime): Optional<String> =
        if (participations.existsActiveInOngoingActivityByUserId(userId, now)) {
            Optional.of(BLOCKING_REASON)
        } else {
            Optional.empty()
        }

    companion object {
        private const val BLOCKING_REASON = "存在有效活动报名"
    }
}
