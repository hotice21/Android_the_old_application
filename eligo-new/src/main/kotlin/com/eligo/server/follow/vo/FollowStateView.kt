package com.eligo.server.follow.vo

import java.time.Instant

data class FollowStateView(
    val following: Boolean,
    val followedAt: Instant?
)
