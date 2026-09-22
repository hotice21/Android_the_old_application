package com.eligo.server.participation.vo

import com.eligo.server.activity.vo.PublicActivitySummaryView
import java.time.Instant

data class MyParticipationView(
    val participationId: String?,
    val userId: String?,
    val status: String?,
    val joinedAt: Instant?,
    val cancelledAt: Instant?,
    val terminatedAt: Instant?,
    val terminationReason: String?,
    val activity: PublicActivitySummaryView?
)
