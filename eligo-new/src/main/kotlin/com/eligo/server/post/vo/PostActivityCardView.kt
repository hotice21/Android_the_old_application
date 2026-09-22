package com.eligo.server.post.vo

import com.eligo.server.activity.vo.PublicImageView
import java.time.Instant

data class PostActivityCardView(
    val activityId: String?,
    val status: String?,
    val title: String?,
    val cover: PublicImageView?,
    val startsAt: Instant?,
    val endsAt: Instant?
)
