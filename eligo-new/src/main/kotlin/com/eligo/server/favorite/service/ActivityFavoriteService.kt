package com.eligo.server.favorite.service

import com.eligo.server.common.api.CursorPage
import com.eligo.server.favorite.vo.FavoriteActivityView
import com.eligo.server.favorite.vo.FavoriteStateView
import com.eligo.server.security.UserPrincipal

interface ActivityFavoriteService {
    fun favorite(principal: UserPrincipal, activityId: Long): FavoriteStateView
    fun getState(principal: UserPrincipal, activityId: Long): FavoriteStateView
    fun unfavorite(principal: UserPrincipal, activityId: Long): FavoriteStateView
    fun listMine(principal: UserPrincipal, cursor: String?, limit: Int): CursorPage<FavoriteActivityView>
}
