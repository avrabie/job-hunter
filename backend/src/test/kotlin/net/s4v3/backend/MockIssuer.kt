package net.s4v3.backend

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.time.Instant
import java.util.Date

/**
 * A stand-in for the auth service: serves the OIDC discovery document and the signing keys over HTTP
 * on a random local port, and signs tokens, so the backend's real token validation runs end to end.
 */
class MockIssuer {

    private val key: RSAKey = RSAKeyGenerator(2048).keyID("test-key").generate()
    private val server = FakeHttpServer()

    val issuer: String = server.baseUrl

    init {
        server.on("/.well-known/openid-configuration") {
            FakeHttpServer.Reply(body = """{"issuer":"$issuer","jwks_uri":"$issuer/jwks"}""")
        }
        server.on("/jwks") { FakeHttpServer.Reply(body = JWKSet(key.toPublicJWK()).toString()) }
    }

    fun token(
        sub: String?,
        roles: List<String> = listOf("USER"),
        issuer: String = this.issuer,
        expiresAt: Instant = Instant.now().plusSeconds(900),
        extraClaims: Map<String, Any> = emptyMap(),
    ): String {
        val claims = JWTClaimsSet.Builder()
            .subject(sub)
            .issuer(issuer)
            .issueTime(Date.from(Instant.now().minusSeconds(10)))
            .expirationTime(Date.from(expiresAt))
            .claim("roles", roles)
        extraClaims.forEach { (name, value) -> claims.claim(name, value) }
        val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build(), claims.build())
        jwt.sign(RSASSASigner(key))
        return jwt.serialize()
    }

    fun stop() = server.close()
}
