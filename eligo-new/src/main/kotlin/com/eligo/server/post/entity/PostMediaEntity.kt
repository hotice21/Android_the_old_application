package com.eligo.server.post.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

@TableName("post_media")
class PostMediaEntity {
    @TableId(type = IdType.ASSIGN_ID)
    var id: Long? = null
    var postId: Long? = null
    var fileId: Long? = null
    var sortOrder: Int? = null
    var createdAt: LocalDateTime? = null
}
