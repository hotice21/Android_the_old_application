package com.eligo.server.account.vo

import java.time.Instant

data class DeactivationView(
    val requestId: String?,
    val status: String?,
    val requestedAt: Instant?,
    val executeAfter: Instant?,
    val canCancel: Boolean
)
