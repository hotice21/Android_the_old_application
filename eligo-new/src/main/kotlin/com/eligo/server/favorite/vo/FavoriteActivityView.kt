package com.eligo.server.favorite.vo

import com.eligo.server.activity.vo.PublicActivitySummaryView
import java.time.Instant

data class FavoriteActivityView(
    val favoritedAt: Instant,
    val activity: PublicActivitySummaryView
)
