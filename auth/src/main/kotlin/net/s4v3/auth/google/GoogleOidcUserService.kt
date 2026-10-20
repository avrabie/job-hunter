package net.s4v3.auth.google

import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service

/**
 * Turns Google's user into our user. The returned principal is named after the account's UUID (through the extra
 * `uid` claim used as the name attribute), so the token `sub` is the same UUID as for form login. Only the Google
 * subject, email and names are used; no Google token is stored or logged.
 */
@Service
class GoogleOidcUserService(private val accounts: GoogleAccountService) : OAuth2UserService<OidcUserRequest, OidcUser> {

    private val delegate = OidcUserService()

    override fun loadUser(request: OidcUserRequest): OidcUser {
        val google = delegate.loadUser(request)
        val email = google.email
        if (google.emailVerified != true || email.isNullOrBlank()) {
            throw OAuth2AuthenticationException(
                OAuth2Error("unverified_email", "Google has not verified an email address for this account", null),
            )
        }
        val user = try {
            accounts.signIn(google.subject!!, email, google.givenName, google.familyName)
        } catch (e: GoogleSignInRejectedException) {
            throw OAuth2AuthenticationException(OAuth2Error("sign_in_rejected", e.message, null))
        }

        val claims = LinkedHashMap(google.idToken.claims).apply { put(UID_CLAIM, user.id.toString()) }
        val idToken = OidcIdToken(google.idToken.tokenValue, google.idToken.issuedAt, google.idToken.expiresAt, claims)
        val authorities = user.roleNames.map { SimpleGrantedAuthority("ROLE_$it") }
        return DefaultOidcUser(authorities, idToken, google.userInfo, UID_CLAIM)
    }

    private companion object {
        const val UID_CLAIM = "uid"
    }
}
