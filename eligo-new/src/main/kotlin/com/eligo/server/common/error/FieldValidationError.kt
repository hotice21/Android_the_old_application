package com.eligo.server.common.error

data class FieldValidationError(
    val field: String,
    val message: String
)
