package com.eligo.server.post.service

import com.eligo.server.post.dto.PostDraftCreateRequest
import com.eligo.server.post.dto.PostDraftReplaceRequest
import com.eligo.server.post.vo.ManagedPostDetailView
import com.eligo.server.security.UserPrincipal

interface PostCommandService {
    fun createPersonal(
        principal: UserPrincipal?,
        request: PostDraftCreateRequest?,
        idempotencyKey: String
    ): PostCreateOutcome

    fun createOrganization(
        principal: UserPrincipal?,
        organizationId: Long,
        request: PostDraftCreateRequest?,
        idempotencyKey: String
    ): PostCreateOutcome

    fun replacePersonal(
        principal: UserPrincipal?,
        postId: Long,
        request: PostDraftReplaceRequest?
    ): ManagedPostDetailView

    fun replaceOrganization(
        principal: UserPrincipal?,
        organizationId: Long,
        postId: Long,
        request: PostDraftReplaceRequest?
    ): ManagedPostDetailView

    fun publish(principal: UserPrincipal?, postId: Long): ManagedPostDetailView

    fun delete(principal: UserPrincipal?, postId: Long)
}
