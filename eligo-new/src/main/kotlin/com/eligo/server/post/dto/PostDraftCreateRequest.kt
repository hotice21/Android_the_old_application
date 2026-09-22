package com.eligo.server.post.dto

data class PostDraftCreateRequest(
    val title: String? = null,
    val content: String? = null,
    val visibility: String? = null,
    val activityId: Long? = null,
    val mediaFileIds: List<Long?>? = null
)
