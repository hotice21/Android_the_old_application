package com.eligo.server.activity.vo

import java.math.BigDecimal
import java.time.Instant

data class ManagedActivityDetailView(
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
    val description: String?,
    val media: List<PublicImageView> = emptyList(),
    val registrationStartsAt: Instant?,
    val registrationEndsAt: Instant?,
    val signupDetails: String?,
    val organizerMessage: String?,
    val createdAt: Instant?,
    val publishedAt: Instant?,
    val registrationGender: String?,
    val organizerPhone: String?,
    val organizerWechat: String?,
    val organizerWechatQr: PublicImageView?,
    val refundPolicy: String? = null,
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
        updatedAt: Instant?,
        description: String?,
        media: List<PublicImageView>,
        registrationStartsAt: Instant?,
        registrationEndsAt: Instant?,
        signupDetails: String?,
        organizerMessage: String?,
        createdAt: Instant?,
        publishedAt: Instant?
    ) : this(
        activityId, status, title, categoryCode, cover, owner,
        startsAt, endsAt, regionCode, addressDetail, null, null,
        capacity, participantCount, feeType, version, updatedAt,
        description, media, registrationStartsAt, registrationEndsAt,
        signupDetails, organizerMessage, createdAt, publishedAt,
        "UNLIMITED", null, null, null, null, null, null, emptyList()
    )
}
