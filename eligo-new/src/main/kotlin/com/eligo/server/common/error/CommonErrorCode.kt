package com.eligo.server.common.error

import org.springframework.http.HttpStatus

enum class CommonErrorCode(
    override val code: Int,
    override val message: String,
    override val httpStatus: HttpStatus
) : ErrorCode {
    MALFORMED_REQUEST(10000, "请求格式错误", HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED(10001, "参数校验失败", HttpStatus.BAD_REQUEST),
    METHOD_NOT_ALLOWED(10002, "请求方法不支持", HttpStatus.METHOD_NOT_ALLOWED),
    MEDIA_TYPE_NOT_SUPPORTED(10003, "请求媒体类型不支持", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    MISSING_PARAMETER(10004, "缺少必要参数", HttpStatus.BAD_REQUEST),
    TYPE_MISMATCH(10005, "参数类型错误", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(10006, "请求资源不存在", HttpStatus.NOT_FOUND),
    CONFLICT(10007, "请求与当前状态冲突", HttpStatus.CONFLICT),
    TOO_MANY_REQUESTS(10008, "请求过于频繁", HttpStatus.TOO_MANY_REQUESTS),
    AUTHENTICATION_REQUIRED(10100, "请先登录", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(10101, "无权执行此操作", HttpStatus.FORBIDDEN),
    INTERNAL_ERROR(10999, "服务暂时不可用", HttpStatus.INTERNAL_SERVER_ERROR);
}
