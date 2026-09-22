package com.eligo.server.comment.vo

import java.time.Instant

data class ActivityCommentView(
    val commentId: String,
    val activityId: String,
    val parentCommentId: String?,
    val deleted: Boolean,
    val content: String?,
    val author: ActivityCommentAuthorView?,
    val createdAt: Instant,
    val deletedAt: Instant?
)
