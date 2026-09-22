package com.eligo.server.recommendation.service

import com.eligo.server.common.api.CursorPage
import com.eligo.server.post.vo.PublicPostView
import com.eligo.server.security.UserPrincipal

interface RecommendedFeedService {

    fun list(
        principal: UserPrincipal?,
        cursor: String?,
        limit: Int
    ): CursorPage<PublicPostView>
}
