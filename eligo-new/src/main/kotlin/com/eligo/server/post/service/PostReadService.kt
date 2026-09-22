package com.eligo.server.post.service

import com.eligo.server.common.api.CursorPage
import com.eligo.server.post.vo.ManagedPostDetailView
import com.eligo.server.post.vo.ManagedPostSummaryView
import com.eligo.server.post.vo.PublicPostView
import com.eligo.server.security.UserPrincipal

interface PostReadService {
    fun listManagedPosts(
        principal: UserPrincipal?,
        cursor: String?,
        limit: Int,
        status: String?,
        authorType: String?
    ): CursorPage<ManagedPostSummaryView>

    fun getManagedPost(principal: UserPrincipal?, postId: Long): ManagedPostDetailView

    fun getManagedPostForReplay(
        principal: UserPrincipal?,
        postId: Long
    ): ManagedPostDetailView

    fun getPost(principal: UserPrincipal?, postId: Long): PublicPostView

    fun listPublicPosts(cursor: String?, limit: Int): CursorPage<PublicPostView>

    fun listUserPosts(
        principal: UserPrincipal?,
        userId: Long,
        cursor: String?,
        limit: Int
    ): CursorPage<PublicPostView>

    fun listOrganizationPosts(
        principal: UserPrincipal?,
        organizationId: Long,
        cursor: String?,
        limit: Int
    ): CursorPage<PublicPostView>

    fun listFollowingFeed(
        principal: UserPrincipal?,
        cursor: String?,
        limit: Int
    ): CursorPage<PublicPostView>

    fun canReadMedia(principal: UserPrincipal?, fileId: Long): Boolean
}
