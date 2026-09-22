package com.eligo.server.participation.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

@TableName("activity_participations")
class ActivityParticipationEntity {
    @TableId(type = IdType.ASSIGN_ID)
    var id: Long? = null
    var activityId: Long? = null
    var userId: Long? = null
    var status: Int? = null
    var joinedAt: LocalDateTime? = null
    var cancelledAt: LocalDateTime? = null
    var terminatedAt: LocalDateTime? = null
    var terminationReason: Int? = null
    var version: Int? = null
    var createdAt: LocalDateTime? = null
    var updatedAt: LocalDateTime? = null
}
