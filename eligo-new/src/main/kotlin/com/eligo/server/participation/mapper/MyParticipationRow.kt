package com.eligo.server.participation.mapper

import java.math.BigDecimal
import java.time.LocalDateTime

data class MyParticipationRow(
    val participationId: Long? = null,
    val userId: Long? = null,
    val participationStatus: Int? = null,
    val joinedAt: LocalDateTime? = null,
    val cancelledAt: LocalDateTime? = null,
    val terminatedAt: LocalDateTime? = null,
    val terminationReason: Int? = null,
    val activityId: Long? = null,
    val activityStatus: Int? = null,
    val title: String? = null,
    val categoryCode: String? = null,
    val coverFileId: Long? = null,
    val ownerType: String? = null,
    val ownerId: Long? = null,
    val ownerDisplayName: String? = null,
    val ownerAvatarFileId: Long? = null,
    val registrationStartsAt: LocalDateTime? = null,
    val registrationEndsAt: LocalDateTime? = null,
    val startsAt: LocalDateTime? = null,
    val endsAt: LocalDateTime? = null,
    val regionCode: String? = null,
    val addressDetail: String? = null,
    val latitude: BigDecimal? = null,
    val longitude: BigDecimal? = null,
    val capacity: Int? = null,
    val participantCount: Int? = null
)
