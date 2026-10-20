package net.s4v3.auth.token

import net.s4v3.auth.user.UserRepository
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.core.oidc.OidcScopes
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames
import org.springframework.security.oauth2.core.oidc.StandardClaimNames
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Writes the claims `backend` reads: `roles` (names without `ROLE_`) on access tokens, and `email` and `name` on
 * access and ID tokens when the `email` and `profile` scopes were granted and the values exist. The `sub` is the
 * principal's name, the user's UUID. Nothing about passwords is ever added.
 */
@Component
class UserClaimsCustomizer(private val users: UserRepository) : OAuth2TokenCustomizer<JwtEncodingContext> {

    override fun customize(context: JwtEncodingContext) {
        val tokenType = context.tokenType.value
        val isAccessToken = tokenType == OAuth2TokenType.ACCESS_TOKEN.value
        val isIdToken = tokenType == OidcParameterNames.ID_TOKEN
        if (!isAccessToken && !isIdToken) return

        if (isAccessToken) {
            val roles = principal(context).authorities
                .mapNotNull { it.authority }
                .filter { it.startsWith(ROLE_PREFIX) }
                .map { it.removePrefix(ROLE_PREFIX) }
                .sorted()
            context.claims.claim("roles", roles)
        }

        val user = runCatching { UUID.fromString(principal(context).name) }
            .getOrNull()?.let { users.findById(it).orElse(null) } ?: return
        val scopes = context.authorizedScopes
        if (OidcScopes.EMAIL in scopes) {
            context.claims.claim(StandardClaimNames.EMAIL, user.email)
        }
        if (OidcScopes.PROFILE in scopes) {
            user.displayName?.let { context.claims.claim(StandardClaimNames.NAME, it) }
        }
    }

    private fun principal(context: JwtEncodingContext): Authentication = context.getPrincipal<Authentication>()!!

    private companion object {
        const val ROLE_PREFIX = "ROLE_"
    }
}
