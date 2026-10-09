package com.eligo.server.block.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

@TableName("user_blocks")
class UserBlockEntity {
    @TableId(type = IdType.ASSIGN_ID)
    var id: Long? = null
    var blockerUserId: Long? = null
    var blockedUserId: Long? = null
    var blockedAt: LocalDateTime? = null
    var reason: String? = null
}
