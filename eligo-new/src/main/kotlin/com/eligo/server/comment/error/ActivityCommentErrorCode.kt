package com.eligo.server.comment.error

import com.eligo.server.common.error.ErrorCode
import org.springframework.http.HttpStatus

enum class ActivityCommentErrorCode(
    override val code: Int,
    override val message: String,
    override val httpStatus: HttpStatus
) : ErrorCode {
    IDEMPOTENCY_KEY_CONFLICT(11711, "评论幂等键已用于其他请求", HttpStatus.CONFLICT)
}
