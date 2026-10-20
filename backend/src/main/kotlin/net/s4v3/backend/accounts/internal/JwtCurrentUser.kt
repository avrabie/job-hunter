package net.s4v3.backend.accounts.internal

import net.s4v3.backend.accounts.CurrentUser
import net.s4v3.backend.accounts.NoCurrentUserException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

@Component
internal class JwtCurrentUser : CurrentUser {

    override fun id(): String {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        val subject = (principal as? Jwt)?.subject
        return subject?.takeIf { it.isNotBlank() } ?: throw NoCurrentUserException()
    }
}
