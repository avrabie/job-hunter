package net.s4v3.auth.token

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2ErrorCodes
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.web.authentication.AuthenticationConverter

/*
 * Spring Authorization Server authenticates a public client only through PKCE on the authorization code exchange,
 * so a public client cannot present a refresh token. These two classes add that single case: the refresh_token grant
 * from a registered public client that sends only its client_id. The refresh token itself is the credential
 * (single use, rotated by Spring for public clients, bound to the client it was issued to).
 */

/** Recognises `grant_type=refresh_token` with a `client_id` and no other client credentials. */
class PublicClientRefreshConverter : AuthenticationConverter {

    override fun convert(request: HttpServletRequest): Authentication? {
        if (request.method != "POST" || request.getHeader(HttpHeaders.AUTHORIZATION) != null) return null
        if (request.getParameter(OAuth2ParameterNames.GRANT_TYPE) != AuthorizationGrantType.REFRESH_TOKEN.value) return null
        if (request.getParameter(OAuth2ParameterNames.CLIENT_SECRET) != null ||
            request.getParameter(OAuth2ParameterNames.CLIENT_ASSERTION) != null
        ) {
            return null
        }
        val clientIds = request.getParameterValues(OAuth2ParameterNames.CLIENT_ID)
        if (clientIds == null || clientIds.size != 1 || clientIds[0].isBlank()) return null

        val additional = request.parameterMap.filterKeys { it != OAuth2ParameterNames.CLIENT_ID }
            .mapValues { (_, values) -> if (values.size == 1) values[0] else values }
        return OAuth2ClientAuthenticationToken(clientIds[0], ClientAuthenticationMethod.NONE, null, additional)
    }
}

/** Accepts such a request for a registered client that is public and has the refresh token grant. */
class PublicClientRefreshProvider(private val clients: RegisteredClientRepository) : AuthenticationProvider {

    override fun authenticate(authentication: Authentication): Authentication? {
        val token = authentication as OAuth2ClientAuthenticationToken
        if (token.clientAuthenticationMethod != ClientAuthenticationMethod.NONE) return null
        if (token.additionalParameters[OAuth2ParameterNames.GRANT_TYPE] != AuthorizationGrantType.REFRESH_TOKEN.value) return null

        val client = clients.findByClientId(token.principal.toString()) ?: throw invalidClient("client_id")
        if (ClientAuthenticationMethod.NONE !in client.clientAuthenticationMethods ||
            AuthorizationGrantType.REFRESH_TOKEN !in client.authorizationGrantTypes
        ) {
            throw invalidClient("authentication_method")
        }
        return OAuth2ClientAuthenticationToken(client, ClientAuthenticationMethod.NONE, null)
    }

    override fun supports(authentication: Class<*>): Boolean =
        OAuth2ClientAuthenticationToken::class.java.isAssignableFrom(authentication)

    private fun invalidClient(what: String) =
        OAuth2AuthenticationException(OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT, "Client authentication failed: $what", null))
}
