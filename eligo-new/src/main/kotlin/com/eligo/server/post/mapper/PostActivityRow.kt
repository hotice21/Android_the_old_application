package com.eligo.server.post.mapper

import java.time.LocalDateTime

data class PostActivityRow(
    val activityId: Long? = null,
    val status: Int? = null,
    val title: String? = null,
    val coverFileId: Long? = null,
    val startsAt: LocalDateTime? = null,
    val endsAt: LocalDateTime? = null
)
