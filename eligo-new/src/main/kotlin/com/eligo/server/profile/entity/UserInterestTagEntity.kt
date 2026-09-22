package com.eligo.server.profile.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

@TableName("user_interest_tags")
class UserInterestTagEntity {
    @TableId(type = IdType.ASSIGN_ID)
    var id: Long? = null
    var userId: Long? = null
    var interestTagId: Long? = null
    var selectedAt: LocalDateTime? = null
}
