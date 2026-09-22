package com.eligo.server.account.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

@TableName("account_security_events")
class AccountSecurityEventEntity {
    @TableId(type = IdType.ASSIGN_ID)
    var id: Long? = null
    var userId: Long? = null
    var deviceId: Long? = null
    var sessionId: Long? = null
    var eventType: String? = null
    var severity: Int? = null
    var regionCode: String? = null
    var ipLookupHash: ByteArray? = null
    var detailJson: String? = null
    var notifiedAt: LocalDateTime? = null
    var occurredAt: LocalDateTime? = null
    var createdAt: LocalDateTime? = null
}
