package com.eligo.server.account.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

@TableName("user_phone_bindings")
class UserPhoneBindingEntity {
    @TableId(type = IdType.ASSIGN_ID)
    var id: Long? = null
    var userId: Long? = null
    var countryCode: String? = null
    var phoneCiphertext: ByteArray? = null
    var phoneLookupHash: ByteArray? = null
    var phoneLastFour: String? = null
    var status: Int? = null
    var boundAt: LocalDateTime? = null
    var unboundAt: LocalDateTime? = null
    var unbindReason: String? = null
    var createdAt: LocalDateTime? = null
    var updatedAt: LocalDateTime? = null
}
