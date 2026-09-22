package com.eligo.server.participation.vo

import java.time.Instant

data class ActivityParticipationView(
    val participationId: String?,
    val activityId: String?,
    val userId: String?,
    val status: String?,
    val joinedAt: Instant?,
    val cancelledAt: Instant?,
    val terminatedAt: Instant?,
    val terminationReason: String?,
    val participantCount: Int?
)
