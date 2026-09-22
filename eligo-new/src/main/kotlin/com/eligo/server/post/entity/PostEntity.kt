package com.eligo.server.post.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

@TableName("posts")
class PostEntity {
    @TableId(type = IdType.ASSIGN_ID)
    var id: Long? = null
    var authorUserId: Long? = null
    var authorOrganizationId: Long? = null
    var operatorUserId: Long? = null
    var status: Int? = null
    var visibility: Int? = null
    var title: String? = null
    var content: String? = null
    var activityId: Long? = null
    var createIdempotencyScope: String? = null
    var createIdempotencyKey: String? = null
    var createRequestFingerprint: String? = null
    var createIdempotencyExpiresAt: LocalDateTime? = null
    var publishedAt: LocalDateTime? = null
    var hiddenAt: LocalDateTime? = null
    var deletedAt: LocalDateTime? = null
    var version: Int? = null
    var createdAt: LocalDateTime? = null
    var updatedAt: LocalDateTime? = null

    companion object {
        const val STATUS_DRAFT = 1
        const val STATUS_PUBLISHED = 2
        const val STATUS_DELETED = 3
        const val STATUS_HIDDEN = 4
        const val VISIBILITY_PUBLIC = 1
        const val VISIBILITY_FOLLOWERS_ONLY = 2
        const val VISIBILITY_PRIVATE = 3
    }
}
