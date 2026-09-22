package com.eligo.server.comment.vo

import com.eligo.server.activity.vo.PublicImageView

data class ActivityCommentAuthorView(
    val userId: String,
    val nickname: String,
    val avatar: PublicImageView?
)
