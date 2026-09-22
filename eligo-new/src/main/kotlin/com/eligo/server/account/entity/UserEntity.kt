package com.eligo.server.account.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

@TableName("users")
class UserEntity {
    @TableId(type = IdType.ASSIGN_ID)
    var id: Long? = null
    var status: Int? = null
    var lastLoginAt: LocalDateTime? = null
    var deactivatedAt: LocalDateTime? = null
    var version: Int? = null
    var createdAt: LocalDateTime? = null
    var updatedAt: LocalDateTime? = null
}
