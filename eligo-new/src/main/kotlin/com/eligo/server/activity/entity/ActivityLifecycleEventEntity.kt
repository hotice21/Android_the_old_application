package com.eligo.server.activity.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

@TableName("activity_lifecycle_events")
class ActivityLifecycleEventEntity {
    @TableId(type = IdType.ASSIGN_ID)
    var id: Long? = null
    var activityId: Long? = null
    var fromStatus: Int? = null
    var toStatus: Int? = null
    var operatorUserId: Long? = null
    var createdAt: LocalDateTime? = null
}
