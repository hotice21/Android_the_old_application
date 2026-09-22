package com.eligo.server.comment.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

@TableName("activity_comments")
class ActivityCommentEntity {
    @TableId(type = IdType.ASSIGN_ID)
    var id: Long? = null
    var activityId: Long? = null
    var authorUserId: Long? = null
    var parentCommentId: Long? = null
    var status: Int? = null
    var content: String? = null
    var idempotencyKey: String? = null
    var requestFingerprint: String? = null
    var createdAt: LocalDateTime? = null
    var deletedAt: LocalDateTime? = null

    companion object {
        const val STATUS_ACTIVE = 1
        const val STATUS_DELETED = 2
    }
}
