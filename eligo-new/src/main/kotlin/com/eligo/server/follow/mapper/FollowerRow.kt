package com.eligo.server.follow.mapper

import java.time.LocalDateTime

data class FollowerRow(
    val followId: Long? = null,
    val userId: Long? = null,
    val nickname: String? = null,
    val avatarFileId: Long? = null,
    val followedAt: LocalDateTime? = null
)
