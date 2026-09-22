package com.eligo.server.activity.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

@TableName("activity_media")
class ActivityMediaEntity {
    @TableId(type = IdType.ASSIGN_ID)
    var id: Long? = null
    var activityId: Long? = null
    var fileId: Long? = null
    var sortOrder: Int? = null
    var createdAt: LocalDateTime? = null
}
