package com.eligo.server.account.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

@TableName("user_data_request_events")
class UserDataRequestEventEntity {
    @TableId(type = IdType.ASSIGN_ID)
    var id: Long? = null
    var requestId: Long? = null
    var eventType: String? = null
    var fromStatus: Int? = null
    var toStatus: Int? = null
    var actorType: Int? = null
    var actorId: Long? = null
    var resultCode: String? = null
    var detailJson: String? = null
    var traceRequestId: String? = null
    var createdAt: LocalDateTime? = null

    companion object {
        const val ACTOR_USER = 1
        const val ACTOR_ADMIN = 2
        const val ACTOR_SYSTEM = 3
    }
}
