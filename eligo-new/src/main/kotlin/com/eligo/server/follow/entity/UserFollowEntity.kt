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
    /** 发起关注的时间 */
    var followedAt: LocalDateTime? = null
    /** 好友请求状态：0 待处理 1 已接受 2 已拒绝 */
    var status: Int? = null
    var acceptedAt: LocalDateTime? = null
    var rejectedAt: LocalDateTime? = null
    var remarkName: String? = null

    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_ACCEPTED = 1
        const val STATUS_REJECTED = 2
    }
}
