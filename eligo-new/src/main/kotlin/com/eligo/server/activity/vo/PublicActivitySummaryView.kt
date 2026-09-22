package com.eligo.server.activity.vo

import java.math.BigDecimal
import java.time.Instant

data class PublicActivitySummaryView(
    val activityId: String,
    val status: String,
    val title: String,
    val categoryCode: String?,
    val cover: PublicImageView?,
    val owner: ActivityOwnerSummaryView,
    val startsAt: Instant?,
    val endsAt: Instant?,
    val regionCode: String?,
    val addressDetail: String?,
    val latitude: BigDecimal?,
    val longitude: BigDecimal?,
    val capacity: Int?,
    val participantCount: Int?,
    val registrationStatus: String,
    val feeType: String,
    val placeName: String? = null,
    val coordinateSystem: String? = null,
    val distanceMeters: Long? = null,
    val topics: List<String> = emptyList()
)
