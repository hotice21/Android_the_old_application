package com.eligo.server.activity.vo

import java.math.BigDecimal
import java.time.Instant

data class PublicActivityDetailView(
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
    val description: String?,
    val media: List<PublicImageView> = emptyList(),
    val registrationStartsAt: Instant?,
    val registrationEndsAt: Instant?,
    val signupDetails: String?,
    val organizerMessage: String?,
    val myParticipationStatus: String?,
    val viewerIsOwner: Boolean,
    val publishedAt: Instant?,
    val updatedAt: Instant?,
    val registrationGender: String?,
    val organizerPhone: String?,
    val organizerWechat: String?,
    val organizerWechatQr: PublicImageView?,
    val refundPolicy: String? = null,
    val placeName: String? = null,
    val coordinateSystem: String? = null,
    val distanceMeters: Long? = null,
    val topics: List<String> = emptyList()
)
