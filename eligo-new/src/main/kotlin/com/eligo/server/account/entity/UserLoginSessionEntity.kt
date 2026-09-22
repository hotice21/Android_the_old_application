package com.eligo.server.account.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

@TableName("user_login_sessions")
class UserLoginSessionEntity {
    @TableId(type = IdType.ASSIGN_ID)
    var id: Long? = null
    var userId: Long? = null
    var deviceId: Long? = null
    var sessionKey: String? = null
    var refreshTokenHash: ByteArray? = null
    var refreshTokenVersion: Int? = null
    var status: Int? = null
    var expiresAt: LocalDateTime? = null
    var lastUsedAt: LocalDateTime? = null
    var revokedAt: LocalDateTime? = null
    var revokeReason: String? = null
    var version: Int? = null
    var createdAt: LocalDateTime? = null
    var updatedAt: LocalDateTime? = null
}
