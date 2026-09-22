package com.eligo.server.post.vo

import com.eligo.server.activity.vo.PublicImageView
import java.time.Instant

data class PostAuthorSummaryView(
    val authorType: String?,
    val authorId: String?,
    val displayName: String?,
    val avatar: PublicImageView?
)
