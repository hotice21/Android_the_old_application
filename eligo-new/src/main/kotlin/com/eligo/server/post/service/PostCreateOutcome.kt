package com.eligo.server.post.service

import com.eligo.server.post.vo.ManagedPostDetailView

data class PostCreateOutcome(
    val view: ManagedPostDetailView,
    val replayed: Boolean
)
