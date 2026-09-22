package com.eligo.server.post.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

@TableName("post_status_events")
class PostStatusEventEntity {
    @TableId(type = IdType.ASSIGN_ID)
    var id: Long? = null
    var postId: Long? = null
    var fromStatus: Int? = null
    var toStatus: Int? = null
    var actorType: Int? = null
    var actorUserId: Long? = null
    var reasonCode: String? = null
    var createdAt: LocalDateTime? = null

    companion object {
        const val ACTOR_USER = 1
        const val ACTOR_SYSTEM = 2
    }
}
