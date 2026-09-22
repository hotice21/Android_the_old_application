package com.eligo.server.profile.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

@TableName("interest_tags")
class InterestTagEntity {
    @TableId(type = IdType.ASSIGN_ID)
    var id: Long? = null
    var tagCode: String? = null
    var tagName: String? = null
    var sortOrder: Int? = null
    var status: Int? = null
    var createdAt: LocalDateTime? = null
    var updatedAt: LocalDateTime? = null
}
