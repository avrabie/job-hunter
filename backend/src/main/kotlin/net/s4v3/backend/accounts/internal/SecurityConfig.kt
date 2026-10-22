package net.s4v3.backend.accounts.internal

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain

/**
 * The backend is an OAuth2 resource server: it validates JWTs issued by the auth service
 * (issuer URI and signing keys discovered lazily through Spring Boot's `issuer-uri` support).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableMethodSecurity
internal class SecurityConfig {

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtAuthenticationConverter: JwtAuthenticationConverter,
        provisioner: UserProvisioner,
        @Value($$"${jobhunter.api-docs.enabled:true}") apiDocsEnabled: Boolean,
    ): SecurityFilterChain =
        http
            .csrf(AbstractHttpConfigurer<*, *>::disable)
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.GET, "/actuator/health/**").permitAll()
                // The API description holds shapes only, no data, so it is readable without a token (reads only).
                if (apiDocsEnabled) {
                    it.requestMatchers(HttpMethod.GET, "/api/openapi.json", "/api/openapi.json/swagger-config", "/api/docs", "/api/docs/**", "/api/swagger-ui/**").permitAll()
                }
                // Every path under /api/admin needs the ADMIN role, so a new admin endpoint is protected by default.
                it.requestMatchers("/api/admin/**").hasRole("ADMIN")
                it.anyRequest().authenticated()
            }
            .oauth2ResourceServer { it.jwt { jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter) } }
            .addFilterAfter(UserProvisioningFilter(provisioner), BearerTokenAuthenticationFilter::class.java)
            .httpBasic(AbstractHttpConfigurer<*, *>::disable)
            .formLogin(AbstractHttpConfigurer<*, *>::disable)
            .build()

    /** Maps the `roles` claim (names without prefix, e.g. `USER`) to `ROLE_*` authorities. */
    @Bean
    fun jwtAuthenticationConverter(@Value($$"${app.security.roles-claim:roles}") rolesClaim: String): JwtAuthenticationConverter {
        val authorities = JwtGrantedAuthoritiesConverter().apply {
            setAuthoritiesClaimName(rolesClaim)
            setAuthorityPrefix("ROLE_")
        }
        return JwtAuthenticationConverter().apply { setJwtGrantedAuthoritiesConverter(authorities) }
    }
}
