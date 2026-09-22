package com.eligo.server.comment.service

import com.eligo.server.comment.vo.ActivityCommentView

data class ActivityCommentCreateOutcome(
    val view: ActivityCommentView,
    val replayed: Boolean
)
