package com.eligo.server.activity.mapper

import java.math.BigDecimal
import java.time.LocalDateTime

data class ActivityMapRow(
    val activityId: Long? = null,
    val title: String? = null,
    val categoryCode: String? = null,
    val coverFileId: Long? = null,
    val registrationStartsAt: LocalDateTime? = null,
    val registrationEndsAt: LocalDateTime? = null,
    val startsAt: LocalDateTime? = null,
    val endsAt: LocalDateTime? = null,
    val regionCode: String? = null,
    val addressDetail: String? = null,
    val latitude: BigDecimal? = null,
    val longitude: BigDecimal? = null,
    val capacity: Int? = null,
    val participantCount: Int? = null,
    val placeName: String? = null,
    val distanceMeters: Long? = null
)
