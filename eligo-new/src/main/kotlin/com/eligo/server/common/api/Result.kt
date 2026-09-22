package com.eligo.server.common.api

import com.eligo.server.common.error.ErrorCode
import com.eligo.server.common.web.RequestIdContext
import java.time.Instant

data class Result<T>(
    val code: Int,
    val message: String,
    val data: T?,
    val requestId: String?,
    val timestamp: Instant
) {
    companion object {
        private const val SUCCESS_CODE = 0
        private const val SUCCESS_MESSAGE = "成功"

        @JvmStatic
        fun success(): Result<Void> = success<Void>(null)

        @JvmStatic
        fun <T> success(data: T?): Result<T> = Result(
            SUCCESS_CODE,
            SUCCESS_MESSAGE,
            data,
            RequestIdContext.current(),
            Instant.now()
        )

        @JvmStatic
        fun <T> failure(errorCode: ErrorCode): Result<T> =
            failure(errorCode.code, errorCode.message, null)

        @JvmStatic
        fun <T> failure(errorCode: ErrorCode, data: T): Result<T> =
            failure(errorCode.code, errorCode.message, data)

        @JvmStatic
        fun <T> failure(code: Int, message: String, data: T?): Result<T> = Result(
            code,
            message,
            data,
            RequestIdContext.current(),
            Instant.now()
        )
    }
}
