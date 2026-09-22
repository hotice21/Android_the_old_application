package com.eligo.server.common.error

import org.springframework.http.HttpStatus

enum class AccountUserFileErrorCode(
    override val code: Int,
    override val message: String,
    override val httpStatus: HttpStatus
) : ErrorCode {
    WECHAT_CREDENTIAL_INVALID(11001, "微信凭证无效", HttpStatus.UNAUTHORIZED),
    WECHAT_IDENTITY_CONFLICT(11002, "微信身份绑定冲突", HttpStatus.CONFLICT),
    ACCESS_TOKEN_INVALID(11003, "访问令牌无效或过期", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_INVALID(11004, "刷新令牌无效或已被使用", HttpStatus.UNAUTHORIZED),
    LOGIN_SESSION_INVALID(11005, "登录会话已失效", HttpStatus.UNAUTHORIZED),
    PHONE_ALREADY_BOUND(11101, "手机号已被其他用户绑定", HttpStatus.CONFLICT),
    PHONE_NOT_BOUND(11102, "当前未绑定手机号", HttpStatus.NOT_FOUND),
    DEVICE_OR_SESSION_NOT_FOUND(11103, "设备或会话不存在", HttpStatus.NOT_FOUND),
    PROFILE_INCOMPLETE(11201, "用户资料尚未完成", HttpStatus.CONFLICT),
    NICKNAME_UPDATE_TOO_FREQUENT(11202, "昵称修改过于频繁", HttpStatus.TOO_MANY_REQUESTS),
    EMAIL_ALREADY_USED(11203, "邮箱已被使用", HttpStatus.CONFLICT),
    LATEST_AGREEMENT_REQUIRED(11301, "需要先同意最新协议", HttpStatus.FORBIDDEN),
    AGREEMENT_NOT_FOUND_OR_INACTIVE(11302, "协议不存在或未生效", HttpStatus.NOT_FOUND),
    UNSUPPORTED_FILE_TYPE(11401, "不支持的文件类型", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    FILE_TOO_LARGE(11402, "文件大小超出限制", HttpStatus.BAD_REQUEST),
    FILE_SECURITY_CHECK_FAILED(11403, "文件安全检查未通过", HttpStatus.CONFLICT),
    FILE_STATE_CONFLICT(11404, "文件状态不允许此操作", HttpStatus.CONFLICT),
    ACCOUNT_CANCELLATION_CONFLICT(11501, "账号注销状态冲突", HttpStatus.CONFLICT),
    DATA_EXPORT_IN_PROGRESS(11502, "已存在进行中的数据导出", HttpStatus.CONFLICT),
    DATA_EXPORT_EXPIRED(11503, "数据导出结果已过期", HttpStatus.CONFLICT);
}
