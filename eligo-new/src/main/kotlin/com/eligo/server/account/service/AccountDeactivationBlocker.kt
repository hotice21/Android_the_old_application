package com.eligo.server.account.service

import java.time.LocalDateTime
import java.util.Optional

@FunctionalInterface
interface AccountDeactivationBlocker {
    fun blockingReason(userId: Long, now: LocalDateTime): Optional<String>
}
