package com.eligo.server.common.web

import com.eligo.server.common.api.Result
import com.eligo.server.common.error.AccountUserFileErrorCode
import com.eligo.server.common.error.BusinessException
import com.eligo.server.common.error.CommonErrorCode
import com.eligo.server.common.error.ErrorCode
import com.eligo.server.common.error.FieldValidationError
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BindException
import org.springframework.validation.FieldError
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(
        exception: MethodArgumentNotValidException
    ): ResponseEntity<Result<List<FieldValidationError>>> = response(
        CommonErrorCode.VALIDATION_FAILED,
        CommonErrorCode.VALIDATION_FAILED.message,
        toFieldErrors(exception.bindingResult.fieldErrors)
    )

    @ExceptionHandler(BindException::class)
    fun handleBindException(exception: BindException): ResponseEntity<Result<List<FieldValidationError>>> = response(
        CommonErrorCode.VALIDATION_FAILED,
        CommonErrorCode.VALIDATION_FAILED.message,
        toFieldErrors(exception.bindingResult.fieldErrors)
    )

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(
        exception: ConstraintViolationException
    ): ResponseEntity<Result<List<FieldValidationError>>> {
        val errors = exception.constraintViolations
            .map { violation ->
                FieldValidationError(
                    violation.propertyPath.toString(),
                    violation.message
                )
            }
            .sortedBy { it.field }
        return response(
            CommonErrorCode.VALIDATION_FAILED,
            CommonErrorCode.VALIDATION_FAILED.message,
            errors
        )
    }

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleHandlerMethodValidation(
        exception: HandlerMethodValidationException
    ): ResponseEntity<Result<List<FieldValidationError>>> {
        val errors = exception.parameterValidationResults
            .flatMap { validationResult ->
                val parameterName = validationResult.methodParameter.parameterName
                val field = parameterName
                    ?: "arg" + validationResult.methodParameter.parameterIndex
                validationResult.resolvableErrors.map { error ->
                    FieldValidationError(
                        field,
                        error.defaultMessage ?: DEFAULT_VALIDATION_MESSAGE
                    )
                }
            }
            .sortedBy { it.field }
        return response(
            CommonErrorCode.VALIDATION_FAILED,
            CommonErrorCode.VALIDATION_FAILED.message,
            errors
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadable(exception: HttpMessageNotReadableException): ResponseEntity<Result<Void>> =
        response(CommonErrorCode.MALFORMED_REQUEST)

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingRequestParameter(
        exception: MissingServletRequestParameterException
    ): ResponseEntity<Result<List<FieldValidationError>>> = response(
        CommonErrorCode.MISSING_PARAMETER,
        CommonErrorCode.MISSING_PARAMETER.message,
        listOf(FieldValidationError(exception.parameterName, "参数不能为空"))
    )

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingRequestHeader(
        exception: MissingRequestHeaderException
    ): ResponseEntity<Result<List<FieldValidationError>>> = response(
        CommonErrorCode.MISSING_PARAMETER,
        CommonErrorCode.MISSING_PARAMETER.message,
        listOf(FieldValidationError(exception.headerName, "参数不能为空"))
    )

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleMethodArgumentTypeMismatch(
        exception: MethodArgumentTypeMismatchException
    ): ResponseEntity<Result<List<FieldValidationError>>> = response(
        CommonErrorCode.TYPE_MISMATCH,
        CommonErrorCode.TYPE_MISMATCH.message,
        listOf(FieldValidationError(exception.name, "参数类型不正确"))
    )

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSizeExceeded(exception: MaxUploadSizeExceededException): ResponseEntity<Result<Void>> =
        response(AccountUserFileErrorCode.FILE_TOO_LARGE)

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(exception: HttpRequestMethodNotSupportedException): ResponseEntity<Result<Void>> =
        response(CommonErrorCode.METHOD_NOT_ALLOWED)

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleMediaTypeNotSupported(exception: HttpMediaTypeNotSupportedException): ResponseEntity<Result<Void>> =
        response(CommonErrorCode.MEDIA_TYPE_NOT_SUPPORTED)

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFound(exception: NoResourceFoundException): ResponseEntity<Result<Void>> =
        response(CommonErrorCode.RESOURCE_NOT_FOUND)

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(exception: BusinessException): ResponseEntity<Result<Void>> =
        response(exception.errorCode, exception.message ?: exception.errorCode.message, null)

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(exception: Exception): ResponseEntity<Result<Void>> {
        log.error("未处理异常", exception)
        return response(CommonErrorCode.INTERNAL_ERROR)
    }

    private fun toFieldErrors(fieldErrors: List<FieldError>): List<FieldValidationError> = fieldErrors
        .map { error ->
            FieldValidationError(
                error.field,
                error.defaultMessage ?: DEFAULT_VALIDATION_MESSAGE
            )
        }
        .sortedBy { it.field }

    private fun response(errorCode: ErrorCode): ResponseEntity<Result<Void>> =
        response(errorCode, errorCode.message, null)

    private fun <T> response(
        errorCode: ErrorCode,
        message: String,
        data: T?
    ): ResponseEntity<Result<T>> = ResponseEntity
        .status(errorCode.httpStatus)
        .body(Result.failure(errorCode.code, message, data))

    companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
        private const val DEFAULT_VALIDATION_MESSAGE = "参数值不合法"
    }
}
