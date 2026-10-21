package net.s4v3.auth.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.DefaultRedirectStrategy
import org.springframework.security.web.util.matcher.RequestMatcher
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8

/**
 * True for an authorization request that asks for `prompt=none` and names a registered client and one of its
 * registered redirect URIs (exact comparison, as the authorization endpoint does). Anything else is left to the
 * login redirect, so the silent answer is never sent to an unvalidated URI.
 */
class PromptNoneMatcher(private val clients: RegisteredClientRepository) : RequestMatcher {
    override fun matches(request: HttpServletRequest): Boolean = registeredRedirect(request) != null

    fun registeredRedirect(request: HttpServletRequest): String? {
        if (request.servletPath != AUTHORIZE_PATH) return null
        val prompts = request.getParameterValues("prompt").orEmpty().flatMap { it.split(' ') }
        if ("none" !in prompts) return null
        val clientId = request.getParameter("client_id") ?: return null
        val redirectUri = request.getParameter("redirect_uri") ?: return null
        val client = clients.findByClientId(clientId) ?: return null
        return redirectUri.takeIf { it in client.redirectUris }
    }

    private companion object {
        const val AUTHORIZE_PATH = "/oauth2/authorize"
    }
}

/** Answers an unauthenticated `prompt=none` request with `error=login_required` and the caller's `state`. */
class PromptNoneEntryPoint(private val matcher: PromptNoneMatcher) : AuthenticationEntryPoint {
    private val redirects = DefaultRedirectStrategy()

    override fun commence(request: HttpServletRequest, response: HttpServletResponse, e: AuthenticationException) {
        val redirectUri = matcher.registeredRedirect(request) ?: error("not a prompt=none request")
        val query = buildList {
            add("error=login_required")
            request.getParameter("state")?.let { add("state=${URLEncoder.encode(it, UTF_8)}") }
        }.joinToString("&")
        val separator = if ('?' in redirectUri) '&' else '?'
        redirects.sendRedirect(request, response, "$redirectUri$separator$query")
    }
}
