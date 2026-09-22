package com.eligo.server.account.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

@TableName("user_wechat_accounts")
class UserWechatAccountEntity {
    @TableId(type = IdType.ASSIGN_ID)
    var id: Long? = null
    var userId: Long? = null
    var appId: String? = null
    var openidCiphertext: ByteArray? = null
    var openidLookupHash: ByteArray? = null
    var unionidCiphertext: ByteArray? = null
    var unionidLookupHash: ByteArray? = null
    var status: Int? = null
    var boundAt: LocalDateTime? = null
    var unboundAt: LocalDateTime? = null
    var lastLoginAt: LocalDateTime? = null
    var createdAt: LocalDateTime? = null
    var updatedAt: LocalDateTime? = null
}
