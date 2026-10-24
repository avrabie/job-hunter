package net.s4v3.backend.applications.internal

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

/** The answer `{"error": code}` with [status]. The code is a fixed word, never input. */
internal fun codeError(status: HttpStatus, code: String): ResponseEntity<Any> =
    ResponseEntity.status(status).body(mapOf("error" to code))
