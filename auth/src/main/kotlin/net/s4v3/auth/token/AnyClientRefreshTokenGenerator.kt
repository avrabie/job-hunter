package net.s4v3.auth.token

import org.springframework.security.crypto.keygen.Base64StringKeyGenerator
import org.springframework.security.oauth2.core.OAuth2RefreshToken
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator
import java.time.Clock
import java.util.Base64

/** Like Spring's refresh token generator, but also for public clients (see AuthorizationServerConfig). */
class AnyClientRefreshTokenGenerator : OAuth2TokenGenerator<OAuth2RefreshToken> {

    private val keys = Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 96)
    private val clock = Clock.systemUTC()

    override fun generate(context: OAuth2TokenContext): OAuth2RefreshToken? {
        if (OAuth2TokenType.REFRESH_TOKEN != context.tokenType) return null
        val issuedAt = clock.instant()
        val ttl = context.registeredClient.tokenSettings.refreshTokenTimeToLive
        return OAuth2RefreshToken(keys.generateKey(), issuedAt, issuedAt.plus(ttl))
    }
}
