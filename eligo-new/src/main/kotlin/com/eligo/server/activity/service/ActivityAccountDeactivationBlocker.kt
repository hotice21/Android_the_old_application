package com.eligo.server.activity.service

import com.eligo.server.account.service.AccountDeactivationBlocker
import com.eligo.server.activity.mapper.ActivityMapper
import java.time.LocalDateTime
import java.util.Optional
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test")
class ActivityAccountDeactivationBlocker(
    private val activities: ActivityMapper
) : AccountDeactivationBlocker {

    @Transactional(readOnly = true)
    override fun blockingReason(userId: Long, now: LocalDateTime): Optional<String> =
        if (activities.existsOngoingManagedByUserId(userId, now)) {
            Optional.of(BLOCKING_REASON)
        } else {
            Optional.empty()
        }

    companion object {
        private const val BLOCKING_REASON = "存在进行中的发起活动"
    }
}
