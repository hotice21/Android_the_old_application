package com.eligo.server.follow.mapper

import java.time.LocalDateTime

data class FollowTargetRow(
    val followId: Long? = null,
    val targetType: String? = null,
    val targetId: Long? = null,
    val displayName: String? = null,
    val avatarFileId: Long? = null,
    val followedAt: LocalDateTime? = null
)
