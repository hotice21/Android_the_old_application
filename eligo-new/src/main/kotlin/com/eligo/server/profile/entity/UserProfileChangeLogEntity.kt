package com.eligo.server.profile.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

@TableName("user_profile_change_logs")
class UserProfileChangeLogEntity {
    @TableId(type = IdType.ASSIGN_ID)
    var id: Long? = null
    var userId: Long? = null
    var actorUserId: Long? = null
    var actorAdminId: Long? = null
    var fieldCode: String? = null
    var reason: String? = null
    var requestId: String? = null
    var oldValueCiphertext: ByteArray? = null
    var newValueCiphertext: ByteArray? = null
    var sourceType: Int? = null
    var createdAt: LocalDateTime? = null
}
