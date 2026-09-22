package com.eligo.server.favorite.vo

import java.time.Instant

data class FavoriteStateView(val favorited: Boolean, val favoritedAt: Instant?)
