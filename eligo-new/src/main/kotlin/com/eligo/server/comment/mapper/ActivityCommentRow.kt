package com.eligo.server.comment.mapper

import java.time.LocalDateTime

data class ActivityCommentRow(
    val commentId: Long? = null,
    val activityId: Long? = null,
    val authorUserId: Long? = null,
    val parentCommentId: Long? = null,
    val status: Int? = null,
    val content: String? = null,
    val authorNickname: String? = null,
    val authorAvatarFileId: Long? = null,
    val createdAt: LocalDateTime? = null,
    val deletedAt: LocalDateTime? = null
)
