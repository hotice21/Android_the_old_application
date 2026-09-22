package com.eligo.server.account.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

@TableName("user_devices")
class UserDeviceEntity {
    @TableId(type = IdType.ASSIGN_ID)
    var id: Long? = null
    var userId: Long? = null
    var installationIdHash: ByteArray? = null
    var deviceName: String? = null
    var platformCode: String? = null
    var osVersion: String? = null
    var appVersion: String? = null
    var regionCode: String? = null
    var status: Int? = null
    var firstSeenAt: LocalDateTime? = null
    var lastSeenAt: LocalDateTime? = null
    var statusChangedAt: LocalDateTime? = null
    var createdAt: LocalDateTime? = null
    var updatedAt: LocalDateTime? = null
}
