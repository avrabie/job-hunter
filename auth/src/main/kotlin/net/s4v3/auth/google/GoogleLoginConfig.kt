package net.s4v3.auth.google

import net.s4v3.auth.config.AuthProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.core.type.AnnotatedTypeMetadata
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository

/** True only when both Google settings are non-blank, so empty placeholders from the environment do not count. */
class GoogleCredentialsPresent : Condition {
    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
        val env = context.environment
        return !env.getProperty("app.google.client-id").isNullOrBlank() &&
            !env.getProperty("app.google.client-secret").isNullOrBlank()
    }
}

/**
 * Registers Google as the only OAuth2 login provider, and only when credentials are configured. The redirect URI
 * is built from the request (`{baseUrl}/login/oauth2/code/google`), so register
 * `<public issuer>/login/oauth2/code/google` in the Google console.
 */
@Configuration(proxyBeanMethods = false)
@Conditional(GoogleCredentialsPresent::class)
class GoogleLoginConfig(private val properties: AuthProperties) {

    @Bean
    fun clientRegistrationRepository(): ClientRegistrationRepository =
        InMemoryClientRegistrationRepository(
            CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(properties.google.clientId!!)
                .clientSecret(properties.google.clientSecret!!)
                .build(),
        )
}
