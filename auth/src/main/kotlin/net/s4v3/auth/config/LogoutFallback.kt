package net.s4v3.auth.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.web.DefaultRedirectStrategy
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8

/**
 * Answers a rejected end-session request. The standard endpoint refuses any ID token it no longer knows (for example one
 * replaced by a token refresh, or one from before a restart), which left the SPA's sign-out on a bare 400 page. Here the
 * session is still ended and the browser goes on to the post-logout URI, but only to a URI registered for the client
 * (exact match), so nothing becomes an open redirect. Anything else stays a 400. Only the error code and the name of the
 * rejected parameter are logged, never the token.
 */
class LogoutFallbackHandler(
    private val clients: RegisteredClientRepository,
    private val defaultClientId: String,
) : AuthenticationFailureHandler {

    private val log = LoggerFactory.getLogger(LogoutFallbackHandler::class.java)
    private val redirects = DefaultRedirectStrategy()

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        val error = (exception as? OAuth2AuthenticationException)?.error
        log.warn("End-session request rejected: {} ({})", error?.errorCode, error?.description)

        val client = clients.findByClientId(request.getParameter("client_id")?.takeIf { it.isNotBlank() } ?: defaultClientId)
        val target = request.getParameter("post_logout_redirect_uri")
        if (client == null || target == null || target !in client.postLogoutRedirectUris) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, error?.errorCode ?: "invalid_request")
            return
        }
        SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().authentication)
        val state = request.getParameter("state")?.let { (if ('?' in target) "&" else "?") + "state=" + URLEncoder.encode(it, UTF_8) }.orEmpty()
        redirects.sendRedirect(request, response, target + state)
    }
}
