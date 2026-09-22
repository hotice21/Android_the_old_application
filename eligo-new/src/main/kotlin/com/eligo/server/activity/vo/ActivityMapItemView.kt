package com.eligo.server.activity.vo

import java.math.BigDecimal
import java.time.Instant

data class ActivityMapItemView(
    val activityId: String,
    val title: String,
    val categoryCode: String?,
    val cover: PublicImageView?,
    val startsAt: Instant?,
    val endsAt: Instant?,
    val regionCode: String?,
    val addressDetail: String?,
    val latitude: BigDecimal?,
    val longitude: BigDecimal?,
    val registrationStatus: String,
    val feeType: String,
    val placeName: String? = null,
    val coordinateSystem: String? = null,
    val distanceMeters: Long? = null,
    val topics: List<String> = emptyList()
)
