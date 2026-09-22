package com.eligo.server.follow.service

import com.eligo.server.common.api.CursorPage
import com.eligo.server.follow.vo.FollowTargetSummaryView
import com.eligo.server.follow.vo.FollowerSummaryView
import com.eligo.server.follow.vo.PublicUserProfileView
import com.eligo.server.security.UserPrincipal

interface FollowReadService {

    fun listFollowing(
        principal: UserPrincipal?,
        cursor: String?,
        limit: Int,
        type: String?,
        keyword: String?,
        sort: String?
    ): CursorPage<FollowTargetSummaryView>

    fun listFollowers(
        principal: UserPrincipal?,
        cursor: String?,
        limit: Int,
        keyword: String?,
        sort: String?
    ): CursorPage<FollowerSummaryView>

    fun getPublicUserProfile(userId: Long): PublicUserProfileView
}
