package com.eligo.server.activity.mapper

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * MyBatis @ConstructorArgs 映射目标。
 * Kotlin data class 的全默认值主构造器在某些 JVM 场景下
 * 与 MyBatis 的 getDeclaredConstructor 类型匹配存在偏差，
 * 这里额外提供一个公开无参构造器 + 可变属性，允许 MyBatis
 * 回退到 setter 注入路径。
 */
class ActivityPublicRow {
    constructor()

    constructor(
        activityId: Long? = null,
        status: Int? = null,
        title: String? = null,
        categoryCode: String? = null,
        coverFileId: Long? = null,
        ownerType: String? = null,
        ownerId: Long? = null,
        ownerDisplayName: String? = null,
        ownerAvatarFileId: Long? = null,
        registrationStartsAt: LocalDateTime? = null,
        registrationEndsAt: LocalDateTime? = null,
        startsAt: LocalDateTime? = null,
        endsAt: LocalDateTime? = null,
        regionCode: String? = null,
        addressDetail: String? = null,
        latitude: BigDecimal? = null,
        longitude: BigDecimal? = null,
        capacity: Int? = null,
        participantCount: Int? = null,
        description: String? = null,
        signupDetails: String? = null,
        organizerMessage: String? = null,
        publishedAt: LocalDateTime? = null,
        createdAt: LocalDateTime? = null,
        updatedAt: LocalDateTime? = null,
        registrationGender: Int? = null,
        organizerPhoneCiphertext: ByteArray? = null,
        organizerWechatCiphertext: ByteArray? = null,
        organizerWechatQrFileId: Long? = null,
        refundPolicy: String? = null,
        placeName: String? = null,
        distanceMeters: Long? = null
    ) {
        this.activityId = activityId
        this.status = status
        this.title = title
        this.categoryCode = categoryCode
        this.coverFileId = coverFileId
        this.ownerType = ownerType
        this.ownerId = ownerId
        this.ownerDisplayName = ownerDisplayName
        this.ownerAvatarFileId = ownerAvatarFileId
        this.registrationStartsAt = registrationStartsAt
        this.registrationEndsAt = registrationEndsAt
        this.startsAt = startsAt
        this.endsAt = endsAt
        this.regionCode = regionCode
        this.addressDetail = addressDetail
        this.latitude = latitude
        this.longitude = longitude
        this.capacity = capacity
        this.participantCount = participantCount
        this.description = description
        this.signupDetails = signupDetails
        this.organizerMessage = organizerMessage
        this.publishedAt = publishedAt
        this.createdAt = createdAt
        this.updatedAt = updatedAt
        this.registrationGender = registrationGender
        this.organizerPhoneCiphertext = organizerPhoneCiphertext
        this.organizerWechatCiphertext = organizerWechatCiphertext
        this.organizerWechatQrFileId = organizerWechatQrFileId
        this.refundPolicy = refundPolicy
        this.placeName = placeName
        this.distanceMeters = distanceMeters
    }

    var activityId: Long? = null
    var status: Int? = null
    var title: String? = null
    var categoryCode: String? = null
    var coverFileId: Long? = null
    var ownerType: String? = null
    var ownerId: Long? = null
    var ownerDisplayName: String? = null
    var ownerAvatarFileId: Long? = null
    var registrationStartsAt: LocalDateTime? = null
    var registrationEndsAt: LocalDateTime? = null
    var startsAt: LocalDateTime? = null
    var endsAt: LocalDateTime? = null
    var regionCode: String? = null
    var addressDetail: String? = null
    var latitude: BigDecimal? = null
    var longitude: BigDecimal? = null
    var capacity: Int? = null
    var participantCount: Int? = null
    var description: String? = null
    var signupDetails: String? = null
    var organizerMessage: String? = null
    var publishedAt: LocalDateTime? = null
    var createdAt: LocalDateTime? = null
    var updatedAt: LocalDateTime? = null
    var registrationGender: Int? = null
    var organizerPhoneCiphertext: ByteArray? = null
    var organizerWechatCiphertext: ByteArray? = null
    var organizerWechatQrFileId: Long? = null
    var refundPolicy: String? = null
    var placeName: String? = null
    var distanceMeters: Long? = null
}
