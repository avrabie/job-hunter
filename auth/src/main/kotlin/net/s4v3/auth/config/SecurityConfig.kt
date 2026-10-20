package net.s4v3.auth.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.FactorGrantedAuthority
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler
import org.springframework.security.web.savedrequest.HttpSessionRequestCache
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher
import java.time.Instant
import net.s4v3.auth.token.PublicClientRefreshConverter
import net.s4v3.auth.token.PublicClientRefreshProvider
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class SecurityConfig {

    /** Authorization server endpoints (authorize, token, JWKS, discovery, userinfo, logout). */
    @Bean
    @Order(1)
    fun authorizationServerChain(
        http: HttpSecurity,
        jwtDecoder: JwtDecoder,
        clients: RegisteredClientRepository,
    ): SecurityFilterChain {
        http
            .oauth2AuthorizationServer { server ->
                http.securityMatcher(server.endpointsMatcher)
                server.oidc(Customizer.withDefaults())
                // The SPA is a public client; let it present its refresh token with only its client_id.
                server.clientAuthentication {
                    it.authenticationConverter(PublicClientRefreshConverter())
                    it.authenticationProvider(PublicClientRefreshProvider(clients))
                }
            }
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            // userinfo is called with a bearer token issued by this server
            .oauth2ResourceServer { it.jwt { jwt -> jwt.decoder(jwtDecoder) } }
            // Unauthenticated browser requests go to the login page. The path is relative on purpose: the redirect
            // strategy prepends the context path (/auth) itself.
            .exceptionHandling {
                it.defaultAuthenticationEntryPointFor(
                    LoginUrlAuthenticationEntryPoint("/login"),
                    MediaTypeRequestMatcher(MediaType.TEXT_HTML),
                )
            }
        return http.build()
    }

    /** Self-service endpoints for signed-in users, protected by tokens issued by this server. */
    @Bean
    @Order(2)
    fun accountApiChain(http: HttpSecurity, jwtDecoder: JwtDecoder): SecurityFilterChain {
        http
            .securityMatcher("/api/me/**")
            .csrf(AbstractHttpConfigurer<*, *>::disable)
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .oauth2ResourceServer { it.jwt { jwt -> jwt.decoder(jwtDecoder) } }
        return http.build()
    }

    /** Login page, open JSON APIs, probes and (when configured) Google login. */
    @Bean
    @Order(3)
    fun defaultChain(
        http: HttpSecurity,
        clientRegistrations: ObjectProvider<ClientRegistrationRepository>,
    ): SecurityFilterChain {
        http
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.POST, *OPEN_POSTS).permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/health/**").permitAll()
                    .requestMatchers("/error").permitAll()
                    .anyRequest().authenticated()
            }
            // These POSTs act on no session, so there is no ambient authority for a forged request to ride on.
            .csrf { it.ignoringRequestMatchers(*OPEN_POSTS) }
            .formLogin { it.successHandler(loginSuccessHandler()) }
        if (clientRegistrations.ifAvailable != null) {
            http.oauth2Login { login ->
                login.redirectionEndpoint { it.baseUri("/login/oauth2/code/*") }
                login.userInfoEndpoint { it.userAuthoritiesMapper(authorizationCodeFactorMapper()) }
            }
        }
        return http.build()
    }

    /** Spring Security 7 tracks how a user authenticated; mark OAuth2 logins as an authorization-code factor. */
    private fun authorizationCodeFactorMapper() = GrantedAuthoritiesMapper { authorities ->
        authorities.toMutableSet<GrantedAuthority>().apply {
            add(
                FactorGrantedAuthority.withAuthority(FactorGrantedAuthority.AUTHORIZATION_CODE_AUTHORITY)
                    .issuedAt(Instant.now())
                    .build(),
            )
        }
    }

    /**
     * After form login, resume the saved authorization request. Without one (someone opened /auth/login directly)
     * the default fallback would be /auth/, which nothing maps; send the browser to the host root instead (303, so
     * the POST is not repeated).
     */
    private fun loginSuccessHandler(): AuthenticationSuccessHandler {
        val requestCache = HttpSessionRequestCache()
        return object : SavedRequestAwareAuthenticationSuccessHandler() {
            override fun onAuthenticationSuccess(
                request: HttpServletRequest,
                response: HttpServletResponse,
                authentication: Authentication,
            ) {
                if (requestCache.getRequest(request, response) != null) {
                    super.onAuthenticationSuccess(request, response, authentication)
                    return
                }
                response.status = HttpServletResponse.SC_SEE_OTHER
                response.setHeader(HttpHeaders.LOCATION, "/")
            }
        }
    }

    private companion object {
        val OPEN_POSTS = arrayOf(
            "/api/register",
            "/api/password-reset/request",
            "/api/password-reset/confirm",
        )
    }
}
