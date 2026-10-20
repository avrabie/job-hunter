package net.s4v3.auth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** A stand-in for Google's OpenID Connect endpoints: authorize, token and JWKS, signing real ID tokens. */
class FakeGoogle : AutoCloseable {

    data class Identity(
        val sub: String = "g-${UUID.randomUUID()}",
        val email: String = "gina-${UUID.randomUUID()}@example.com",
        val emailVerified: Boolean = true,
        val given: String? = "Gina",
        val family: String? = "Google",
    )

    /** The identity the next authorization will sign in as. */
    @Volatile
    var identity: Identity = Identity()

    /** Every access and ID token this fake handed out, so tests can look for them in logs. */
    val issued = java.util.concurrent.CopyOnWriteArrayList<String>()

    private data class Pending(val nonce: String?, val identity: Identity)

    private val pending = ConcurrentHashMap<String, Pending>()
    private val key: RSAKey = RSAKeyGenerator(2048).keyID("fake-google").generate()
    private val server: HttpServer = HttpServer.create(InetSocketAddress("localhost", 0), 0)
    val base: String get() = "http://localhost:${server.address.port}"

    init {
        server.createContext("/authorize") { authorize(it) }
        server.createContext("/token") { token(it) }
        server.createContext("/jwks") { reply(it, 200, "application/json", JWKSet(key.toPublicJWK()).toString()) }
        server.start()
    }

    private fun authorize(exchange: HttpExchange) {
        val query = queryOf(exchange.requestURI)
        val code = "fake-code-${UUID.randomUUID()}"
        pending[code] = Pending(query["nonce"], identity)
        val redirect = query.getValue("redirect_uri")
        exchange.responseHeaders.add("Location", "$redirect?code=$code&state=${query.getValue("state")}")
        exchange.sendResponseHeaders(302, -1)
        exchange.close()
    }

    private fun token(exchange: HttpExchange) {
        val form = queryOf(exchange.requestBody.readBytes().toString(UTF_8))
        val granted = pending.remove(form["code"]) ?: return reply(exchange, 400, "application/json", """{"error":"invalid_grant"}""")
        val id = granted.identity
        val now = Date()
        val claims = JWTClaimsSet.Builder()
            .issuer(base).audience(CLIENT_ID).subject(id.sub)
            .issueTime(now).expirationTime(Date(now.time + 300_000))
            .claim("email", id.email).claim("email_verified", id.emailVerified)
            .apply {
                id.given?.let { claim("given_name", it) }
                id.family?.let { claim("family_name", it) }
                granted.nonce?.let { claim("nonce", it) }
            }
            .build()
        val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build(), claims)
        jwt.sign(RSASSASigner(key))
        val accessToken = "fake-google-access-${UUID.randomUUID()}"
        issued += listOf(accessToken, jwt.serialize())
        reply(
            exchange, 200, "application/json",
            """{"access_token":"$accessToken","token_type":"Bearer","expires_in":3600,"scope":"openid profile email","id_token":"${jwt.serialize()}"}""",
        )
    }

    private fun reply(exchange: HttpExchange, status: Int, type: String, body: String) {
        val bytes = body.toByteArray(UTF_8)
        exchange.responseHeaders.add("Content-Type", type)
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun queryOf(uri: URI): Map<String, String> = queryOf(uri.rawQuery.orEmpty())

    private fun queryOf(raw: String): Map<String, String> =
        raw.split("&").filter { it.contains("=") }.associate {
            val (k, v) = it.split("=", limit = 2)
            URLDecoder.decode(k, UTF_8) to URLDecoder.decode(v, UTF_8)
        }

    fun registration(): ClientRegistration =
        ClientRegistration.withRegistrationId("google")
            .clientId(CLIENT_ID).clientSecret("fake-secret")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid", "profile", "email")
            .authorizationUri("$base/authorize").tokenUri("$base/token").jwkSetUri("$base/jwks")
            .issuerUri(base).userNameAttributeName("sub").clientName("Google")
            .build()

    override fun close() = server.stop(0)

    companion object {
        const val CLIENT_ID = "fake-google-client"
    }
}

/** Replaces the production Google registration with one that talks to [FakeGoogle]. */
@TestConfiguration(proxyBeanMethods = false)
class FakeGoogleConfig {
    @Bean(destroyMethod = "close")
    fun fakeGoogle() = FakeGoogle()

    @Bean
    fun clientRegistrationRepository(google: FakeGoogle): ClientRegistrationRepository =
        InMemoryClientRegistrationRepository(google.registration())
}
