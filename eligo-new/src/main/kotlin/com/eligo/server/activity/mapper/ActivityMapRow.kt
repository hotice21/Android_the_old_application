package com.eligo.server.activity.mapper

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * MyBatis 行映射目标。
 * 提供公开无参构造器 + 可变属性，允许 MyBatis 回退到 setter 注入路径。
 */
class ActivityMapRow {
    constructor()

    constructor(
        activityId: Long? = null,
        title: String? = null,
        categoryCode: String? = null,
        coverFileId: Long? = null,
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
        placeName: String? = null,
        distanceMeters: Long? = null
    ) {
        this.activityId = activityId
        this.title = title
        this.categoryCode = categoryCode
        this.coverFileId = coverFileId
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
        this.placeName = placeName
        this.distanceMeters = distanceMeters
    }

    var activityId: Long? = null
    var title: String? = null
    var categoryCode: String? = null
    var coverFileId: Long? = null
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
    var placeName: String? = null
    var distanceMeters: Long? = null
}
