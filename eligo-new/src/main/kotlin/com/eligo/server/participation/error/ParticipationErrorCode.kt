package com.eligo.server.participation.error

import com.eligo.server.common.error.ErrorCode
import org.springframework.http.HttpStatus

enum class ParticipationErrorCode(
    override val code: Int,
    override val message: String,
    override val httpStatus: HttpStatus
) : ErrorCode {
    REGISTRATION_NOT_STARTED(11701, "报名尚未开始", HttpStatus.CONFLICT),
    REGISTRATION_ENDED(11702, "报名已经截止", HttpStatus.CONFLICT),
    CAPACITY_FULL(11703, "活动名额已满", HttpStatus.CONFLICT),
    OWNER_CANNOT_JOIN(11704, "活动发起者不能报名自己的活动", HttpStatus.CONFLICT),
    ACTIVITY_STARTED(11705, "活动已经开始", HttpStatus.CONFLICT),
    ACTIVITY_CANCELLED(11706, "活动已取消", HttpStatus.CONFLICT),
    ACTIVITY_ENDED(11707, "活动已结束", HttpStatus.CONFLICT),
    PARTICIPATION_NOT_REMOVABLE(11708, "当前参与记录不可由发起者移除", HttpStatus.CONFLICT),
    REMOVED_PARTICIPANT_CANNOT_REJOIN(
        11709, "已被活动发起者移除，不能再次报名", HttpStatus.CONFLICT),
    REGISTRATION_GENDER_MISMATCH(11710, "不符合活动报名性别限制", HttpStatus.CONFLICT);
}
