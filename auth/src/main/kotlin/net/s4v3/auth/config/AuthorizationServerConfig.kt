package net.s4v3.auth.config

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import net.s4v3.auth.token.AnyClientRefreshTokenGenerator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.oidc.OidcScopes
import org.springframework.security.oauth2.core.OAuth2Token
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Duration
import java.util.UUID

@Configuration(proxyBeanMethods = false)
class AuthorizationServerConfig(private val properties: AuthProperties) {

    /** Fixed issuer: never derived from the request, so forwarded headers cannot change it. */
    @Bean
    fun authorizationServerSettings(): AuthorizationServerSettings =
        AuthorizationServerSettings.builder().issuer(properties.issuerUri!!).build()

    /** The single public client of the SPA: authorization code and refresh token, PKCE required, no consent. */
    @Bean
    fun registeredClientRepository(): RegisteredClientRepository {
        val spa = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId(SPA_CLIENT_ID)
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUris { it.addAll(properties.spa.redirectUris) }
            .postLogoutRedirectUris { it.addAll(properties.spa.postLogoutRedirectUris) }
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)
            .scope(OidcScopes.EMAIL)
            .clientSettings(
                ClientSettings.builder().requireAuthorizationConsent(false).requireProofKey(true).build(),
            )
            .tokenSettings(
                TokenSettings.builder()
                    .accessTokenTimeToLive(ACCESS_TOKEN_TTL)
                    .refreshTokenTimeToLive(REFRESH_TOKEN_TTL)
                    .reuseRefreshTokens(false)
                    .build(),
            )
            .build()
        return InMemoryRegisteredClientRepository(spa)
    }

    /**
     * The only place signing keys are created. An RSA key is generated at startup, so a restart invalidates every
     * token; replacing this bean with a persistent source is the whole change needed to keep them.
     */
    @Bean
    fun jwkSource(): JWKSource<SecurityContext> {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val rsaKey = RSAKey.Builder(keyPair.public as RSAPublicKey)
            .privateKey(keyPair.private as RSAPrivateKey)
            .keyID(UUID.randomUUID().toString())
            .build()
        return ImmutableJWKSet(JWKSet(rsaKey))
    }

    /** Validates this server's own tokens (for the self-service API and userinfo) with the same key. */
    @Bean
    fun jwtDecoder(jwkSource: JWKSource<SecurityContext>): JwtDecoder =
        OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource)

    /**
     * Spring's default generator never issues a refresh token to a public client on the authorization code grant.
     * The SPA is a public client and must be able to renew its session, so refresh tokens are generated here for
     * any client that has the grant. The client is configured to rotate them (each use returns a new one and kills the old).
     */
    @Bean
    fun tokenGenerator(
        jwkSource: JWKSource<SecurityContext>,
        customizer: OAuth2TokenCustomizer<JwtEncodingContext>,
    ): OAuth2TokenGenerator<out OAuth2Token> {
        val jwtGenerator = JwtGenerator(NimbusJwtEncoder(jwkSource)).apply { setJwtCustomizer(customizer) }
        return DelegatingOAuth2TokenGenerator(jwtGenerator, OAuth2AccessTokenGenerator(), AnyClientRefreshTokenGenerator())
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    companion object {
        const val SPA_CLIENT_ID = "spa"
        val ACCESS_TOKEN_TTL: Duration = Duration.ofMinutes(15)
        val REFRESH_TOKEN_TTL: Duration = Duration.ofMinutes(60)
    }
}
