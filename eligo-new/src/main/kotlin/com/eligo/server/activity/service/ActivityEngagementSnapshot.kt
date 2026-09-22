package com.eligo.server.activity.service

import java.time.LocalDateTime

data class ActivityEngagementSnapshot(
    val activityId: Long,
    val status: Int,
    val endsAt: LocalDateTime?,
    val ownerUserId: Long?,
    val ownerOrganizationId: Long?
)
