package net.s4v3.auth.web

import net.s4v3.auth.password.InvalidResetTokenException
import net.s4v3.auth.password.NoPasswordException
import net.s4v3.auth.password.SamePasswordException
import net.s4v3.auth.password.WrongPasswordException
import net.s4v3.auth.user.DuplicateUserException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

data class ApiError(val error: String, val fields: Map<String, String> = emptyMap())

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalid(e: MethodArgumentNotValidException): ResponseEntity<ApiError> {
        val fields = e.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "invalid") }
        return ResponseEntity.badRequest().body(ApiError("validation_failed", fields))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadable(): ResponseEntity<ApiError> = ResponseEntity.badRequest().body(ApiError("malformed_request"))

    @ExceptionHandler(DuplicateUserException::class)
    fun duplicate(): ResponseEntity<ApiError> = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("duplicate_email"))

    @ExceptionHandler(InvalidResetTokenException::class)
    fun invalidToken(): ResponseEntity<ApiError> = ResponseEntity.badRequest().body(ApiError("invalid_or_expired_token"))

    @ExceptionHandler(WrongPasswordException::class)
    fun wrongPassword(): ResponseEntity<ApiError> = ResponseEntity.badRequest().body(ApiError("wrong_password"))

    @ExceptionHandler(SamePasswordException::class)
    fun samePassword(): ResponseEntity<ApiError> = ResponseEntity.badRequest().body(ApiError("same_password"))

    @ExceptionHandler(NoPasswordException::class)
    fun noPassword(): ResponseEntity<ApiError> = ResponseEntity.badRequest().body(ApiError("no_password"))
}
