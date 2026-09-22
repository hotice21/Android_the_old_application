package com.eligo.server.account.vo

import java.time.Instant

data class SecurityEventView(
    val eventId: String?,
    val type: String?,
    val severity: String?,
    val deviceName: String?,
    val regionCode: String?,
    val occurredAt: Instant?
)
