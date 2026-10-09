package com.eligo.server.contentfilter.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

@TableName("sensitive_words")
class SensitiveWordEntity {
    @TableId(type = IdType.ASSIGN_ID)
    var id: Long? = null
    var categoryCode: String? = null
    var wordPattern: String? = null
    /** 1 替换 2 拦截 3 标记 */
    var severity: Int? = null
    var replacementText: String? = null
    var status: Int? = null
    var hitCount: Int? = null
    var version: Int? = null
    var createdAt: LocalDateTime? = null
    var updatedAt: LocalDateTime? = null
}
