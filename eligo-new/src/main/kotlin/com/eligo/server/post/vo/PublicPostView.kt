package com.eligo.server.post.vo

import com.eligo.server.activity.vo.PublicImageView
import java.time.Instant

data class PublicPostView(
    val postId: String?,
    val author: PostAuthorSummaryView?,
    val visibility: String?,
    val title: String?,
    val content: String?,
    val media: List<PublicImageView>?,
    val activity: PostActivityCardView?,
    val publishedAt: Instant?
)
