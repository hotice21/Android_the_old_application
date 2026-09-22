package com.eligo.server.common.error

import org.springframework.http.HttpStatus

interface ErrorCode {
    val code: Int
    val message: String
    val httpStatus: HttpStatus
}
