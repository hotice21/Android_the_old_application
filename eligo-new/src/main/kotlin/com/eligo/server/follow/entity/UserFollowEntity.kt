package com.eligo.server.follow.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

@TableName("user_follows")
class UserFollowEntity {
    @TableId(type = IdType.ASSIGN_ID)
    var id: Long? = null
    var followerUserId: Long? = null
    var followedUserId: Long? = null
    var followedAt: LocalDateTime? = null
}
