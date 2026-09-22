package com.eligo.server.activity.mapper

import java.math.BigDecimal
import java.time.LocalDateTime

data class ActivityManagedDetailRow(
    val activityId: Long? = null,
    val status: Int? = null,
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
    val participantCount: Int? = null,
    val description: String? = null,
    val signupDetails: String? = null,
    val organizerMessage: String? = null,
    val publishedAt: LocalDateTime? = null,
    val version: Int? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
    val registrationGender: Int? = null,
    val organizerPhoneCiphertext: ByteArray? = null,
    val organizerWechatCiphertext: ByteArray? = null,
    val organizerWechatQrFileId: Long? = null,
    val refundPolicy: String? = null,
    val placeName: String? = null
)
