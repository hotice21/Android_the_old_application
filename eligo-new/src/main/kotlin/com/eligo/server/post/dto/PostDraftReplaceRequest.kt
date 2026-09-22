package com.eligo.server.post.dto

data class PostDraftReplaceRequest(
    val version: Int? = null,
    val title: String? = null,
    val content: String? = null,
    val visibility: String? = null,
    val activityId: Long? = null,
    val mediaFileIds: List<Long?>? = null
)
