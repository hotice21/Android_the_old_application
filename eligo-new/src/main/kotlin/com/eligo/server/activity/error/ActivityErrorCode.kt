package com.eligo.server.activity.error

import com.eligo.server.common.error.ErrorCode
import org.springframework.http.HttpStatus

enum class ActivityErrorCode(
    override val code: Int,
    override val message: String,
    override val httpStatus: HttpStatus
) : ErrorCode {
    IDEMPOTENCY_KEY_CONFLICT(11601, "幂等键已用于其他请求", HttpStatus.CONFLICT),
    STATUS_CONFLICT(11602, "活动状态不允许当前操作", HttpStatus.CONFLICT),
    VERSION_CONFLICT(11603, "活动版本已发生变化", HttpStatus.CONFLICT),
    CAPACITY_CONFLICT(11604, "活动容量低于当前报名人数", HttpStatus.CONFLICT),
    IDEMPOTENCY_RESULT_DELETED(11605, "幂等请求对应的活动草稿已删除", HttpStatus.CONFLICT);
}
