package com.eligo.server.activity.mapper

import java.math.BigDecimal
import java.time.LocalDateTime

data class ActivityManagedRow(
    val activityId: Long? = null,
    val status: Int? = null,
    val title: String? = null,
    val categoryCode: String? = null,
    val coverFileId: Long? = null,
    val ownerType: String? = null,
    val ownerId: Long? = null,
    val ownerDisplayName: String? = null,
    val ownerAvatarFileId: Long? = null,
    val startsAt: LocalDateTime? = null,
    val endsAt: LocalDateTime? = null,
    val regionCode: String? = null,
    val addressDetail: String? = null,
    val latitude: BigDecimal? = null,
    val longitude: BigDecimal? = null,
    val capacity: Int? = null,
    val participantCount: Int? = null,
    val version: Int? = null,
    val updatedAt: LocalDateTime? = null,
    val placeName: String? = null
)
