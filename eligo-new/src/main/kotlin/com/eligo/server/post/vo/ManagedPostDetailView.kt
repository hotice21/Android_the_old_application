package com.eligo.server.post.vo

import com.eligo.server.activity.vo.PublicImageView
import java.time.Instant

data class ManagedPostDetailView(
    val postId: String?,
    val author: PostAuthorSummaryView?,
    val status: String?,
    val visibility: String?,
    val title: String?,
    val content: String?,
    val media: List<PublicImageView>?,
    val activity: PostActivityCardView?,
    val version: Int,
    val publishedAt: Instant?,
    val updatedAt: Instant?,
    val createdAt: Instant?,
    val hiddenAt: Instant?
)
