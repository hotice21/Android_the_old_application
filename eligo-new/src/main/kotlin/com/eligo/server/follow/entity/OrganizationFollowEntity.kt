package com.eligo.server.follow.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

@TableName("organization_follows")
class OrganizationFollowEntity {
    @TableId(type = IdType.ASSIGN_ID)
    var id: Long? = null
    var followerUserId: Long? = null
    var organizationId: Long? = null
    var followedAt: LocalDateTime? = null
}
