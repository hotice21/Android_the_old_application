package com.eligo.server.follow.error

import com.eligo.server.common.error.ErrorCode
import org.springframework.http.HttpStatus

enum class FollowErrorCode(
    override val code: Int,
    override val message: String,
    override val httpStatus: HttpStatus
) : ErrorCode {
    CANNOT_FOLLOW_SELF(11801, "不能关注自己", HttpStatus.CONFLICT)
}
