package com.eligo.server.follow.vo

import com.eligo.server.activity.vo.PublicImageView
import java.time.Instant

data class FollowTargetSummaryView(
    val followId: String,
    val targetType: String,
    val targetId: String,
    val displayName: String,
    val avatar: PublicImageView?,
    val followedAt: Instant
)
