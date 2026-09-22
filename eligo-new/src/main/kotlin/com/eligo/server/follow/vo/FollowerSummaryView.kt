package com.eligo.server.follow.vo

import com.eligo.server.activity.vo.PublicImageView
import java.time.Instant

data class FollowerSummaryView(
    val followId: String,
    val userId: String,
    val nickname: String,
    val avatar: PublicImageView?,
    val followedAt: Instant
)
