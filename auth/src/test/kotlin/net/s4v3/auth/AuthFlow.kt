package net.s4v3.auth

import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.CookieManager
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class Resp(val status: Int, val headers: Map<String, List<String>>, val body: String) {
    val location: String? get() = headers["location"]?.firstOrNull()
    fun header(name: String): String? = headers[name.lowercase()]?.firstOrNull()
    fun json(): JsonNode = JsonMapper.builder().build().readTree(body)
}

data class Pkce(val verifier: String, val challenge: String)

/** A browser-like client (cookies, no automatic redirects) that drives the authorization code flow with PKCE. */
class AuthFlow(port: Int, private val clientId: String = "spa") {
    val base = "http://localhost:$port/auth"
    private val http: HttpClient = HttpClient.newBuilder()
        .cookieHandler(CookieManager())
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    fun get(url: String, vararg headers: Pair<String, String>): Resp =
        send(HttpRequest.newBuilder(URI.create(url)).GET(), headers)

    fun postForm(url: String, form: Map<String, String>, vararg headers: Pair<String, String>): Resp =
        send(
            HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encode(form))),
            headers,
        )

    fun postJson(url: String, json: String, vararg headers: Pair<String, String>): Resp =
        send(
            HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)),
            headers,
        )

    fun putJson(url: String, json: String, vararg headers: Pair<String, String>): Resp =
        send(
            HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json)),
            headers,
        )

    private fun send(builder: HttpRequest.Builder, headers: Array<out Pair<String, String>>): Resp {
        headers.forEach { (name, value) -> builder.header(name, value) }
        val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        return Resp(response.statusCode(), response.headers().map(), response.body())
    }

    fun pkce(): Pkce {
        val verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
        val challenge = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(UTF_8)))
        return Pkce(verifier, challenge)
    }

    fun authorizeUrl(
        pkce: Pkce?,
        redirectUri: String = REDIRECT,
        scope: String = "openid profile email",
        state: String = "state-1",
    ): String {
        val params = linkedMapOf(
            "response_type" to "code",
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "scope" to scope,
            "state" to state,
        )
        if (pkce != null) {
            params["code_challenge"] = pkce.challenge
            params["code_challenge_method"] = "S256"
        }
        return "$base/oauth2/authorize?${encode(params)}"
    }

    /** Submits the login form of the current session; returns the response of the POST. */
    fun login(email: String, password: String): Resp {
        val page = get("$base/login")
        val csrf = CSRF.find(page.body)?.groupValues?.get(1) ?: error("no CSRF token on the login page:\n${page.body}")
        return postForm("$base/login", mapOf("username" to email, "password" to password, "_csrf" to csrf))
    }

    /** Full sign-in: authorize, log in, follow the saved request, return the final redirect to the client. */
    fun authorizeAndLogin(email: String, password: String, pkce: Pkce, scope: String = "openid profile email"): Resp {
        val authorize = get(authorizeUrl(pkce, scope = scope))
        check(authorize.status == 302 && authorize.location!!.contains("/login")) { "expected login redirect: $authorize" }
        val loggedIn = login(email, password)
        check(loggedIn.status == 302) { "login did not redirect: $loggedIn" }
        return get(loggedIn.location!!)
    }

    /**
     * Sign-in through the Google button during an authorization request. The fake Google redirects straight back,
     * so the whole round trip is followed by hand until the browser would land on the client's redirect URI.
     * Returns that final redirect (a code on success, an error page redirect when the sign-in was rejected).
     */
    fun authorizeWithGoogle(pkce: Pkce): Resp {
        val authorize = get(authorizeUrl(pkce))
        check(authorize.status == 302 && authorize.location!!.contains("/login")) { "expected login redirect: $authorize" }
        var response = get("$base/oauth2/authorization/google")
        repeat(10) {
            val location = response.location ?: return response
            if (location.startsWith(REDIRECT) || location.contains("/login?error")) return response
            response = get(location)
        }
        error("too many redirects: $response")
    }

    fun codeFrom(redirect: Resp): String =
        queryOf(redirect.location!!)["code"] ?: error("no code in ${redirect.location}")

    fun exchange(code: String, verifier: String, redirectUri: String = REDIRECT): Resp =
        postForm(
            "$base/oauth2/token",
            mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to redirectUri,
                "client_id" to clientId,
                "code_verifier" to verifier,
            ),
        )

    /** Convenience: complete sign-in and return the token response as JSON. */
    fun tokens(email: String, password: String, scope: String = "openid profile email"): JsonNode {
        val pkce = pkce()
        val code = codeFrom(authorizeAndLogin(email, password, pkce, scope))
        val response = exchange(code, pkce.verifier)
        check(response.status == 200) { "token exchange failed: $response" }
        return response.json()
    }

    companion object {
        const val REDIRECT = "http://localhost:5173/callback"
        private val CSRF = Regex("""name="_csrf"[^>]*value="([^"]+)"""")

        fun encode(params: Map<String, String>): String =
            params.entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }

        fun enc(value: String): String = URLEncoder.encode(value, UTF_8)

        fun queryOf(url: String): Map<String, String> =
            (URI.create(url).rawQuery ?: "").split("&").filter { it.isNotEmpty() }.associate {
                val (k, v) = it.split("=", limit = 2).let { p -> p[0] to p.getOrElse(1) { "" } }
                URLDecoder.decode(k, UTF_8) to URLDecoder.decode(v, UTF_8)
            }

        fun claims(jwt: String): JWTClaimsSet = SignedJWT.parse(jwt).jwtClaimsSet
    }
}
