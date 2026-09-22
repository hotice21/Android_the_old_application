package com.eligo.server.comment.dto

import jakarta.validation.constraints.NotBlank

data class ActivityCommentCreateRequest(
    @field:NotBlank val content: String?,
    val parentCommentId: Long?
)
