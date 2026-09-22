package com.eligo.server.favorite.mapper

import java.time.LocalDateTime

data class ActivityFavoriteRow(
    val favoriteId: Long? = null,
    val activityId: Long? = null,
    val favoritedAt: LocalDateTime? = null
)
