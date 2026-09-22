package com.eligo.server.activity.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.math.BigDecimal
import java.time.LocalDateTime

@TableName("activities")
class ActivityEntity {
    @TableId(type = IdType.ASSIGN_ID)
    var id: Long? = null
    var ownerUserId: Long? = null
    var ownerOrganizationId: Long? = null
    var operatorUserId: Long? = null
    var createIdempotencyScope: String? = null
    var createIdempotencyKey: String? = null
    var createIdempotencyFingerprint: String? = null
    var status: Int? = null
    var title: String? = null
    var categoryCode: String? = null
    var description: String? = null
    var coverFileId: Long? = null
    var registrationStartsAt: LocalDateTime? = null
    var registrationEndsAt: LocalDateTime? = null
    var startsAt: LocalDateTime? = null
    var endsAt: LocalDateTime? = null
    var regionCode: String? = null
    var addressDetail: String? = null
    var placeName: String? = null
    var latitude: BigDecimal? = null
    var longitude: BigDecimal? = null
    var capacity: Int? = null
    var participantCount: Int? = null
    var registrationGender: Int? = null
    var organizerPhoneCiphertext: ByteArray? = null
    var organizerWechatCiphertext: ByteArray? = null
    var organizerWechatQrFileId: Long? = null
    var signupDetails: String? = null
    var organizerMessage: String? = null
    var publishedAt: LocalDateTime? = null
    var version: Int? = null
    var createdAt: LocalDateTime? = null
    var updatedAt: LocalDateTime? = null
}
