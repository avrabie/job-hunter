package net.s4v3.backend.accounts.internal

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Runs after the bearer token has been validated and makes sure a user record exists for its `sub`.
 * Not a Spring bean: it is added to the security filter chain only, so it never runs before authentication.
 */
internal class UserProvisioningFilter(private val provisioner: UserProvisioner) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication is JwtAuthenticationToken) {
            val jwt = authentication.token
            val sub = jwt.subject
            if (sub.isNullOrBlank()) {
                // A token without a subject cannot identify a user.
                SecurityContextHolder.clearContext()
                response.status = HttpStatus.UNAUTHORIZED.value()
                return
            }
            provisioner.ensureExists(sub, jwt.getClaimAsString("email"), jwt.getClaimAsString("name"))
        }
        chain.doFilter(request, response)
    }
}
