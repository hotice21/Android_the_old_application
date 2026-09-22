package com.eligo.server.activity.vo

import java.math.BigDecimal
import java.time.Instant

data class ManagedActivitySummaryView(
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
    val feeType: String,
    val version: Int?,
    val updatedAt: Instant?,
    val placeName: String? = null,
    val coordinateSystem: String? = null,
    val topics: List<String> = emptyList()
) {
    constructor(
        activityId: String,
        status: String,
        title: String,
        categoryCode: String?,
        cover: PublicImageView?,
        owner: ActivityOwnerSummaryView,
        startsAt: Instant?,
        endsAt: Instant?,
        regionCode: String?,
        addressDetail: String?,
        capacity: Int?,
        participantCount: Int?,
        feeType: String,
        version: Int?,
        updatedAt: Instant?
    ) : this(
        activityId, status, title, categoryCode, cover, owner,
        startsAt, endsAt, regionCode, addressDetail, null, null,
        capacity, participantCount, feeType, version, updatedAt,
        null, null, emptyList()
    )
}
