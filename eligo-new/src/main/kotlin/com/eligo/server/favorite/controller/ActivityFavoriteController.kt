package com.eligo.server.favorite.controller

import com.eligo.server.common.api.CursorPage
import com.eligo.server.common.api.Result
import com.eligo.server.favorite.service.ActivityFavoriteService
import com.eligo.server.favorite.vo.FavoriteActivityView
import com.eligo.server.favorite.vo.FavoriteStateView
import com.eligo.server.security.UserPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ActivityFavoriteController(private val favorites: ActivityFavoriteService) {

    @PutMapping("/api/v1/activities/{activityId}/favorite")
    fun favorite(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @PathVariable activityId: Long
    ): Result<FavoriteStateView> =
        Result.success(favorites.favorite(principal!!, activityId))

    @GetMapping("/api/v1/activities/{activityId}/favorite")
    fun getState(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @PathVariable activityId: Long
    ): Result<FavoriteStateView> =
        Result.success(favorites.getState(principal!!, activityId))

    @DeleteMapping("/api/v1/activities/{activityId}/favorite")
    fun unfavorite(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @PathVariable activityId: Long
    ): Result<FavoriteStateView> =
        Result.success(favorites.unfavorite(principal!!, activityId))

    @GetMapping("/api/v1/users/me/favorite-activities")
    fun listMine(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ): Result<CursorPage<FavoriteActivityView>> =
        Result.success(favorites.listMine(principal!!, cursor, limit))
}
