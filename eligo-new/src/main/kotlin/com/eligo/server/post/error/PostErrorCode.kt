package com.eligo.server.post.error

import com.eligo.server.common.error.ErrorCode
import org.springframework.http.HttpStatus

enum class PostErrorCode(
    override val code: Int,
    override val message: String,
    override val httpStatus: HttpStatus
) : ErrorCode {
    IDEMPOTENCY_KEY_CONFLICT(11901, "动态创建幂等键冲突", HttpStatus.CONFLICT),
    STATUS_CONFLICT(11902, "动态状态不允许当前操作", HttpStatus.CONFLICT),
    VERSION_CONFLICT(11903, "动态版本已发生变化", HttpStatus.CONFLICT),
    IDEMPOTENCY_RESULT_DELETED(11904, "幂等请求对应的动态草稿已删除", HttpStatus.CONFLICT),
    CONTENT_REJECTED(11905, "动态内容未通过安全检查", HttpStatus.CONFLICT),
    ACTIVITY_REFERENCE_UNAVAILABLE(11906, "引用活动不可用", HttpStatus.CONFLICT);
}
