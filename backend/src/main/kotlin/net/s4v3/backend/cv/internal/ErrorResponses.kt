package net.s4v3.backend.cv.internal

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

/** The answer `{"error": code}` with [status]. The code is a fixed word, never input. */
internal fun codeError(status: HttpStatus, code: String): ResponseEntity<Any> =
    ResponseEntity.status(status).body(mapOf("error" to code))

/** The answer `{"error": "invalid_cv", "message": message}` with [status]. */
internal fun cvError(status: HttpStatus, message: String): ResponseEntity<Map<String, String>> =
    ResponseEntity.status(status).body(mapOf("error" to "invalid_cv", "message" to message))
