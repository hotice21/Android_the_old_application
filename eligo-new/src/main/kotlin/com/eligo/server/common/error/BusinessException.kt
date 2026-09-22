package com.eligo.server.common.error

/**
 * 业务异常。`message` 默认取 [ErrorCode.message]，与原始 Java 双构造器行为一致。
 *
 * 由于 Kotlin 类型系统已强制 `errorCode` 与 `message` 非空，原始 Java 的 `Objects.requireNonNull`
 * 运行时校验在编译期已被覆盖。
 */
class BusinessException(
    val errorCode: ErrorCode,
    message: String = errorCode.message
) : RuntimeException(message)
