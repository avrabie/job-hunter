package net.s4v3.fixture.token

import org.springframework.security.core.context.SecurityContextHolder

/** Deliberate violation: a class outside `accounts` that reads the security context itself. */
class TokenReader {
    fun caller(): Any? = SecurityContextHolder.getContext().authentication?.principal
}
