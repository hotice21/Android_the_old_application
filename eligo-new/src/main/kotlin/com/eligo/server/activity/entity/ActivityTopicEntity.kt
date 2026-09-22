package com.eligo.server.activity.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

@TableName("activity_topics")
class ActivityTopicEntity {
    @TableId(type = IdType.ASSIGN_ID)
    var id: Long? = null
    var normalizedName: String? = null
    var displayName: String? = null
    var createdAt: LocalDateTime? = null
}
