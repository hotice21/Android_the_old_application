package com.eligo.server.post.mapper

import java.time.LocalDateTime

data class PostDetailRow(
    val postId: Long? = null,
    val authorUserId: Long? = null,
    val authorOrganizationId: Long? = null,
    val authorDisplayName: String? = null,
    val authorAvatarFileId: Long? = null,
    val status: Int? = null,
    val visibility: Int? = null,
    val title: String? = null,
    val content: String? = null,
    val activityId: Long? = null,
    val version: Int? = null,
    val publishedAt: LocalDateTime? = null,
    val hiddenAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
)
