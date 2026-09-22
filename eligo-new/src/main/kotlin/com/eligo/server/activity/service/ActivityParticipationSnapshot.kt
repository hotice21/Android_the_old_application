package com.eligo.server.activity.service

import java.time.LocalDateTime

data class ActivityParticipationSnapshot(
    val activityId: Long,
    val status: Int?,
    val ownerUserId: Long?,
    val ownerOrganizationId: Long?,
    val registrationStartsAt: LocalDateTime?,
    val registrationEndsAt: LocalDateTime?,
    val startsAt: LocalDateTime?,
    val endsAt: LocalDateTime?,
    val capacity: Int?,
    val participantCount: Int?,
    val registrationGender: Int? = 1
)
